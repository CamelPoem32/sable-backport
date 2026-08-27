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
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
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
        return mapNamedToProduction(inputJar, mappingsFile, outputJar, Collections.emptyList());
    }

    public static Result mapNamedToProduction(File inputJar, File mappingsFile, File outputJar,
                                              Iterable<File> hierarchyClasspath) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        ClassHierarchy hierarchy = ClassHierarchy.read(hierarchyClasspath);
        Files.createDirectories(outputJar.toPath().getParent());

        int transformedClasses = 0;
        int memberReferenceRemaps = 0;
        int inheritedMemberReferenceRemaps = 0;
        int invokedynamicHandleRemaps = 0;
        int invokedynamicSamNameRemaps = 0;
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
                    CountingRemapper remapper = new CountingRemapper(mappings, hierarchy);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor visitor = new ClassRemapper(writer, remapper);
                    reader.accept(visitor, 0);
                    SamNameRemapResult samResult = remapInvokedynamicSamNames(
                            writer.toByteArray(), mappings, hierarchy);
                    if (remapper.changed() || samResult.changed()) {
                        transformedClasses++;
                    }
                    memberReferenceRemaps += remapper.memberReferenceRemaps;
                    inheritedMemberReferenceRemaps += remapper.inheritedMemberReferenceRemaps;
                    invokedynamicHandleRemaps += remapper.handleRemaps;
                    invokedynamicSamNameRemaps += samResult.remaps;
                    inheritedMemberReferenceRemaps += samResult.inheritedRemaps;
                    minecraftReferenceOwners.addAll(remapper.minecraftReferenceOwners);
                    minecraftReferenceOwners.addAll(samResult.minecraftReferenceOwners);
                    bytes = samResult.bytes;
                }

                ZipEntry outputEntry = new ZipEntry(sourceEntry.getName());
                outputEntry.setTime(0L);
                output.putNextEntry(outputEntry);
                output.write(bytes);
                output.closeEntry();
            }
        }

        ReferenceReport report = referenceReport(outputJar, mappingsFile, hierarchyClasspath);
        return new Result(sha256(inputJar), sha256(outputJar), transformedClasses,
                memberReferenceRemaps, inheritedMemberReferenceRemaps, invokedynamicHandleRemaps,
                invokedynamicSamNameRemaps,
                minecraftReferenceOwners, report.namedMinecraftMemberReferences,
                report.productionMinecraftMemberReferences, report.unresolvedMinecraftMemberReferences,
                report.totalInvokedynamicSites, report.lambdaMetafactorySites,
                report.minecraftLambdaMetafactorySites);
    }

    public static ReferenceReport referenceReport(File jar, File mappingsFile) throws IOException {
        return referenceReport(jar, mappingsFile, Collections.emptyList());
    }

    public static ReferenceReport referenceReport(File jar, File mappingsFile,
                                                  Iterable<File> hierarchyClasspath) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        ClassHierarchy hierarchy = ClassHierarchy.read(hierarchyClasspath);
        Set<String> minecraftReferenceOwners = new TreeSet<>();
        Set<String> namedMinecraftMemberReferences = new TreeSet<>();
        Set<String> productionMinecraftMemberReferences = new TreeSet<>();
        Set<String> unresolvedMinecraftMemberReferences = new TreeSet<>();
        int totalInvokedynamicSites = 0;
        int lambdaMetafactorySites = 0;
        int minecraftLambdaMetafactorySites = 0;

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
                            scanReference(field.owner, field.name, field.desc, false, mappings, hierarchy,
                                    minecraftReferenceOwners, namedMinecraftMemberReferences,
                                    productionMinecraftMemberReferences, unresolvedMinecraftMemberReferences);
                        } else if (instruction instanceof MethodInsnNode called) {
                            scanReference(called.owner, called.name, called.desc, true, mappings, hierarchy,
                                    minecraftReferenceOwners, namedMinecraftMemberReferences,
                                    productionMinecraftMemberReferences, unresolvedMinecraftMemberReferences);
                        } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                            totalInvokedynamicSites++;
                            if (isLambdaMetafactory(indy.bsm)) {
                                lambdaMetafactorySites++;
                            }
                            LambdaSamSite samSite = lambdaSamSite(indy);
                            if (samSite != null) {
                                minecraftLambdaMetafactorySites++;
                                scanReference(samSite.owner, indy.name, samSite.descriptor, true, mappings, hierarchy,
                                        minecraftReferenceOwners, namedMinecraftMemberReferences,
                                        productionMinecraftMemberReferences, unresolvedMinecraftMemberReferences,
                                        "INDY_SAM ");
                            }
                            scanHandle(indy.bsm, mappings, hierarchy, minecraftReferenceOwners,
                                    namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                                    unresolvedMinecraftMemberReferences);
                            for (Object argument : indy.bsmArgs) {
                                scanBootstrapValue(argument, mappings, hierarchy, minecraftReferenceOwners,
                                        namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                                        unresolvedMinecraftMemberReferences);
                            }
                        } else if (instruction instanceof LdcInsnNode ldc) {
                            scanBootstrapValue(ldc.cst, mappings, hierarchy, minecraftReferenceOwners,
                                    namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                                    unresolvedMinecraftMemberReferences);
                        }
                    }
                }
            }
        }

        return new ReferenceReport(minecraftReferenceOwners, namedMinecraftMemberReferences,
                productionMinecraftMemberReferences, unresolvedMinecraftMemberReferences,
                totalInvokedynamicSites, lambdaMetafactorySites, minecraftLambdaMetafactorySites);
    }

    private static SamNameRemapResult remapInvokedynamicSamNames(byte[] classBytes, MappingSet mappings,
                                                                 ClassHierarchy hierarchy) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, 0);
        int remaps = 0;
        int inheritedRemaps = 0;
        Set<String> minecraftReferenceOwners = new TreeSet<>();
        for (Object methodObject : node.methods) {
            org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) methodObject;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof InvokeDynamicInsnNode indy)) {
                    continue;
                }
                LambdaSamSite samSite = lambdaSamSite(indy);
                if (samSite == null) {
                    continue;
                }
                scanDescriptor(samSite.owner, minecraftReferenceOwners);
                scanDescriptor(samSite.descriptor, minecraftReferenceOwners);
                MemberMapping mapped = mappings.mapMethodName(
                        samSite.owner, indy.name, samSite.descriptor, hierarchy);
                if (!mapped.name.equals(indy.name)) {
                    indy.name = mapped.name;
                    remaps++;
                    if (mapped.inherited) {
                        inheritedRemaps++;
                    }
                }
            }
        }
        if (remaps == 0) {
            return new SamNameRemapResult(classBytes, 0, 0, minecraftReferenceOwners);
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return new SamNameRemapResult(writer.toByteArray(), remaps, inheritedRemaps, minecraftReferenceOwners);
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

    public static Set<String> instructionMemberReferences(File jar, String className,
                                                          String methodName) throws IOException {
        return instructionMemberReferences(jar, className, methodName, null);
    }

    public static Set<String> instructionMemberReferences(File jar, String className,
                                                          String methodName,
                                                          String methodDescriptor) throws IOException {
        String classEntry = className.replace('.', '/') + ".class";
        try (ZipFile source = new ZipFile(jar)) {
            ZipEntry entry = source.getEntry(classEntry);
            if (entry == null) {
                throw new IOException("Jar is missing " + classEntry);
            }
            ClassNode node = new ClassNode();
            try (InputStream input = source.getInputStream(entry)) {
                new ClassReader(input.readAllBytes()).accept(node, 0);
            }
            List<org.objectweb.asm.tree.MethodNode> matches = new ArrayList<>();
            node.methods.stream()
                    .filter(method -> method.name.equals(methodName))
                    .filter(method -> methodDescriptor == null || method.desc.equals(methodDescriptor))
                    .forEach(matches::add);
            if (matches.size() != 1) {
                throw new IOException("Expected exactly one " + className + "." + methodName
                        + (methodDescriptor == null ? "" : methodDescriptor)
                        + " method, found " + matches.size());
            }
            Set<String> references = new LinkedHashSet<>();
            for (AbstractInsnNode instruction : matches.get(0).instructions) {
                if (instruction instanceof FieldInsnNode field) {
                    references.add("FIELD " + field.owner + "." + field.name + field.desc);
                } else if (instruction instanceof MethodInsnNode method) {
                    references.add("METHOD " + method.owner + "." + method.name + method.desc);
                }
            }
            return Collections.unmodifiableSet(references);
        }
    }

    public static AbiReport crossArtifactAbiReport(File sourceJar, Iterable<File> targetArtifacts,
                                                   String ownerPrefix) throws IOException {
        ClassHierarchy targetHierarchy = ClassHierarchy.read(targetArtifacts);
        Set<String> sourceClasses = classEntries(sourceJar);
        Set<String> referencedClasses = new TreeSet<>();
        Set<String> resolvedClasses = new TreeSet<>();
        Set<String> unresolvedClasses = new TreeSet<>();
        Set<String> resolvedMethods = new TreeSet<>();
        Set<String> unresolvedMethods = new TreeSet<>();
        Set<String> resolvedFields = new TreeSet<>();
        Set<String> unresolvedFields = new TreeSet<>();

        try (ZipFile source = new ZipFile(sourceJar)) {
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
                collectAbiClassReference(node.superName, ownerPrefix, sourceClasses, targetHierarchy,
                        referencedClasses, resolvedClasses, unresolvedClasses);
                node.interfaces.forEach(owner -> collectAbiClassReference((String) owner, ownerPrefix,
                        sourceClasses, targetHierarchy, referencedClasses, resolvedClasses, unresolvedClasses));
                for (Object fieldObject : node.fields) {
                    org.objectweb.asm.tree.FieldNode field = (org.objectweb.asm.tree.FieldNode) fieldObject;
                    collectAbiDescriptorReferences(field.desc, ownerPrefix, sourceClasses, targetHierarchy,
                            referencedClasses, resolvedClasses, unresolvedClasses);
                }
                for (Object methodObject : node.methods) {
                    org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) methodObject;
                    collectAbiDescriptorReferences(method.desc, ownerPrefix, sourceClasses, targetHierarchy,
                            referencedClasses, resolvedClasses, unresolvedClasses);
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof FieldInsnNode field) {
                            collectAbiMemberReference(field.owner, field.name, field.desc, false, ownerPrefix,
                                    sourceClasses, targetHierarchy, referencedClasses, resolvedClasses,
                                    unresolvedClasses, resolvedMethods, unresolvedMethods,
                                    resolvedFields, unresolvedFields);
                        } else if (instruction instanceof MethodInsnNode called) {
                            collectAbiMemberReference(called.owner, called.name, called.desc, true, ownerPrefix,
                                    sourceClasses, targetHierarchy, referencedClasses, resolvedClasses,
                                    unresolvedClasses, resolvedMethods, unresolvedMethods,
                                    resolvedFields, unresolvedFields);
                        } else if (instruction instanceof TypeInsnNode type) {
                            collectAbiClassReference(type.desc, ownerPrefix, sourceClasses, targetHierarchy,
                                    referencedClasses, resolvedClasses, unresolvedClasses);
                        } else if (instruction instanceof MultiANewArrayInsnNode multiArray) {
                            collectAbiDescriptorReferences(multiArray.desc, ownerPrefix, sourceClasses, targetHierarchy,
                                    referencedClasses, resolvedClasses, unresolvedClasses);
                        } else if (instruction instanceof InvokeDynamicInsnNode indy) {
                            collectAbiDescriptorReferences(indy.desc, ownerPrefix, sourceClasses, targetHierarchy,
                                    referencedClasses, resolvedClasses, unresolvedClasses);
                            collectAbiHandle(indy.bsm, ownerPrefix, sourceClasses, targetHierarchy,
                                    referencedClasses, resolvedClasses, unresolvedClasses,
                                    resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
                            for (Object argument : indy.bsmArgs) {
                                collectAbiBootstrapValue(argument, ownerPrefix, sourceClasses, targetHierarchy,
                                        referencedClasses, resolvedClasses, unresolvedClasses,
                                        resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
                            }
                        } else if (instruction instanceof LdcInsnNode ldc) {
                            collectAbiBootstrapValue(ldc.cst, ownerPrefix, sourceClasses, targetHierarchy,
                                    referencedClasses, resolvedClasses, unresolvedClasses,
                                    resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
                        }
                    }
                }
            }
        }

        return new AbiReport(referencedClasses, resolvedClasses, unresolvedClasses,
                resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
    }

    private static void collectAbiMemberReference(String owner, String name, String descriptor, boolean method,
                                                  String ownerPrefix, Set<String> sourceClasses,
                                                  ClassHierarchy targetHierarchy,
                                                  Set<String> referencedClasses, Set<String> resolvedClasses,
                                                  Set<String> unresolvedClasses, Set<String> resolvedMethods,
                                                  Set<String> unresolvedMethods, Set<String> resolvedFields,
                                                  Set<String> unresolvedFields) {
        collectAbiClassReference(owner, ownerPrefix, sourceClasses, targetHierarchy,
                referencedClasses, resolvedClasses, unresolvedClasses);
        collectAbiDescriptorReferences(descriptor, ownerPrefix, sourceClasses, targetHierarchy,
                referencedClasses, resolvedClasses, unresolvedClasses);
        if (!owner.startsWith(ownerPrefix) || sourceClasses.contains(owner + ".class")) {
            return;
        }
        if (method) {
            if (targetHierarchy.resolvesNamedMember(owner, name, descriptor, true)) {
                resolvedMethods.add(owner + "." + name + descriptor);
            } else {
                unresolvedMethods.add(owner + "." + name + descriptor);
            }
        } else {
            if (targetHierarchy.resolvesNamedMember(owner, name, descriptor, false)) {
                resolvedFields.add(owner + "." + name + descriptor);
            } else {
                unresolvedFields.add(owner + "." + name + descriptor);
            }
        }
    }

    private static void collectAbiHandle(Handle handle, String ownerPrefix, Set<String> sourceClasses,
                                         ClassHierarchy targetHierarchy,
                                         Set<String> referencedClasses, Set<String> resolvedClasses,
                                         Set<String> unresolvedClasses, Set<String> resolvedMethods,
                                         Set<String> unresolvedMethods, Set<String> resolvedFields,
                                         Set<String> unresolvedFields) {
        boolean method = switch (handle.getTag()) {
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL,
                    Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE -> true;
            default -> false;
        };
        collectAbiMemberReference(handle.getOwner(), handle.getName(), handle.getDesc(), method,
                ownerPrefix, sourceClasses, targetHierarchy, referencedClasses, resolvedClasses,
                unresolvedClasses, resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
    }

    private static void collectAbiBootstrapValue(Object value, String ownerPrefix, Set<String> sourceClasses,
                                                 ClassHierarchy targetHierarchy,
                                                 Set<String> referencedClasses, Set<String> resolvedClasses,
                                                 Set<String> unresolvedClasses, Set<String> resolvedMethods,
                                                 Set<String> unresolvedMethods, Set<String> resolvedFields,
                                                 Set<String> unresolvedFields) {
        if (value instanceof Handle handle) {
            collectAbiHandle(handle, ownerPrefix, sourceClasses, targetHierarchy,
                    referencedClasses, resolvedClasses, unresolvedClasses,
                    resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
        } else if (value instanceof Type type) {
            collectAbiDescriptorReferences(type.getDescriptor(), ownerPrefix, sourceClasses, targetHierarchy,
                    referencedClasses, resolvedClasses, unresolvedClasses);
        } else if (value instanceof ConstantDynamic constantDynamic) {
            collectAbiDescriptorReferences(constantDynamic.getDescriptor(), ownerPrefix, sourceClasses, targetHierarchy,
                    referencedClasses, resolvedClasses, unresolvedClasses);
            collectAbiHandle(constantDynamic.getBootstrapMethod(), ownerPrefix, sourceClasses, targetHierarchy,
                    referencedClasses, resolvedClasses, unresolvedClasses,
                    resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
            for (int index = 0; index < constantDynamic.getBootstrapMethodArgumentCount(); index++) {
                collectAbiBootstrapValue(constantDynamic.getBootstrapMethodArgument(index), ownerPrefix,
                        sourceClasses, targetHierarchy, referencedClasses, resolvedClasses, unresolvedClasses,
                        resolvedMethods, unresolvedMethods, resolvedFields, unresolvedFields);
            }
        }
    }

    private static void collectAbiDescriptorReferences(String descriptor, String ownerPrefix, Set<String> sourceClasses,
                                                       ClassHierarchy targetHierarchy,
                                                       Set<String> referencedClasses, Set<String> resolvedClasses,
                                                       Set<String> unresolvedClasses) {
        if (descriptor == null) {
            return;
        }
        if (!descriptor.contains(ownerPrefix)) {
            return;
        }
        for (String token : descriptor.split("[;()\\[ ]+")) {
            if (token.startsWith("L")) {
                token = token.substring(1);
            }
            collectAbiClassReference(token, ownerPrefix, sourceClasses, targetHierarchy,
                    referencedClasses, resolvedClasses, unresolvedClasses);
        }
    }

    private static void collectAbiClassReference(String owner, String ownerPrefix, Set<String> sourceClasses,
                                                 ClassHierarchy targetHierarchy,
                                                 Set<String> referencedClasses, Set<String> resolvedClasses,
                                                 Set<String> unresolvedClasses) {
        if (owner == null || !owner.startsWith(ownerPrefix) || sourceClasses.contains(owner + ".class")) {
            return;
        }
        referencedClasses.add(owner);
        if (targetHierarchy.hasClass(owner)) {
            resolvedClasses.add(owner);
        } else {
            unresolvedClasses.add(owner);
        }
    }

    private static void scanHandle(Handle handle, MappingSet mappings, ClassHierarchy hierarchy,
                                   Set<String> minecraftReferenceOwners,
                                   Set<String> namedMinecraftMemberReferences,
                                   Set<String> productionMinecraftMemberReferences,
                                   Set<String> unresolvedMinecraftMemberReferences) {
        boolean method = switch (handle.getTag()) {
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL,
                    Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE -> true;
            default -> false;
        };
        scanReference(handle.getOwner(), handle.getName(), handle.getDesc(), method, mappings, hierarchy,
                minecraftReferenceOwners, namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                unresolvedMinecraftMemberReferences);
    }

    private static LambdaSamSite lambdaSamSite(InvokeDynamicInsnNode indy) {
        if (!isLambdaMetafactory(indy.bsm) || indy.bsmArgs.length == 0
                || !(indy.bsmArgs[0] instanceof Type samMethodType)
                || samMethodType.getSort() != Type.METHOD) {
            return null;
        }
        Type returnType = Type.getReturnType(indy.desc);
        if (returnType.getSort() != Type.OBJECT) {
            return null;
        }
        String owner = returnType.getInternalName();
        if (!isMinecraftOwner(owner)) {
            return null;
        }
        return new LambdaSamSite(owner, samMethodType.getDescriptor());
    }

    private static boolean isLambdaMetafactory(Handle handle) {
        return handle.getTag() == Opcodes.H_INVOKESTATIC
                && "java/lang/invoke/LambdaMetafactory".equals(handle.getOwner())
                && ("metafactory".equals(handle.getName()) || "altMetafactory".equals(handle.getName()));
    }

    private static void scanBootstrapValue(Object value, MappingSet mappings, ClassHierarchy hierarchy,
                                           Set<String> minecraftReferenceOwners,
                                           Set<String> namedMinecraftMemberReferences,
                                           Set<String> productionMinecraftMemberReferences,
                                           Set<String> unresolvedMinecraftMemberReferences) {
        if (value instanceof Handle handle) {
            scanHandle(handle, mappings, hierarchy, minecraftReferenceOwners,
                    namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                    unresolvedMinecraftMemberReferences);
        } else if (value instanceof Type type) {
            scanDescriptor(type.getDescriptor(), minecraftReferenceOwners);
        } else if (value instanceof ConstantDynamic constantDynamic) {
            scanDescriptor(constantDynamic.getDescriptor(), minecraftReferenceOwners);
            scanHandle(constantDynamic.getBootstrapMethod(), mappings, hierarchy, minecraftReferenceOwners,
                    namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                    unresolvedMinecraftMemberReferences);
            for (int index = 0; index < constantDynamic.getBootstrapMethodArgumentCount(); index++) {
                scanBootstrapValue(constantDynamic.getBootstrapMethodArgument(index), mappings, hierarchy,
                        minecraftReferenceOwners, namedMinecraftMemberReferences,
                        productionMinecraftMemberReferences, unresolvedMinecraftMemberReferences);
            }
        }
    }

    private static void scanReference(String owner, String name, String descriptor, boolean method,
                                      MappingSet mappings, ClassHierarchy hierarchy,
                                      Set<String> minecraftReferenceOwners,
                                      Set<String> namedMinecraftMemberReferences,
                                      Set<String> productionMinecraftMemberReferences,
                                      Set<String> unresolvedMinecraftMemberReferences) {
        scanReference(owner, name, descriptor, method, mappings, hierarchy, minecraftReferenceOwners,
                namedMinecraftMemberReferences, productionMinecraftMemberReferences,
                unresolvedMinecraftMemberReferences, "");
    }

    private static void scanReference(String owner, String name, String descriptor, boolean method,
                                      MappingSet mappings, ClassHierarchy hierarchy,
                                      Set<String> minecraftReferenceOwners,
                                      Set<String> namedMinecraftMemberReferences,
                                      Set<String> productionMinecraftMemberReferences,
                                      Set<String> unresolvedMinecraftMemberReferences,
                                      String prefix) {
        scanDescriptor(owner, minecraftReferenceOwners);
        scanDescriptor(descriptor, minecraftReferenceOwners);
        if (!isMinecraftOwner(owner)) {
            return;
        }
        MemberMapping mapped = method
                ? mappings.mapMethodName(owner, name, descriptor, hierarchy)
                : mappings.mapFieldName(owner, name, descriptor, hierarchy);
        if (!mapped.name.equals(name)) {
            namedMinecraftMemberReferences.add(prefix + owner + "." + name + descriptor + " -> " + mapped.name
                    + " (declared by " + mapped.declaringOwner + ")");
        } else if (mappings.isProductionMember(owner, name, descriptor, method, hierarchy)) {
            productionMinecraftMemberReferences.add(prefix + owner + "." + name + descriptor);
        } else if (!hierarchy.resolvesNamedMember(owner, name, descriptor, method)) {
            unresolvedMinecraftMemberReferences.add(prefix + owner + "." + name + descriptor);
        }
    }

    private static boolean isMinecraftOwner(String owner) {
        return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/");
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
        private final ClassHierarchy hierarchy;
        private final Set<String> minecraftReferenceOwners = new TreeSet<>();
        private int memberReferenceRemaps;
        private int inheritedMemberReferenceRemaps;
        private int handleRemaps;

        private CountingRemapper(MappingSet mappings, ClassHierarchy hierarchy) {
            this.mappings = mappings;
            this.hierarchy = hierarchy;
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
            MemberMapping mappedMember = mapHandleName(handle);
            String mappedName = mappedMember.name;
            if (!mappedOwner.equals(handle.getOwner())
                    || !mappedDescriptor.equals(handle.getDesc())
                    || !mappedName.equals(handle.getName())) {
                if (!mappedName.equals(handle.getName())) {
                    handleRemaps++;
                    if (mappedMember.inherited) {
                        inheritedMemberReferenceRemaps++;
                    }
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
            MemberMapping mapped = mappings.mapFieldName(owner, name, descriptor, hierarchy);
            if (!mapped.name.equals(name)) {
                memberReferenceRemaps++;
                if (mapped.inherited) {
                    inheritedMemberReferenceRemaps++;
                }
            }
            return mapped.name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            scanDescriptor(owner, minecraftReferenceOwners);
            scanDescriptor(descriptor, minecraftReferenceOwners);
            MemberMapping mapped = mappings.mapMethodName(owner, name, descriptor, hierarchy);
            if (!mapped.name.equals(name)) {
                memberReferenceRemaps++;
                if (mapped.inherited) {
                    inheritedMemberReferenceRemaps++;
                }
            }
            return mapped.name;
        }

        private MemberMapping mapHandleName(Handle handle) {
            return switch (handle.getTag()) {
                case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC ->
                        mappings.mapFieldName(handle.getOwner(), handle.getName(), handle.getDesc(), hierarchy);
                case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL,
                        Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE ->
                        mappings.mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc(), hierarchy);
                default -> MemberMapping.unchanged(handle.getOwner(), handle.getName());
            };
        }

        private boolean changed() {
            return memberReferenceRemaps > 0 || handleRemaps > 0;
        }
    }

    private static final class ClassHierarchy {
        private static final Set<String> JAVA_LANG_ENUM_METHODS = Set.of(
                "name()Ljava/lang/String;",
                "ordinal()I",
                "toString()Ljava/lang/String;",
                "equals(Ljava/lang/Object;)Z",
                "hashCode()I",
                "compareTo(Ljava/lang/Enum;)I",
                "getDeclaringClass()Ljava/lang/Class;",
                "describeConstable()Ljava/util/Optional;");
        private static final Set<String> JAVA_LANG_OBJECT_METHODS = Set.of(
                "<init>()V",
                "getClass()Ljava/lang/Class;",
                "hashCode()I",
                "equals(Ljava/lang/Object;)Z",
                "clone()Ljava/lang/Object;",
                "toString()Ljava/lang/String;",
                "notify()V",
                "notifyAll()V",
                "wait()V",
                "wait(J)V",
                "wait(JI)V",
                "finalize()V");

        private final Map<String, ClassInfo> classes;
        private final Map<String, List<String>> supertypes = new HashMap<>();

        private ClassHierarchy(Map<String, ClassInfo> classes) {
            this.classes = classes;
        }

        private static ClassHierarchy read(Iterable<File> classpath) throws IOException {
            Map<String, ClassInfo> classes = new LinkedHashMap<>();
            for (File entry : classpath) {
                if (entry == null || !entry.exists()) {
                    continue;
                }
                if (entry.isDirectory()) {
                    try (var paths = Files.walk(entry.toPath())) {
                        paths.filter(Files::isRegularFile)
                                .filter(path -> path.toString().endsWith(".class"))
                                .forEach(path -> {
                                    try {
                                        addClass(classes, Files.readAllBytes(path));
                                    } catch (IOException exception) {
                                        throw new HierarchyReadException(exception);
                                    }
                                });
                    } catch (HierarchyReadException exception) {
                        throw exception.ioException;
                    }
                } else if (entry.getName().endsWith(".jar")) {
                    try (ZipFile jar = new ZipFile(entry)) {
                        Enumeration<? extends ZipEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry classEntry = entries.nextElement();
                            if (classEntry.isDirectory() || !classEntry.getName().endsWith(".class")) {
                                continue;
                            }
                            try (InputStream input = jar.getInputStream(classEntry)) {
                                addClass(classes, input.readAllBytes());
                            }
                        }
                    }
                }
            }
            return new ClassHierarchy(classes);
        }

        private static void addClass(Map<String, ClassInfo> classes, byte[] bytes) {
            ClassReader reader = new ClassReader(bytes);
            String name = reader.getClassName();
            if (!name.startsWith("net/minecraft/") && !name.startsWith("com/mojang/")
                    && !name.startsWith("net/minecraftforge/")
                    && !name.startsWith("dev/ryanhcode/sable/")) {
                return;
            }
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            Set<String> fields = new LinkedHashSet<>();
            node.fields.forEach(field -> fields.add(field.name + field.desc));
            Set<String> methods = new LinkedHashSet<>();
            node.methods.forEach(method -> methods.add(method.name + method.desc));
            classes.putIfAbsent(node.name,
                    new ClassInfo(node.superName, new ArrayList<>(node.interfaces), fields, methods));
        }

        private List<String> supertypes(String owner) {
            return supertypes.computeIfAbsent(owner, this::resolveSupertypes);
        }

        private List<String> resolveSupertypes(String owner) {
            List<String> resolved = new ArrayList<>();
            Set<String> visited = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            addDirectSupertypes(owner, pending);
            while (!pending.isEmpty()) {
                String type = pending.removeFirst();
                if (!visited.add(type)) {
                    continue;
                }
                resolved.add(type);
                addDirectSupertypes(type, pending);
            }
            return Collections.unmodifiableList(resolved);
        }

        private void addDirectSupertypes(String owner, Deque<String> pending) {
            ClassInfo info = classes.get(owner);
            if (info == null) {
                return;
            }
            if (info.superName != null) {
                pending.addLast(info.superName);
            }
            info.interfaces.forEach(pending::addLast);
        }

        private boolean declaresField(String owner, String name, String descriptor) {
            ClassInfo info = classes.get(owner);
            return info != null && info.fields.contains(name + descriptor);
        }

        private boolean hasClass(String owner) {
            return classes.containsKey(owner);
        }

        private boolean declaresMethod(String owner, String name, String descriptor) {
            if ("java/lang/Enum".equals(owner)) {
                return JAVA_LANG_ENUM_METHODS.contains(name + descriptor)
                        || JAVA_LANG_OBJECT_METHODS.contains(name + descriptor);
            }
            if ("java/lang/Object".equals(owner)) {
                return JAVA_LANG_OBJECT_METHODS.contains(name + descriptor);
            }
            ClassInfo info = classes.get(owner);
            return info != null && info.methods.contains(name + descriptor);
        }

        private boolean resolvesNamedMember(String owner, String name, String descriptor, boolean method) {
            if (method ? declaresMethod(owner, name, descriptor) : declaresField(owner, name, descriptor)) {
                return true;
            }
            for (String supertype : supertypes(owner)) {
                if (method
                        ? declaresMethod(supertype, name, descriptor)
                        : declaresField(supertype, name, descriptor)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ClassInfo {
        private final String superName;
        private final List<String> interfaces;
        private final Set<String> fields;
        private final Set<String> methods;

        private ClassInfo(String superName, List<String> interfaces, Set<String> fields, Set<String> methods) {
            this.superName = superName;
            this.interfaces = interfaces;
            this.fields = fields;
            this.methods = methods;
        }
    }

    private static final class HierarchyReadException extends RuntimeException {
        private final IOException ioException;

        private HierarchyReadException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
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

        private MemberMapping mapFieldName(String owner, String name, String descriptor,
                                           ClassHierarchy hierarchy) {
            String mapped = fieldMappings.getOrDefault(owner, Collections.emptyMap()).get(name);
            if (mapped != null) {
                return new MemberMapping(mapped, owner, false);
            }
            for (String supertype : hierarchy.supertypes(owner)) {
                mapped = fieldMappings.getOrDefault(supertype, Collections.emptyMap()).get(name);
                if (mapped != null && hierarchy.declaresField(supertype, name, descriptor)) {
                    return new MemberMapping(mapped, supertype, true);
                }
            }
            return MemberMapping.unchanged(owner, name);
        }

        private MemberMapping mapMethodName(String owner, String name, String descriptor,
                                            ClassHierarchy hierarchy) {
            String mapped = methodMappings.getOrDefault(owner, Collections.emptyMap()).get(name + descriptor);
            if (mapped != null) {
                return new MemberMapping(mapped, owner, false);
            }
            for (String supertype : hierarchy.supertypes(owner)) {
                mapped = methodMappings.getOrDefault(supertype, Collections.emptyMap()).get(name + descriptor);
                if (mapped != null && hierarchy.declaresMethod(supertype, name, descriptor)) {
                    return new MemberMapping(mapped, supertype, true);
                }
            }
            return MemberMapping.unchanged(owner, name);
        }

        private boolean isProductionMember(String owner, String name, String descriptor, boolean method,
                                           ClassHierarchy hierarchy) {
            if (isProductionMemberExact(owner, name, descriptor, method)) {
                return true;
            }
            for (String supertype : hierarchy.supertypes(owner)) {
                if (isProductionMemberExact(supertype, name, descriptor, method)) {
                    String named = method
                            ? productionMethods.get(supertype).get(name + descriptor)
                            : productionFields.get(supertype).get(name);
                    if (method
                            ? hierarchy.declaresMethod(supertype, named, descriptor)
                            : hierarchy.declaresField(supertype, named, descriptor)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isProductionMemberExact(String owner, String name, String descriptor, boolean method) {
            return method
                    ? productionMethods.getOrDefault(owner, Collections.emptyMap()).containsKey(name + descriptor)
                    : productionFields.getOrDefault(owner, Collections.emptyMap()).containsKey(name);
        }
    }

    private static final class MemberMapping {
        private final String name;
        private final String declaringOwner;
        private final boolean inherited;

        private MemberMapping(String name, String declaringOwner, boolean inherited) {
            this.name = name;
            this.declaringOwner = declaringOwner;
            this.inherited = inherited;
        }

        private static MemberMapping unchanged(String owner, String name) {
            return new MemberMapping(name, owner, false);
        }
    }

    private static final class LambdaSamSite {
        private final String owner;
        private final String descriptor;

        private LambdaSamSite(String owner, String descriptor) {
            this.owner = owner;
            this.descriptor = descriptor;
        }
    }

    private static final class SamNameRemapResult {
        private final byte[] bytes;
        private final int remaps;
        private final int inheritedRemaps;
        private final Set<String> minecraftReferenceOwners;

        private SamNameRemapResult(byte[] bytes, int remaps, int inheritedRemaps,
                                   Set<String> minecraftReferenceOwners) {
            this.bytes = bytes;
            this.remaps = remaps;
            this.inheritedRemaps = inheritedRemaps;
            this.minecraftReferenceOwners = minecraftReferenceOwners;
        }

        private boolean changed() {
            return remaps > 0;
        }
    }

    public static final class Result {
        public final String inputSha256;
        public final String outputSha256;
        public final int transformedClasses;
        public final int memberReferenceRemaps;
        public final int inheritedMemberReferenceRemaps;
        public final int invokedynamicHandleRemaps;
        public final int invokedynamicSamNameRemaps;
        public final Set<String> minecraftReferenceOwners;
        public final Set<String> namedMinecraftMemberReferences;
        public final Set<String> productionMinecraftMemberReferences;
        public final Set<String> unresolvedMinecraftMemberReferences;
        public final int totalInvokedynamicSites;
        public final int lambdaMetafactorySites;
        public final int minecraftLambdaMetafactorySites;

        private Result(String inputSha256, String outputSha256, int transformedClasses,
                       int memberReferenceRemaps, int inheritedMemberReferenceRemaps,
                       int invokedynamicHandleRemaps,
                       int invokedynamicSamNameRemaps,
                       Set<String> minecraftReferenceOwners,
                       Set<String> namedMinecraftMemberReferences,
                       Set<String> productionMinecraftMemberReferences,
                       Set<String> unresolvedMinecraftMemberReferences,
                       int totalInvokedynamicSites,
                       int lambdaMetafactorySites,
                       int minecraftLambdaMetafactorySites) {
            this.inputSha256 = inputSha256;
            this.outputSha256 = outputSha256;
            this.transformedClasses = transformedClasses;
            this.memberReferenceRemaps = memberReferenceRemaps;
            this.inheritedMemberReferenceRemaps = inheritedMemberReferenceRemaps;
            this.invokedynamicHandleRemaps = invokedynamicHandleRemaps;
            this.invokedynamicSamNameRemaps = invokedynamicSamNameRemaps;
            this.minecraftReferenceOwners = Collections.unmodifiableSet(new LinkedHashSet<>(minecraftReferenceOwners));
            this.namedMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(namedMinecraftMemberReferences));
            this.productionMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(productionMinecraftMemberReferences));
            this.unresolvedMinecraftMemberReferences = Collections.unmodifiableSet(
                    new LinkedHashSet<>(unresolvedMinecraftMemberReferences));
            this.totalInvokedynamicSites = totalInvokedynamicSites;
            this.lambdaMetafactorySites = lambdaMetafactorySites;
            this.minecraftLambdaMetafactorySites = minecraftLambdaMetafactorySites;
        }
    }

    public static final class ReferenceReport {
        public final Set<String> minecraftReferenceOwners;
        public final Set<String> namedMinecraftMemberReferences;
        public final Set<String> productionMinecraftMemberReferences;
        public final Set<String> unresolvedMinecraftMemberReferences;
        public final int totalInvokedynamicSites;
        public final int lambdaMetafactorySites;
        public final int minecraftLambdaMetafactorySites;

        private ReferenceReport(Set<String> minecraftReferenceOwners, Set<String> namedMinecraftMemberReferences,
                                Set<String> productionMinecraftMemberReferences,
                                Set<String> unresolvedMinecraftMemberReferences,
                                int totalInvokedynamicSites,
                                int lambdaMetafactorySites,
                                int minecraftLambdaMetafactorySites) {
            this.minecraftReferenceOwners = Collections.unmodifiableSet(new LinkedHashSet<>(minecraftReferenceOwners));
            this.namedMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(namedMinecraftMemberReferences));
            this.productionMinecraftMemberReferences = Collections.unmodifiableSet(new LinkedHashSet<>(productionMinecraftMemberReferences));
            this.unresolvedMinecraftMemberReferences = Collections.unmodifiableSet(
                    new LinkedHashSet<>(unresolvedMinecraftMemberReferences));
            this.totalInvokedynamicSites = totalInvokedynamicSites;
            this.lambdaMetafactorySites = lambdaMetafactorySites;
            this.minecraftLambdaMetafactorySites = minecraftLambdaMetafactorySites;
        }
    }

    public static final class AbiReport {
        public final Set<String> referencedClasses;
        public final Set<String> resolvedClasses;
        public final Set<String> unresolvedClasses;
        public final Set<String> resolvedMethods;
        public final Set<String> unresolvedMethods;
        public final Set<String> resolvedFields;
        public final Set<String> unresolvedFields;

        private AbiReport(Set<String> referencedClasses, Set<String> resolvedClasses,
                          Set<String> unresolvedClasses, Set<String> resolvedMethods,
                          Set<String> unresolvedMethods, Set<String> resolvedFields,
                          Set<String> unresolvedFields) {
            this.referencedClasses = Collections.unmodifiableSet(new LinkedHashSet<>(referencedClasses));
            this.resolvedClasses = Collections.unmodifiableSet(new LinkedHashSet<>(resolvedClasses));
            this.unresolvedClasses = Collections.unmodifiableSet(new LinkedHashSet<>(unresolvedClasses));
            this.resolvedMethods = Collections.unmodifiableSet(new LinkedHashSet<>(resolvedMethods));
            this.unresolvedMethods = Collections.unmodifiableSet(new LinkedHashSet<>(unresolvedMethods));
            this.resolvedFields = Collections.unmodifiableSet(new LinkedHashSet<>(resolvedFields));
            this.unresolvedFields = Collections.unmodifiableSet(new LinkedHashSet<>(unresolvedFields));
        }

        public boolean passed() {
            return unresolvedClasses.isEmpty() && unresolvedMethods.isEmpty() && unresolvedFields.isEmpty();
        }
    }
}
