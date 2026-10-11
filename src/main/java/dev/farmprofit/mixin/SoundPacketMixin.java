package dev.farmprofit.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the mod mute sounds the server plays (explosions, chest dings...). Skipped safely if not found. */
@Mixin(ClientPacketListener.class)
public abstract class SoundPacketMixin {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyassist$muteSounds(ClientboundSoundPacket packet, CallbackInfo ci) {
        try {
            if (dev.farmprofit.Safe.paused("Sound muting")) return;
            if (dev.farmprofit.Sounds.shouldMute(packet)) ci.cancel();
        } catch (Throwable t) {
            dev.farmprofit.Safe.report("Sound muting", t);
        }
    }
}
