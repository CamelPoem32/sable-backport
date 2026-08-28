package dev.ryanhcode.sable.mixin.m11.create;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ValueSettingsPacket.class, remap = false)
public interface ValueSettingsPacketAccessor {
    @Accessor("row")
    int sable$getRow();

    @Accessor("value")
    int sable$getValue();

    @Accessor("interactHand")
    @Nullable
    InteractionHand sable$getInteractHand();

    @Accessor("hitResult")
    @Nullable
    BlockHitResult sable$getHitResult();

    @Accessor("side")
    Direction sable$getSide();

    @Accessor("ctrlDown")
    boolean sable$getCtrlDown();

    @Accessor("behaviourIndex")
    int sable$getBehaviourIndex();

    @Invoker("applySettings")
    void sable$applySettings(ServerPlayer player, SmartBlockEntity blockEntity);
}
