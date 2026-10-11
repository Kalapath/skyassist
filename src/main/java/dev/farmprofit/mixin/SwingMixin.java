package dev.farmprofit.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Slow swing": your arm swing animation takes longer (looks like the old 1.8 swing). Visual only. */
@Mixin(LivingEntity.class)
public abstract class SwingMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyassist$slowSwing(CallbackInfoReturnable<Integer> cir) {
        try {
            if (dev.farmprofit.Config.get().slowSwing && (Object) this == Minecraft.getInstance().player)
                cir.setReturnValue(Math.max(2, dev.farmprofit.Config.get().swingDuration));
        } catch (Throwable t) {
            dev.farmprofit.Safe.report("Slow swing", t);
        }
    }
}
