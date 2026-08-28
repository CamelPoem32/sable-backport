package dev.ryanhcode.sable.mixin.m11.create;

import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BlockEntityConfigurationPacket.class, remap = false)
public interface BlockEntityConfigurationPacketAccessor {
    @Accessor("pos")
    BlockPos sable$getPos();

    @Invoker("causeUpdate")
    boolean sable$causeUpdate();
}
