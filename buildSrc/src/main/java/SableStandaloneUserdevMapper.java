import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class SableStandaloneUserdevMapper {

    private SableStandaloneUserdevMapper() {
    }

    public static Result map(File inputJar, File mappingsFile, File outputJar) throws IOException {
        return map(inputJar, mappingsFile, outputJar, Collections.emptyList());
    }

    public static Result map(File inputJar, File mappingsFile, File outputJar,
                             Iterable<File> hierarchyClasspath) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        Files.createDirectories(outputJar.toPath().getParent());

        int transformedClasses = 0;
        int declarationRemaps = 0;
        int referenceRemaps = 0;
        int selfReferenceRemaps = 0;
        int annotationRemaps = 0;
        int hierarchyDeclarationRemaps = 0;
        int hierarchyReferenceRemaps = 0;
        int bridgeHierarchyDeclarationRemaps = 0;
        int bridgeHierarchyReferenceRemaps = 0;
        int handleReferenceRemaps = 0;
        int constantDynamicHandleReferenceRemaps = 0;

        try (ZipFile source = new ZipFile(inputJar);
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(outputJar.toPath()))) {
            List<? extends ZipEntry> entries = entries(source);
            entries.sort((left, right) -> left.getName().compareTo(right.getName()));
            Map<String, byte[]> contentsByEntry = new LinkedHashMap<>();
            Map<String, ClassNode> sableClasses = new LinkedHashMap<>();

            for (ZipEntry sourceEntry : entries) {
                byte[] bytes;
                if (sourceEntry.isDirectory()) {
                    bytes = new byte[0];
                } else {
                    try (InputStream input = source.getInputStream(sourceEntry)) {
                        bytes = input.readAllBytes();
                    }
                }
                contentsByEntry.put(sourceEntry.getName(), bytes);
                if (sourceEntry.getName().endsWith(".class")
                        && sourceEntry.getName().startsWith("dev/ryanhcode/sable/")) {
                    ClassNode node = readClass(bytes);
                    sableClasses.put(node.name, node);
                }
            }

            Map<String, ClassNode> hierarchyClasses = readHierarchyClasses(hierarchyClasspath);
            Map<String, ClassNode> allClasses = new LinkedHashMap<>(hierarchyClasses);
            allClasses.putAll(sableClasses);
            Map<String, Map<String, String>> hierarchyMethodRemaps =
                    hierarchyMethodDeclarationRemaps(sableClasses, hierarchyClasses, mappings);
            Map<String, Set<String>> bridgeHierarchyMethodRemaps =
                    bridgeHierarchyMethodRemaps(sableClasses, hierarchyMethodRemaps);
            Map<String, Set<String>> completeHierarchyCache = new HashMap<>();

            for (ZipEntry sourceEntry : entries) {
                byte[] bytes = contentsByEntry.get(sourceEntry.getName());

                if (sourceEntry.getName().endsWith(".class")
                        && sourceEntry.getName().startsWith("dev/ryanhcode/sable/")) {
                    ClassMapResult classResult = mapClass(bytes, mappings, hierarchyMethodRemaps,
                            bridgeHierarchyMethodRemaps, allClasses, completeHierarchyCache);
                    bytes = classResult.bytes;
                    if (classResult.changed()) {
                        transformedClasses++;
                        declarationRemaps += classResult.declarationRemaps;
                        referenceRemaps += classResult.referenceRemaps;
                        selfReferenceRemaps += classResult.selfReferenceRemaps;
                        annotationRemaps += classResult.annotationRemaps;
                        hierarchyDeclarationRemaps += classResult.hierarchyDeclarationRemaps;
                        hierarchyReferenceRemaps += classResult.hierarchyReferenceRemaps;
                        bridgeHierarchyDeclarationRemaps += classResult.bridgeHierarchyDeclarationRemaps;
                        bridgeHierarchyReferenceRemaps += classResult.bridgeHierarchyReferenceRemaps;
                        handleReferenceRemaps += classResult.handleReferenceRemaps;
                        constantDynamicHandleReferenceRemaps += classResult.constantDynamicHandleReferenceRemaps;
                    }
                }

                ZipEntry outputEntry = new ZipEntry(sourceEntry.getName());
                outputEntry.setTime(0L);
                output.putNextEntry(outputEntry);
                output.write(bytes);
                output.closeEntry();
            }
        }

        return new Result(
                sha256(inputJar),
                transformedClasses,
                declarationRemaps,
                referenceRemaps,
                selfReferenceRemaps,
                annotationRemaps,
                hierarchyDeclarationRemaps,
                hierarchyReferenceRemaps,
                bridgeHierarchyDeclarationRemaps,
                bridgeHierarchyReferenceRemaps,
                handleReferenceRemaps,
                constantDynamicHandleReferenceRemaps,
                staleHierarchyMethodDeclarations(outputJar, mappingsFile, hierarchyClasspath).size(),
                staleHierarchyMethodReferences(outputJar, mappingsFile, hierarchyClasspath).size()
        );
    }

    public static ClassMembers classMembers(File jar, String classEntryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(classEntryName);
            if (entry == null) {
                throw new IOException("Missing class " + classEntryName + " in " + jar);
            }
            byte[] bytes;
            try (InputStream input = zip.getInputStream(entry)) {
                bytes = input.readAllBytes();
            }
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            Set<String> fields = new LinkedHashSet<>();
            for (FieldNode field : node.fields) {
                fields.add(field.name);
            }
            Set<String> methods = new LinkedHashSet<>();
            for (MethodNode method : node.methods) {
                methods.add(method.name + method.desc);
            }
            return new ClassMembers(fields, methods);
        }
    }

    public static String sha256Hex(File file) throws IOException {
        return sha256(file);
    }

    public static ClassReferences classReferences(File jar, String classEntryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(classEntryName);
            if (entry == null) {
                throw new IOException("Missing class " + classEntryName + " in " + jar);
            }
            byte[] bytes;
            try (InputStream input = zip.getInputStream(entry)) {
                bytes = input.readAllBytes();
            }
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            Set<String> fieldReferences = new LinkedHashSet<>();
            Set<String> methodReferences = new LinkedHashSet<>();
            Set<String> handleReferences = new LinkedHashSet<>();
            Set<String> constantDynamicHandleReferences = new LinkedHashSet<>();
            Set<String> annotationValues = new LinkedHashSet<>();
            collectAnnotationValues(node.visibleAnnotations, annotationValues);
            collectAnnotationValues(node.invisibleAnnotations, annotationValues);
            for (MethodNode method : node.methods) {
                collectAnnotationValues(method.visibleAnnotations, annotationValues);
                collectAnnotationValues(method.invisibleAnnotations, annotationValues);
                collectAnnotationValues(method.visibleParameterAnnotations, annotationValues);
                collectAnnotationValues(method.invisibleParameterAnnotations, annotationValues);
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        fieldReferences.add(field.owner + "." + field.name + ":" + field.desc);
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode called = (MethodInsnNode) instruction;
                        methodReferences.add(called.owner + "." + called.name + called.desc);
                    } else if (instruction instanceof InvokeDynamicInsnNode) {
                        InvokeDynamicInsnNode invokedynamic = (InvokeDynamicInsnNode) instruction;
                        collectHandleReference(invokedynamic.bsm, handleReferences);
                        for (Object argument : invokedynamic.bsmArgs) {
                            collectBootstrapReferences(argument, handleReferences, constantDynamicHandleReferences);
                        }
                    } else if (instruction instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) instruction;
                        collectBootstrapReferences(ldc.cst, handleReferences, constantDynamicHandleReferences);
                    }
                }
            }
            for (FieldNode field : node.fields) {
                collectAnnotationValues(field.visibleAnnotations, annotationValues);
                collectAnnotationValues(field.invisibleAnnotations, annotationValues);
            }
            return new ClassReferences(fieldReferences, methodReferences, handleReferences,
                    constantDynamicHandleReferences, annotationValues);
        }
    }

    public static List<String> staleHierarchyMethodDeclarations(File jar, File mappingsFile) throws IOException {
        return staleHierarchyMethodDeclarations(jar, mappingsFile, Collections.emptyList());
    }

    public static List<String> staleHierarchyMethodDeclarations(
            File jar,
            File mappingsFile,
            Iterable<File> hierarchyClasspath) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        Map<String, ClassNode> sableClasses = readSableClasses(jar);
        Map<String, ClassNode> hierarchyClasses = readHierarchyClasses(hierarchyClasspath);
        Map<String, Map<String, String>> remaps =
                hierarchyMethodDeclarationRemaps(sableClasses, hierarchyClasses, mappings);
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, ClassNode> entry : sableClasses.entrySet()) {
            Map<String, String> classRemaps = remaps.getOrDefault(entry.getKey(), Collections.emptyMap());
            for (MethodNode method : entry.getValue().methods) {
                String key = method.name + method.desc;
                if (classRemaps.containsKey(key)) {
                    stale.add(entry.getKey() + "." + key + " -> " + classRemaps.get(key));
                }
            }
        }
        return stale;
    }

    public static List<String> staleHierarchyMethodReferences(File jar, File mappingsFile) throws IOException {
        return staleHierarchyMethodReferences(jar, mappingsFile, Collections.emptyList());
    }

    public static List<String> staleHierarchyMethodReferences(
            File jar,
            File mappingsFile,
            Iterable<File> hierarchyClasspath) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        Map<String, ClassNode> sableClasses = readSableClasses(jar);
        Map<String, ClassNode> hierarchyClasses = readHierarchyClasses(hierarchyClasspath);
        Map<String, Map<String, String>> remaps =
                hierarchyMethodDeclarationRemaps(sableClasses, hierarchyClasses, mappings);
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, ClassNode> entry : sableClasses.entrySet()) {
            for (MethodNode method : entry.getValue().methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode called = (MethodInsnNode) instruction;
                        String key = called.name + called.desc;
                        if (remaps.getOrDefault(called.owner, Collections.emptyMap()).containsKey(key)) {
                            stale.add(entry.getKey() + "." + method.name + method.desc
                                    + " -> " + called.owner + "." + key);
                        }
                    }
                }
            }
        }
        return stale;
    }

    public static StaleMemberReferenceScan staleKnownMemberReferences(
            File jar,
            File mappingsFile,
            Iterable<File> hierarchyClasspath) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        Map<String, ClassNode> sableClasses = readSableClasses(jar);
        Map<String, ClassNode> hierarchyClasses = readHierarchyClasses(hierarchyClasspath);
        Map<String, ClassNode> allClasses = new LinkedHashMap<>(hierarchyClasses);
        allClasses.putAll(sableClasses);
        Map<String, Map<String, String>> hierarchyMethodRemaps =
                hierarchyMethodDeclarationRemaps(sableClasses, hierarchyClasses, mappings);
        Map<String, Set<String>> completeHierarchyCache = new HashMap<>();
        List<String> stale = new ArrayList<>();
        int ordinary = 0;
        int handles = 0;
        int constantDynamicHandles = 0;
        for (Map.Entry<String, ClassNode> entry : sableClasses.entrySet()) {
            for (MethodNode method : entry.getValue().methods) {
                String methodOwner = entry.getKey() + "." + method.name + method.desc;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (isStaleMappedField(field.owner, field.name, mappings)) {
                            ordinary++;
                            stale.add(methodOwner + " -> field " + field.owner + "." + field.name + ":" + field.desc);
                        }
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode called = (MethodInsnNode) instruction;
                        if (isStaleMappedMethod(
                                called.owner,
                                called.name,
                                called.desc,
                                mappings,
                                hierarchyMethodRemaps,
                                allClasses,
                                completeHierarchyCache)) {
                            ordinary++;
                            stale.add(methodOwner + " -> method " + called.owner + "." + called.name + called.desc);
                        }
                    } else if (instruction instanceof InvokeDynamicInsnNode) {
                        InvokeDynamicInsnNode invokedynamic = (InvokeDynamicInsnNode) instruction;
                        if (isStaleMappedHandle(invokedynamic.bsm, mappings, hierarchyMethodRemaps,
                                allClasses, completeHierarchyCache)) {
                            handles++;
                            stale.add(methodOwner + " -> indy bsm " + handleText(invokedynamic.bsm));
                        }
                        for (Object argument : invokedynamic.bsmArgs) {
                            StaleBootstrapScan argumentScan = staleBootstrapReferences(
                                    argument,
                                    false,
                                    mappings,
                                    hierarchyMethodRemaps,
                                    allClasses,
                                    completeHierarchyCache);
                            handles += argumentScan.handles;
                            constantDynamicHandles += argumentScan.constantDynamicHandles;
                            for (String reference : argumentScan.references) {
                                stale.add(methodOwner + " -> indy arg " + reference);
                            }
                        }
                    } else if (instruction instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) instruction;
                        StaleBootstrapScan constantScan = staleBootstrapReferences(
                                ldc.cst,
                                true,
                                mappings,
                                hierarchyMethodRemaps,
                                allClasses,
                                completeHierarchyCache);
                        constantDynamicHandles += constantScan.handles + constantScan.constantDynamicHandles;
                        for (String reference : constantScan.references) {
                            stale.add(methodOwner + " -> ldc " + reference);
                        }
                    }
                }
            }
        }
        return new StaleMemberReferenceScan(ordinary, handles, constantDynamicHandles, stale);
    }

    private static ClassMapResult mapClass(byte[] bytes, MappingSet mappings,
                                           Map<String, Map<String, String>> hierarchyMethodRemaps,
                                           Map<String, Set<String>> bridgeHierarchyMethodRemaps,
                                           Map<String, ClassNode> allClasses,
                                           Map<String, Set<String>> completeHierarchyCache) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        Set<String> mixinTargets = mixinTargets(node);
        int declarationRemaps = 0;
        int referenceRemaps = 0;
        int selfReferenceRemaps = 0;
        int annotationRemaps = 0;
        int hierarchyDeclarationRemaps = 0;
        int hierarchyReferenceRemaps = 0;
        int bridgeHierarchyDeclarationRemaps = 0;
        int bridgeHierarchyReferenceRemaps = 0;
        int handleReferenceRemaps = 0;
        int constantDynamicHandleReferenceRemaps = 0;
        Map<String, String> selfFieldRemaps = new LinkedHashMap<>();
        Map<String, String> selfMethodRemaps = new LinkedHashMap<>();
        Map<String, String> classHierarchyMethodRemaps =
                hierarchyMethodRemaps.getOrDefault(node.name, Collections.emptyMap());
        Set<String> classBridgeHierarchyMethodRemaps =
                bridgeHierarchyMethodRemaps.getOrDefault(node.name, Collections.emptySet());

        for (FieldNode field : node.fields) {
            String mapped = mapMixinFieldDeclaration(mixinTargets, field.name, mappings);
            if (!mapped.equals(field.name)) {
                selfFieldRemaps.put(field.name, mapped);
                field.name = mapped;
                declarationRemaps++;
            }
            annotationRemaps += mapAnnotations(field.visibleAnnotations, mixinTargets, mappings);
            annotationRemaps += mapAnnotations(field.invisibleAnnotations, mixinTargets, mappings);
        }

        for (MethodNode method : node.methods) {
            String key = method.name + method.desc;
            String mapped = classHierarchyMethodRemaps.getOrDefault(
                    key,
                    mapMixinMethodDeclaration(mixinTargets, method.name, method.desc, mappings)
            );
            if (!mapped.equals(method.name)) {
                selfMethodRemaps.put(key, mapped);
                method.name = mapped;
                declarationRemaps++;
                if (classHierarchyMethodRemaps.containsKey(key)) {
                    hierarchyDeclarationRemaps++;
                    if (classBridgeHierarchyMethodRemaps.contains(key)) {
                        bridgeHierarchyDeclarationRemaps++;
                    }
                }
            }
        }

        for (MethodNode method : node.methods) {
            annotationRemaps += mapAnnotations(method.visibleAnnotations, mixinTargets, mappings);
            annotationRemaps += mapAnnotations(method.invisibleAnnotations, mixinTargets, mappings);
            annotationRemaps += mapAnnotations(method.visibleParameterAnnotations, mixinTargets, mappings);
            annotationRemaps += mapAnnotations(method.invisibleParameterAnnotations, mixinTargets, mappings);

            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    boolean selfReference = field.owner.equals(node.name) && selfFieldRemaps.containsKey(field.name);
                    String mappedName = selfReference
                            ? selfFieldRemaps.get(field.name)
                            : mappings.mapField(field.owner, field.name);
                    if (!mappedName.equals(field.name)) {
                        field.name = mappedName;
                        referenceRemaps++;
                        if (selfReference) {
                            selfReferenceRemaps++;
                        }
                    }
                } else if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode called = (MethodInsnNode) instruction;
                    String selfKey = called.name + called.desc;
                    boolean selfReference = called.owner.equals(node.name) && selfMethodRemaps.containsKey(selfKey);
                    boolean hierarchyReference = !selfReference
                            && hierarchyMethodRemaps.getOrDefault(called.owner, Collections.emptyMap())
                            .containsKey(selfKey);
                    boolean bridgeHierarchyReference = selfReference
                            ? classBridgeHierarchyMethodRemaps.contains(selfKey)
                            : hierarchyReference
                            && bridgeHierarchyMethodRemaps.getOrDefault(called.owner, Collections.emptySet())
                            .contains(selfKey);
                    String mappedName = selfReference
                            ? selfMethodRemaps.get(selfKey)
                            : hierarchyReference
                            ? hierarchyMethodRemaps.get(called.owner).get(selfKey)
                            : mappings.mapMethod(called.owner, called.name, called.desc);
                    if (!mappedName.equals(called.name)) {
                        called.name = mappedName;
                        referenceRemaps++;
                        if (selfReference) {
                            selfReferenceRemaps++;
                        }
                        if (hierarchyReference) {
                            hierarchyReferenceRemaps++;
                        }
                        if (bridgeHierarchyReference) {
                            bridgeHierarchyReferenceRemaps++;
                        }
                    }
                } else if (instruction instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode invokedynamic = (InvokeDynamicInsnNode) instruction;
                    HandleRemapResult bootstrap = remapHandle(
                            invokedynamic.bsm,
                            mappings,
                            hierarchyMethodRemaps,
                            allClasses,
                            completeHierarchyCache);
                    invokedynamic.bsm = bootstrap.handle;
                    handleReferenceRemaps += bootstrap.changed ? 1 : 0;
                    for (int index = 0; index < invokedynamic.bsmArgs.length; index++) {
                        BootstrapValueRemapResult argument = remapBootstrapValue(
                                invokedynamic.bsmArgs[index],
                                mappings,
                                hierarchyMethodRemaps,
                                allClasses,
                                completeHierarchyCache);
                        invokedynamic.bsmArgs[index] = argument.value;
                        handleReferenceRemaps += argument.handleRemaps;
                        constantDynamicHandleReferenceRemaps += argument.constantDynamicHandleRemaps;
                    }
                } else if (instruction instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) instruction;
                    BootstrapValueRemapResult constant = remapBootstrapValue(
                            ldc.cst,
                            mappings,
                            hierarchyMethodRemaps,
                            allClasses,
                            completeHierarchyCache);
                    ldc.cst = constant.value;
                    constantDynamicHandleReferenceRemaps += constant.handleRemaps
                            + constant.constantDynamicHandleRemaps;
                }
            }
        }

        annotationRemaps += mapAnnotations(node.visibleAnnotations, mixinTargets, mappings);
        annotationRemaps += mapAnnotations(node.invisibleAnnotations, mixinTargets, mappings);

        if (declarationRemaps == 0 && referenceRemaps == 0 && selfReferenceRemaps == 0 && annotationRemaps == 0
                && handleReferenceRemaps == 0 && constantDynamicHandleReferenceRemaps == 0) {
            return new ClassMapResult(bytes, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new ClassMapResult(
                writer.toByteArray(),
                declarationRemaps,
                referenceRemaps,
                selfReferenceRemaps,
                annotationRemaps,
                hierarchyDeclarationRemaps,
                hierarchyReferenceRemaps,
                bridgeHierarchyDeclarationRemaps,
                bridgeHierarchyReferenceRemaps,
                handleReferenceRemaps,
                constantDynamicHandleReferenceRemaps
        );
    }

    private static String mapMixinFieldDeclaration(Set<String> mixinTargets, String name, MappingSet mappings) {
        if (!name.startsWith("f_")) {
            return name;
        }
        Set<String> mapped = new LinkedHashSet<>();
        for (String target : mixinTargets) {
            String mappedName = mappings.mapField(target, name);
            if (!mappedName.equals(name)) {
                mapped.add(mappedName);
            }
        }
        return mapped.size() == 1 ? mapped.iterator().next() : name;
    }

    private static String mapMixinMethodDeclaration(Set<String> mixinTargets, String name, String descriptor,
                                                    MappingSet mappings) {
        if (!name.startsWith("m_")) {
            return name;
        }
        Set<String> mapped = new LinkedHashSet<>();
        for (String target : mixinTargets) {
            String mappedName = mappings.mapMethod(target, name, descriptor);
            if (!mappedName.equals(name)) {
                mapped.add(mappedName);
            }
        }
        return mapped.size() == 1 ? mapped.iterator().next() : name;
    }

    private static BootstrapValueRemapResult remapBootstrapValue(
            Object value,
            MappingSet mappings,
            Map<String, Map<String, String>> hierarchyMethodRemaps,
            Map<String, ClassNode> allClasses,
            Map<String, Set<String>> completeHierarchyCache) {
        if (value instanceof Handle) {
            HandleRemapResult remapped = remapHandle(
                    (Handle) value,
                    mappings,
                    hierarchyMethodRemaps,
                    allClasses,
                    completeHierarchyCache);
            return new BootstrapValueRemapResult(remapped.handle, remapped.changed ? 1 : 0, 0);
        }
        if (value instanceof ConstantDynamic) {
            ConstantDynamic constant = (ConstantDynamic) value;
            HandleRemapResult bootstrap = remapHandle(
                    constant.getBootstrapMethod(),
                    mappings,
                    hierarchyMethodRemaps,
                    allClasses,
                    completeHierarchyCache);
            Object[] arguments = new Object[constant.getBootstrapMethodArgumentCount()];
            boolean changed = bootstrap.changed;
            int handleRemaps = 0;
            int constantDynamicHandleRemaps = bootstrap.changed ? 1 : 0;
            for (int index = 0; index < arguments.length; index++) {
                BootstrapValueRemapResult argument = remapBootstrapValue(
                        constant.getBootstrapMethodArgument(index),
                        mappings,
                        hierarchyMethodRemaps,
                        allClasses,
                        completeHierarchyCache);
                arguments[index] = argument.value;
                changed |= argument.changed;
                constantDynamicHandleRemaps += argument.handleRemaps + argument.constantDynamicHandleRemaps;
            }
            Object remappedValue = changed
                    ? new ConstantDynamic(
                    constant.getName(),
                    constant.getDescriptor(),
                    bootstrap.handle,
                    arguments)
                    : value;
            return new BootstrapValueRemapResult(remappedValue, handleRemaps, constantDynamicHandleRemaps);
        }
        return new BootstrapValueRemapResult(value, 0, 0);
    }

    private static HandleRemapResult remapHandle(
            Handle handle,
            MappingSet mappings,
            Map<String, Map<String, String>> hierarchyMethodRemaps,
            Map<String, ClassNode> allClasses,
            Map<String, Set<String>> completeHierarchyCache) {
        String mappedName = handle.getName();
        if (isFieldHandle(handle.getTag())) {
            mappedName = mappings.mapField(handle.getOwner(), handle.getName());
        } else if (isMethodHandle(handle.getTag())) {
            mappedName = mapMethodReference(
                    handle.getOwner(),
                    handle.getName(),
                    handle.getDesc(),
                    mappings,
                    hierarchyMethodRemaps,
                    allClasses,
                    completeHierarchyCache);
        }
        if (mappedName.equals(handle.getName())) {
            return new HandleRemapResult(handle, false);
        }
        return new HandleRemapResult(new Handle(
                handle.getTag(),
                handle.getOwner(),
                mappedName,
                handle.getDesc(),
                handle.isInterface()), true);
    }

    private static String mapMethodReference(
            String owner,
            String name,
            String descriptor,
            MappingSet mappings,
            Map<String, Map<String, String>> hierarchyMethodRemaps,
            Map<String, ClassNode> allClasses,
            Map<String, Set<String>> completeHierarchyCache) {
        String mapped = hierarchyMethodRemaps.getOrDefault(owner, Collections.emptyMap())
                .getOrDefault(name + descriptor, name);
        if (!mapped.equals(name)) {
            return mapped;
        }
        mapped = mappings.mapMethodExact(owner, name, descriptor);
        if (!mapped.equals(name)) {
            return mapped;
        }
        for (String supertype : completeHierarchy(owner, allClasses, completeHierarchyCache)) {
            if (!supertype.startsWith("net/minecraft/")) {
                continue;
            }
            mapped = mappings.mapMethodExact(supertype, name, descriptor);
            if (!mapped.equals(name)) {
                return mapped;
            }
        }
        return name;
    }

    private static boolean isFieldHandle(int tag) {
        return tag == Opcodes.H_GETFIELD
                || tag == Opcodes.H_GETSTATIC
                || tag == Opcodes.H_PUTFIELD
                || tag == Opcodes.H_PUTSTATIC;
    }

    private static boolean isMethodHandle(int tag) {
        return tag == Opcodes.H_INVOKEVIRTUAL
                || tag == Opcodes.H_INVOKESTATIC
                || tag == Opcodes.H_INVOKESPECIAL
                || tag == Opcodes.H_NEWINVOKESPECIAL
                || tag == Opcodes.H_INVOKEINTERFACE;
    }

    private static StaleBootstrapScan staleBootstrapReferences(
            Object value,
            boolean ldcConstant,
            MappingSet mappings,
            Map<String, Map<String, String>> hierarchyMethodRemaps,
            Map<String, ClassNode> allClasses,
            Map<String, Set<String>> completeHierarchyCache) {
        List<String> references = new ArrayList<>();
        int handles = 0;
        int constantDynamicHandles = 0;
        if (value instanceof Handle) {
            Handle handle = (Handle) value;
            if (isStaleMappedHandle(handle, mappings, hierarchyMethodRemaps, allClasses, completeHierarchyCache)) {
                if (ldcConstant) {
                    constantDynamicHandles++;
                } else {
                    handles++;
                }
                references.add(handleText(handle));
            }
        } else if (value instanceof ConstantDynamic) {
            ConstantDynamic constant = (ConstantDynamic) value;
            if (isStaleMappedHandle(
                    constant.getBootstrapMethod(),
                    mappings,
                    hierarchyMethodRemaps,
                    allClasses,
                    completeHierarchyCache)) {
                constantDynamicHandles++;
                references.add("condy bsm " + handleText(constant.getBootstrapMethod()));
            }
            for (int index = 0; index < constant.getBootstrapMethodArgumentCount(); index++) {
                StaleBootstrapScan argumentScan = staleBootstrapReferences(
                        constant.getBootstrapMethodArgument(index),
                        true,
                        mappings,
                        hierarchyMethodRemaps,
                        allClasses,
                        completeHierarchyCache);
                constantDynamicHandles += argumentScan.handles + argumentScan.constantDynamicHandles;
                references.addAll(argumentScan.references);
            }
        }
        return new StaleBootstrapScan(handles, constantDynamicHandles, references);
    }

    private static boolean isStaleMappedHandle(
            Handle handle,
            MappingSet mappings,
            Map<String, Map<String, String>> hierarchyMethodRemaps,
            Map<String, ClassNode> allClasses,
            Map<String, Set<String>> completeHierarchyCache) {
        if (isFieldHandle(handle.getTag())) {
            return isStaleMappedField(handle.getOwner(), handle.getName(), mappings);
        }
        return isMethodHandle(handle.getTag())
                && isStaleMappedMethod(
                handle.getOwner(),
                handle.getName(),
                handle.getDesc(),
                mappings,
                hierarchyMethodRemaps,
                allClasses,
                completeHierarchyCache);
    }

    private static boolean isStaleMappedField(String owner, String name, MappingSet mappings) {
        return owner.startsWith("net/minecraft/") && !mappings.mapField(owner, name).equals(name);
    }

    private static boolean isStaleMappedMethod(
            String owner,
            String name,
            String descriptor,
            MappingSet mappings,
            Map<String, Map<String, String>> hierarchyMethodRemaps,
            Map<String, ClassNode> allClasses,
            Map<String, Set<String>> completeHierarchyCache) {
        return owner.startsWith("net/minecraft/")
                && !mapMethodReference(
                owner,
                name,
                descriptor,
                mappings,
                hierarchyMethodRemaps,
                allClasses,
                completeHierarchyCache).equals(name);
    }

    private static String handleText(Handle handle) {
        return handle.getOwner() + "." + handle.getName() + handle.getDesc();
    }

    private static int mapAnnotations(List<AnnotationNode> annotations, Set<String> mixinTargets, MappingSet mappings) {
        if (annotations == null) {
            return 0;
        }
        int remaps = 0;
        for (AnnotationNode annotation : annotations) {
            remaps += mapAnnotation(annotation, mixinTargets, mappings);
        }
        return remaps;
    }

    private static int mapAnnotations(List<AnnotationNode>[] annotations, Set<String> mixinTargets, MappingSet mappings) {
        if (annotations == null) {
            return 0;
        }
        int remaps = 0;
        for (List<AnnotationNode> parameterAnnotations : annotations) {
            remaps += mapAnnotations(parameterAnnotations, mixinTargets, mappings);
        }
        return remaps;
    }

    private static void collectAnnotationValues(List<AnnotationNode>[] annotations, Set<String> values) {
        if (annotations == null) {
            return;
        }
        for (List<AnnotationNode> parameterAnnotations : annotations) {
            collectAnnotationValues(parameterAnnotations, values);
        }
    }

    private static void collectAnnotationValues(List<AnnotationNode> annotations, Set<String> values) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            collectAnnotationValues(annotation, values);
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectAnnotationValues(AnnotationNode annotation, Set<String> values) {
        if (annotation.values == null) {
            return;
        }
        for (int index = 1; index < annotation.values.size(); index += 2) {
            Object value = annotation.values.get(index);
            if (value instanceof String) {
                values.add((String) value);
            } else if (value instanceof AnnotationNode) {
                collectAnnotationValues((AnnotationNode) value, values);
            } else if (value instanceof List<?>) {
                for (Object element : (List<?>) value) {
                    if (element instanceof String) {
                        values.add((String) element);
                    } else if (element instanceof AnnotationNode) {
                        collectAnnotationValues((AnnotationNode) element, values);
                    }
                }
            }
        }
    }

    private static void collectBootstrapReferences(
            Object value,
            Set<String> handleReferences,
            Set<String> constantDynamicHandleReferences) {
        if (value instanceof Handle) {
            collectHandleReference((Handle) value, handleReferences);
        } else if (value instanceof ConstantDynamic) {
            ConstantDynamic constant = (ConstantDynamic) value;
            collectHandleReference(constant.getBootstrapMethod(), constantDynamicHandleReferences);
            for (int index = 0; index < constant.getBootstrapMethodArgumentCount(); index++) {
                collectBootstrapReferences(
                        constant.getBootstrapMethodArgument(index),
                        handleReferences,
                        constantDynamicHandleReferences);
            }
        }
    }

    private static void collectHandleReference(Handle handle, Set<String> values) {
        values.add(handle.getOwner() + "." + handle.getName() + handle.getDesc());
    }

    @SuppressWarnings("unchecked")
    private static int mapAnnotation(AnnotationNode annotation, Set<String> mixinTargets, MappingSet mappings) {
        if (annotation.values == null) {
            return 0;
        }
        int remaps = 0;
        for (int index = 1; index < annotation.values.size(); index += 2) {
            Object value = annotation.values.get(index);
            RemappedValue remapped = remapAnnotationValue(value, mixinTargets, mappings);
            if (remapped.changed) {
                annotation.values.set(index, remapped.value);
                remaps += remapped.count;
            }
        }
        return remaps;
    }

    @SuppressWarnings("unchecked")
    private static RemappedValue remapAnnotationValue(Object value, Set<String> mixinTargets, MappingSet mappings) {
        if (value instanceof String) {
            String text = (String) value;
            String mapped = mapMixinAnnotationString(text, mixinTargets, mappings);
            return new RemappedValue(mapped, !mapped.equals(text), mapped.equals(text) ? 0 : 1);
        }
        if (value instanceof AnnotationNode) {
            int count = mapAnnotation((AnnotationNode) value, mixinTargets, mappings);
            return new RemappedValue(value, count > 0, count);
        }
        if (value instanceof List<?>) {
            List<Object> list = (List<Object>) value;
            int count = 0;
            boolean changed = false;
            for (int index = 0; index < list.size(); index++) {
                RemappedValue remapped = remapAnnotationValue(list.get(index), mixinTargets, mappings);
                if (remapped.changed) {
                    list.set(index, remapped.value);
                    changed = true;
                    count += remapped.count;
                }
            }
            return new RemappedValue(value, changed, count);
        }
        return new RemappedValue(value, false, 0);
    }

    private static String mapMixinAnnotationString(String text, Set<String> mixinTargets, MappingSet mappings) {
        String mapped = text;
        for (String target : mixinTargets) {
            mapped = mappings.mapAnnotationString(target, mapped);
        }
        return mapped;
    }

    private static Set<String> mixinTargets(ClassNode node) {
        Set<String> targets = new LinkedHashSet<>();
        collectMixinTargets(node.visibleAnnotations, targets);
        collectMixinTargets(node.invisibleAnnotations, targets);
        return targets;
    }

    @SuppressWarnings("unchecked")
    private static void collectMixinTargets(List<AnnotationNode> annotations, Set<String> targets) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(annotation.desc) || annotation.values == null) {
                continue;
            }
            for (int index = 0; index < annotation.values.size(); index += 2) {
                String key = (String) annotation.values.get(index);
                Object value = annotation.values.get(index + 1);
                if ("value".equals(key) && value instanceof List<?>) {
                    for (Object element : (List<?>) value) {
                        if (element instanceof Type) {
                            targets.add(((Type) element).getInternalName());
                        }
                    }
                } else if ("targets".equals(key) && value instanceof List<?>) {
                    for (Object element : (List<?>) value) {
                        if (element instanceof String) {
                            targets.add(((String) element).replace('.', '/'));
                        }
                    }
                }
            }
        }
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private static Map<String, ClassNode> readSableClasses(File jar) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar)) {
            List<? extends ZipEntry> zipEntries = entries(zip);
            zipEntries.sort((left, right) -> left.getName().compareTo(right.getName()));
            for (ZipEntry entry : zipEntries) {
                if (!entry.getName().endsWith(".class")
                        || !entry.getName().startsWith("dev/ryanhcode/sable/")) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    ClassNode node = readClass(input.readAllBytes());
                    classes.put(node.name, node);
                }
            }
        }
        return classes;
    }

    private static Map<String, ClassNode> readHierarchyClasses(Iterable<File> classpath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        for (File file : classpath) {
            if (file == null || !file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            try (ZipFile zip = new ZipFile(file)) {
                for (ZipEntry entry : entries(zip)) {
                    if (!entry.getName().endsWith(".class")
                            || !entry.getName().startsWith("net/minecraft/")) {
                        continue;
                    }
                    try (InputStream input = zip.getInputStream(entry)) {
                        ClassReader reader = new ClassReader(input.readAllBytes());
                        ClassNode node = new ClassNode();
                        reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        classes.putIfAbsent(node.name, node);
                    }
                }
            }
        }
        return classes;
    }

    private static Map<String, Map<String, String>> hierarchyMethodDeclarationRemaps(
            Map<String, ClassNode> sableClasses,
            Map<String, ClassNode> hierarchyClasses,
            MappingSet mappings) {
        Map<String, ClassNode> allClasses = new LinkedHashMap<>(hierarchyClasses);
        allClasses.putAll(sableClasses);
        Map<String, Set<String>> hierarchyCache = new HashMap<>();
        Map<String, Map<String, String>> remaps = new LinkedHashMap<>();
        for (Map.Entry<String, ClassNode> entry : sableClasses.entrySet()) {
            Set<String> supertypes = completeHierarchy(entry.getKey(), allClasses, hierarchyCache);
            Map<String, String> classRemaps = new LinkedHashMap<>();
            for (MethodNode method : entry.getValue().methods) {
                if (!method.name.startsWith("m_") || method.name.equals("<init>") || method.name.equals("<clinit>")) {
                    continue;
                }
                Set<String> mappedNames = new LinkedHashSet<>();
                for (String supertype : supertypes) {
                    if (!supertype.startsWith("net/minecraft/")) {
                        continue;
                    }
                    String mapped = mappings.mapMethodExact(supertype, method.name, method.desc);
                    if (!mapped.equals(method.name)) {
                        mappedNames.add(mapped);
                    }
                }
                if (mappedNames.size() == 1) {
                    classRemaps.put(method.name + method.desc, mappedNames.iterator().next());
                }
            }
            if (!classRemaps.isEmpty()) {
                remaps.put(entry.getKey(), classRemaps);
            }
        }
        return remaps;
    }

    private static Map<String, Set<String>> bridgeHierarchyMethodRemaps(
            Map<String, ClassNode> sableClasses,
            Map<String, Map<String, String>> hierarchyMethodRemaps) {
        Map<String, Set<String>> remaps = new LinkedHashMap<>();
        for (Map.Entry<String, ClassNode> entry : sableClasses.entrySet()) {
            Map<String, String> classRemaps = hierarchyMethodRemaps.getOrDefault(entry.getKey(), Collections.emptyMap());
            if (classRemaps.isEmpty()) {
                continue;
            }
            Set<String> bridgeMethods = new LinkedHashSet<>();
            for (MethodNode method : entry.getValue().methods) {
                String key = method.name + method.desc;
                if (classRemaps.containsKey(key) && isBridgeOrSynthetic(method)) {
                    bridgeMethods.add(key);
                }
            }
            if (!bridgeMethods.isEmpty()) {
                remaps.put(entry.getKey(), Collections.unmodifiableSet(bridgeMethods));
            }
        }
        return remaps;
    }

    private static boolean isBridgeOrSynthetic(MethodNode method) {
        return (method.access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0;
    }

    private static Set<String> completeHierarchy(String className, Map<String, ClassNode> sableClasses,
                                                 Map<String, Set<String>> cache) {
        Set<String> cached = cache.get(className);
        if (cached != null) {
            return cached;
        }
        Set<String> hierarchy = new LinkedHashSet<>();
        ClassNode node = sableClasses.get(className);
        if (node != null) {
            if (node.superName != null) {
                hierarchy.add(node.superName);
                hierarchy.addAll(completeHierarchy(node.superName, sableClasses, cache));
            }
            for (String interfaceName : node.interfaces) {
                hierarchy.add(interfaceName);
                hierarchy.addAll(completeHierarchy(interfaceName, sableClasses, cache));
            }
        }
        Set<String> immutable = Collections.unmodifiableSet(hierarchy);
        cache.put(className, immutable);
        return immutable;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file.toPath()));
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
            }
            return builder.toString().toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<? extends ZipEntry> entries(ZipFile zipFile) {
        List<ZipEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            entries.add(enumeration.nextElement());
        }
        return entries;
    }

    public static final class Result {
        public final String inputSha256;
        public final int transformedClasses;
        public final int declarationRemaps;
        public final int referenceRemaps;
        public final int selfReferenceRemaps;
        public final int annotationRemaps;
        public final int hierarchyDeclarationRemaps;
        public final int hierarchyReferenceRemaps;
        public final int bridgeHierarchyDeclarationRemaps;
        public final int bridgeHierarchyReferenceRemaps;
        public final int handleReferenceRemaps;
        public final int constantDynamicHandleReferenceRemaps;
        public final int unresolvedHierarchyDeclarations;
        public final int unresolvedHierarchyReferences;

        private Result(String inputSha256, int transformedClasses, int declarationRemaps,
                       int referenceRemaps, int selfReferenceRemaps, int annotationRemaps,
                       int hierarchyDeclarationRemaps, int hierarchyReferenceRemaps,
                       int bridgeHierarchyDeclarationRemaps, int bridgeHierarchyReferenceRemaps,
                       int handleReferenceRemaps, int constantDynamicHandleReferenceRemaps,
                       int unresolvedHierarchyDeclarations, int unresolvedHierarchyReferences) {
            this.inputSha256 = inputSha256;
            this.transformedClasses = transformedClasses;
            this.declarationRemaps = declarationRemaps;
            this.referenceRemaps = referenceRemaps;
            this.selfReferenceRemaps = selfReferenceRemaps;
            this.annotationRemaps = annotationRemaps;
            this.hierarchyDeclarationRemaps = hierarchyDeclarationRemaps;
            this.hierarchyReferenceRemaps = hierarchyReferenceRemaps;
            this.bridgeHierarchyDeclarationRemaps = bridgeHierarchyDeclarationRemaps;
            this.bridgeHierarchyReferenceRemaps = bridgeHierarchyReferenceRemaps;
            this.handleReferenceRemaps = handleReferenceRemaps;
            this.constantDynamicHandleReferenceRemaps = constantDynamicHandleReferenceRemaps;
            this.unresolvedHierarchyDeclarations = unresolvedHierarchyDeclarations;
            this.unresolvedHierarchyReferences = unresolvedHierarchyReferences;
        }
    }

    public static final class ClassMembers {
        public final Set<String> fields;
        public final Set<String> methods;

        private ClassMembers(Set<String> fields, Set<String> methods) {
            this.fields = Collections.unmodifiableSet(fields);
            this.methods = Collections.unmodifiableSet(methods);
        }
    }

    public static final class ClassReferences {
        public final Set<String> fields;
        public final Set<String> methods;
        public final Set<String> handles;
        public final Set<String> constantDynamicHandles;
        public final Set<String> annotationValues;

        private ClassReferences(
                Set<String> fields,
                Set<String> methods,
                Set<String> handles,
                Set<String> constantDynamicHandles,
                Set<String> annotationValues) {
            this.fields = Collections.unmodifiableSet(fields);
            this.methods = Collections.unmodifiableSet(methods);
            this.handles = Collections.unmodifiableSet(handles);
            this.constantDynamicHandles = Collections.unmodifiableSet(constantDynamicHandles);
            this.annotationValues = Collections.unmodifiableSet(annotationValues);
        }
    }

    public static final class StaleMemberReferenceScan {
        public final int ordinaryInstructionReferences;
        public final int handleReferences;
        public final int constantDynamicOrLdcHandleReferences;
        public final List<String> references;

        private StaleMemberReferenceScan(
                int ordinaryInstructionReferences,
                int handleReferences,
                int constantDynamicOrLdcHandleReferences,
                List<String> references) {
            this.ordinaryInstructionReferences = ordinaryInstructionReferences;
            this.handleReferences = handleReferences;
            this.constantDynamicOrLdcHandleReferences = constantDynamicOrLdcHandleReferences;
            this.references = Collections.unmodifiableList(references);
        }
    }

    private static final class ClassMapResult {
        private final byte[] bytes;
        private final int declarationRemaps;
        private final int referenceRemaps;
        private final int selfReferenceRemaps;
        private final int annotationRemaps;
        private final int hierarchyDeclarationRemaps;
        private final int hierarchyReferenceRemaps;
        private final int bridgeHierarchyDeclarationRemaps;
        private final int bridgeHierarchyReferenceRemaps;
        private final int handleReferenceRemaps;
        private final int constantDynamicHandleReferenceRemaps;

        private ClassMapResult(byte[] bytes, int declarationRemaps, int referenceRemaps,
                               int selfReferenceRemaps, int annotationRemaps,
                               int hierarchyDeclarationRemaps, int hierarchyReferenceRemaps,
                               int bridgeHierarchyDeclarationRemaps, int bridgeHierarchyReferenceRemaps,
                               int handleReferenceRemaps, int constantDynamicHandleReferenceRemaps) {
            this.bytes = bytes;
            this.declarationRemaps = declarationRemaps;
            this.referenceRemaps = referenceRemaps;
            this.selfReferenceRemaps = selfReferenceRemaps;
            this.annotationRemaps = annotationRemaps;
            this.hierarchyDeclarationRemaps = hierarchyDeclarationRemaps;
            this.hierarchyReferenceRemaps = hierarchyReferenceRemaps;
            this.bridgeHierarchyDeclarationRemaps = bridgeHierarchyDeclarationRemaps;
            this.bridgeHierarchyReferenceRemaps = bridgeHierarchyReferenceRemaps;
            this.handleReferenceRemaps = handleReferenceRemaps;
            this.constantDynamicHandleReferenceRemaps = constantDynamicHandleReferenceRemaps;
        }

        private boolean changed() {
            return declarationRemaps > 0 || referenceRemaps > 0 || annotationRemaps > 0
                    || hierarchyDeclarationRemaps > 0 || hierarchyReferenceRemaps > 0
                    || handleReferenceRemaps > 0 || constantDynamicHandleReferenceRemaps > 0;
        }
    }

    private static final class RemappedValue {
        private final Object value;
        private final boolean changed;
        private final int count;

        private RemappedValue(Object value, boolean changed, int count) {
            this.value = value;
            this.changed = changed;
            this.count = count;
        }
    }

    private static final class HandleRemapResult {
        private final Handle handle;
        private final boolean changed;

        private HandleRemapResult(Handle handle, boolean changed) {
            this.handle = handle;
            this.changed = changed;
        }
    }

    private static final class BootstrapValueRemapResult {
        private final Object value;
        private final boolean changed;
        private final int handleRemaps;
        private final int constantDynamicHandleRemaps;

        private BootstrapValueRemapResult(Object value, int handleRemaps, int constantDynamicHandleRemaps) {
            this.value = value;
            this.handleRemaps = handleRemaps;
            this.constantDynamicHandleRemaps = constantDynamicHandleRemaps;
            this.changed = handleRemaps > 0 || constantDynamicHandleRemaps > 0;
        }
    }

    private static final class StaleBootstrapScan {
        private final int handles;
        private final int constantDynamicHandles;
        private final List<String> references;

        private StaleBootstrapScan(int handles, int constantDynamicHandles, List<String> references) {
            this.handles = handles;
            this.constantDynamicHandles = constantDynamicHandles;
            this.references = references;
        }
    }

    private static final class MappingSet {
        private final Map<String, Map<String, String>> fields;
        private final Map<String, Map<String, String>> methods;
        private final Map<String, String> uniqueFields;
        private final Map<String, String> uniqueMethods;

        private MappingSet(Map<String, Map<String, String>> fields, Map<String, Map<String, String>> methods,
                           Map<String, String> uniqueFields, Map<String, String> uniqueMethods) {
            this.fields = fields;
            this.methods = methods;
            this.uniqueFields = uniqueFields;
            this.uniqueMethods = uniqueMethods;
        }

        private static MappingSet read(File mappingsFile) throws IOException {
            Map<String, Map<String, String>> fields = new HashMap<>();
            Map<String, Map<String, String>> methods = new HashMap<>();
            Map<String, String> uniqueFields = new HashMap<>();
            Map<String, String> uniqueMethods = new HashMap<>();
            for (String line : Files.readAllLines(mappingsFile.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("FD: ")) {
                    String[] parts = line.split(" ");
                    String left = parts[1];
                    String right = parts[2];
                    String owner = owner(left);
                    String sourceName = simple(left);
                    String targetName = simple(right);
                    fields.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                            .put(sourceName, targetName);
                    putUnique(uniqueFields, sourceName, targetName);
                } else if (line.startsWith("MD: ")) {
                    String[] parts = line.split(" ");
                    String left = parts[1];
                    String leftDescriptor = parts[2];
                    String right = parts[3];
                    String owner = owner(left);
                    String sourceName = simple(left);
                    String targetName = simple(right);
                    methods.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                            .put(sourceName + leftDescriptor, targetName);
                    putUnique(uniqueMethods, sourceName + leftDescriptor, targetName);
                }
            }
            uniqueFields.values().removeIf(MappingSet::ambiguous);
            uniqueMethods.values().removeIf(MappingSet::ambiguous);
            return new MappingSet(fields, methods, uniqueFields, uniqueMethods);
        }

        private String mapField(String owner, String name) {
            return fields.getOrDefault(owner, Collections.emptyMap())
                    .getOrDefault(name, uniqueFields.getOrDefault(name, name));
        }

        private String mapMethod(String owner, String name, String descriptor) {
            return methods.getOrDefault(owner, Collections.emptyMap())
                    .getOrDefault(name + descriptor, uniqueMethods.getOrDefault(name + descriptor, name));
        }

        private String mapMethodExact(String owner, String name, String descriptor) {
            return methods.getOrDefault(owner, Collections.emptyMap())
                    .getOrDefault(name + descriptor, name);
        }

        private String mapAnnotationString(String owner, String text) {
            String mapped = text;
            for (Map.Entry<String, String> field : fields.getOrDefault(owner, Collections.emptyMap()).entrySet()) {
                mapped = remapAnnotationMember(mapped, field.getKey(), field.getValue(), null);
            }
            for (Map.Entry<String, String> method : methods.getOrDefault(owner, Collections.emptyMap()).entrySet()) {
                String key = method.getKey();
                int descriptorStart = key.indexOf('(');
                if (descriptorStart < 0) {
                    continue;
                }
                mapped = remapAnnotationMember(
                        mapped,
                        key.substring(0, descriptorStart),
                        method.getValue(),
                        key.substring(descriptorStart)
                );
            }
            return mapped;
        }

        private static String remapAnnotationMember(String text, String srgName, String mappedName, String descriptor) {
            if (text.equals(srgName)) {
                return mappedName;
            }
            if (descriptor != null && text.equals(srgName + descriptor)) {
                return mappedName + descriptor;
            }
            if (descriptor != null && text.startsWith(srgName + "(")) {
                return mappedName + text.substring(srgName.length());
            }
            return text;
        }

        private static String owner(String memberPath) {
            return memberPath.substring(0, memberPath.lastIndexOf('/'));
        }

        private static String simple(String memberPath) {
            return memberPath.substring(memberPath.lastIndexOf('/') + 1);
        }

        private static void putUnique(Map<String, String> values, String sourceName, String targetName) {
            String previous = values.putIfAbsent(sourceName, targetName);
            if (previous != null && !previous.equals(targetName)) {
                values.put(sourceName, "<ambiguous>");
            }
        }

        private static boolean ambiguous(String value) {
            return "<ambiguous>".equals(value);
        }
    }
}
