import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RapierProductionJarMapper {
    private RapierProductionJarMapper() {
    }

    public static Result mapNamedToProduction(File inputJar, File mappingsFile, File outputJar) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        Files.createDirectories(outputJar.toPath().getParent());

        int transformedClasses = 0;
        int memberReferenceRemaps = 0;
        int invokedynamicHandleRemaps = 0;
        Set<String> minecraftReferenceOwners = new TreeSet<>();

        try (ZipFile source = new ZipFile(inputJar);
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(outputJar.toPath()))) {
            List<? extends ZipEntry> entries = entries(source);
            entries.sort((left, right) -> left.getName().compareTo(right.getName()));

            for (ZipEntry sourceEntry : entries) {
                byte[] bytes;
                if (sourceEntry.isDirectory()) {
                    bytes = new byte[0];
                } else {
                    try (InputStream input = source.getInputStream(sourceEntry)) {
                        bytes = input.readAllBytes();
                    }
                }

                if (sourceEntry.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(bytes);
                    CountingRemapper remapper = new CountingRemapper(mappings);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor visitor = new ClassRemapper(writer, remapper);
                    reader.accept(visitor, 0);
                    if (remapper.changed()) {
                        transformedClasses++;
                    }
                    memberReferenceRemaps += remapper.memberReferenceRemaps;
                    invokedynamicHandleRemaps += remapper.handleRemaps;
                    minecraftReferenceOwners.addAll(remapper.minecraftReferenceOwners);
                    bytes = writer.toByteArray();
                }

                ZipEntry outputEntry = new ZipEntry(sourceEntry.getName());
                outputEntry.setTime(0L);
                output.putNextEntry(outputEntry);
                output.write(bytes);
                output.closeEntry();
            }
        }

        ReferenceReport report = referenceReport(outputJar, mappingsFile);
        return new Result(sha256(inputJar), sha256(outputJar), transformedClasses,
                memberReferenceRemaps, invokedynamicHandleRemaps, minecraftReferenceOwners,
                report.namedMinecraftMemberReferences, report.productionMinecraftMemberReferences);
    }

    public static ReferenceReport referenceReport(File jar, File mappingsFile) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        Set<String> minecraftReferenceOwners = new TreeSet<>();
        Set<String> namedMinecraftMemberReferences = new TreeSet<>();
        Set<String> productionMinecraftMemberReferences = new TreeSet<>();

        try (ZipFile source = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                ClassNode node = new ClassNode();
                try (InputStream input = source.getInputStream(entry)) {
                    new ClassReader(input.readAllBytes()).accept(node, 0);
                }
                scanDescriptor(node.superName, minecraftReferenceOwners);
                node.interfaces.forEach(owner -> scanDescriptor((String) owner, minecraftReferenceOwners));
                for (Object fieldObject : node.fields) {
                    org.objectweb.asm.tree.FieldNode field = (org.objectweb.asm.tree.FieldNode) fieldObject;
                    scanDescriptor(field.desc, minecraftReferenceOwners);
                }
                for (Object methodObject : node.methods) {
                    org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) methodObject;
                    scanDescriptor(method.desc, minecraftReferenceOwners);
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode field) {
                            scanReference(field.owner, field.name, field.desc, false, mappings,
                                    minecraftReferenceOwners, namedMinecraftMemberReferences,
                                    productionMinecraftMemberReferences);
                        } else if (instruction instanceof MethodInsnNode called) {
                            scanReference(called.owner, called.name, called.desc, true, mappings,
                                    minecraftReferenceOwners, namedMinecraftMemberReferences,
                                    productionMinecraftMemberReferences);
                        } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                            scanHandle(indy.bsm, mappings, minecraftReferenceOwners,
                                    namedMinecraftMemberReferences, productionMinecraftMemberReferences);
                            for (Object argument : indy.bsmArgs) {
                                scanBootstrapValue(argument, mappings, minecraftReferenceOwners,
                                        namedMinecraftMemberReferences, productionMinecraftMemberReferences);
                            }
                        } else if (instruction instanceof LdcInsnNode ldc) {
                            scanBootstrapValue(ldc.cst, mappings, minecraftReferenceOwners,
                                    namedMinecraftMemberReferences, productionMinecraftMemberReferences);
                        }
                    }
                }
            }
        }

        return new ReferenceReport(minecraftReferenceOwners, namedMinecraftMemberReferences,
                productionMinecraftMemberReferences);
    }

    public static Set<String> classEntries(File jar) throws IOException {
        Set<String> entries = new TreeSet<>();
        try (ZipFile source = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> zipEntries = source.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
    }

    private static void scanHandle(Handle handle, MappingSet mappings, Set<String> minecraftReferenceOwners,
                                   Set<String> namedMinecraftMemberReferences,
                                   Set<String> productionMinecraftMemberReferences) {
        boolean method = switch (handle.getTag()) {
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL,
                    Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE -> true;
            default -> false;
        };
        scanReference(handle.getOwner(), handle.getName(), handle.getDesc(), method, mappings,
                minecraftReferenceOwners, namedMinecraftMemberReferences, productionMinecraftMemberReferences);
    }

    private static void scanBootstrapValue(Object value, MappingSet mappings, Set<String> minecraftReferenceOwners,
                                           Set<String> namedMinecraftMemberReferences,
                                           Set<String> productionMinecraftMemberReferences) {
        if (value instanceof Handle handle) {
            scanHandle(handle, mappings, minecraftReferenceOwners,
                    namedMinecraftMemberReferences, productionMinecraftMemberReferences);
        } else if (value instanceof Type type) {
            scanDescriptor(type.getDescriptor(), minecraftReferenceOwners);
        } else if (value instanceof ConstantDynamic constantDynamic) {
            scanDescriptor(constantDynamic.getDescriptor(), minecraftReferenceOwners);
            scanHandle(constantDynamic.getBootstrapMethod(), mappings, minecraftReferenceOwners,
                    namedMinecraftMemberReferences, productionMinecraftMemberReferences);
            for (int index = 0; index < constantDynamic.getBootstrapMethodArgumentCount(); index++) {
                scanBootstrapValue(constantDynamic.getBootstrapMethodArgument(index), mappings,
                        minecraftReferenceOwners, namedMinecraftMemberReferences,
                        productionMinecraftMemberReferences);
            }
        }
    }

    private static void scanReference(String owner, String name, String descriptor, boolean method,
                                      MappingSet mappings, Set<String> minecraftReferenceOwners,
                                      Set<String> namedMinecraftMemberReferences,
                                      Set<String> productionMinecraftMemberReferences) {
        scanDescriptor(owner, minecraftReferenceOwners);
        scanDescriptor(descriptor, minecraftReferenceOwners);
        if (!owner.startsWith("net/minecraft/") && !owner.startsWith("com/mojang/")) {
            return;
        }
        String mapped = method ? mappings.mapMethodName(owner, name, descriptor) : mappings.mapFieldName(owner, name);
        if (!mapped.equals(name)) {
            namedMinecraftMemberReferences.add(owner + "." + name + descriptor + " -> " + mapped);
        } else if (mappings.isProductionMember(owner, name, descriptor, method)) {
            productionMinecraftMemberReferences.add(owner + "." + name + descriptor);
        }
    }

    private static void scanDescriptor(String descriptor, Set<String> minecraftReferenceOwners) {
        if (descriptor == null) {
            return;
        }
        if (descriptor.startsWith("net/minecraft/") || descriptor.startsWith("com/mojang/")) {
            minecraftReferenceOwners.add(descriptor);
            return;
        }
        for (String token : descriptor.split("[;()\\[ ]+")) {
            if (token.startsWith("L")) {
                token = token.substring(1);
            }
            if (token.startsWith("net/minecraft/") || token.startsWith("com/mojang/")) {
                minecraftReferenceOwners.add(token);
            }
        }
    }

    private static List<? extends ZipEntry> entries(ZipFile file) {
        return Collections.list(file.entries());
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static final class CountingRemapper extends Remapper {
        private final MappingSet mappings;
        private final Set<String> minecraftReferenceOwners = new TreeSet<>();
        private int memberReferenceRemaps;
        private int handleRemaps;

        private CountingRemapper(MappingSet mappings) {
            this.mappings = mappings;
        }

        @Override
        public String map(String internalName) {
            scanDescriptor(internalName, minecraftReferenceOwners);
            return mappings.mapClassName(internalName);
        }

        @Override
        public String mapDesc(String descriptor) {
            scanDescriptor(descriptor, minecraftReferenceOwners);
            return super.mapDesc(descriptor);
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof Handle handle) {
                return mapHandleValue(handle);
            }
            if (value instanceof ConstantDynamic constantDynamic) {
                return mapConstantDynamic(constantDynamic);
            }
            return super.mapValue(value);
        }

        private Handle mapHandleValue(Handle handle) {
            String mappedOwner = mapType(handle.getOwner());
            String mappedDescriptor = handle.getDesc().startsWith("(")
                    ? mapMethodDesc(handle.getDesc())
                    : mapDesc(handle.getDesc());
            String mappedName = mapHandleName(handle);
            if (!mappedOwner.equals(handle.getOwner())
                    || !mappedDescriptor.equals(handle.getDesc())
                    || !mappedName.equals(handle.getName())) {
                if (!mappedName.equals(handle.getName())) {
                    handleRemaps++;
                }
                return new Handle(handle.getTag(), mappedOwner, mappedName,
                        mappedDescriptor, handle.isInterface());
            }
            return handle;
        }

        private ConstantDynamic mapConstantDynamic(ConstantDynamic constantDynamic) {
            String mappedDescriptor = mapDesc(constantDynamic.getDescriptor());
            Handle mappedBootstrap = mapHandleValue(constantDynamic.getBootstrapMethod());
            Object[] mappedArguments = new Object[constantDynamic.getBootstrapMethodArgumentCount()];
            boolean changed = !mappedDescriptor.equals(constantDynamic.getDescriptor())
                    || mappedBootstrap != constantDynamic.getBootstrapMethod();
            for (int index = 0; index < mappedArguments.length; index++) {
                Object original = constantDynamic.getBootstrapMethodArgument(index);
                Object mapped = mapValue(original);
                mappedArguments[index] = mapped;
                changed |= mapped != original;
            }
            if (!changed) {
                return constantDynamic;
            }
            return new ConstantDynamic(constantDynamic.getName(), mappedDescriptor,
                    mappedBootstrap, mappedArguments);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            scanDescriptor(owner, minecraftReferenceOwners);
            scanDescriptor(descriptor, minecraftReferenceOwners);
            String mapped = mappings.mapFieldName(owner, name);
            if (!mapped.equals(name)) {
                memberReferenceRemaps++;
            }
            return mapped;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            scanDescriptor(owner, minecraftReferenceOwners);
            scanDescriptor(descriptor, minecraftReferenceOwners);
            String mapped = mappings.mapMethodName(owner, name, descriptor);
            if (!mapped.equals(name)) {
                memberReferenceRemaps++;
            }
            return mapped;
        }

        private String mapHandleName(Handle handle) {
            return switch (handle.getTag()) {
                case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC ->
                        mappings.mapFieldName(handle.getOwner(), handle.getName());
                case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL,
                        Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE ->
                        mappings.mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc());
                default -> handle.getName();
            };
        }

        private boolean changed() {
            return memberReferenceRemaps > 0 || handleRemaps > 0;
        }
    }

    private static final class MappingSet {
        private final Map<String, String> classMappings;
        private final Map<String, Map<String, String>> fieldMappings;
        private final Map<String, Map<String, String>> methodMappings;
        private final Map<String, Map<String, String>> productionFields;
        private final Map<String, Map<String, String>> productionMethods;

        private MappingSet(Map<String, String> classMappings,
                           Map<String, Map<String, String>> fieldMappings,
                           Map<String, Map<String, String>> methodMappings,
                           Map<String, Map<String, String>> productionFields,
                           Map<String, Map<String, String>> productionMethods) {
            this.classMappings = classMappings;
            this.fieldMappings = fieldMappings;
            this.methodMappings = methodMappings;
            this.productionFields = productionFields;
            this.productionMethods = productionMethods;
        }

        private static MappingSet read(File mappingsFile) throws IOException {
            Map<String, String> classes = new LinkedHashMap<>();
            Map<String, Map<String, String>> fields = new LinkedHashMap<>();
            Map<String, Map<String, String>> methods = new LinkedHashMap<>();
            Map<String, Map<String, String>> productionFields = new LinkedHashMap<>();
            Map<String, Map<String, String>> productionMethods = new LinkedHashMap<>();

            String owner = null;
            for (String line : Files.readAllLines(mappingsFile.toPath(), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("tsrg2")) {
                    continue;
                }
                if (!line.startsWith("\t")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        owner = parts[0];
                        classes.put(parts[0], parts[1]);
                    }
                    continue;
                }
                if (owner == null || line.startsWith("\t\t")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    fields.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(parts[0], parts[1]);
                    productionFields.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(parts[1], parts[0]);
                } else if (parts.length >= 3) {
                    String key = parts[0] + parts[1];
                    methods.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(key, parts[2]);
                    productionMethods.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(parts[2] + parts[1], parts[0]);
                }
            }

            return new MappingSet(classes, fields, methods, productionFields, productionMethods);
        }

        private String mapClassName(String internalName) {
            return classMappings.getOrDefault(internalName, internalName);
        }

        private String mapFieldName(String owner, String name) {
            return fieldMappings.getOrDefault(owner, Collections.emptyMap()).getOrDefault(name, name);
        }

        private String mapMethodName(String owner, String name, String descriptor) {
            return methodMappings.getOrDefault(owner, Collections.emptyMap()).getOrDefault(name + descriptor, name);
        }

        private boolean isProductionMember(String owner, String name, String descriptor, boolean method) {
            if (method) {
                return productionMethods.getOrDefault(owner, Collections.emptyMap()).containsKey(name + descriptor);
            }
            return productionFields.getOrDefault(owner, Collections.emptyMap()).containsKey(name);
        }
    }

    public static final class Result {
        public final String inputSha256;
        public final String outputSha256;
        public final int transformedClasses;
        public final int memberReferenceRemaps;
        public final int invokedynamicHandleRemaps;
        public final Set<String> minecraftReferenceOwners;
        public final Set<String> namedMinecraftMemberReferences;
        public final Set<String> productionMinecraftMemberReferences;

        private Result(String inputSha256, String outputSha256, int transformedClasses,
                       int memberReferenceRemaps, int invokedynamicHandleRemaps,
                       Set<String> minecraftReferenceOwners,
                       Set<String> namedMinecraftMemberReferences,
                       Set<String> productionMinecraftMemberReferences) {
            this.inputSha256 = inputSha256;
            this.outputSha256 = outputSha256;
            this.transformedClasses = transformedClasses;
            this.memberReferenceRemaps = memberReferenceRemaps;
            this.invokedynamicHandleRemaps = invokedynamicHandleRemaps;
            this.minecraftReferenceOwners = Collections.unmodifiableSet(new LinkedHashSet<>(minecraftReferenceOwners));
            this.namedMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(namedMinecraftMemberReferences));
            this.productionMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(productionMinecraftMemberReferences));
        }
    }

    public static final class ReferenceReport {
        public final Set<String> minecraftReferenceOwners;
        public final Set<String> namedMinecraftMemberReferences;
        public final Set<String> productionMinecraftMemberReferences;

        private ReferenceReport(Set<String> minecraftReferenceOwners, Set<String> namedMinecraftMemberReferences,
                                Set<String> productionMinecraftMemberReferences) {
            this.minecraftReferenceOwners = Collections.unmodifiableSet(new LinkedHashSet<>(minecraftReferenceOwners));
            this.namedMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(namedMinecraftMemberReferences));
            this.productionMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(productionMinecraftMemberReferences));
        }
    }
}
