package su.plo.voice.client.audio.device.source

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.lwjgl.openal.AL11
import org.lwjgl.system.MemoryUtil
import su.plo.voice.BaseVoice
import su.plo.voice.api.client.PlasmoVoiceClient
import su.plo.voice.api.client.audio.device.DeviceException
import su.plo.voice.api.client.audio.device.source.AlSource
import su.plo.voice.api.client.event.audio.device.source.*
import su.plo.voice.api.client.time.TimeSupplier
import su.plo.voice.api.util.AudioUtil
import su.plo.voice.client.audio.AlUtil
import su.plo.voice.client.audio.device.AlOutputDevice
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class StreamAlSource private constructor(
    client: PlasmoVoiceClient,
    device: AlOutputDevice,
    stereo: Boolean,
    numBuffers: Int,
    pointer: Int
) : BaseAlSource(client, device, stereo, pointer) {

    private val timeSupplier: TimeSupplier
        get() = client.timeSupplier

    private var closeTimeoutMs = 25000L

    private val numBuffers: Int
    private val queue = LinkedBlockingQueue<ByteBuffer>()
    private val isStreaming = AtomicBoolean(false)
    private val emptyBuffer: ByteArray

    private var job: Job? = null
    private lateinit var buffers: IntArray
    private val availableBuffer = IntArray(1)
    private val emptyFilled = AtomicBoolean(false)
    private var lastBufferTime: Long = 0

    init {
        this.numBuffers = if (numBuffers == 0) client.config.advanced.alPlaybackBuffers.value() else numBuffers
        this.emptyBuffer = ByteArray(device.frameSize)
    }

    override fun play() {
        AlUtil.checkDeviceContext(device)

        if (!client.eventBus.fire(AlSourcePlayEvent(this))) return

        val isStreaming = isStreaming.get()
        val state = state

        if (isStreaming && state == AlSource.State.PAUSED) {
            AL11.alSourcePlay(pointer)
            AlUtil.checkErrors("Source play")
            return
        } else if (isStreaming) {
            return
        }

        startStreamThread()
    }

    override fun stop() {
        AlUtil.checkDeviceContext(device)

        AlSourceStopEvent(this).also {
            if (!client.eventBus.fire(it)) return
        }

        AL11.alSourceStop(pointer)
        AlUtil.checkErrors("Source stop")
        isStreaming.set(false)

        queue.clear()
    }

    override fun pause() {
        AlUtil.checkDeviceContext(device)

        AlSourcePauseEvent(this).also {
            if (!client.eventBus.fire(it)) return
        }

        AL11.alSourcePause(pointer)
        AlUtil.checkErrors("Source pause")
    }

    override fun setCloseTimeoutMs(timeoutMs: Long) {
        this.closeTimeoutMs = timeoutMs
    }

    override fun write(samples: ShortArray, applyFilters: Boolean) {
        val processedSamples = if (applyFilters) {
            device.processFilters(samples)
        } else {
            samples
        }

        write(AudioUtil.shortsToBytes(processedSamples))
    }

    override fun write(samples: ByteArray) {
        if (!isStreaming.get()) return
        if (samples.isEmpty()) {
            write(emptyBuffer)
            return
        }

        if (queue.size > 100) {
            BaseVoice.DEBUG_LOGGER.log("Queue overflow, dropping samples")
            return
        }

        val writeEvent = AlSourceWriteEvent(this, samples)
        if (!client.eventBus.fire(writeEvent)) return

        val newSamples = writeEvent.samples

        val buffer = MemoryUtil.memAlloc(newSamples.size)
        buffer.put(newSamples)
        (buffer as Buffer).flip()

        queue.offer(buffer)
        if (newSamples != emptyBuffer) {
            emptyFilled.set(false)
            lastBufferTime = timeSupplier.currentTimeMillis
        }
    }

    override fun clearBuffer() {
        if (queue.isEmpty()) return
        queue.clear()
    }

    override suspend fun close() {
        if (!isStreaming.get()) return
        device.runInContext {
            closeSync()
        }
    }

    override fun closeAsync(): CompletableFuture<Void?> {
        if (!isStreaming.get()) return CompletableFuture.completedFuture(null)

        return device.coroutineScope.future {
            closeSync()
            null
        }
    }

    private fun closeSync() {
        stop()

        // to my future self:
        // don't join job coroutine here,
        // because it can be invoked from the job itself
        // and cause a deadlock
        // (I've done it twice already pepega)
        // job?.join()

        // play a source if its state is initial, so a source can be deleted properly
        if (state == AlSource.State.INITIAL) {
            AL11.alSourcePlay(pointer)
            AlUtil.checkErrors("Source play")

            AL11.alSourceStop(pointer)
            AlUtil.checkErrors("Source stop")
        }

        client.eventBus.fire(AlSourceClosedEvent(this@StreamAlSource))

        removeProcessedBuffers()

        AL11.alDeleteBuffers(buffers)
        AlUtil.checkErrors("Delete buffers")

        AL11.alDeleteSources(intArrayOf(pointer))
        AlUtil.checkErrors("Delete source")

        pointer = 0
    }

    private fun startStreamThread() {
        isStreaming.set(true)
        val alSource = this

        job = device.coroutineScope.launch {
            buffers = IntArray(numBuffers)
            AL11.alGenBuffers(buffers)
            AlUtil.checkErrors("Source gen buffers")

            queueWithEmptyBuffers()
            fillQueue()

            lastBufferTime = timeSupplier.currentTimeMillis
            availableBuffer[0] = -1

            while (isStreaming.get()) {
                val queueSize = queue.size

                var processedBuffers = getInt(AL11.AL_BUFFERS_PROCESSED)
                AlUtil.checkErrors("Get processed buffers")

                while (processedBuffers > 0 || availableBuffer[0] != -1) {
                    if (availableBuffer[0] == -1) {
                        AL11.alSourceUnqueueBuffers(pointer, availableBuffer)
                        AlUtil.checkErrors("Unqueue buffer")

                        // Bits can be 0 if the format or parameters are corrupt, avoid division by zero
                        val bits = AL11.alGetBufferi(availableBuffer[0], AL11.AL_BITS)
                        AlUtil.checkErrors("Source get buffer int")
                        if (bits == 0) {
                            LOGGER.warn("Corrupted stream")
                            continue
                        }

                        if (availableBuffer[0] != -1) {
                            val unqueuedEvent = AlSourceBufferUnqueuedEvent(alSource, availableBuffer[0])
                            client.eventBus.fire(unqueuedEvent)
                        }
                    }

                    if (availableBuffer[0] != -1 && fillAndPushBuffer(availableBuffer[0])) {
                        availableBuffer[0] = -1
                        processedBuffers--
                    } else {
                        break
                    }
                }

                val state = state
                if (state == AlSource.State.STOPPED && queueSize == 0 && !emptyFilled.get()) {
                    removeProcessedBuffers()
                    availableBuffer[0] = -1

                    queueWithEmptyBuffers()
                    fillQueue()

                    client.eventBus.fire(AlStreamSourceStoppedEvent(alSource))
                    continue
                } else if (state != AlSource.State.PLAYING && state != AlSource.State.PAUSED && queueSize > 0) {
                    AL11.alSourcePlay(pointer)
                    AlUtil.checkErrors("Source play")
                    continue
                } else if (state == AlSource.State.INITIAL) {
                    AL11.alSourcePlay(pointer)
                    AlUtil.checkErrors("Source play")
                    continue
                }

                if (closeTimeoutMs > 0L && timeSupplier.currentTimeMillis - lastBufferTime > closeTimeoutMs) {
                    BaseVoice.DEBUG_LOGGER.log("Stream timed out. Closing...")
                    close()
                    break
                }

                delay(5L)
            }
        }
    }

    private fun queueWithEmptyBuffers() {
        for (i in 0 until numBuffers) {
            write(emptyBuffer)
        }
        emptyFilled.set(true)
    }

    private fun fillQueue() {
        for (i in 0 until numBuffers) {
            fillAndPushBuffer(buffers[i])
        }
    }

    private fun fillAndPushBuffer(buffer: Int): Boolean {
        val byteBuffer = queue.poll() ?: return false

        AL11.alBufferData(buffer, format, byteBuffer, device.format.sampleRate.toInt())
        if (AlUtil.checkErrors("Assigning buffer data")) return false

        AL11.alSourceQueueBuffers(pointer, intArrayOf(buffer))
        if (AlUtil.checkErrors("Queue buffer data")) return false

        client.eventBus.fire(AlSourceBufferQueuedEvent(this, byteBuffer, buffer))

        return true
    }

    private fun removeProcessedBuffers() {
        var processedBuffers = getInt(AL11.AL_BUFFERS_PROCESSED)
        AlUtil.checkErrors("Get processed buffers")

        while (processedBuffers > 0) {
            val buffer = IntArray(1)

            AL11.alSourceUnqueueBuffers(pointer, buffer)
            AlUtil.checkErrors("Unqueue buffer")

            processedBuffers--
        }
    }

    companion object {

        private val LOGGER = LogManager.getLogger(StreamAlSource::class.java)

        @JvmStatic
        fun create(device: AlOutputDevice, client: PlasmoVoiceClient, stereo: Boolean, numBuffers: Int): AlSource {
            AlUtil.checkDeviceContext(device)

            val pointer = IntArray(1)
            AL11.alGenSources(pointer)
            if (AlUtil.checkErrors("Allocate new source")) {
                throw DeviceException("Failed to allocate new source")
            }

            return StreamAlSource(client, device, stereo, numBuffers, pointer[0]).also { source ->
                client.eventBus.fire(AlSourceCreatedEvent(source))
            }
        }
    }
}
