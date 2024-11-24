package su.plo.voice.paper

import org.bstats.bukkit.Metrics
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import su.plo.slib.spigot.SpigotServerLib
import su.plo.voice.paper.integration.SayanVanishIntegration
import su.plo.voice.paper.integration.SuperVanishIntegration
import su.plo.voice.paper.integration.VoicePlaceholder
import su.plo.voice.server.BaseVoiceServer
import su.plo.voice.util.version.ModrinthLoader

class PaperVoiceServer(
    private val plugin: JavaPlugin
) : BaseVoiceServer(ModrinthLoader.PAPER), Listener {

    private val minecraftServerLib = SpigotServerLib(plugin)

    private lateinit var metrics: Metrics

    public override fun onInitialize() {
        minecraftServerLib.onInitialize()

        super.onInitialize()

        minecraftServerLib.players.forEach { player ->
            playerManager.getPlayerById(player.uuid)
                .ifPresent { voicePlayer ->
                    if (player.registeredChannels.contains(CHANNEL_STRING)) {
                        tcpPacketManager.requestPlayerInfo(voicePlayer)
                    }
                }
        }

        this.metrics = Metrics(plugin, BSTATS_PROJECT_ID)

        // Initialize integrations
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            VoicePlaceholder(this).register()
        }

        if (Bukkit.getPluginManager().getPlugin("SuperVanish") != null ||
            Bukkit.getPluginManager().getPlugin("PremiumVanish") != null
        ) {
            plugin.server.pluginManager.registerEvents(SuperVanishIntegration(this), plugin)
            LOGGER.info("SuperVanish event listener attached")
        }

        if (Bukkit.getPluginManager().getPlugin("SayanVanish") != null
        ) {
            plugin.server.pluginManager.registerEvents(SayanVanishIntegration(this), plugin)
            LOGGER.info("SayanVanish event listener attached")
        }
    }

    public override fun onShutdown() {
        metrics.shutdown()
        super.onShutdown()
        minecraftServerLib.onShutdown()
    }

    override fun getConfigFolder() = plugin.dataFolder

    override fun getMinecraftServer() = minecraftServerLib
}
