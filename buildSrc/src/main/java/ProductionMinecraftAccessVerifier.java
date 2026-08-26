import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public final class ProductionMinecraftAccessVerifier {
    private static final String DIMENSION_DATA_STORAGE = "net/minecraft/world/level/storage/DimensionDataStorage";
    private static final String LEVEL_LIGHT_ENGINE = "net/minecraft/world/level/lighting/LevelLightEngine";
    private static final String CHUNK_HOLDER = "net/minecraft/server/level/ChunkHolder";
    private static final String CHUNK_MAP = "net/minecraft/server/level/ChunkMap";
    private static final String CLIP_CONTEXT = "net/minecraft/world/level/ClipContext";
    private static final String BLOCK_RENDER_DISPATCHER =
            "net/minecraft/client/renderer/block/BlockRenderDispatcher";
    private static final String SERVER_GAME_PACKET_LISTENER =
            "net/minecraft/server/network/ServerGamePacketListenerImpl";

    private static final Set<String> ACCESSOR_MANAGED_FIELDS = Set.of(
            DIMENSION_DATA_STORAGE + ".f_78146_",
            DIMENSION_DATA_STORAGE + ".dataFolder",
            LEVEL_LIGHT_ENGINE + ".f_75802_",
            LEVEL_LIGHT_ENGINE + ".blockEngine",
            LEVEL_LIGHT_ENGINE + ".f_75803_",
            LEVEL_LIGHT_ENGINE + ".skyEngine",
            CHUNK_HOLDER + ".f_140002_",
            CHUNK_HOLDER + ".fullChunkFuture",
            CHUNK_HOLDER + ".f_140003_",
            CHUNK_HOLDER + ".tickingChunkFuture",
            CHUNK_HOLDER + ".f_140004_",
            CHUNK_HOLDER + ".entityTickingChunkFuture",
            CHUNK_MAP + ".f_140140_",
            CHUNK_MAP + ".modified",
            CLIP_CONTEXT + ".f_45684_",
            CLIP_CONTEXT + ".block",
            CLIP_CONTEXT + ".f_45685_",
            CLIP_CONTEXT + ".fluid",
            CLIP_CONTEXT + ".f_45686_",
            CLIP_CONTEXT + ".collisionContext",
            SERVER_GAME_PACKET_LISTENER + ".f_9742_",
            SERVER_GAME_PACKET_LISTENER + ".connection");
    private static final Set<String> PUBLIC_API_MANAGED_FIELDS = Set.of(
            BLOCK_RENDER_DISPATCHER + ".f_110900_",
            BLOCK_RENDER_DISPATCHER + ".modelRenderer");
    private static final Set<String> FORBIDDEN_DIRECT_FIELDS = union(
            ACCESSOR_MANAGED_FIELDS,
            PUBLIC_API_MANAGED_FIELDS);
    private static final Set<String> ACCESSOR_MANAGED_METHODS = Set.of(
            CHUNK_MAP + ".m_287285_(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V",
            CHUNK_MAP + ".onFullChunkStatusChange(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V");

    private ProductionMinecraftAccessVerifier() {
    }

    public static MinecraftVisibilityDatabase loadMinecraftVisibilityDatabase(File minecraftSrgJar) throws IOException {
        Map<String, ClassInfo> classes = new HashMap<>();
        loadClasses(minecraftSrgJar, classes);
        return new MinecraftVisibilityDatabase(classes);
    }

    public static MinecraftVisibilityDatabase loadMinecraftVisibilityDatabase(List<File> classpath) throws IOException {
        Map<String, ClassInfo> classes = new HashMap<>();
        for (File entry : classpath) {
            loadClasses(entry, classes);
        }
        return new MinecraftVisibilityDatabase(classes);
    }

    private static void loadClasses(File entry, Map<String, ClassInfo> classes) throws IOException {
        if (entry == null || !entry.exists()) {
            return;
        }
        if (entry.isDirectory()) {
            loadClassesFromDirectory(entry, entry, classes);
            return;
        }
        if (!entry.isFile() || !entry.getName().endsWith(".jar")) {
            return;
        }
        try (JarFile jar = new JarFile(entry)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var jarEntry = entries.nextElement();
                if (jarEntry.isDirectory() || !jarEntry.getName().endsWith(".class")) {
                    continue;
                }
                loadClass(jar.getInputStream(jarEntry).readAllBytes(), classes);
            }
        }
    }

    private static void loadClassesFromDirectory(File root, File directory, Map<String, ClassInfo> classes)
            throws IOException {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                loadClassesFromDirectory(root, child, classes);
            } else if (child.isFile() && child.getName().endsWith(".class")) {
                loadClass(java.nio.file.Files.readAllBytes(child.toPath()), classes);
            }
        }
    }

    private static void loadClass(byte[] bytes, Map<String, ClassInfo> classes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        ClassInfo info = new ClassInfo(node.name, node.superName, List.copyOf(node.interfaces), node.access);
        for (var field : node.fields) {
            info.fields.put(field.name + field.desc, new MemberInfo(node.name, field.name, field.desc, field.access));
        }
        for (MethodNode method : node.methods) {
            info.methods.put(method.name + method.desc, new MemberInfo(node.name, method.name, method.desc, method.access));
        }
        ClassInfo existing = classes.get(node.name);
        if (existing == null) {
            classes.put(node.name, info);
        } else {
            info.fields.forEach(existing.fields::putIfAbsent);
            info.methods.forEach(existing.methods::putIfAbsent);
            if (info.superName != null && !info.superName.equals(existing.superName)) {
                existing.superName = info.superName;
            }
            for (String interfaceName : info.interfaces) {
                if (!existing.interfaces.contains(interfaceName)) {
                    existing.interfaces.add(interfaceName);
                }
            }
        }
    }

    public static List<String> directLevelLightEngineFieldAccesses(byte[] classBytes) {
        return directAccessorManagedFieldAccesses(classBytes, Set.of(
                LEVEL_LIGHT_ENGINE + ".f_75802_",
                LEVEL_LIGHT_ENGINE + ".blockEngine",
                LEVEL_LIGHT_ENGINE + ".f_75803_",
                LEVEL_LIGHT_ENGINE + ".skyEngine"));
    }

    public static List<String> directAccessorManagedFieldAccesses(byte[] classBytes) {
        return directAccessorManagedFieldAccesses(classBytes, ACCESSOR_MANAGED_FIELDS);
    }

    public static List<String> directAccessorManagedMethodAccesses(byte[] classBytes) {
        return directAccessorManagedMethodAccesses(classBytes, ACCESSOR_MANAGED_METHODS);
    }

    public static void verifyVisibilitySelfTest() {
        Set<String> emptyAt = Set.of();
        Set<String> callerHierarchy = Set.of("dev/ryanhcode/sable/TestCaller");
        assertDecision(
                AccessDecision.SAFE_PUBLIC,
                classifyAccess("dev/ryanhcode/sable/TestCaller", callerHierarchy,
                        new MemberInfo("net/minecraft/TestTarget", "publicMethod", "()V", Opcodes.ACC_PUBLIC),
                        emptyAt),
                "public method");
        assertDecision(
                AccessDecision.INVALID_PRIVATE,
                classifyAccess("dev/ryanhcode/sable/TestCaller", callerHierarchy,
                        new MemberInfo("net/minecraft/TestTarget", "privateMethod", "()V", Opcodes.ACC_PRIVATE),
                        emptyAt),
                "private method");
        assertDecision(
                AccessDecision.INVALID_PACKAGE,
                classifyAccess("dev/ryanhcode/sable/TestCaller", callerHierarchy,
                        new MemberInfo("net/minecraft/TestTarget", "packageMethod", "()V", 0),
                        emptyAt),
                "package-private method from another package");
        assertDecision(
                AccessDecision.INVALID_PROTECTED,
                classifyAccess("dev/ryanhcode/sable/TestCaller", callerHierarchy,
                        new MemberInfo("net/minecraft/TestTarget", "protectedMethod", "()V", Opcodes.ACC_PROTECTED),
                        emptyAt),
                "illegal protected method");
        assertDecision(
                AccessDecision.SAFE_AT,
                classifyAccess("dev/ryanhcode/sable/TestCaller", callerHierarchy,
                        new MemberInfo("net/minecraft/TestTarget", "atMethod", "()V", Opcodes.ACC_PRIVATE),
                        Set.of("net/minecraft/TestTarget.atMethod()V")),
                "AT-backed method");
        assertDecision(
                AccessDecision.SAFE_PROTECTED,
                classifyAccess("dev/ryanhcode/sable/TestCaller", Set.of("net/minecraft/TestTarget"),
                        new MemberInfo("net/minecraft/TestTarget", "protectedMethod", "()V", Opcodes.ACC_PROTECTED),
                        emptyAt),
                "subclass protected method");
    }

    public static AccessAuditResult auditMinecraftMemberReferences(
            byte[] classBytes,
            Set<String> accessTransformerMembers,
            MinecraftVisibilityDatabase minecraft) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, 0);
        int minecraftFieldReferences = 0;
        int minecraftMethodReferences = 0;
        int resolvedReferences = 0;
        int safePublic = 0;
        int safeProtected = 0;
        int safePackage = 0;
        int safeAt = 0;
        int atBackedFieldReferences = 0;
        int atBackedMethodReferences = 0;
        Set<String> minecraftOwners = new HashSet<>();
        List<String> invalidPrivate = new ArrayList<>();
        List<String> invalidPackage = new ArrayList<>();
        List<String> invalidProtected = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<String> forbiddenDirectAccesses = new ArrayList<>();
        Set<String> callerHierarchy = minecraft.resolveKnownHierarchy(node.name, node.superName, node.interfaces);

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.startsWith("net/minecraft/")) {
                    minecraftFieldReferences++;
                    minecraftOwners.add(field.owner);
                    String source = node.name + "." + method.name + method.desc + " -> "
                            + field.owner + "." + field.name + ":" + field.desc;
                    if (FORBIDDEN_DIRECT_FIELDS.contains(field.owner + "." + field.name)) {
                        forbiddenDirectAccesses.add(source);
                    }
                    MemberInfo member = minecraft.resolveField(field.owner, field.name, field.desc);
                    if (member == null) {
                        unresolved.add(source);
                        continue;
                    }
                    resolvedReferences++;
                    AccessDecision decision = classifyAccess(node.name, callerHierarchy, member, accessTransformerMembers);
                    atBackedFieldReferences += decision == AccessDecision.SAFE_AT ? 1 : 0;
                    safePublic += decision == AccessDecision.SAFE_PUBLIC ? 1 : 0;
                    safeProtected += decision == AccessDecision.SAFE_PROTECTED ? 1 : 0;
                    safePackage += decision == AccessDecision.SAFE_PACKAGE ? 1 : 0;
                    safeAt += decision == AccessDecision.SAFE_AT ? 1 : 0;
                    addInvalid(decision, invalidPrivate, invalidPackage, invalidProtected, source + " declared "
                            + member.owner + "." + member.name + ":" + member.desc);
                } else if (instruction instanceof MethodInsnNode methodInsn && methodInsn.owner.startsWith("net/minecraft/")) {
                    minecraftMethodReferences++;
                    minecraftOwners.add(methodInsn.owner);
                    String source = node.name + "." + method.name + method.desc + " -> "
                            + methodInsn.owner + "." + methodInsn.name + methodInsn.desc;
                    if (ACCESSOR_MANAGED_METHODS.contains(methodInsn.owner + "." + methodInsn.name + methodInsn.desc)) {
                        forbiddenDirectAccesses.add(source);
                    }
                    MemberInfo member = minecraft.resolveMethod(methodInsn.owner, methodInsn.name, methodInsn.desc);
                    if (member == null) {
                        unresolved.add(source);
                        continue;
                    }
                    resolvedReferences++;
                    AccessDecision decision = classifyAccess(node.name, callerHierarchy, member, accessTransformerMembers);
                    atBackedMethodReferences += decision == AccessDecision.SAFE_AT ? 1 : 0;
                    safePublic += decision == AccessDecision.SAFE_PUBLIC ? 1 : 0;
                    safeProtected += decision == AccessDecision.SAFE_PROTECTED ? 1 : 0;
                    safePackage += decision == AccessDecision.SAFE_PACKAGE ? 1 : 0;
                    safeAt += decision == AccessDecision.SAFE_AT ? 1 : 0;
                    addInvalid(decision, invalidPrivate, invalidPackage, invalidProtected, source + " declared "
                            + member.owner + "." + member.name + member.desc);
                }
            }
        }

        return new AccessAuditResult(
                minecraftFieldReferences,
                minecraftMethodReferences,
                resolvedReferences,
                safePublic,
                safeProtected,
                safePackage,
                safeAt,
                atBackedFieldReferences,
                atBackedMethodReferences,
                minecraftOwners.size(),
                forbiddenDirectAccesses,
                invalidPrivate,
                invalidPackage,
                invalidProtected,
                unresolved);
    }

    private static void addInvalid(
            AccessDecision decision,
            List<String> invalidPrivate,
            List<String> invalidPackage,
            List<String> invalidProtected,
            String source) {
        if (decision == AccessDecision.INVALID_PRIVATE) {
            invalidPrivate.add(source);
        } else if (decision == AccessDecision.INVALID_PACKAGE) {
            invalidPackage.add(source);
        } else if (decision == AccessDecision.INVALID_PROTECTED) {
            invalidProtected.add(source);
        }
    }

    private static void assertDecision(AccessDecision expected, AccessDecision actual, String label) {
        if (actual != expected) {
            throw new AssertionError(label + " classified as " + actual + ", expected " + expected);
        }
    }

    private static AccessDecision classifyAccess(
            String caller,
            Set<String> callerHierarchy,
            MemberInfo member,
            Set<String> accessTransformerMembers) {
        if (accessTransformerMembers.contains(member.owner + "." + member.name)
                || accessTransformerMembers.contains(member.owner + "." + member.name + member.desc)) {
            return AccessDecision.SAFE_AT;
        }
        if ((member.access & Opcodes.ACC_PUBLIC) != 0) {
            return AccessDecision.SAFE_PUBLIC;
        }
        if ((member.access & Opcodes.ACC_PRIVATE) != 0) {
            return AccessDecision.INVALID_PRIVATE;
        }
        if ((member.access & Opcodes.ACC_PROTECTED) != 0) {
            if (samePackage(caller, member.owner) || callerHierarchy.contains(member.owner)) {
                return AccessDecision.SAFE_PROTECTED;
            }
            return AccessDecision.INVALID_PROTECTED;
        }
        if (samePackage(caller, member.owner)) {
            return AccessDecision.SAFE_PACKAGE;
        }
        return AccessDecision.INVALID_PACKAGE;
    }

    private static boolean samePackage(String left, String right) {
        int leftSlash = left.lastIndexOf('/');
        int rightSlash = right.lastIndexOf('/');
        if (leftSlash != rightSlash) {
            return false;
        }
        if (leftSlash < 0) {
            return true;
        }
        return left.regionMatches(0, right, 0, leftSlash);
    }

    private static List<String> directAccessorManagedFieldAccesses(byte[] classBytes, Set<String> forbiddenFields) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, 0);
        List<String> accesses = new ArrayList<>();
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field
                        && forbiddenFields.contains(field.owner + "." + field.name)) {
                    accesses.add(node.name + "." + method.name + method.desc + " -> "
                            + field.owner + "." + field.name + ":" + field.desc);
                }
            }
        }
        return accesses;
    }

    public static List<String> directForbiddenFieldAccesses(byte[] classBytes) {
        return directAccessorManagedFieldAccesses(classBytes, FORBIDDEN_DIRECT_FIELDS);
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> combined = new HashSet<>(left);
        combined.addAll(right);
        return Set.copyOf(combined);
    }

    private static List<String> directAccessorManagedMethodAccesses(byte[] classBytes, Set<String> forbiddenMethods) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, 0);
        List<String> accesses = new ArrayList<>();
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode methodInsn
                        && forbiddenMethods.contains(methodInsn.owner + "." + methodInsn.name + methodInsn.desc)) {
                    accesses.add(node.name + "." + method.name + method.desc + " -> "
                            + methodInsn.owner + "." + methodInsn.name + methodInsn.desc);
                }
            }
        }
        return accesses;
    }

    private enum AccessDecision {
        SAFE_PUBLIC,
        SAFE_PROTECTED,
        SAFE_PACKAGE,
        SAFE_AT,
        INVALID_PRIVATE,
        INVALID_PACKAGE,
        INVALID_PROTECTED
    }

    public static final class MinecraftVisibilityDatabase {
        private final Map<String, ClassInfo> classes;

        MinecraftVisibilityDatabase(Map<String, ClassInfo> classes) {
            this.classes = new HashMap<>(classes);
        }

        public int classCount() {
            return classes.size();
        }

        public MemberInfo resolveField(String owner, String name, String desc) {
            return resolve(owner, name + desc, false);
        }

        public MemberInfo resolveMethod(String owner, String name, String desc) {
            return resolve(owner, name + desc, true);
        }

        Set<String> resolveKnownHierarchy(String owner, String superName, List<String> interfaces) {
            Set<String> hierarchy = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            if (owner != null) {
                queue.add(owner);
            }
            if (superName != null) {
                queue.add(superName);
            }
            queue.addAll(interfaces);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!hierarchy.add(current)) {
                    continue;
                }
                ClassInfo info = classes.get(current);
                if (info == null) {
                    info = loadJavaRuntimeClass(current);
                }
                if (info == null) {
                    continue;
                }
                if (info.superName != null) {
                    queue.addLast(info.superName);
                }
                queue.addAll(info.interfaces);
            }
            return hierarchy;
        }

        private MemberInfo resolve(String owner, String key, boolean method) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(owner);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                ClassInfo info = classes.get(current);
                if (info == null) {
                    info = loadJavaRuntimeClass(current);
                }
                if (info == null) {
                    continue;
                }
                MemberInfo member = method ? info.methods.get(key) : info.fields.get(key);
                if (member != null) {
                    return member;
                }
                if (info.superName != null) {
                    queue.addLast(info.superName);
                }
                queue.addAll(info.interfaces);
            }
            return null;
        }

        private ClassInfo loadJavaRuntimeClass(String internalName) {
            if (!internalName.startsWith("java/")) {
                return null;
            }
            try {
                Class<?> type = Class.forName(internalName.replace('/', '.'));
                Class<?> superClass = type.getSuperclass();
                List<String> interfaces = new ArrayList<>();
                for (Class<?> interfaceType : type.getInterfaces()) {
                    interfaces.add(interfaceType.getName().replace('.', '/'));
                }
                ClassInfo info = new ClassInfo(
                        internalName,
                        superClass == null ? null : superClass.getName().replace('.', '/'),
                        List.copyOf(interfaces),
                        type.getModifiers());
                for (var field : type.getDeclaredFields()) {
                    String desc = org.objectweb.asm.Type.getDescriptor(field.getType());
                    info.fields.put(field.getName() + desc,
                            new MemberInfo(internalName, field.getName(), desc, field.getModifiers()));
                }
                for (var method : type.getDeclaredMethods()) {
                    String desc = org.objectweb.asm.Type.getMethodDescriptor(method);
                    info.methods.put(method.getName() + desc,
                            new MemberInfo(internalName, method.getName(), desc, method.getModifiers()));
                }
                classes.put(internalName, info);
                return info;
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
    }

    private static final class ClassInfo {
        final String name;
        String superName;
        final List<String> interfaces;
        final int access;
        final Map<String, MemberInfo> fields = new HashMap<>();
        final Map<String, MemberInfo> methods = new HashMap<>();

        ClassInfo(String name, String superName, List<String> interfaces, int access) {
            this.name = name;
            this.superName = superName;
            this.interfaces = new ArrayList<>(interfaces);
            this.access = access;
        }
    }

    public static final class MemberInfo {
        public final String owner;
        public final String name;
        public final String desc;
        public final int access;

        MemberInfo(String owner, String name, String desc, int access) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.access = access;
        }
    }

    public static final class AccessAuditResult {
        public final int minecraftFieldReferences;
        public final int minecraftMethodReferences;
        public final int resolvedReferences;
        public final int safePublicReferences;
        public final int safeProtectedReferences;
        public final int safePackageReferences;
        public final int safeAtReferences;
        public final int atBackedFieldReferences;
        public final int atBackedMethodReferences;
        public final int distinctMinecraftOwners;
        public final List<String> forbiddenDirectAccesses;
        public final List<String> invalidPrivateAccesses;
        public final List<String> invalidPackageAccesses;
        public final List<String> invalidProtectedAccesses;
        public final List<String> unresolvedAccesses;

        AccessAuditResult(
                int minecraftFieldReferences,
                int minecraftMethodReferences,
                int resolvedReferences,
                int safePublicReferences,
                int safeProtectedReferences,
                int safePackageReferences,
                int safeAtReferences,
                int atBackedFieldReferences,
                int atBackedMethodReferences,
                int distinctMinecraftOwners,
                List<String> forbiddenDirectAccesses,
                List<String> invalidPrivateAccesses,
                List<String> invalidPackageAccesses,
                List<String> invalidProtectedAccesses,
                List<String> unresolvedAccesses) {
            this.minecraftFieldReferences = minecraftFieldReferences;
            this.minecraftMethodReferences = minecraftMethodReferences;
            this.resolvedReferences = resolvedReferences;
            this.safePublicReferences = safePublicReferences;
            this.safeProtectedReferences = safeProtectedReferences;
            this.safePackageReferences = safePackageReferences;
            this.safeAtReferences = safeAtReferences;
            this.atBackedFieldReferences = atBackedFieldReferences;
            this.atBackedMethodReferences = atBackedMethodReferences;
            this.distinctMinecraftOwners = distinctMinecraftOwners;
            this.forbiddenDirectAccesses = List.copyOf(forbiddenDirectAccesses);
            this.invalidPrivateAccesses = List.copyOf(invalidPrivateAccesses);
            this.invalidPackageAccesses = List.copyOf(invalidPackageAccesses);
            this.invalidProtectedAccesses = List.copyOf(invalidProtectedAccesses);
            this.unresolvedAccesses = List.copyOf(unresolvedAccesses);
        }
    }
}
