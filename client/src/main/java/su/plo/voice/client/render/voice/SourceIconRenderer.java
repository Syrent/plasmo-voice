package su.plo.voice.client.render.voice;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.NonNull;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import org.jetbrains.annotations.NotNull;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.lib.mod.client.render.RenderUtil;
import su.plo.lib.mod.client.render.VertexBuilder;
import su.plo.lib.mod.client.render.VertexFormatMode;
import su.plo.lib.mod.extensions.PoseStackKt;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.slib.api.position.Pos3d;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.line.ClientSourceLine;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.api.client.connection.ServerConnection;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.client.audio.source.ClientStaticSource;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.event.LivingEntityRenderEvent;
import su.plo.voice.client.event.render.LevelRenderEvent;
import su.plo.voice.client.gui.PlayerVolumeAction;
import su.plo.voice.client.render.ModCamera;
import su.plo.voice.proto.data.audio.source.EntitySourceInfo;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.proto.data.audio.source.StaticSourceInfo;
import su.plo.voice.proto.data.player.VoicePlayerInfo;

import java.util.Collection;
import java.util.Optional;

import static su.plo.slib.mod.extension.ScoreboardKt.getObjectiveBelowName;
import static su.plo.voice.client.extension.MathKt.toVec3;

public final class SourceIconRenderer {

    private final PlasmoVoiceClient voiceClient;
    private final VoiceClientConfig config;
    private final PlayerVolumeAction volumeAction;

    public SourceIconRenderer(
            @NotNull PlasmoVoiceClient voiceClient,
            @NotNull VoiceClientConfig config,
            @NotNull PlayerVolumeAction volumeAction
    ) {
        this.voiceClient = voiceClient;
        this.config = config;
        this.volumeAction = volumeAction;

        LivingEntityRenderEvent.INSTANCE.registerListener(this::onLivingEntityRender);
    }

    @EventSubscribe
    public void onLevelRender(@NotNull LevelRenderEvent event) {
        if (isIconHidden() ||
                !config.getOverlay().getShowStaticSourceIcons().value()
        ) return;

        for (ClientAudioSource<?> source : voiceClient.getSourceManager().getSources()) {
            if (!(source.getSourceInfo() instanceof StaticSourceInfo)
                    || !source.getSourceInfo().isIconVisible()
                    || !source.isActivated()
            ) continue;

            Optional<ClientSourceLine> sourceLine = voiceClient.getSourceLineManager()
                    .getLineById(source.getSourceInfo().getLineId());
            if (!sourceLine.isPresent()) return;

            ClientStaticSource staticSource = (ClientStaticSource) source;
            Pos3d sourcePosition = staticSource.getSourceInfo().getPosition();

            renderStatic(
                    event.getStack(),
                    event.getCamera(),
                    event.getLightSupplier().getLight(sourcePosition),
                    ResourceLocation.tryParse(sourceLine.get().getIcon()),
                    staticSource,
                    event.getDelta()
            );
        }
    }

    private void onLivingEntityRender(
            @NotNull LivingEntity entity,
            @NotNull PoseStack stack,
            int light,
            boolean shouldShowName
    ) {
        if (entity instanceof Player) {
            renderPlayer((Player) entity, stack, light, shouldShowName);
        } else {
            renderLivingEntity(entity, stack, light, shouldShowName);
        }
    }

    private void renderPlayer(
            @NotNull Player player,
            @NotNull PoseStack stack,
            int light,
            boolean shouldShowName
    ) {
        Optional<ServerConnection> connection = voiceClient.getServerConnection();
        if (!connection.isPresent()) return;

        LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) return;

        boolean isFakePlayer = !Minecraft.getInstance().getConnection().getOnlinePlayerIds().contains(player.getUUID());

        if (isIconHidden()
                || player.getUUID().equals(clientPlayer.getUUID())
                || isFakePlayer
                || player.isInvisibleTo(clientPlayer)
        ) return;

        boolean hasPercent = false;
        String iconLocation;

