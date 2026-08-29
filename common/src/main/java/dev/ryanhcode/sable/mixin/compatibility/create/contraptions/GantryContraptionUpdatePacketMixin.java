package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.simibubi.create.content.contraptions.gantry.GantryContraptionUpdatePacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ports Create 6.0.9's Gantry update packet precision semantics to Create 6.0.8.
 * The packet fields are already doubles in 6.0.8; only the wire format narrows them.
 */
@Mixin(value = GantryContraptionUpdatePacket.class, remap = false)
public class GantryContraptionUpdatePacketMixin {
    @Shadow
    double coord;
    @Shadow
    double motion;
    @Shadow
    double sequenceLimit;

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"))
    private void sable$readPreciseGantryUpdate(final FriendlyByteBuf buffer, final CallbackInfo ci) {
        final int legacyPacketSize = Integer.BYTES + Float.BYTES + Float.BYTES + Float.BYTES;
        final int packetStart = buffer.readerIndex() - legacyPacketSize;
        if (packetStart < 0) {
            return;
        }
        final int doublePayloadEnd = packetStart
                + Integer.BYTES
                + Double.BYTES
                + Double.BYTES
                + Double.BYTES;
        if (buffer.writerIndex() < doublePayloadEnd) {
            return;
        }

        buffer.readerIndex(packetStart + Integer.BYTES);
        this.coord = buffer.readDouble();
        this.motion = buffer.readDouble();
        this.sequenceLimit = buffer.readDouble();
    }

    @Redirect(method = "write",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeFloat(F)Lio/netty/buffer/ByteBuf;",
                    ordinal = 0,
                    remap = true))
    private ByteBuf sable$writeCoordAsDouble(final FriendlyByteBuf buffer, final float ignored) {
        return buffer.writeDouble(this.coord);
    }

    @Redirect(method = "write",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeFloat(F)Lio/netty/buffer/ByteBuf;",
                    ordinal = 1,
                    remap = true))
    private ByteBuf sable$writeMotionAsDouble(final FriendlyByteBuf buffer, final float ignored) {
        return buffer.writeDouble(this.motion);
    }

    @Redirect(method = "write",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeFloat(F)Lio/netty/buffer/ByteBuf;",
                    ordinal = 2,
                    remap = true))
    private ByteBuf sable$writeSequenceLimitAsDouble(final FriendlyByteBuf buffer, final float ignored) {
        return buffer.writeDouble(this.sequenceLimit);
    }
}
