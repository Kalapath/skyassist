package dev.farmprofit.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Disable swimming": you stay upright in water instead of the 1.13+ swimming / crawling pose. Your player only. */
@Mixin(Entity.class)
public abstract class SwimMixin {
    @Inject(method = "updateSwimming", at = @At("TAIL"), require = 0)
    private void skyassist$noSwim(CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            if (dev.farmprofit.Config.get().disableSwimming && self == Minecraft.getInstance().player) self.setSwimming(false);
        } catch (Throwable t) {
            dev.farmprofit.Safe.report("Disable swimming", t);
        }
    }
}
