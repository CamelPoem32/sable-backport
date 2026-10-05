import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Structural production-namespace smoke test, without bootstrapping Minecraft or loading target classes. */
public final class M39RuntimeMixinTargetVerifier {
    private static final String ASSEMBLY = "dev/simulated_team/simulated/util/SimAssemblyHelper";
    private static final String SEATS = "dev/ryanhcode/sable/compatibility/create/seat/SableSeatPassengers";
    private static final String TRANSFER = "dev/ryanhcode/sable/compatibility/create/contraptions/SableNestedBearingOwnershipTransfer";
    private static final String DISASSEMBLY = "(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;I)Ldev/simulated_team/simulated/util/SimAssemblyHelper$DisassemblyResult;";
    private static final String BEGIN = "(Lnet/minecraft/server/level/ServerLevel;Ljava/util/Collection;Ljava/lang/String;)V";

    private M39RuntimeMixinTargetVerifier() { }

    public static int verifyInjections(File artifact, File create, Map<?, ?> config, Map<?, ?> refmap) throws IOException {
        try (JarFile sableJar = new JarFile(artifact); JarFile createJar = new JarFile(create)) {
            List<String> selected = new ArrayList<>();
            for (String side : List.of("mixins", "client", "server")) {
                for (Object name : list(config.get(side))) {
                    if (name.toString().startsWith("compatibility.create.seat.") || name.toString().equals("m39.SeatOuterDisassemblyMixin")) {
                        selected.add(config.get("package") + "." + name);
                    }
                }
            }
            if (selected.isEmpty()) throw new IllegalStateException("M39 packaged mixin configuration is empty");
            for (String required : List.of("SeatContraptionEntityMixin", "SeatMovementBehaviourMixin", "SeatInteractionPacketMixin",
                    "SeatInteractionClientMixin", "SeatOuterRemovalMixin")) {
                if (selected.stream().noneMatch(name -> name.endsWith("." + required))) {
                    throw new IllegalStateException("M39 required packaged mixin missing " + required);
                }
            }
            int checked = 0;
            for (String name : selected) {
                ClassNode mixin = read(name.replace('.', '/'), sableJar, createJar);
                AnnotationNode declaration = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
                List<?> owners = list(value(declaration, "value"));
                if (owners.size() != 1) throw new IllegalStateException("M39 requires one exact mixin target: " + name);
                ClassNode target = read(((Type) owners.get(0)).getInternalName(), sableJar, createJar);
                Map<?, ?> mappings = map(map(refmap.get("mappings")).get(mixin.name));
                for (MethodNode handler : mixin.methods) {
                    AnnotationNode inject = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
                    AnnotationNode wrap = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
                    AnnotationNode operation = inject == null ? wrap : inject;
                    if (operation == null) continue;
                    if (Integer.valueOf(0).equals(value(operation, "require"))) throw new IllegalStateException("M39 required hook made optional: " + handler.name);
                    List<MethodNode> methods = new ArrayList<>();
                    for (Object selector : list(value(operation, "method"))) {
                        String resolved = resolve(selector.toString(), mappings);
                        for (MethodNode method : target.methods) {
                            if ((method.name.equals(resolved) || (method.name + method.desc).equals(resolved)) && !methods.contains(method)) methods.add(method);
                        }
                    }
                    if (methods.size() != 1) throw new IllegalStateException("M39 target method count=" + methods.size() + " " + name + "." + handler.name);
                    MethodNode method = methods.get(0);
                    if (inject != null) checkCallback(handler, method);
                    Object atValue = value(operation, "at");
                    List<?> points = atValue instanceof AnnotationNode ? List.of(atValue) : list(atValue);
                    if (points.size() != 1) throw new IllegalStateException("M39 requires one explicit injection point " + handler.name);
                    AnnotationNode at = (AnnotationNode) points.get(0);
                    String kind = String.valueOf(value(at, "value"));
                    if (kind.equals("INVOKE")) {
                        String raw = String.valueOf(value(at, "target"));
                        String member = resolve(raw, mappings);
                        // A Minecraft call site that needs SRG names must have a packaged mapping, not a guessed alias.
                        if (raw.startsWith("Lnet/minecraft/") && raw.equals(member) && !raw.contains(";m_")) {
                            throw new IllegalStateException("M39 missing production refmap entry: " + raw);
                        }
                        int matches = memberMatches(method, member);
                        int expected = handler.name.contains("physicalRay") ? 2 : 1;
                        if (matches != expected) throw new IllegalStateException("M39 injection point matches=" + matches + " expected=" + expected
                                + " mixin=" + name + " handler=" + handler.name + " method=" + method.name + method.desc + " at=" + member);
                    } else if (kind.equals("RETURN")) {
                        if (instructions(method).stream().noneMatch(i -> i.getOpcode() >= Opcodes.IRETURN && i.getOpcode() <= Opcodes.RETURN)) {
                            throw new IllegalStateException("M39 RETURN injection has no return: " + handler.name);
                        }
                    } else if (!kind.equals("HEAD")) throw new IllegalStateException("Unaudited M39 injection point " + kind);
                    checked++;
                }
            }
            int expectedHooks = selected.stream().anyMatch(name -> name.endsWith(".SeatOuterDisassemblyMixin")) ? 16 : 15;
            if (checked != expectedHooks) throw new IllegalStateException("M39 packaged hook count changed: " + checked + " expected=" + expectedHooks);
            return checked;
        }
    }

