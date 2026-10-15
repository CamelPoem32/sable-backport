import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Read-only pinned-bytecode inventory. Never loads Minecraft or applies gameplay transformations. */
public final class CreateMovementCoverageVerifier {
    private static final String API = "com/simibubi/create/api/behaviour/movement/MovementBehaviour";
    private static final String FIXTURE_OBSERVER = "dev/ryanhcode/sable/mixin/compatibility/create/behaviour_compatibility/funnel/FunnelFixtureTimingMixin";
    private CreateMovementCoverageVerifier() { }

    public static Map<String, Object> inventory(File create) throws Exception {
        try (JarFile jar = new JarFile(create)) {
            Map<String, ClassNode> classes = new TreeMap<>();
            Map<String, String> hashes = new TreeMap<>();
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                byte[] bytes = jar.getInputStream(entry).readAllBytes();
                ClassNode node = node(bytes);
                classes.put(node.name, node);
                hashes.put(node.name, hash(bytes));
            }
            List<Map<String, Object>> behaviours = new ArrayList<>();
            for (ClassNode type : classes.values()) {
                if (type.name.equals(API) || !inherits(classes, type.name, API, new HashSet<>())) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("class", type.name);
                item.put("abstract", (type.access & Opcodes.ACC_ABSTRACT) != 0);
                item.put("super", type.superName);
                item.put("sha256", hashes.get(type.name));
                item.put("methods", methods(type));
                behaviours.add(item);
            }
            List<String> registrations = new ArrayList<>();
            List<String> variants = new ArrayList<>();
            Map<String, String> registrationOwners = new TreeMap<>();
            for (ClassNode type : classes.values()) {
                for (MethodNode method : type.methods) {
                    int occurrence = 0;
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (!(instruction instanceof MethodInsnNode call)) continue;
                        boolean factory = call.owner.equals(API) && call.name.equals("movementBehaviour");
                        boolean defaults = type.name.equals("com/simibubi/create/AllMovementBehaviours")
                                && call.owner.equals("com/simibubi/create/api/registry/SimpleRegistry") && call.name.equals("register");
                        if (factory || defaults) {
                            registrations.add(type.name + ";" + method.name + method.desc + "#" + occurrence++ + "->" + call.owner + ";" + call.name + call.desc);
                            registrationOwners.put(type.name, hashes.get(type.name));
                        }
                        if (type.name.equals("com/simibubi/create/AllBlocks") && (call.name.equals("slidingDoor") || call.name.equals("seat"))) {
                            variants.add(method.name + method.desc + "->" + call.name + call.desc + ":" + nearbyConstants(instruction));
                        }
                    }
                }
            }
            Collections.sort(registrations);
            Collections.sort(variants);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("behaviours", behaviours);
            result.put("registrations", registrations);
            result.put("variantCallsites", variants);
            result.put("registrationOwnerHashes", registrationOwners);
            result.put("interfaceMethods", methods(classes.get(API)));
            result.put("interfaceSha256", hashes.get(API));
            result.put("dyedBlockListSha256", hashes.get("com/simibubi/create/foundation/block/DyedBlockList"));
            result.put("seatBlockVariants", Collections.list(jar.entries()).stream().map(e -> e.getName())
                    .filter(n -> n.matches("assets/create/blockstates/[a-z_]+_seat\\.json"))
                    .map(n -> "create:" + n.substring(n.lastIndexOf('/') + 1, n.length() - 5)).sorted().toList());
            return result;
        }
    }

    private static List<String> nearbyConstants(AbstractInsnNode instruction) {
        List<String> values = new ArrayList<>();
        for (int i = 0; instruction != null && i < 8; i++, instruction = instruction.getPrevious()) {
            if (instruction instanceof LdcInsnNode constant) values.add(String.valueOf(constant.cst));
            if (instruction instanceof FieldInsnNode field) values.add(field.owner + ";" + field.name + field.desc);
        }
        return values;
    }

    public static String fingerprint(Object value) throws Exception {
        return hash(canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(key.toString(), item));
            StringBuilder text = new StringBuilder("m[");
            sorted.forEach((key, item) -> text.append(canonical(key)).append(canonical(item)));
            return text.append(']').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder text = new StringBuilder("l[");
            list.forEach(item -> text.append(canonical(item)));
            return text.append(']').toString();
        }
        String text = String.valueOf(value);
        return "s" + text.length() + ":" + text;
    }

    private static List<Map<String, Object>> methods(ClassNode type) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MethodNode method : type.methods) {
            Set<String> calls = new TreeSet<>();
            Set<String> fields = new TreeSet<>();
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) calls.add(call.owner + ";" + call.name + call.desc);
                if (instruction instanceof FieldInsnNode field) fields.add(field.owner + ";" + field.name + ":" + field.desc);
                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) if (argument instanceof org.objectweb.asm.Handle handle) {
                        calls.add(handle.getOwner() + ";" + handle.getName() + handle.getDesc());
                    }
                }
            }
            result.add(Map.of("method", method.name + method.desc, "calls", new ArrayList<>(calls), "fields", new ArrayList<>(fields)));
        }
        return result;
    }

    public static Map<String, String> productionHashes(File artifact) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (JarFile jar = new JarFile(artifact)) {
            for (var entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class") || !name.startsWith("dev/") || name.contains("/command/")) continue;
                result.put(name, hash(jar.getInputStream(entry).readAllBytes()));
            }
        }
        return result;
    }

    public static Map<String, Object> mixins(File artifact, File create, File minecraft, List<File> dependencies, Map<?, ?> config, Map<?, ?> refmap) throws Exception {
        List<File> files = new ArrayList<>(List.of(artifact, create, minecraft));
        files.addAll(dependencies.stream().filter(f -> f.isFile() && f.getName().endsWith(".jar")).toList());
        try (Library library = new Library(files.toArray(File[]::new))) {
            List<Map<String, Object>> checks = new ArrayList<>();
            List<String> exclusions = new ArrayList<>();
            Map<?, ?> references = map(refmap.get("mappings"));
            for (String side : List.of("mixins", "client", "server")) for (Object relative : list(config.get(side))) {
                String name = (config.get("package") + "." + relative).replace('.', '/');
                ClassNode mixin = library.require(name);
                AnnotationNode declaration = annotation(mixin, "Mixin");
                List<String> targets = new ArrayList<>();
                for (Object target : list(value(declaration, "value"))) targets.add(((Type) target).getInternalName());
                for (Object target : list(value(declaration, "targets"))) targets.add(target.toString().replace('.', '/'));
                boolean relevant = relative.toString().contains("compatibility.create.") || relative.toString().matches("m(?:11|17|18|19|20|24|28|29|39)\\..*")
                        || targets.stream().anyMatch(t -> t.startsWith("com/simibubi/create/"));
                if (!relevant) continue;
                for (String targetName : targets) {
                    ClassNode target = library.require(targetName);
                    Map<?, ?> mappings = map(references.get(mixin.name));
                    for (MethodNode handler : mixin.methods) for (AnnotationNode operation : annotations(handler)) {
                        String kind = Type.getType(operation.desc).getClassName();
                        if (kind.endsWith(".Accessor")) {
                            String raw = String.valueOf(value(operation, "value"));
                            String member = resolve(raw, mappings);
                            if (member.contains(":")) member = member.substring(0, member.indexOf(':'));
                            Type fieldType = Type.getReturnType(handler.desc).equals(Type.VOID_TYPE)
                                    ? Type.getArgumentTypes(handler.desc)[0] : Type.getReturnType(handler.desc);
                            if (!library.field(target, member, fieldType.getDescriptor(), new HashSet<>())) {
                                throw new IllegalStateException("M41 missing accessor field " + name + ";" + raw + ":" + fieldType);
                            }
                            checks.add(Map.of("mixin", name, "handler", handler.name + handler.desc, "target", targetName,
                                    "accessor", member + ":" + fieldType.getDescriptor()));
                            continue;
                        }
                        if (kind.endsWith(".Invoker")) {
                            String raw = String.valueOf(value(operation, "value"));
                            String selector = resolve(raw, mappings);
                            if (!selector.contains("(")) selector += handler.desc;
                            if (!library.method(target, selector, new HashSet<>())) {
                                throw new IllegalStateException("M41 missing invoker method " + name + ";" + selector);
                            }
                            checks.add(Map.of("mixin", name, "handler", handler.name + handler.desc, "target", targetName, "invoker", selector));
                            continue;
                        }
                        if (kind.endsWith(".Overwrite")) {
                            String selector = resolve(handler.name + handler.desc, mappings);
                            if (target.methods.stream().noneMatch(m -> matches(m, selector))) throw new IllegalStateException("M41 missing overwrite " + name + ";" + selector);
                            checks.add(Map.of("mixin", name, "handler", handler.name + handler.desc, "target", targetName, "overwrite", selector));
                            continue;
                        }
                        if (!(kind.startsWith("org.spongepowered.asm.mixin.injection.") || kind.startsWith("com.llamalad7.mixinextras.injector."))) continue;
                        if (kind.endsWith(".At") || kind.endsWith(".Slice") || kind.endsWith(".Constant")) continue;
                        List<String> selectors = list(value(operation, "method")).stream().map(Object::toString).toList();
                        if (selectors.isEmpty()) continue;
                        Map<String, MethodNode> selected = new LinkedHashMap<>();
                        for (String raw : selectors) {
                            String selector = resolve(raw, mappings);
                            for (MethodNode method : target.methods) if (matches(method, selector)) selected.put(method.name + method.desc, method);
                        }
                        if (selected.isEmpty()) throw new IllegalStateException("M41 missing target method " + name + ";" + handler.name + " target=" + targetName + " selectors=" + selectors);
                        List<AnnotationNode> points = points(value(operation, "at"));
                        if (kind.endsWith("WrapMethod")) points = List.of();
                        for (AnnotationNode point : points) {
                            String at = String.valueOf(value(point, "value"));
                            String member = value(point, "target") == null ? "" : resolve(value(point, "target").toString(), mappings);
                            int ordinal = value(point, "ordinal") instanceof Integer index ? index : -1;
                            if (selected.values().stream().allMatch(m -> count(m, at, member) >= 0)
                                    && selected.values().stream().noneMatch(m -> count(m, at, member) > Math.max(0, ordinal))) {
                                throw new IllegalStateException("M41 injection has no eligible callsite " + name + ";" + handler.name + " at=" + at + ":" + member);
                            }
                        }
                        for (MethodNode method : selected.values()) {
                            if (points.isEmpty()) checks.add(check(name, handler, targetName, method, kind, "METHOD", 1));
                            for (AnnotationNode point : points) {
                                String at = String.valueOf(value(point, "value"));
                                String member = value(point, "target") == null ? "" : resolve(value(point, "target").toString(), mappings);
                                int count = count(method, at, member);
                                if (count < 0) {
                                    exclusions.add(name + ";" + handler.name + ":" + at + ":" + member);
                                    continue;
                                }
                                Object ordinal = value(point, "ordinal");
                                if (selected.size() == 1 && (count == 0 || ordinal instanceof Integer index && index >= 0 && index >= count)) {
                                    // Broad selectors also match compiler bridges. Eligibility is checked across the selected set above.
                                    throw new IllegalStateException("M41 missing injection point " + name + ";" + handler.name + " method=" + method.name + method.desc + " at=" + at + ":" + member + " matches=" + count);
                                }
                                checks.add(check(name, handler, targetName, method, kind, at + ":" + member, count));
                            }
                        }
                    }
                }
            }
            return Map.of("checks", checks, "specializedPoints", exclusions, "targetFingerprints", library.fingerprints);
        }
    }

    private static Map<String, Object> check(String mixin, MethodNode handler, String target, MethodNode method, String kind, String at, int count) {
        return Map.of("mixin", mixin, "handler", handler.name + handler.desc, "target", target,
                "method", method.name + method.desc, "injector", kind, "at", at, "matches", count);
    }

    private static boolean matches(MethodNode method, String selector) {
        if (selector.startsWith("L") && selector.contains(";")) selector = selector.substring(selector.indexOf(';') + 1);
        return selector.equals(method.name) || selector.equals(method.name + method.desc)
                || selector.endsWith("*") && method.name.startsWith(selector.substring(0, selector.length() - 1));
    }

    private static int count(MethodNode method, String at, String member) {
        if (at.equals("HEAD")) return 1;
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (at.equals("RETURN") || at.equals("TAIL")) {
                if (instruction.getOpcode() >= Opcodes.IRETURN && instruction.getOpcode() <= Opcodes.RETURN) count++;
            } else if (at.equals("INVOKE") || at.equals("INVOKE_ASSIGN")) {
                if (instruction instanceof MethodInsnNode call && ("L" + call.owner + ";" + call.name + call.desc).equals(member)) count++;
            } else if (at.equals("FIELD")) {
                if (instruction instanceof FieldInsnNode field && ("L" + field.owner + ";" + field.name + ":" + field.desc).equals(member)) count++;
            } else if (at.equals("NEW")) {
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                        && (type.desc.equals(member) || ("L" + type.desc + ";").equals(member))) count++;
            } else return -1;
        }
        return count;
    }

    public static void negativeTests() {
        MethodNode method = new MethodNode();
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "owner", "boundary", "(Ljava/util/Collection;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        if (count(method, "INVOKE", "Lowner;boundary(Ljava/util/Collection;)V") != 1
                || count(method, "INVOKE", "Lowner;boundary(Ljava/util/List;)V") != 0) {
            throw new IllegalStateException("M41 exact descriptor negative test failed");
        }
        method.instructions.remove(method.instructions.getFirst());
        if (count(method, "INVOKE", "Lowner;boundary(Ljava/util/Collection;)V") != 0) {
            throw new IllegalStateException("M41 removed callsite negative test failed");
        }
        Map<String, ClassNode> classes = new HashMap<>();
        ClassNode base = new ClassNode(); base.name = "base"; base.interfaces.add(API); classes.put(base.name, base);
        ClassNode anonymous = new ClassNode(); anonymous.name = "new$1"; anonymous.superName = base.name; classes.put(anonymous.name, anonymous);
        if (!inherits(classes, anonymous.name, API, new HashSet<>())) throw new IllegalStateException("M41 inherited/anonymous discovery negative test failed");
    }

    public static int productionBoundary(File artifact) throws Exception {
        int checked = 0;
        try (JarFile jar = new JarFile(artifact)) {
            for (var entry : Collections.list(jar.entries())) {
                String path = entry.getName();
                if (!path.endsWith(".class") || !(path.contains("/compatibility/create/") || path.contains("/mixin/m"))) continue;
                byte[] bytes = jar.getInputStream(entry).readAllBytes();
                ClassNode type = node(bytes);
                if (type.name.equals(FIXTURE_OBSERVER)) {
                    Set<String> calls = new TreeSet<>();
                    for (var method : methods(type)) calls.addAll((List<String>) method.get("calls"));
                    if (calls.stream().filter(c -> c.contains("/command/")).count() != 1
                            || calls.stream().noneMatch(c -> c.contains("M37FunnelFixtureCommands;observeNativeSucc"))) {
                        throw new IllegalStateException("M41 fixture-only observer grew a production dependency");
                    }
                    continue;
                }
                String constants = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (constants.matches("(?s).*dev[/.]ryanhcode[/.]sable[/.]command[/.]M(?:3[1-9]|4[0-3])[A-Za-z]*Fixture[A-Za-z]*.*")
                        || constants.matches("(?s).*sable_m(?:3[1-9]|4[0-3])_(?:origin|ready|output|glue).*")) {
                    throw new IllegalStateException("M41 production depends on fixture ownership/state " + type.name);
                }
                checked++;
            }
        }
        return checked;
    }

    private static boolean inherits(Map<String, ClassNode> classes, String name, String expected, Set<String> seen) {
        if (name == null || !seen.add(name)) return false;
        if (name.equals(expected)) return true;
        ClassNode node = classes.get(name);
        if (node == null) return false;
        if (inherits(classes, node.superName, expected, seen)) return true;
        return node.interfaces.stream().anyMatch(i -> inherits(classes, i, expected, seen));
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }
    private static AnnotationNode annotation(ClassNode type, String name) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (type.visibleAnnotations != null) annotations.addAll(type.visibleAnnotations);
        if (type.invisibleAnnotations != null) annotations.addAll(type.invisibleAnnotations);
        return annotations.stream().filter(a -> a.desc.endsWith("/" + name + ";")).findFirst().orElse(null);
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static List<AnnotationNode> points(Object object) {
        if (object instanceof AnnotationNode point) return List.of(point);
        return list(object).stream().filter(AnnotationNode.class::isInstance).map(AnnotationNode.class::cast).toList();
    }
    private static List<?> list(Object value) { return value instanceof List<?> values ? values : List.of(); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> values ? values : Map.of(); }
    private static String resolve(String selector, Map<?, ?> references) { return references.containsKey(selector) ? references.get(selector).toString() : selector; }

    private static final class Library implements AutoCloseable {
        private final List<JarFile> jars = new ArrayList<>();
        private final Map<String, ClassNode> cache = new HashMap<>();
        private final Map<String, String> fingerprints = new TreeMap<>();
        private final Map<String, byte[]> embedded = new HashMap<>();
        Library(File... files) throws IOException {
            for (File file : files) jars.add(new JarFile(file));
            // Create's embedded Flywheel/Ponder are production namespace; Gradle's mapped-dev copies are not.
            if (files.length >= 2) for (var entry : Collections.list(jars.get(1).entries())) {
                if (!entry.getName().startsWith("META-INF/") || !entry.getName().endsWith(".jar")) continue;
                try (var zip = new java.util.zip.ZipInputStream(jars.get(1).getInputStream(entry))) {
                    java.util.zip.ZipEntry nested;
                    while ((nested = zip.getNextEntry()) != null) if (nested.getName().endsWith(".class")) {
                        embedded.putIfAbsent(nested.getName(), zip.readAllBytes());
                    }
                }
            }
        }
        ClassNode require(String name) throws IOException {
            if (cache.containsKey(name)) return cache.get(name);
            for (int i = 0; i < jars.size(); i++) {
                if (i == 3 && embedded.containsKey(name + ".class")) return remember(name, embedded.get(name + ".class"));
                JarFile jar = jars.get(i);
                var entry = jar.getJarEntry(name + ".class");
                if (entry == null) continue;
                return remember(name, jar.getInputStream(entry).readAllBytes());
            }
            if (embedded.containsKey(name + ".class")) return remember(name, embedded.get(name + ".class"));
            throw new IllegalStateException("M41 packaged/runtime target missing " + name);
        }
        private ClassNode remember(String name, byte[] bytes) throws IOException {
            ClassNode node = node(bytes);
            try { fingerprints.put(name, hash(bytes)); } catch (Exception failure) { throw new IOException(failure); }
            cache.put(name, node);
            return node;
        }
        boolean field(ClassNode target, String name, String desc, Set<String> seen) throws IOException {
            if (!seen.add(target.name)) return false;
            if (target.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))) return true;
            return target.superName != null && !target.superName.equals("java/lang/Object") && field(require(target.superName), name, desc, seen);
        }
        boolean method(ClassNode target, String selector, Set<String> seen) throws IOException {
            if (!seen.add(target.name)) return false;
            if (target.methods.stream().anyMatch(m -> matches(m, selector))) return true;
            return target.superName != null && !target.superName.equals("java/lang/Object") && method(require(target.superName), selector, seen);
        }
        public void close() throws IOException { for (JarFile jar : jars) jar.close(); }
    }
}
