import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Exact production owner audit and actual MixinExtras wrapping-stage smoke, without Minecraft initialization. */
public final class M44GoggleOverlayVerifier {
    public static final String TARGET = "com/simibubi/create/content/equipment/goggles/GoggleOverlayRenderer";
    public static final String EXTERNAL = "com/drmangotea/tfmg/mixin/GoggleOverlayRendererMixin";
    public static final String MIXIN = "dev/ryanhcode/sable/mixin/compatibility/tfmg/GoggleOverlayRendererMixin";
    private static final String DESC = "(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V";
    private M44GoggleOverlayVerifier() { }

    public static void nativeOwner(File create, File tfmg) throws Exception {
        require(hash(Files.readAllBytes(tfmg.toPath())).equals("a789d1519fd4033334d0e217b252c557095b8007922247bfe53ee9db4f76a63d"), "TFMG audit artifact drift");
        try (JarFile nativeJar = new JarFile(create); JarFile external = new JarFile(tfmg)) {
            require(hash(nativeJar.getInputStream(nativeJar.getJarEntry(TARGET + ".class")).readAllBytes()).equals("3fd5af4de8efe089df151429016766e71439d6b8f409aac4ca3db4e5f3a434ae"), "exact Create 6.0.8 overlay bytes");
            ClassNode original = type(nativeJar, TARGET);
            MethodNode overlay = method(original, "renderOverlay", DESC);
            require(typeIndex(overlay, Opcodes.INSTANCEOF, "net/minecraft/world/phys/BlockHitResult") < typeIndex(overlay, Opcodes.CHECKCAST, "net/minecraft/world/phys/BlockHitResult"), "native Create block type guard");
            require(method(original, "proxiedOverlayPosition", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;") != null, "native block proxy");
            require(overlay.instructions.iterator().hasNext(), "native method body");
            byte[] bytes = external.getInputStream(external.getJarEntry(EXTERNAL + ".class")).readAllBytes();
            require(hash(bytes).equals("eb1ea0e6f8318606f01e3e21a6d98eb771c8e0882201c8147ed790db9d4ce4d8"), "exact external handler bytes");
            ClassNode tfmgMixin = type(external, EXTERNAL);
            MethodNode handler = method(tfmgMixin, "renderOverlay", DESC.substring(0, DESC.length() - 2) + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
            int cast = typeIndex(handler, Opcodes.CHECKCAST, "net/minecraft/world/phys/BlockHitResult");
            require(cast >= 0 && typeIndex(handler, Opcodes.INSTANCEOF, "net/minecraft/world/phys/BlockHitResult") == -1, "external unguarded cast ownership");
            require(Arrays.stream(handler.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.owner.equals("net/minecraft/client/Minecraft") && f.name.equals("f_91077_")), "external Minecraft.hitResult read");
            AnnotationNode inject = handler.visibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
            require(Boolean.FALSE.equals(value(inject, "remap")) && Boolean.TRUE.equals(value(inject, "cancellable")), "external injection settings");
            List<AnnotationNode> points = (List<AnnotationNode>) value(inject, "at");
            require(points.size() == 1 && "HEAD".equals(value(points.get(0), "value")), "external injection point");
            String config = text(external, "tfmg.mixins.json");
            require(config.contains("GoggleOverlayRendererMixin") && config.contains("Create-The_Factory_Must_Grow.refmap.json"), "external mixin config ownership");
            require(external.getJarEntry("Create-The_Factory_Must_Grow.refmap.json") == null
                    && external.getJarEntry("mixin.refmap.json") != null, "audited external refmap packaging changed");
            require(text(external, "META-INF/mods.toml").contains("modId=\"tfmg\""), "external owning mod");
        }
    }

    public static void packaged(File artifact, Map report) throws Exception {
        List<Map> rows = ((List<Map>) report.get("checks")).stream().filter(r -> r.get("mixin").equals(MIXIN)).toList();
        require(rows.size() == 1 && rows.get(0).get("method").equals("renderOverlay" + DESC)
                && rows.get(0).get("at").equals("METHOD") && ((Number) rows.get(0).get("matches")).intValue() == 1,
                "packaged wrap target/descriptor/count " + rows);
        try (JarFile jar = new JarFile(artifact)) {
            ClassNode mixin = type(jar, MIXIN);
            require(mixin.visibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/MixinModVersionConstraint;") && "[1.0.2f]".equals(value(a, "value"))), "TFMG version gating");
            for (MethodNode method : mixin.methods) for (AbstractInsnNode i : method.instructions) {
                require(!(i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && f.owner.equals("net/minecraft/client/Minecraft")), "global Minecraft field mutation");
                require(!(i instanceof TypeInsnNode t && t.getOpcode() == Opcodes.CHECKCAST && t.desc.equals("net/minecraft/world/phys/BlockHitResult")), "unsafe Sable mixin cast");
            }
            String config = text(jar, "sable-common-forge.mixins.json");
            require(config.contains("compatibility.tfmg.GoggleOverlayRendererMixin"), "packaged client config");
            require(config.contains("\"defaultRequire\": 1"), "required hook weakened");
        }
    }

    /** Model the external HEAD merge, then run the real framework wrapping stage on that target. No game classes load. */
    public static void wrappingSmoke(File create, File tfmg, File artifact, File extras, List<File> dependencies) throws Exception {
        List<java.net.URL> urls = new ArrayList<>(); urls.add(extras.toURI().toURL());
        for (File dep : dependencies) if (dep.isFile() && dep.getName().endsWith(".jar")) urls.add(dep.toURI().toURL());
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(java.net.URL[]::new), M44GoggleOverlayVerifier.class.getClassLoader());
             JarFile nativeJar = new JarFile(create); JarFile external = new JarFile(tfmg); JarFile sable = new JarFile(artifact)) {
            ClassNode target = type(nativeJar, TARGET);
            MethodNode entry = method(target, "renderOverlay", DESC);
            ClassNode externalMixin = type(external, EXTERNAL);
            MethodNode injected = method(externalMixin, "renderOverlay", DESC.substring(0, DESC.length() - 2) + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
            injected.name = "handler$tfmg$renderOverlay";
            for (AbstractInsnNode i : injected.instructions) {
                if (i instanceof MethodInsnNode c && c.owner.equals(EXTERNAL)) c.owner = TARGET;
                if (i instanceof FieldInsnNode f && f.owner.equals(EXTERNAL)) f.owner = TARGET;
            }
            target.methods.add(injected);
            InsnList head = new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD, 0)); head.add(new VarInsnNode(Opcodes.ALOAD, 1));
            head.add(new VarInsnNode(Opcodes.FLOAD, 2)); head.add(new VarInsnNode(Opcodes.ILOAD, 3)); head.add(new VarInsnNode(Opcodes.ILOAD, 4));
            head.add(new InsnNode(Opcodes.ACONST_NULL)); // Callback value is not executed; this model verifies structural wrapping order only.
            head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, injected.name, injected.desc, false));
            entry.instructions.insert(head);
            MethodNode handler = type(sable, MIXIN).methods.stream().filter(m -> m.name.equals("sable$guardTfmgOverlayEntry")).findFirst().orElseThrow();
            target.methods.add(handler);
            Class<?> stage = loader.loadClass("com.llamalad7.mixinextras.injector.wrapmethod.WrapMethodStage");
            Object vanilla = loader.loadClass(stage.getName() + "$Vanilla").getConstructor(MethodNode.class).newInstance(entry);
            Object wrapper = loader.loadClass(stage.getName() + "$Wrapper").getConstructor(stage, MethodNode.class, Type.class, List.class)
                    .newInstance(vanilla, handler, Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation"), List.of());
            stage.getMethod("apply", ClassNode.class, LinkedHashSet.class).invoke(wrapper, target, new LinkedHashSet<>());
            MethodNode wrappedEntry = method(target, "renderOverlay", DESC);
            require(calls(wrappedEntry, injected.name) == 0 && calls(wrappedEntry, handler.name) == 1, "guard does not precede injected external HEAD");
            require(target.methods.stream().anyMatch(m -> m.name.contains("mixinextras$wrapped") && calls(m, injected.name) == 1), "injected external body not retained inside operation");
        }
    }

    private static int calls(MethodNode method, String name) { return (int) Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode c && c.name.equals(name)).count(); }
    private static int typeIndex(MethodNode method, int opcode, String name) { AbstractInsnNode[] code = method.instructions.toArray(); for (int i = 0; i < code.length; i++) if (code[i] instanceof TypeInsnNode t && t.getOpcode() == opcode && t.desc.equals(name)) return i; return -1; }
    private static Object value(AnnotationNode a, String key) { for (int i = 0; i < a.values.size(); i += 2) if (a.values.get(i).equals(key)) return a.values.get(i + 1); return null; }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String text(JarFile jar, String path) throws Exception { return new String(jar.getInputStream(jar.getJarEntry(path)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    private static ClassNode type(JarFile jar, String name) throws Exception { ClassNode type = new ClassNode(); new ClassReader(jar.getInputStream(jar.getJarEntry(name + ".class")).readAllBytes()).accept(type, 0); return type; }
    private static MethodNode method(ClassNode type, String name, String desc) { return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(() -> new IllegalStateException("M44 missing method " + type.name + ";" + name + desc)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("M44 bytecode canary: " + message); }
}
