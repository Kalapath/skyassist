package dev.farmprofit.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The glowing outline's color comes from the entity's team color: make it bright green for pests. */
@Mixin(Entity.class)
public abstract class GlowColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyassist$pestColor(CallbackInfoReturnable<Integer> cir) {
        // an error in here would crash the frame, so it is caught and reported instead (/skyassist status)
        try {
            if (dev.farmprofit.Safe.paused("Glow outline color")) return;
            Entity self = (Entity) (Object) this;
            if (dev.farmprofit.Glow.isHighlighted(self)) cir.setReturnValue(dev.farmprofit.Glow.color(self));
        } catch (Throwable t) {
            dev.farmprofit.Safe.report("Glow outline color", t);
        }
    }
}
