package su.plo.voice.client.gui.settings.widget;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import su.plo.lib.mod.client.gui.widget.GuiWidgetTexture;
import su.plo.slib.api.chat.component.McTextComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.lib.mod.client.gui.components.AbstractSlider;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.lib.mod.client.gui.components.IconButton;
import su.plo.lib.mod.client.render.RenderUtil;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.audio.device.DeviceManager;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.util.AudioUtil;
import su.plo.voice.client.event.gui.MicrophoneTestStartedEvent;
import su.plo.voice.client.event.gui.MicrophoneTestStoppedEvent;
import su.plo.voice.client.gui.settings.MicrophoneTestController;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;

import java.util.List;

public final class ActivationThresholdWidget extends AbstractSlider implements UpdatableWidget {

    private static final ResourceLocation STOP_ICON = ResourceLocation.tryParse("plasmovoice:textures/icons/speaker_menu.png");
    private static final ResourceLocation START_ICON = ResourceLocation.tryParse("plasmovoice:textures/icons/speaker_menu_disabled.png");
    private static final McTextComponent NOT_AVAILABLE = McTextComponent.translatable("gui.plasmovoice.devices.not_available");

    private final DoubleConfigEntry entry;
    private final MicrophoneTestController controller;

    private final List<Button> microphoneTest;

    public ActivationThresholdWidget(@NotNull VoiceSettingsScreen parent,
                                     @NotNull DoubleConfigEntry entry,
                                     @NotNull AudioCapture audioCapture,
                                     @NotNull DeviceManager devices,
                                     @NotNull MicrophoneTestController controller,
                                     int x,
                                     int y,
                                     int width,
                                     int height) {
        super(x, y, width, height);

        this.entry = entry;
        this.controller = controller;

        IconButton testStop = new IconButton(
                0,
                8,
                20,
                20,
                button -> controller.stop(),
                Button.NO_TOOLTIP,
                STOP_ICON,
                true
        );

        IconButton testStart = new IconButton(
                0,
                20,
                20,
                20,
                button -> controller.start(),
                (button, matrices, mouseX, mouseY) -> {
                    if (!button.isActive()) {
                        parent.setTooltip(NOT_AVAILABLE);
                    }
                },
                START_ICON,
                true
        );


        testStop.setVisible(controller.isActive());
        testStart.setVisible(!controller.isActive());
        testStart.setActive(
                audioCapture.getDevice()
                        .map(InputDevice::isOpen)
                        .orElse(false) &&
                        devices.getOutputDevice().isPresent()
        );
        this.microphoneTest = ImmutableList.of(testStop, testStart);

        updateValue();
    }

    @Override
    protected void updateText() {
        this.text = McTextComponent.literal(String.format("%.0f dB", AudioUtil.doubleRangeToAudioLevel(value)));
    }

    @Override
    protected void applyValue() {
        entry.set(AudioUtil.doubleRangeToAudioLevel(value));
    }

    @Override
    public void updateValue() {
        this.value = AudioUtil.audioLevelToDoubleRange(entry.value());
        updateText();
    }

    @Override
    public boolean isHoveredOrFocused() {
        return super.isHoveredOrFocused() && active;
    }

    @Override
    public void renderButton(@NotNull PoseStack stack, int mouseX, int mouseY, float delta) {
        renderBackground(stack, mouseX, mouseY);

        renderMicrophoneValue(stack, getSliderWidth(), delta);
        renderTrack(stack, mouseX, mouseY);
        renderText(stack, mouseX, mouseY);
    }

    public List<Button> getButtons() {
        return microphoneTest;
    }

    private void renderMicrophoneValue(@NotNull PoseStack stack, int sliderWidth, float delta) {
        if (controller.getMicrophoneValue() > 0.95D) {
            RenderSystem.setShaderColor(1F, 0F, 0F, alpha);
        } else if (controller.getMicrophoneValue() > 0.7D) {
            RenderSystem.setShaderColor(1F, 1F, 0F, alpha);
        } else {
            RenderSystem.setShaderColor(0F, 1F, 0F, alpha);
        }

        GuiWidgetTexture sprite = GuiWidgetTexture.BUTTON_DISABLED;
        RenderUtil.bindTexture(0, sprite.getLocation());
        RenderUtil.blitSprite(stack, sprite, x + 1, y + 1, 1, 1, (int) ((sliderWidth - 2) * controller.getMicrophoneValue()), height - 2);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        controller.tick(delta);
    }

    @EventSubscribe
    public void onTestStarted(@NotNull MicrophoneTestStartedEvent event) {
        microphoneTest.get(0).setVisible(true);
        microphoneTest.get(1).setVisible(false);
    }

    @EventSubscribe
    public void onTestStopped(@NotNull MicrophoneTestStoppedEvent event) {
        microphoneTest.get(0).setVisible(false);
        microphoneTest.get(1).setVisible(true);
    }
}
