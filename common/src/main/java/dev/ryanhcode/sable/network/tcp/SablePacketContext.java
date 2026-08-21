package dev.ryanhcode.sable.network.tcp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface SablePacketContext {

    Level level();

    Player player();

    SablePacketDirection direction();

    static SablePacketContext of(final Level level,
                                 final Player player,
                                 final SablePacketDirection direction) {
        return new DefaultSablePacketContext(level, player, direction);
    }

    record DefaultSablePacketContext(Level level, Player player,
                                     SablePacketDirection direction) implements SablePacketContext {
        public DefaultSablePacketContext {
            java.util.Objects.requireNonNull(level, "level");
            java.util.Objects.requireNonNull(player, "player");
            java.util.Objects.requireNonNull(direction, "direction");
        }
    }
}
