package mod.adrenix.nostalgic.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Matrix4f;
import mod.adrenix.nostalgic.client.config.DefaultConfig;
import mod.adrenix.nostalgic.client.config.MixinConfig;
import mod.adrenix.nostalgic.util.MixinUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin
{
    /* Shadows & Helpers */

    private static boolean isStarRunnableSaved = false;
    private static boolean isBlueRunnableSaved = false;

    @Shadow protected abstract void createStars();

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderBuffers renderBuffers;
    @Unique @Nullable private VertexBuffer blueBuffer;

    @Unique
    private void createBlueBuffer()
    {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        if (this.blueBuffer != null)
            this.blueBuffer.close();
        this.blueBuffer = new VertexBuffer();

        float height = switch(MixinConfig.Candy.getBlueVoid())
                {
                    case ALPHA -> -32.0F;
                    case BETA, MODERN -> -48.0F;
                };

        MixinUtil.World.buildSkyDisc(builder, height);
        this.blueBuffer.upload(builder);
    }

    /* Injections */

    /**
     * Used to instantiate the vertex buffer for the old blue void color.
     */
    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/RenderBuffers;)V", at = @At(value = "TAIL"))
    protected void onInitLevelRenderer(Minecraft minecraft, RenderBuffers renderBuffers, CallbackInfo callback)
    {
        this.createBlueBuffer();

        if (!isStarRunnableSaved)
        {
            isStarRunnableSaved = true;
            MixinUtil.Cache.onSave.add(this::createStars);
        }

        if (!isBlueRunnableSaved)
        {
            isBlueRunnableSaved = true;
            MixinUtil.Cache.onSave.add(this::createBlueBuffer);
        }
    }

    /**
     * Caches the model view matrix and the projection matrix.
     * This is done so the stars and sky can be overlaid with the blue void correctly.
     */
    @Inject(method = "renderSky", at = @At(value = "HEAD"))
    protected void onCacheSkyPose(PoseStack poseStack, Matrix4f matrix4f, float f, Camera camera, boolean bl, Runnable runnable, CallbackInfo callback)
    {
        MixinUtil.World.blueModelView = poseStack.last().pose().copy();
        MixinUtil.World.blueProjection = matrix4f.copy();
    }

    /**
     * Brings back the old blue void color.
     * This buffer needs to be rendered after the sky buffer and before the dark buffer.
     * Controlled by the old blue void color toggle.
     */
    @Inject(method = "renderSky", at = @At(shift = At.Shift.AFTER, value = "INVOKE", ordinal = 1, target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    protected void onDrawSkyBuffer(PoseStack poseStack, Matrix4f matrix4f, float f, Camera camera, boolean bl, Runnable runnable, CallbackInfo callback)
    {
        if (MixinConfig.Candy.getBlueVoid() != DefaultConfig.VERSION.MODERN)
        {
            MixinUtil.World.setBlueVoidColor();
            ShaderInstance shader = RenderSystem.getShader();

            if (this.blueBuffer != null && shader != null)
                this.blueBuffer.drawWithShader(MixinUtil.World.blueModelView, MixinUtil.World.blueProjection, shader);
        }
    }

    /**
     * Disables the rendering of the dark void if the blue void is enabled and its respective override is enabled.
     * Controlled by both old blue void and old blue void override toggles.
     */
    @Redirect(method = "renderSky", at = @At(value = "INVOKE", ordinal = 2, target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    protected void onRenderDarkVoid(VertexBuffer instance, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, ShaderInstance shaderInstance)
    {
        DefaultConfig.VERSION voidState = MixinConfig.Candy.getBlueVoid();
        boolean isBlueRendered = voidState == DefaultConfig.VERSION.ALPHA || voidState == DefaultConfig.VERSION.BETA;
        boolean isDarkOverride = MixinConfig.Candy.oldBlueVoidOverride();

        if (!isBlueRendered || !isDarkOverride)
            instance.drawWithShader(modelViewMatrix, projectionMatrix, shaderInstance);
    }

    /**
     * Used to cache the current level pose stack which is needed for disabling diffused lighting.
     */
    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    protected void onStartLevelRendering(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo callback)
    {
        MixinUtil.Item.levelPoseStack = poseStack.last();
        MixinUtil.Item.levelBufferSource = this.renderBuffers.bufferSource();
    }

    /**
     * Brings back the old cloud height level at y = 108.
     * Controlled by the old cloud height toggle.
     */
    @Redirect(method = "renderClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    protected float onGetCloudHeight(DimensionSpecialEffects instance)
    {
        if (this.minecraft.level != null && this.minecraft.level.dimension() == Level.OVERWORLD)
            return MixinConfig.Candy.getCloudHeight();
        return instance.getCloudHeight();
    }

    /**
     * Change the rotation of the sunrise/sunset sky disc color on ZP by 90 degrees.
     * Controlled by the old at north sunrise toggle.
     */
    @ModifyArg(method = "renderSky", at = @At(value = "INVOKE", ordinal = 2, target = "Lcom/mojang/math/Vector3f;rotationDegrees(F)Lcom/mojang/math/Quaternion;"))
    protected float onRenderSkyDiscColor(float vanilla) { return MixinUtil.World.getSunriseRotation(vanilla); }

    /**
     * Change the rotation of the sun/moon renderer on YP by 90 degrees.
     * Controlled by the old at north sunrise toggle.
     */
    @ModifyArg(method = "renderSky", at = @At(value = "INVOKE", ordinal = 3, target = "Lcom/mojang/math/Vector3f;rotationDegrees(F)Lcom/mojang/math/Quaternion;"))
    protected float onRenderSun(float vanilla) { return MixinUtil.World.getSunriseRotation(vanilla); }

    /**
     * Renders the old big stars in the skybox.
     * Controlled by the old stars toggle.
     */
    @ModifyConstant(method = "drawStars", constant = @Constant(floatValue = 0.15F))
    private float onDrawStarsWidth(float vanilla)
    {
        return MixinConfig.Candy.oldStars() ? 0.25F : 0.15F;
    }

    @ModifyConstant(method = "drawStars", constant = @Constant(floatValue = 0.1F))
    private float onDrawStarsHeight(float vanilla)
    {
        return MixinConfig.Candy.oldStars() ? 0.25F : vanilla;
    }
}
