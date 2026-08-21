package dev.ryanhcode.sable.network;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;

public interface SablePacketCodec<T> {

    void encode(FriendlyByteBuf buffer, T value);

    T decode(FriendlyByteBuf buffer);

    static <T> SablePacketCodec<T> of(final BiConsumer<FriendlyByteBuf, T> encoder,
                                     final Function<FriendlyByteBuf, T> decoder) {
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");

        return new SablePacketCodec<>() {
            @Override
            public void encode(final FriendlyByteBuf buffer, final T value) {
                encoder.accept(buffer, value);
            }

            @Override
            public T decode(final FriendlyByteBuf buffer) {
                return decoder.apply(buffer);
            }
        };
    }

    static <T> SablePacketCodec<T> fromCodec(final Codec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return of(
                (buffer, value) -> buffer.writeWithCodec(NbtOps.INSTANCE, codec, value),
                buffer -> buffer.readWithCodec(NbtOps.INSTANCE, codec)
        );
    }
}
