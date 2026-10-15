import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Verifies the staged mapped runtime, not membership in an unrelated source-set classpath. */
public final class M45VeilRuntimeVerifier {
    private M45VeilRuntimeVerifier() { }

    public static void verify(File prepared, File reconstructed, File staged, Collection<File> development,
                              Collection<File> stagedMods, String resolvedVersion, String expectedVersion,
                              Set<String> modIds, Set<String> expectedModIds) throws Exception {
        require(prepared.isFile(), "Veil prepared artifact missing: " + prepared);
        require(resolvedVersion.equals(expectedVersion + "_mapped_official_1.20.1"), "Wrong resolved Veil version: " + resolvedVersion);
        require(prepared.getName().equals("Veil-forge-1.20.1-" + expectedVersion + "-mapped-dev.jar"), "Wrong Veil prepared identity");
        require(reconstructed.isFile() && Arrays.equals(Files.readAllBytes(prepared.toPath()), Files.readAllBytes(reconstructed.toPath())),
                "Veil prepared bytes differ from pinned mapped/alias/AT preparation (raw or stale artifact)");
        require(staged.isFile() && Arrays.equals(Files.readAllBytes(prepared.toPath()), Files.readAllBytes(staged.toPath())),
                "Missing or incorrect staged Veil dependency: " + staged);
        List<File> stagedVeil = veilFiles(stagedMods);
        require(stagedVeil.size() == 1 && stagedVeil.get(0).getCanonicalFile().equals(staged.getCanonicalFile()),
                "Duplicate/missing effective staged Veil: " + stagedVeil);
        List<File> developmentVeil = veilFiles(development);
        require(developmentVeil.size() <= 1 && developmentVeil.stream().allMatch(f -> same(f, prepared)),
                "Raw/unmapped/duplicate Veil leaked onto development runtime: " + developmentVeil);
        require(modIds.equals(expectedModIds), "Unexpected runtime module leakage: " + modIds);
    }

    private static boolean same(File a, File b) {
        try { return a.getCanonicalFile().equals(b.getCanonicalFile()); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static List<File> veilFiles(Collection<File> files) throws Exception {
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(file)) {
                var entry = jar.getJarEntry("META-INF/mods.toml");
                if (entry != null) {
                    String metadata = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    var sections = java.util.regex.Pattern.compile("(?ms)\\[\\[mods]](.*?)(?=\\[\\[|\\z)").matcher(metadata);
                    while (sections.find()) {
                        if (sections.group(1).matches("(?s).*\\bmodId\\s*=\\s*\"veil\".*")) {
                            result.add(file);
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void negativeTests(File directory) throws Exception {
        Files.createDirectories(directory.toPath());
        File prepared = new File(directory, "Veil-forge-1.20.1-1.0.0.296-mapped-dev.jar");
        File expected = new File(directory, "expected.jar");
        File staged = new File(directory, "staged.jar");
        File raw = new File(directory, "raw.jar");
        writeJar(prepared, "mapped"); writeJar(expected, "mapped"); writeJar(staged, "mapped"); writeJar(raw, "raw");
        Set<String> modules = Set.of("sable", "simulated", "aeronautics", "create", "flywheel", "ponder", "veil");
        verify(prepared, expected, staged, List.of(), List.of(staged), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", modules, modules);
        reject("prepared missing", () -> verify(new File(directory, "absent.jar"), expected, staged, List.of(), List.of(staged), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", modules, modules));
        reject("wrong version", () -> verify(prepared, expected, staged, List.of(), List.of(staged), "1.0.0.295_mapped_official_1.20.1", "1.0.0.296", modules, modules));
        reject("raw prepared bytes", () -> verify(prepared, raw, staged, List.of(), List.of(staged), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", modules, modules));
        reject("raw development leak", () -> verify(prepared, expected, staged, List.of(raw), List.of(staged), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", modules, modules));
        reject("duplicate staged", () -> verify(prepared, expected, staged, List.of(), List.of(staged, raw), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", modules, modules));
        reject("missing staged", () -> verify(prepared, expected, new File(directory, "absent.jar"), List.of(), List.of(), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", modules, modules));
        reject("unexpected module", () -> verify(prepared, expected, staged, List.of(), List.of(staged), "1.0.0.296_mapped_official_1.20.1", "1.0.0.296", Set.of("veil", "unexpected"), modules));
    }

    private static void writeJar(File file, String content) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (var entry : Map.of("META-INF/mods.toml", "[[mods]]\nmodId=\"veil\"\n", "namespace.txt", content).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                ZipEntry zip = new ZipEntry(entry.getKey()); zip.setTime(0); out.putNextEntry(zip);
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            }
        }
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void reject(String label, Checked check) throws Exception {
        try { check.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Veil negative case accepted: " + label);
    }
}
