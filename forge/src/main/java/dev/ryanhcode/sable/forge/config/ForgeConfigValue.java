package dev.ryanhcode.sable.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeConfigValue {

    private ForgeConfigValue() {
    }

    public static final class BooleanValue {
        private final ForgeConfigSpec.BooleanValue value;

        public BooleanValue(final ForgeConfigSpec.BooleanValue value) {
            this.value = value;
        }

        public boolean get() {
            return this.value.get();
        }

        public boolean getAsBoolean() {
            return this.value.get();
        }
    }

    public static final class IntValue {
        private final ForgeConfigSpec.IntValue value;

        public IntValue(final ForgeConfigSpec.IntValue value) {
            this.value = value;
        }

        public int get() {
            return this.value.get();
        }

        public int getAsInt() {
            return this.value.get();
        }
    }

    public static final class DoubleValue {
        private final ForgeConfigSpec.DoubleValue value;

        public DoubleValue(final ForgeConfigSpec.DoubleValue value) {
            this.value = value;
        }

        public double get() {
            return this.value.get();
        }

        public double getAsDouble() {
            return this.value.get();
        }
    }

    public static final class EnumValue<T extends Enum<T>> {
        private final ForgeConfigSpec.EnumValue<T> value;

        public EnumValue(final ForgeConfigSpec.EnumValue<T> value) {
            this.value = value;
        }

        public T get() {
            return this.value.get();
        }
    }
}
