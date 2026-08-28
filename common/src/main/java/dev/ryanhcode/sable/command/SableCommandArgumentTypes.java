package dev.ryanhcode.sable.command;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.ResourceLocation;

public final class SableCommandArgumentTypes {
    public static final ResourceLocation SUB_LEVEL_ID = new ResourceLocation(Sable.MOD_ID, "sub_level");
    public static final ResourceLocation VEC3_ABSOLUTE_ID = new ResourceLocation(Sable.MOD_ID, "vec3_absolute");

    private SableCommandArgumentTypes() {
    }

    public static SubLevelArgumentType.Info registerSubLevelByClass() {
        return ArgumentTypeInfos.registerByClass(SubLevelArgumentType.class, new SubLevelArgumentType.Info());
    }

    public static SingletonArgumentInfo<Vec3ArgumentAbsolute> registerVec3AbsoluteByClass() {
        return ArgumentTypeInfos.registerByClass(
                Vec3ArgumentAbsolute.class,
                SingletonArgumentInfo.contextFree(Vec3ArgumentAbsolute::vec3));
    }
}
