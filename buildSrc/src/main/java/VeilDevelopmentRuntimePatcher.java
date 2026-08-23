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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class VeilDevelopmentRuntimePatcher {

    private static final Map<String, String> EXPECTED_ALIASES = expectedAliases();
    private static final String ACCESS_TRANSFORMER = "META-INF/accesstransformer.cfg";
    private static final Pattern SRG_MEMBER = Pattern.compile("^[fm]_\\d+_$");

    private VeilDevelopmentRuntimePatcher() {
    }

    public static Result patch(File inputJar, File mappingsFile, File outputJar) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        verifyMappings(mappings);

        Set<String> transformedAliases = new LinkedHashSet<>();
        Set<String> unknownAliases = new LinkedHashSet<>();
        AccessTransformerResult accessTransformerResult = null;
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
                } else if (ACCESS_TRANSFORMER.equals(sourceEntry.getName())) {
                    accessTransformerResult = remapAccessTransformer(bytes, mappings);
                    bytes = accessTransformerResult.bytes;
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
        if (accessTransformerResult == null) {
            throw new IllegalStateException("Veil development runtime is missing " + ACCESS_TRANSFORMER);
        }
        if (accessTransformerResult.unresolvedKnownMembers > 0) {
            throw new IllegalStateException(
                    "Veil access transformer has unresolved mapped SRG members: "
                            + accessTransformerResult.unresolvedKnownMembers
            );
        }
        return new Result(
                transformedAliases.size(),
                accessTransformerResult.memberEntries,
                accessTransformerResult.remappedMembers,
                accessTransformerResult.unresolvedKnownMembers
        );
    }

    public static AccessTransformerReport inspectAccessTransformer(File jar, File mappingsFile) throws IOException {
        MappingSet mappings = MappingSet.read(mappingsFile);
        try (ZipFile zipFile = new ZipFile(jar)) {
            ZipEntry entry = zipFile.getEntry(ACCESS_TRANSFORMER);
            if (entry == null) {
                throw new IOException(jar + " is missing " + ACCESS_TRANSFORMER);
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                AccessTransformerResult result = remapAccessTransformer(input.readAllBytes(), mappings);
                String text = new String(inputBytes(jar, ACCESS_TRANSFORMER), StandardCharsets.UTF_8);
                return new AccessTransformerReport(
                        result.memberEntries,
                        result.remappedMembers,
                        result.unresolvedKnownMembers,
                        text
                );
            }
        }
    }

    public static AccessTransformerPatch remapAccessTransformer(byte[] bytes, File mappingsFile) throws IOException {
        AccessTransformerResult result = remapAccessTransformer(bytes, MappingSet.read(mappingsFile));
        return new AccessTransformerPatch(
                result.bytes,
                result.memberEntries,
                result.remappedMembers,
                result.unresolvedKnownMembers
        );
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

    private static void verifyMappings(MappingSet mappings) {
        for (Map.Entry<String, String> entry : EXPECTED_ALIASES.entrySet()) {
            String source = entry.getKey().substring("shadow$".length());
            String target = entry.getValue().substring("shadow$".length());
            if (!target.equals(mappings.uniqueMethodNames.get(source))) {
                throw new IllegalStateException("Missing Veil development alias mapping " + source + " -> " + target);
            }
        }
    }

    private static AccessTransformerResult remapAccessTransformer(byte[] bytes, MappingSet mappings) {
        String original = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = original.split("\\R", -1);
        List<String> remappedLines = new ArrayList<>(lines.length);
        int memberEntries = 0;
        int remappedMembers = 0;
        int unresolvedKnownMembers = 0;

        for (String line : lines) {
            AccessTransformerLineResult result = remapAccessTransformerLine(line, mappings);
            remappedLines.add(result.line);
            memberEntries += result.memberEntry ? 1 : 0;
            remappedMembers += result.remapped ? 1 : 0;
            unresolvedKnownMembers += result.unresolvedKnown ? 1 : 0;
        }

        String lineSeparator = original.contains("\r\n") ? "\r\n" : "\n";
        return new AccessTransformerResult(
                String.join(lineSeparator, remappedLines).getBytes(StandardCharsets.UTF_8),
                memberEntries,
                remappedMembers,
                unresolvedKnownMembers
        );
    }

    private static AccessTransformerLineResult remapAccessTransformerLine(String line, MappingSet mappings) {
        int commentStart = line.indexOf('#');
        String code = commentStart >= 0 ? line.substring(0, commentStart) : line;
        String comment = commentStart >= 0 ? line.substring(commentStart) : "";
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return new AccessTransformerLineResult(line, false, false, false);
        }

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2) {
            return new AccessTransformerLineResult(line, false, false, false);
        }

        String access = tokens[0];
        String owner = mappings.mapClass(tokens[1].replace('.', '/')).replace('/', '.');
        if (tokens.length == 2) {
            return new AccessTransformerLineResult(access + " " + owner + commentWithSpacing(comment), false, false, false);
        }

        String memberToken = tokens[2];
        String descriptor = tokens.length >= 4 ? tokens[3] : null;
        ParsedMember parsed = parseMember(memberToken, descriptor);
        if (parsed == null) {
            return new AccessTransformerLineResult(access + " " + owner + " " + memberToken + commentWithSpacing(comment),
                    false, false, false);
        }

        String ownerInternal = owner.replace('.', '/');
        String mappedName = parsed.method
                ? mappings.mapMethod(ownerInternal, parsed.name, parsed.descriptor)
                : mappings.mapField(ownerInternal, parsed.name);
        boolean remapped = !mappedName.equals(parsed.name);
        boolean unresolvedKnown = !remapped && mappings.hasKnownMapping(ownerInternal, parsed.name, parsed.descriptor, parsed.method)
                && SRG_MEMBER.matcher(parsed.name).matches();

        String mappedMember = parsed.method && parsed.descriptorInMemberToken
                ? mappedName + parsed.descriptor
                : mappedName;
        StringBuilder rebuilt = new StringBuilder(access).append(' ').append(owner).append(' ').append(mappedMember);
        if (parsed.method && !parsed.descriptorInMemberToken) {
            rebuilt.append(' ').append(parsed.descriptor);
        }
        rebuilt.append(commentWithSpacing(comment));
        return new AccessTransformerLineResult(rebuilt.toString(), true, remapped, unresolvedKnown);
    }

    private static ParsedMember parseMember(String memberToken, String descriptorToken) {
        int descriptorStart = memberToken.indexOf('(');
        if (descriptorStart >= 0) {
            return new ParsedMember(
                    memberToken.substring(0, descriptorStart),
                    memberToken.substring(descriptorStart),
                    true,
                    true
            );
        }
        if (descriptorToken != null && descriptorToken.startsWith("(")) {
            return new ParsedMember(memberToken, descriptorToken, true, false);
        }
        return new ParsedMember(memberToken, null, false, false);
    }

    private static String commentWithSpacing(String comment) {
        return comment.isEmpty() ? "" : " " + comment;
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    private static byte[] inputBytes(File jar, String entryName) throws IOException {
        try (ZipFile zipFile = new ZipFile(jar)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                throw new IOException(jar + " is missing " + entryName);
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                return input.readAllBytes();
            }
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

    public static final class Result {
        public final int aliasCount;
        public final int accessTransformerMemberEntries;
        public final int accessTransformerRemappedMembers;
        public final int accessTransformerUnresolvedKnownMembers;

        private Result(int aliasCount, int accessTransformerMemberEntries,
                       int accessTransformerRemappedMembers, int accessTransformerUnresolvedKnownMembers) {
            this.aliasCount = aliasCount;
            this.accessTransformerMemberEntries = accessTransformerMemberEntries;
            this.accessTransformerRemappedMembers = accessTransformerRemappedMembers;
            this.accessTransformerUnresolvedKnownMembers = accessTransformerUnresolvedKnownMembers;
        }
    }

    public static final class AccessTransformerReport {
        public final int memberEntries;
        public final int remappedMembers;
        public final int unresolvedKnownMembers;
        public final String text;

        private AccessTransformerReport(int memberEntries, int remappedMembers, int unresolvedKnownMembers,
                                        String text) {
            this.memberEntries = memberEntries;
            this.remappedMembers = remappedMembers;
            this.unresolvedKnownMembers = unresolvedKnownMembers;
            this.text = text;
        }
    }

    public static final class AccessTransformerPatch {
        public final byte[] bytes;
        public final int memberEntries;
        public final int remappedMembers;
        public final int unresolvedKnownMembers;

        private AccessTransformerPatch(byte[] bytes, int memberEntries, int remappedMembers,
                                       int unresolvedKnownMembers) {
            this.bytes = bytes.clone();
            this.memberEntries = memberEntries;
            this.remappedMembers = remappedMembers;
            this.unresolvedKnownMembers = unresolvedKnownMembers;
        }
    }

    private static final class AccessTransformerResult {
        private final byte[] bytes;
        private final int memberEntries;
        private final int remappedMembers;
        private final int unresolvedKnownMembers;

        private AccessTransformerResult(byte[] bytes, int memberEntries, int remappedMembers,
                                        int unresolvedKnownMembers) {
            this.bytes = bytes;
            this.memberEntries = memberEntries;
            this.remappedMembers = remappedMembers;
            this.unresolvedKnownMembers = unresolvedKnownMembers;
        }
    }

    private static final class AccessTransformerLineResult {
        private final String line;
        private final boolean memberEntry;
        private final boolean remapped;
        private final boolean unresolvedKnown;

        private AccessTransformerLineResult(String line, boolean memberEntry, boolean remapped,
                                            boolean unresolvedKnown) {
            this.line = line;
            this.memberEntry = memberEntry;
            this.remapped = remapped;
            this.unresolvedKnown = unresolvedKnown;
        }
    }

    private static final class ParsedMember {
        private final String name;
        private final String descriptor;
        private final boolean method;
        private final boolean descriptorInMemberToken;

        private ParsedMember(String name, String descriptor, boolean method, boolean descriptorInMemberToken) {
            this.name = name;
            this.descriptor = descriptor;
            this.method = method;
            this.descriptorInMemberToken = descriptorInMemberToken;
        }
    }

    private static final class MappingSet {
        private final Map<String, String> classes;
        private final Map<String, Map<String, String>> fields;
        private final Map<String, Map<String, String>> methods;
        private final Map<String, String> uniqueFieldNames;
        private final Map<String, String> uniqueMethodNames;

        private MappingSet(Map<String, String> classes, Map<String, Map<String, String>> fields,
                           Map<String, Map<String, String>> methods, Map<String, String> uniqueFieldNames,
                           Map<String, String> uniqueMethodNames) {
            this.classes = classes;
            this.fields = fields;
            this.methods = methods;
            this.uniqueFieldNames = uniqueFieldNames;
            this.uniqueMethodNames = uniqueMethodNames;
        }

        private static MappingSet read(File mappingsFile) throws IOException {
            Map<String, String> classes = new HashMap<>();
            Map<String, Map<String, String>> fields = new HashMap<>();
            Map<String, Map<String, String>> methods = new HashMap<>();
            Map<String, String> uniqueFieldNames = new HashMap<>();
            Map<String, String> uniqueMethodNames = new HashMap<>();
            for (String line : Files.readAllLines(mappingsFile.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("CL: ")) {
                    String[] parts = line.split(" ");
                    classes.put(parts[1], parts[2]);
                } else if (line.startsWith("FD: ")) {
                    String[] parts = line.split(" ");
                    String left = parts[1];
                    String right = parts[2];
                    String owner = owner(left);
                    String sourceName = simpleName(left);
                    String targetName = simpleName(right);
                    fields.computeIfAbsent(owner, ignored -> new LinkedHashMap<>()).put(sourceName, targetName);
                    putUnique(uniqueFieldNames, sourceName, targetName);
                } else if (line.startsWith("MD: ")) {
                    String[] parts = line.split(" ");
                    String left = parts[1];
                    String leftDescriptor = parts[2];
                    String right = parts[3];
                    String owner = owner(left);
                    String sourceName = simpleName(left);
                    String targetName = simpleName(right);
                    methods.computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                            .put(sourceName + leftDescriptor, targetName);
                    putUnique(uniqueMethodNames, sourceName, targetName);
                }
            }
            uniqueFieldNames.values().removeIf(MappingSet::ambiguous);
            uniqueMethodNames.values().removeIf(MappingSet::ambiguous);
            return new MappingSet(classes, fields, methods, uniqueFieldNames, uniqueMethodNames);
        }

        private String mapClass(String owner) {
            return classes.getOrDefault(owner, owner);
        }

        private String mapField(String owner, String name) {
            return fields.getOrDefault(owner, Collections.emptyMap())
                    .getOrDefault(name, uniqueFieldNames.getOrDefault(name, name));
        }

        private String mapMethod(String owner, String name, String descriptor) {
            return methods.getOrDefault(owner, Collections.emptyMap())
                    .getOrDefault(name + descriptor, name);
        }

        private boolean hasKnownMapping(String owner, String name, String descriptor, boolean method) {
            if (method) {
                return methods.getOrDefault(owner, Collections.emptyMap()).containsKey(name + descriptor);
            }
            return fields.getOrDefault(owner, Collections.emptyMap()).containsKey(name)
                    || uniqueFieldNames.containsKey(name);
        }

        private static String owner(String memberPath) {
            return memberPath.substring(0, memberPath.lastIndexOf('/'));
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
