import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Exact SRG callsites/order, independent of source-text assertions and without booting Minecraft. */
public final class M43ResidualBytecodeVerifier {
    private static final String CONTEXT = "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;";
    private static final String BASE = "com/simibubi/create/content/kinetics/base/BlockBreakingMovementBehaviour";
    private M43ResidualBytecodeVerifier() { }

    public static void verify(File create) throws Exception {
        try (JarFile jar = new JarFile(create)) {
            ClassNode base = type(jar, BASE);
            MethodNode visit = method(base, "visitNewPosition", "(" + CONTEXT + "Lnet/minecraft/core/BlockPos;)V");
            int damage = index(visit, BASE, "damageEntities");
            int breaking = index(visit, BASE, "canBreak");
            require(damage >= 0 && damage < breaking, "native entity phase must precede canBreak");
            require(index(visit, "net/minecraft/world/level/block/state/BlockState", "m_60796_") < damage,
                    "native redstone-conductor gate");
            MethodNode entities = method(base, "damageEntities", "(" + CONTEXT + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)V");
            require(calls(entities, "net/minecraft/world/level/Level", "m_45976_", "(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;") == 1, "one native entity query");
            require(calls(entities, "net/minecraft/world/entity/Entity", "m_6469_", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z") == 1, "one native hurt site");
            for (String excluded : List.of("net/minecraft/world/entity/item/ItemEntity", "com/simibubi/create/content/contraptions/AbstractContraptionEntity", "net/minecraft/world/entity/vehicle/AbstractMinecart")) {
                require(Arrays.stream(entities.instructions.toArray()).anyMatch(i -> i instanceof TypeInsnNode t && t.getOpcode() == Opcodes.INSTANCEOF && t.desc.equals(excluded)), "native exclusion " + excluded);
            }
            require(index(entities, "net/minecraft/world/entity/Entity", "m_20365_") >= 0, "native same-vehicle exclusion");
            MethodNode throwing = method(base, "throwEntity", "(" + CONTEXT + "Lnet/minecraft/world/entity/Entity;)V");
            require(Arrays.stream(throwing.instructions.toArray()).filter(i -> i instanceof FieldInsnNode f && f.name.equals("motion") && f.getOpcode() == Opcodes.GETFIELD).count() == 2, "native throw motion input");
            require(calls(throwing, "net/minecraft/world/phys/Vec3", "m_82520_", "(DDD)Lnet/minecraft/world/phys/Vec3;") == 1, "native world-up lift");
            require(calls(throwing, "net/minecraft/world/entity/Entity", "m_20256_", "(Lnet/minecraft/world/phys/Vec3;)V") == 1, "native final velocity mutation");
            ClassNode plough = type(jar, "com/simibubi/create/content/contraptions/actors/plough/PloughMovementBehaviour");
            require(calls(method(plough, "throwEntity", throwing.desc), BASE, "throwEntity", throwing.desc) == 1, "plough inherited native throw once");
            ClassNode drill = type(jar, "com/simibubi/create/content/kinetics/drill/DrillMovementBehaviour");
            ClassNode saw = type(jar, "com/simibubi/create/content/kinetics/saw/SawMovementBehaviour");
            require(drill.superName.equals(BASE) && drill.methods.stream().noneMatch(m -> m.name.equals("visitNewPosition")), "Drill inherited visit");
            require(saw.superName.equals(BASE) && calls(method(saw, "visitNewPosition", visit.desc), BASE, "visitNewPosition", visit.desc) == 1, "Saw native super visit once");
            for (ClassNode actor : List.of(drill, saw)) {
                MethodNode source = method(actor, "getDamageSource", "(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/damagesource/DamageSource;");
                require(index(source, "com/simibubi/create/foundation/damageTypes/CreateDamageSources", actor == drill ? "drill" : "saw") >= 0, "native damage-source identity");
            }
            ClassNode blaze = type(jar, "com/simibubi/create/content/processing/burner/BlazeBurnerMovementBehaviour");
            MethodNode gaze = method(blaze, "getTargetAngle", "(" + CONTEXT + ")F");
            require(calls(gaze, "net/minecraft/world/entity/Entity", "m_20182_", "()Lnet/minecraft/world/phys/Vec3;") == 1, "native cameraEntity.position source");
            require(calls(gaze, "net/minecraft/world/phys/Vec3", "m_82546_", "(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;") == 1, "native single subtraction");
            require(calls(gaze, "com/simibubi/create/content/contraptions/AbstractContraptionEntity", "reverseRotation", "(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;") == 1, "native inner yaw compensation");
            require(Arrays.stream(gaze.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.FRETURN).count() == 3, "exact gaze return sites");
        }
    }

    public static void packagedChecks(Map report) {
        List<Map> checks = (List<Map>) report.get("checks");
        Map<String, Integer> expected = Map.of("sable$rotateNativeEntityMotion", 1, "sable$avoidSecondEntityPhase", 1, "sable$observeNativeEntityQuery", 1,
                "sable$observeNativeDamage", 1, "sable$physicalGazeBeforeSubtraction", 1, "sable$observeNativeGazeAngle", 3);
        expected.forEach((handler, count) -> {
            List<Map> matches = checks.stream().filter(row -> row.get("handler").toString().startsWith(handler + "(")).toList();
            // The generic verifier stores the name and descriptor separately on some report versions.
            if (matches.isEmpty()) matches = checks.stream().filter(row -> row.get("handler").toString().equals(handler)).toList();
            require(matches.size() == 1 && ((Number) matches.get(0).get("matches")).intValue() == count, "packaged injection exact match count " + handler + " " + matches);
        });
    }

    private static void require(boolean test, String message) { if (!test) throw new IllegalStateException("M43 bytecode canary: " + message); }
    private static ClassNode type(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class"); require(entry != null, "missing native class " + name);
        ClassNode node = new ClassNode(); new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(node, 0); return node;
    }
    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(() -> new IllegalStateException("M43 exact method missing " + type.name + ";" + name + desc));
    }
    private static int calls(MethodNode method, String owner, String name, String desc) {
        return (int) Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc)).count();
    }
    private static int index(MethodNode method, String owner, String name) {
        AbstractInsnNode[] code = method.instructions.toArray();
        for (int i = 0; i < code.length; i++) if (code[i] instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name)) return i;
        return -1;
    }
}
