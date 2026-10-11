package dev.farmprofit.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pests get Minecraft's glowing outline (visible through walls). Skipped safely if the method isn't found. */
@Mixin(Minecraft.class)
public abstract class GlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyassist$glowPests(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (dev.farmprofit.Safe.paused("Glow outline")) return;
            if (dev.farmprofit.Glow.isHighlighted(entity)) cir.setReturnValue(true);
        } catch (Throwable t) {
            dev.farmprofit.Safe.report("Glow outline", t);
        }
    }
}
