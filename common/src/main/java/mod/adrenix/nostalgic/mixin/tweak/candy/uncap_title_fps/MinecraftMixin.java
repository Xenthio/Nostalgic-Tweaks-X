package mod.adrenix.nostalgic.mixin.tweak.candy.uncap_title_fps;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin
{
    @Shadow
    public abstract Window getWindow();

    /**
     * Uncaps the framerate limit imposed on the title screen.
     */
    @ModifyReturnValue(
        method = "getFramerateLimit",
        at = @At("RETURN")
    )
    private int NT$onGetFramerateLimit(int framerate)
    {
        // TODO: Implement tweak control
        return this.getWindow().getFramerateLimit();
    }
}