    public static void verifyDirectTeardown(File artifact, List<?> configured) throws IOException {
        try (JarFile jar = new JarFile(artifact)) {
            String obsolete = "dev/ryanhcode/sable/mixin/m39/SeatOuterDisassemblyMixin.class";
            if (jar.getJarEntry(obsolete) != null || configured.contains("m39.SeatOuterDisassemblyMixin")) {
                throw new IllegalStateException("M39 obsolete self-mixin remains packaged");
            }
            ClassNode target = read(ASSEMBLY, jar);
            List<MethodNode> methods = target.methods.stream().filter(m -> m.name.equals("disassembleSubLevel") && m.desc.equals(DISASSEMBLY)).toList();
            if (methods.size() != 1) throw new IllegalStateException("M39 packaged disassembly descriptor missing");
            MethodNode method = methods.get(0);
            String detach = "L" + SEATS + ";detachOuter(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V";
            String begin = "L" + TRANSFER + ";begin" + BEGIN;
            if (memberMatches(method, detach) != 1 || memberMatches(method, begin) != 1) {
                throw new IllegalStateException("M39 direct detach/ownership transfer must each occur exactly once");
            }
            List<AbstractInsnNode> code = instructions(method);
            int detachIndex = index(code, SEATS, "detachOuter");
            int occupiedIndex = index(code, "dev/simulated_team/simulated/util/assembly/SimAssemblyException", "occupied");
            int beginIndex = index(code, TRANSFER, "begin");
            int moveIndex = index(code, "dev/ryanhcode/sable/api/SubLevelAssemblyHelper", "moveBlocks");
            if (occupiedIndex < 0 || occupiedIndex >= detachIndex || detachIndex >= beginIndex || beginIndex >= moveIndex
                    || code.get(detachIndex).getPrevious().getOpcode() != Opcodes.ALOAD
                    || ((org.objectweb.asm.tree.VarInsnNode) code.get(detachIndex).getPrevious()).var != 1) {
                throw new IllegalStateException("M39 detach must use method's body argument after occupancy refusal and before transfer/move");
            }
            // Regression: List is a valid Java subtype, but an invalid exact JVM descriptor for this call.
            String oldSelector = begin.replace("Ljava/util/Collection;", "Ljava/util/List;");
            if (memberMatches(method, oldSelector) != 0) throw new IllegalStateException("M39 List/Collection negative canary changed");
        }
    }

    private static void checkCallback(MethodNode handler, MethodNode target) {
        Type[] arguments = Type.getArgumentTypes(handler.desc);
        String callback = Type.getReturnType(target.desc).equals(Type.VOID_TYPE) ? "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"
                : "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
        if (arguments.length == 0 || !arguments[arguments.length - 1].getInternalName().equals(callback)) {
            throw new IllegalStateException("M39 callback signature mismatch " + handler.name);
        }
        Type[] targetArguments = Type.getArgumentTypes(target.desc);
        if (arguments.length > 1) {
            if (arguments.length != targetArguments.length + 1) throw new IllegalStateException("M39 callback argument count mismatch " + handler.name);
            for (int i = 0; i < targetArguments.length; i++) if (!arguments[i].equals(targetArguments[i])) {
                throw new IllegalStateException("M39 callback argument descriptor mismatch " + handler.name);
            }
        }
    }

    private static int index(List<AbstractInsnNode> code, String owner, String name) {
        for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        return -1;
    }

    private static int memberMatches(MethodNode method, String selector) {
        int count = 0;
        for (AbstractInsnNode instruction : instructions(method)) {
            if (instruction instanceof MethodInsnNode call && ("L" + call.owner + ";" + call.name + call.desc).equals(selector)) count++;
        }
        return count;
    }

    private static List<AbstractInsnNode> instructions(MethodNode method) {
        return List.of(method.instructions.toArray());
    }

    private static ClassNode read(String name, JarFile... jars) throws IOException {
        for (JarFile jar : jars) {
            var entry = jar.getJarEntry(name + ".class");
            if (entry == null) continue;
            ClassNode node = new ClassNode();
            try (var stream = jar.getInputStream(entry)) { new ClassReader(stream.readAllBytes()).accept(node, 0); }
            return node;
        }
        throw new IllegalStateException("M39 packaged target class missing " + name);
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String descriptor) {
        List<List<AnnotationNode>> groups = new ArrayList<>();
        if (visible != null) groups.add(visible);
        if (invisible != null) groups.add(invisible);
        for (List<AnnotationNode> annotations : groups) {
            for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

    private static List<?> list(Object value) { return value instanceof List<?> values ? values : List.of(); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> values ? values : Map.of(); }
    private static String resolve(String value, Map<?, ?> mapping) { return mapping.containsKey(value) ? String.valueOf(mapping.get(value)) : value; }
}
