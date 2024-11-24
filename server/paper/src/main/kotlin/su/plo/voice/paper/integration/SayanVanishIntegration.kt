package su.plo.voice.paper.integration

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.sayandev.sayanvanish.bukkit.api.event.BukkitUserUnVanishEvent
import org.sayandev.sayanvanish.bukkit.api.event.BukkitUserVanishEvent
import su.plo.voice.api.server.PlasmoVoiceServer

class SayanVanishIntegration(
    private val voiceServer: PlasmoVoiceServer
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerHide(event: BukkitUserVanishEvent) {
        if (event.isCancelled) return
        val user = event.user
        val player = Bukkit.getPlayer(user.uniqueId) ?: return
        val voicePlayer = voiceServer.playerManager.getPlayerByInstance(player)
        if (!voicePlayer.hasVoiceChat()) return

        voiceServer.tcpPacketManager.broadcastPlayerDisconnect(voicePlayer)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerShow(event: BukkitUserUnVanishEvent) {
        if (event.isCancelled) return
        val user = event.user
        val player = Bukkit.getPlayer(user.uniqueId) ?: return
        val voicePlayer = voiceServer.playerManager.getPlayerByInstance(player)
        if (!voicePlayer.hasVoiceChat()) return

        voiceServer.tcpPacketManager.broadcastPlayerInfoUpdate(voicePlayer)
    }
}
