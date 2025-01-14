package su.plo.voice.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.event.render.HudRenderEvent;

//#if MC>=12100
//$$ import net.minecraft.client.DeltaTracker;
//#endif

//#if MC>=12000
//$$ import net.minecraft.client.gui.GuiGraphics;
//#endif

public final class ModHudRenderer extends ModRenderer {

    public ModHudRenderer(@NotNull PlasmoVoiceClient voiceClient) {
        super(voiceClient);
    }

    //#if MC>=12100
    //$$ public void render(@NotNull GuiGraphics graphics, DeltaTracker delta) {
    //$$     render(graphics, delta.getRealtimeDeltaTicks());
    //$$ }
    //$$
    //$$ public void render(@NotNull GuiGraphics graphics, float partialTicks) {
    //$$     voiceClient.getEventBus().fire(new HudRenderEvent(graphics.pose(), partialTicks));
    //$$ }
    //#elseif MC>=12000
    //$$ public void render(@NotNull GuiGraphics graphics, float delta) {
    //$$     voiceClient.getEventBus().fire(new HudRenderEvent(graphics.pose(), delta));
    //$$ }
    //#else
    public void render(@NotNull PoseStack poseStack, float delta) {
        voiceClient.getEventBus().fire(new HudRenderEvent(poseStack, delta));
    }
    //#endif
}