        Optional<VoicePlayerInfo> playerInfo = connection.get().getPlayerById(player.getUUID());
        if (!playerInfo.isPresent()) { // not installed
            iconLocation = "plasmovoice:textures/icons/headset_not_installed.png";
        } else if (config.getVoice().getVolumes().getMute("source_" + player.getUUID()).value()) { // client mute
            iconLocation = "plasmovoice:textures/icons/speaker_disabled.png";
        } else if (playerInfo.get().isMuted()) { // server mute
            iconLocation = "plasmovoice:textures/icons/speaker_muted.png";
        } else if (playerInfo.get().isVoiceDisabled()) { // client disabled voicechat
            iconLocation = "plasmovoice:textures/icons/headset_disabled.png";
        } else {
            Collection<ClientAudioSource<PlayerSourceInfo>> sources = voiceClient.getSourceManager()
                    .getPlayerSources(player.getUUID());

            hasPercent = volumeAction.isShown(player);
            if (hasPercent) {
                renderPercent(
                        player,
                        stack,
                        light,
                        shouldShowName
                );
            }

            if (sources.isEmpty()) return;

            ClientSourceLine highestSourceLine = getHighestActivatedSourceLine(sources);
            if (highestSourceLine == null) return;

            // speaking
            iconLocation = highestSourceLine.getIcon();
        }

