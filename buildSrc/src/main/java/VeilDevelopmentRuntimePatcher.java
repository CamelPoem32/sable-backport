import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class VeilDevelopmentRuntimePatcher {

    private static final Map<String, String> EXPECTED_ALIASES = expectedAliases();

    private VeilDevelopmentRuntimePatcher() {
    }

    public static int patch(File inputJar, File mappingsFile, File outputJar) throws IOException {
        verifyMappings(mappingsFile);

        Set<String> transformedAliases = new LinkedHashSet<>();
        Set<String> unknownAliases = new LinkedHashSet<>();
        Files.createDirectories(outputJar.toPath().getParent());

        try (ZipFile source = new ZipFile(inputJar);
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(outputJar.toPath()))) {
            List<? extends ZipEntry> entries = entries(source);
            entries.sort((left, right) -> left.getName().compareTo(right.getName()));

            for (ZipEntry sourceEntry : entries) {
                if (sourceEntry.getName().startsWith("org/lwjgl/")
                        || sourceEntry.getName().startsWith("META-INF/versions/")) {
                    continue;
                }
                byte[] bytes;
                if (sourceEntry.isDirectory()) {
                    bytes = new byte[0];
                } else {
                    try (InputStream input = source.getInputStream(sourceEntry)) {
                        bytes = input.readAllBytes();
                    }
                }

                if (sourceEntry.getName().endsWith(".class")) {
                    bytes = patchClass(bytes, transformedAliases, unknownAliases);
                }

                ZipEntry outputEntry = new ZipEntry(sourceEntry.getName());
                outputEntry.setTime(0L);
                output.putNextEntry(outputEntry);
                output.write(bytes);
                output.closeEntry();
            }
        }

        if (!unknownAliases.isEmpty()) {
            throw new IllegalStateException("Unmapped Veil shadow aliases: " + unknownAliases);
        }
        if (!transformedAliases.equals(EXPECTED_ALIASES.keySet())) {
            throw new IllegalStateException(
                    "Expected Veil aliases " + EXPECTED_ALIASES.keySet() + ", transformed " + transformedAliases
            );
        }
        return transformedAliases.size();
    }

    private static byte[] patchClass(byte[] bytes, Set<String> transformedAliases, Set<String> unknownAliases) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                if (name.startsWith("shadow$m_") && !EXPECTED_ALIASES.containsKey(name)) {
                    unknownAliases.add(name);
                }
                String mappedName = EXPECTED_ALIASES.getOrDefault(name, name);
                if (!mappedName.equals(name)) {
                    transformedAliases.add(name);
                }
                MethodVisitor delegate = super.visitMethod(access, mappedName, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String invokedName,
                                                String invokedDescriptor, boolean isInterface) {
                        super.visitMethodInsn(
                                opcode,
                                owner,
                                EXPECTED_ALIASES.getOrDefault(invokedName, invokedName),
                                invokedDescriptor,
                                isInterface
                        );
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static void verifyMappings(File mappingsFile) throws IOException {
        Map<String, String> methodMappings = new HashMap<>();
        for (String line : Files.readAllLines(mappingsFile.toPath(), StandardCharsets.UTF_8)) {
            if (!line.startsWith("MD: ")) {
                continue;
            }
            String[] parts = line.split(" ");
            methodMappings.put(simpleName(parts[1]), simpleName(parts[3]));
        }

        for (Map.Entry<String, String> entry : EXPECTED_ALIASES.entrySet()) {
            String source = entry.getKey().substring("shadow$".length());
            String target = entry.getValue().substring("shadow$".length());
            if (!target.equals(methodMappings.get(source))) {
                throw new IllegalStateException("Missing Veil development alias mapping " + source + " -> " + target);
            }
        }
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    private static List<? extends ZipEntry> entries(ZipFile zipFile) {
        List<ZipEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            entries.add(enumeration.nextElement());
        }
        return entries;
    }

    private static Map<String, String> expectedAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("shadow$m_252880_", "shadow$translate");
        aliases.put("shadow$m_85841_", "shadow$scale");
        aliases.put("shadow$m_252781_", "shadow$mulPose");
        aliases.put("shadow$m_272245_", "shadow$rotateAround");
        aliases.put("shadow$m_85836_", "shadow$pushPose");
        aliases.put("shadow$m_85849_", "shadow$popPose");
        return Map.copyOf(aliases);
    }
}
