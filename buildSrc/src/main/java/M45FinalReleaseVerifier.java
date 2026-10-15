import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

/** Packaged-artifact evidence only: no Minecraft classes are initialized. */
public final class M45FinalReleaseVerifier {
    private M45FinalReleaseVerifier() { }

    public static String category(String name) {
        if (name.contains("/command/M") || name.contains("FunnelFixtureTimingMixin")) return "FIXTURE_RUNTIME";
        if (name.contains("RuntimeSmoke") || name.contains("Diagnostic") || name.contains("Trace") || name.contains("Probe")) return "DEBUG_PRODUCTION";
        if (name.contains("/compatibility/") || name.matches(".*/mixin/m\\d+/.*")) return "COMPAT_PRODUCTION";
        return "CORE_PRODUCTION";
    }

    public static Map<String, Object> artifact(File file) throws Exception {
        Map<String, byte[]> classes = new TreeMap<>();
        Map<String, String> owners = new TreeMap<>();
        Map<String, List<String>> packages = new TreeMap<>();
        Map<String, Integer> versions = new TreeMap<>();
        List<Map<String, Object>> services = new ArrayList<>();
        List<String> resources = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        try (JarFile jar = new JarFile(file)) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            for (var entry : Collections.list(jar.entries())) {
                if (entry.isDirectory()) continue;
                if (entries.put(entry.getName(), jar.getInputStream(entry).readAllBytes()) != null) duplicates.add(entry.getName());
            }
            scan(file.getName(), entries, classes, owners, packages, versions, services, resources, duplicates);
        }
        List<Map<String, Object>> classified = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, String> hashes = new TreeMap<>();
        List<String> forbidden = new ArrayList<>();
        List<String> java21 = new ArrayList<>();
        for (var entry : classes.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith("com/simibubi/create/") || name.startsWith("net/minecraft/")
                    || name.startsWith("net/neoforged/") || name.startsWith("net/fabricmc/")) forbidden.add(name);
            if (!name.startsWith("dev/")) continue;
            String category = category(name);
            counts.merge(category, 1, Integer::sum);
            classified.add(Map.of("class", name, "category", category, "owner", owners.get(name)));
            hashes.put(name + ".class", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entry.getValue())));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        List<Map<String, Object>> bootstrap = new ArrayList<>();
        for (var entry : classes.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith("dev/") || category(name).equals("FIXTURE_RUNTIME")) continue;
            ClassNode node = node(entry.getValue());
            for (MethodNode method : node.methods) {
                if (method.desc.contains("dev/ryanhcode/sable/command/M")) edges.add(Map.of("class", name, "method", method.name, "reason", "fixture type in production method descriptor"));
                for (AbstractInsnNode insn : method.instructions) if (insn instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        String reference = argument instanceof org.objectweb.asm.Handle handle ? handle.getOwner() + handle.getDesc()
                                : argument instanceof Type type ? type.getDescriptor() : "";
                        if (reference.contains("dev/ryanhcode/sable/command/M")) edges.add(Map.of("class", name, "method", method.name, "reason", "fixture method reference/type in invokedynamic", "target", reference));
                    }
                }
            }
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && ((Set.of("java/util/List", "java/util/ArrayList", "java/util/SequencedCollection", "java/util/SequencedMap", "java/util/SequencedSet").contains(call.owner)
                        && Set.of("getFirst", "getLast", "reversed", "addFirst", "addLast", "removeFirst", "removeLast").contains(call.name)) || call.owner.equals("java/lang/Thread") && call.name.equals("ofVirtual"))) java21.add(name + ";" + method.name + "->" + call.owner + ";" + call.name + call.desc);
                String owner = insn instanceof MethodInsnNode call ? call.owner : insn instanceof FieldInsnNode field ? field.owner : insn instanceof TypeInsnNode type ? type.desc : "";
                if (!owner.startsWith("dev/ryanhcode/sable/command/M")) continue;
                Map<String, Object> edge = Map.of("class", name, "method", method.name + method.desc, "fixture", owner);
                boolean registration = (name.equals("dev/ryanhcode/sable/forge/SableForge") || name.equals("dev/ryanhcode/sable/command/SableCommand"))
                        && insn instanceof MethodInsnNode call && Set.of("register", "install").contains(call.name)
                        && Set.of("<init>", "registerCommand", "register").contains(method.name);
                if (registration) bootstrap.add(edge); else edges.add(edge);
            }
            for (FieldNode field : node.fields) if (field.desc.contains("dev/ryanhcode/sable/command/M")) edges.add(Map.of("class", name, "field", field.name));
            String pool = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
            if (pool.matches("(?s).*sable_m(?:3[1-9]|4[0-4])_(?:origin|ready|output|glue).*")) edges.add(Map.of("class", name, "reason", "fixture ownership constant"));
        }
        List<String> serviceFailures = new ArrayList<>();
        Set<String> providerKeys = new HashSet<>();
        for (Map<String, Object> service : services) {
            if (!providerKeys.add(service.get("service") + ":" + service.get("provider"))) serviceFailures.add("Duplicate effective provider " + service);
            String provider = service.get("provider").toString().replace('.', '/');
            boolean present = classes.containsKey(provider);
            service.put("providerPresent", present);
            service.put("providerClassOwner", owners.getOrDefault(provider, "EXTERNAL"));
            if (!present) serviceFailures.add(service.toString());
            if (present) {
                ClassNode providerClass = node(classes.get(provider));
                service.put("publicConcrete", (providerClass.access & Opcodes.ACC_PUBLIC) != 0 && (providerClass.access & Opcodes.ACC_ABSTRACT) == 0);
                service.put("publicNoArgConstructor", providerClass.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals("()V") && (m.access & Opcodes.ACC_PUBLIC) != 0));
                service.put("directSuper", providerClass.superName);
                service.put("interfaces", providerClass.interfaces);
                boolean compatible = inherits(classes, provider, service.get("service").toString().replace('.', '/'), new HashSet<>());
                service.put("assignableByPackagedHierarchy", compatible);
                if (!Boolean.TRUE.equals(service.get("publicConcrete")) || !Boolean.TRUE.equals(service.get("publicNoArgConstructor"))
                        || service.get("service").toString().startsWith("dev.ryanhcode.") && !compatible) serviceFailures.add(service.toString());
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("classes", classified); report.put("classificationCounts", counts); report.put("hashes", hashes);
        report.put("allClassCount", classes.size()); report.put("classVersions", versions); report.put("duplicateEntriesOrClasses", duplicates);
        report.put("splitPackages", packages.entrySet().stream().filter(e -> e.getValue().size() > 1).toList().stream().map(e -> Map.of("package", e.getKey(), "owners", e.getValue())).toList());
        report.put("services", services); report.put("serviceFailures", serviceFailures);
        report.put("productionFixtureEdges", edges); report.put("explicitFixtureRegistrationEdges", bootstrap);
        report.put("forbiddenBundledClasses", forbidden); report.put("resources", resources);
        report.put("java21ApiReferences", java21);
        return report;
    }

    private static boolean inherits(Map<String, byte[]> classes, String name, String expected, Set<String> seen) {
        if (name.equals(expected)) return true;
        if (!seen.add(name)) return false;
        byte[] bytes = classes.get(name);
        if (bytes == null && (name.startsWith("java/") || name.startsWith("javax/"))) {
            try (InputStream input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
                if (input != null) bytes = input.readAllBytes();
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        if (bytes == null) return false;
        ClassNode type = node(bytes);
        return type.interfaces.stream().anyMatch(i -> inherits(classes, i, expected, seen))
                || type.superName != null && inherits(classes, type.superName, expected, seen);
    }

    private static void scan(String owner, Map<String, byte[]> entries, Map<String, byte[]> classes,
                             Map<String, String> owners, Map<String, List<String>> packages, Map<String, Integer> versions,
                             List<Map<String, Object>> services, List<String> resources, List<String> duplicates) throws Exception {
        for (var entry : entries.entrySet()) {
            String path = entry.getKey(); byte[] bytes = entry.getValue();
            if (path.endsWith(".class")) {
                int major = (bytes[6] & 255) << 8 | bytes[7] & 255;
                versions.merge(owner + ":" + major, 1, Integer::sum);
                if (major > 61) throw new IllegalStateException("M45 Java >17 " + owner + "!" + path + " major=" + major);
                if (path.startsWith("META-INF/versions/") || path.equals("module-info.class")) continue;
                String name = path.substring(0, path.length() - 6);
                if (classes.putIfAbsent(name, bytes) != null) duplicates.add(owner + "!" + path + " duplicates " + owners.get(name));
                owners.putIfAbsent(name, owner);
                String pkg = name.substring(0, Math.max(0, name.lastIndexOf('/')));
                List<String> pkgOwners = packages.computeIfAbsent(pkg, ignored -> new ArrayList<>());
                if (!pkgOwners.contains(owner)) pkgOwners.add(owner);
            } else if (path.endsWith(".jar")) {
                Map<String, byte[]> nested = new LinkedHashMap<>();
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                    java.util.zip.ZipEntry next;
                    while ((next = zip.getNextEntry()) != null) if (!next.isDirectory() && nested.put(next.getName(), zip.readAllBytes()) != null) duplicates.add(owner + "!" + path + "!" + next.getName());
                }
                scan(owner + "!" + path, nested, classes, owners, packages, versions, services, resources, duplicates);
            } else if (path.startsWith("META-INF/services/")) {
                Set<String> seen = new HashSet<>();
                for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
                    String provider = line.split("#", 2)[0].trim();
                    if (provider.isEmpty()) continue;
                    if (!seen.add(provider)) duplicates.add(owner + "!" + path + ":" + provider);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("service", path.substring("META-INF/services/".length())); row.put("provider", provider); row.put("descriptorOwner", owner); services.add(row);
                }
            } else if (path.contains("mixins.json") || path.contains("refmap") || path.contains("accesstransformer") || path.endsWith("mods.toml") || path.startsWith("data/sable/functions/")) resources.add(owner + "!" + path);
        }
    }

    public static List<Map<String, Object>> mixinInventory(File artifact, Map<?, ?> config, Map<?, ?> refmap) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<?, ?> mappings = refmap.get("mappings") instanceof Map<?, ?> map ? map : Map.of();
        try (JarFile jar = new JarFile(artifact)) {
            Set<String> seen = new HashSet<>();
            for (String side : List.of("mixins", "client", "server")) {
                List<?> names = config.get(side) instanceof List<?> list ? list : List.of();
                for (Object relative : names) {
                    String name = (config.get("package") + "." + relative).replace('.', '/');
                    if (!seen.add(name)) throw new IllegalStateException("M45 duplicate configured mixin " + name);
                    var entry = jar.getJarEntry(name + ".class");
                    if (entry == null) throw new IllegalStateException("M45 stale mixin " + name);
                    ClassNode mixin = node(jar.getInputStream(entry).readAllBytes());
                    List<String> targets = new ArrayList<>();
                    for (AnnotationNode annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) if (annotation.desc.endsWith("/Mixin;")) {
                        for (Object target : values(value(annotation, "value"))) targets.add(((Type) target).getInternalName());
                        for (Object target : values(value(annotation, "targets"))) targets.add(target.toString().replace('.', '/'));
                    }
                    String dependency = name.contains("/compatibility/") ? name.substring(name.indexOf("/compatibility/") + 15).split("/")[0] : "minecraft/sable";
                    String version = "mod-presence (target-pack versions only)";
                    for (AnnotationNode annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) if (annotation.desc.endsWith("/MixinModVersionConstraint;")) version = String.valueOf(value(annotation, "value"));
                    List<Map<String, Object>> operations = new ArrayList<>();
                    for (MethodNode method : mixin.methods) for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                        if (annotation.desc.contains("/injection/") || annotation.desc.contains("/injector/") || annotation.desc.endsWith("/Accessor;") || annotation.desc.endsWith("/Invoker;") || annotation.desc.endsWith("/Overwrite;")) {
                            Map<String, Object> operation = new LinkedHashMap<>();
                            operation.put("handler", method.name + method.desc); operation.put("annotation", annotation.desc);
                            operation.put("parameters", stringify(annotation)); operations.add(operation);
                        }
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("class", name); row.put("targets", targets); row.put("side", side);
                    var milestone = java.util.regex.Pattern.compile("/m(\\d+)/").matcher(name);
                    row.put("milestoneOwner", milestone.find() ? "M" + milestone.group(1) : name.contains("/goggles/") || dependency.equals("tfmg") ? "M44" : name.contains("/residuals/") ? "M43" : name.contains("/sliding_door/") ? "M42" : "retained core/compatibility; source package identifies subsystem");
                    row.put("reason", name.contains("FunnelFixtureTimingMixin") ? "Read-only fixture-owned native-query timing observer" : "Retained " + relative + " integration; exact operations and runtime matches recorded separately");
                    row.put("optionalDependency", dependency); row.put("versionGate", version);
                    row.put("defaultRequire", config.get("injectors")); row.put("operations", operations);
                    row.put("refmap", mappings.containsKey(name) ? mappings.get(name) : Map.of());
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private static Object stringify(Object object) {
        if (object instanceof AnnotationNode a) {
            Map<String, Object> map = new LinkedHashMap<>(); map.put("annotation", a.desc);
            if (a.values != null) for (int i = 0; i < a.values.size(); i += 2) map.put(a.values.get(i).toString(), stringify(a.values.get(i + 1)));
            return map;
        }
        if (object instanceof List<?> list) return list.stream().map(M45FinalReleaseVerifier::stringify).toList();
        if (object instanceof String[] array) return Arrays.asList(array);
        return object == null ? "" : object.toString();
    }

    private static List<?> values(Object object) { return object instanceof List<?> list ? list : List.of(); }
    private static Object value(AnnotationNode a, String key) {
        if (a.values != null) for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals(key)) return a.values.get(i + 1);
        return null;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> result = new ArrayList<>(); if (a != null) result.addAll(a); if (b != null) result.addAll(b); return result;
    }
    private static ClassNode node(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }

    public static void negativeTests() throws Exception {
        if (!category("dev/ryanhcode/sable/command/M43ResidualFixtureCommands$Task").equals("FIXTURE_RUNTIME")
                || !category("dev/ryanhcode/sable/mixin/compatibility/create/behaviour_compatibility/funnel/FunnelFixtureTimingMixin").equals("FIXTURE_RUNTIME")
                || !category("dev/ryanhcode/sable/compatibility/create/seat/SableSeatPassengers").equals("COMPAT_PRODUCTION")) throw new IllegalStateException("M45 role classification regression");
        Map<String, byte[]> classes = new HashMap<>();
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(61, Opcodes.ACC_PUBLIC, "example/Provider", null, "java/lang/Object", new String[]{"example/Service"}); writer.visitEnd();
        classes.put("example/Provider", writer.toByteArray());
        if (!inherits(classes, "example/Provider", "example/Service", new HashSet<>())
                || inherits(classes, "example/Provider", "example/Unrelated", new HashSet<>())) throw new IllegalStateException("M45 service hierarchy negative regression");
        Map<String, Integer> versions = new HashMap<>();
        byte[] future = writer.toByteArray().clone(); future[7] = 65;
        try {
            scan("negative.jar", Map.of("example/Provider.class", future), new HashMap<>(), new HashMap<>(), new HashMap<>(), versions, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            throw new AssertionError("M45 accepted Java21 bytecode");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("Java >17")) throw expected;
        }
        List<String> duplicates = new ArrayList<>();
        Map<String, byte[]> identity = new HashMap<>(); Map<String, String> owners = new HashMap<>();
        Map<String, List<String>> packages = new HashMap<>();
        for (String owner : List.of("one.jar", "two.jar")) scan(owner, Map.of("example/Provider.class", writer.toByteArray()), identity, owners, packages, versions, new ArrayList<>(), new ArrayList<>(), duplicates);
        if (duplicates.size() != 1 || packages.get("example").size() != 2) throw new IllegalStateException("M45 duplicate/split package negative regression");
    }
}