        renderEntity(
                player,
                stack,
                light,
                shouldShowName,
                ResourceLocation.tryParse(iconLocation),
                hasPercent
        );
    }

    private void renderLivingEntity(
            @NotNull LivingEntity entity,
            @NotNull PoseStack stack,
            int light,
            boolean shouldShowName
    ) {
        Optional<ServerConnection> connection = voiceClient.getServerConnection();
        if (!connection.isPresent()) return;

        LocalPlayer clientPlayer = Minecraft.getInstance().player;
        if (clientPlayer == null) return;

        if (isIconHidden() || entity.isInvisibleTo(clientPlayer)) return;

        Collection<ClientAudioSource<EntitySourceInfo>> sources = voiceClient.getSourceManager()
                .getEntitySources(entity.getId());

        ClientSourceLine highestSourceLine = getHighestActivatedSourceLine(sources);
        if (highestSourceLine == null) return;

        renderEntity(
                entity,
                stack,
                light,
                shouldShowName,
                ResourceLocation.tryParse(highestSourceLine.getIcon()),
                false
        );
    }

    private void renderEntity(
            @NonNull Entity entity,
            @NonNull PoseStack stack,
            int light,
            boolean hasLabel,
            @NonNull ResourceLocation iconLocation,
            boolean hasPercent
    ) {
        Vec3 position = entity.position();

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double distance = camera.getPosition().distanceToSqr(position.x(), position.y(), position.z());
        if (distance > 4096D) return;

        stack.pushPose();

        if (hasPercent) stack.translate(0D, 0.3D, 0D);
        translateEntityMatrix(entity, camera, stack, distance, hasLabel);

        if (entity.isDescending()) {
            vertices(stack, 40, light, iconLocation, false);
        } else {
            vertices(stack, 255, light, iconLocation, false);
            vertices(stack, 40, light, iconLocation, true);
        }

        stack.popPose();
    }

    private void renderPercent(
            @NotNull Entity entity,
            @NonNull PoseStack stack,
            int light,
            boolean hasLabel
    ) {
        Vec3 position = entity.position();

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double distance = camera.getPosition().distanceToSqr(position.x(), position.y(), position.z());
        if (distance > 4096D) return;

        stack.pushPose();

        translateEntityMatrix(entity, camera, stack, distance, hasLabel);
        stack.translate(5D, 0D, 0D);

        // render percents
        DoubleConfigEntry volume = config.getVoice().getVolumes().getVolume("source_" + entity.getUUID());

        McTextComponent text = McTextComponent.literal((int) Math.round((volume.value() * 100D)) + "%");
        int backgroundColor = (int) (0.25F * 255.0F) << 24;
        int xOffset = -RenderUtil.getTextWidth(text) / 2;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);

        RenderUtil.fill(
                stack,
                xOffset - 1,
                -1,
                xOffset + RenderUtil.getTextWidth(text) + 1,
                8,
                backgroundColor
        );
        RenderUtil.drawStringLight(
                stack,
                text,
                xOffset,
                0,
                553648127,
                light,
                !entity.isDescending(),
                false
        );

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderUtil.drawStringLight(
                stack,
                text,
                xOffset,
                0,
                -1,
                light,
                false,
                false
        );

        RenderSystem.disableBlend();
        stack.popPose();
    }

    private void translateEntityMatrix(
            @NotNull Entity entity,
            @NonNull Camera camera,
            @NonNull PoseStack stack,
            double distance,
            boolean hasLabel
    ) {
        if (hasLabel) {
            stack.translate(0D, 0.3D, 0D);

            if (entity instanceof Player) {
                Player player = (Player) entity;

                Objective belowNameObjective = getObjectiveBelowName(player.getScoreboard());
                if (belowNameObjective != null && distance < 100D) {
                    stack.translate(0D, 0.3D, 0D);
                }
            }
        }

        stack.translate(0D, entity.getBbHeight() + 0.5D, 0D);
        PoseStackKt.rotate(stack, -camera.getYRot(), 0.0F, 1.0F, 0.0F);
        PoseStackKt.rotate(stack, camera.getXRot(), 1.0F, 0.0F, 0.0F);
        stack.scale(-0.025F, -0.025F, 0.025F);
        stack.translate(-5D, -1D, 0D);
    }

    private void renderStatic(
            @NonNull PoseStack stack,
            @NonNull ModCamera camera,
            int light,
            @NotNull ResourceLocation iconLocation,
            @NotNull ClientStaticSource staticSource,
            double delta
    ) {
        Pos3d position = staticSource.getSourceInfo().getPosition();
        Pos3d lastPosition = staticSource.getLastRenderPosition();

        if (position.distanceSquared(lastPosition) > 1D) {
            lastPosition.setX(position.getX());
            lastPosition.setY(position.getY());
            lastPosition.setZ(position.getZ());
        } else {
            lastPosition.setX(Mth.lerp(delta, lastPosition.getX(), position.getX()));
            lastPosition.setY(Mth.lerp(delta, lastPosition.getY(), position.getY()));
            lastPosition.setZ(Mth.lerp(delta, lastPosition.getZ(), position.getZ()));
        }

        double distanceToCamera = camera.position().distanceToSqr(toVec3(lastPosition));
        if (distanceToCamera > 4096D) return;

        stack.pushPose();

        stack.translate(
                lastPosition.getX() - camera.position().x,
                lastPosition.getY() - camera.position().y,
                lastPosition.getZ() - camera.position().z
        );
        PoseStackKt.rotate(stack, -camera.pitch(), 0.0F, 1.0F, 0.0F);
        PoseStackKt.rotate(stack, camera.yaw(), 1.0F, 0.0F, 0.0F);
        stack.scale(-0.025F, -0.025F, 0.025F);
        stack.translate(-5D, 0D, 0D);

        vertices(stack, 255, light, iconLocation, false);
        vertices(stack, 40, light, iconLocation, true);

        stack.popPose();
    }

    private void vertices(@NonNull PoseStack stack,
                          int alpha,
                          int light,
                          @NotNull ResourceLocation iconLocation,
                          boolean seeThrough) {
        if (seeThrough) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }

        RenderType renderType;
        if (seeThrough) {
            renderType = RenderType.textSeeThrough(iconLocation);
        } else {
            renderType = RenderType.text(iconLocation);
        }

        BufferBuilder buffer = RenderUtil.beginBufferWithDefaultShader(VertexFormatMode.QUADS, renderType.format());

        vertex(stack, buffer, 0F, 10F, 0F, 0F, 1F, alpha, light);
        vertex(stack, buffer, 10F, 10F, 0F, 1F, 1F, alpha, light);
        vertex(stack, buffer, 10F, 0F, 0F, 1F, 0F, alpha, light);
        vertex(stack, buffer, 0F, 0F, 0F, 0F, 0F, alpha, light);

        //#if MC>=12100
        //$$ renderType.draw(buffer.build());
        //#elseif MC>=12000
        //$$ renderType.end(buffer, RenderSystem.getVertexSorting());
        //#else
        renderType.end(buffer, 0, 0, 0);
        //#endif
    }

    private void vertex(@NonNull PoseStack stack,
                        @NonNull BufferBuilder buffer,
                        float x, float y, float z, float u, float v, int alpha, int light) {
        VertexBuilder.create(buffer)
                .position(stack, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .light(light)
                .normal(stack, 0F, 0F, -1F)
                .end();
    }

    private boolean isIconHidden() {
        int showIcons = config.getOverlay().getShowSourceIcons().value();
        return showIcons == 2 || (Minecraft.getInstance().options.hideGui && showIcons == 0);
    }

    private <T extends SourceInfo> ClientSourceLine getHighestActivatedSourceLine(@NotNull Collection<ClientAudioSource<T>> sources) {
        ClientSourceLine highestSourceLine = null;
        for (ClientAudioSource<?> source : sources) {
            if (!source.isActivated() || !source.getSourceInfo().isIconVisible()) continue;

            Optional<ClientSourceLine> sourceLine = voiceClient.getSourceLineManager()
                    .getLineById(source.getSourceInfo().getLineId());
            if (!sourceLine.isPresent()) continue;

            if (highestSourceLine == null ||
                    highestSourceLine.getWeight() < sourceLine.get().getWeight()
            ) {
                highestSourceLine = sourceLine.get();
            }
        }

        return highestSourceLine;
    }
}
