package dev.ryanhcode.sable.mixinterface.respawn_point;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.Optional;

public interface ServerPlayerRespawnExtension {
    @Nullable UUID sable$getRespawnPoint();

    void sable$takeQueuedFreezeFrom(ServerPlayer oldPlayer);

    @Nullable Pair<UUID, Vector3d> sable$getQueuedFreeze();

    Optional<Vec3> sable$findRespawnPosition(ServerLevel level, BlockPos position, float angle, boolean forced, boolean consumeAnchor);
}
