package dev.ryanhcode.sable.mixin.compatibility.create.contraptions;

import com.simibubi.create.foundation.collision.Matrix3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Matrix3d.class, remap = false)
public interface Matrix3dAccessor {
    @Accessor("m00")
    double sable$getM00();

    @Accessor("m00")
    void sable$setM00(double value);

    @Accessor("m01")
    double sable$getM01();

    @Accessor("m01")
    void sable$setM01(double value);

    @Accessor("m02")
    double sable$getM02();

    @Accessor("m02")
    void sable$setM02(double value);

    @Accessor("m10")
    double sable$getM10();

    @Accessor("m10")
    void sable$setM10(double value);

    @Accessor("m11")
    double sable$getM11();

    @Accessor("m11")
    void sable$setM11(double value);

    @Accessor("m12")
    double sable$getM12();

    @Accessor("m12")
    void sable$setM12(double value);

    @Accessor("m20")
    double sable$getM20();

    @Accessor("m20")
    void sable$setM20(double value);

    @Accessor("m21")
    double sable$getM21();

    @Accessor("m21")
    void sable$setM21(double value);

    @Accessor("m22")
    double sable$getM22();

    @Accessor("m22")
    void sable$setM22(double value);
}
