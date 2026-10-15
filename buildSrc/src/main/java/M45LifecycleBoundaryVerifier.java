import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import javax.tools.*;
import java.io.File;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;

/** ASM release canaries plus isolated execution of the actual fixture subscriber against typed test doubles. */
public final class M45LifecycleBoundaryVerifier {
    private static final String EVENT = "dev/ryanhcode/sable/forge/event/ForgeSableAssemblyCompletedEvent";
    private static final String SUBSCRIBER = "dev/ryanhcode/sable/command/M45FixtureLifecycleSubscriptions";
    private static final String ASSEMBLER = "dev/simulated_team/simulated/content/blocks/physics_assembler/PhysicsAssemblerBlockEntity";
    private M45LifecycleBoundaryVerifier() { }

    public static void verify(File artifact) throws Exception {
        try (JarFile jar = new JarFile(artifact)) {
            ClassNode assembler = read(jar, ASSEMBLER);
            MethodNode method = assembler.methods.stream().filter(m -> m.name.equals("assemble")
                    && m.desc.equals("(Lnet/minecraft/server/level/ServerLevel;)V")).findFirst().orElseThrow();
            List<AbstractInsnNode> instructions = Arrays.asList(method.instructions.toArray());
            List<MethodInsnNode> calls = instructions.stream().filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode)i).toList();
            require(calls.stream().filter(c -> c.owner.equals(EVENT) && c.name.equals("<init>")).count() == 1, "Assembly event construction count");
            MethodInsnNode constructor = calls.stream().filter(c -> c.owner.equals(EVENT)).findFirst().orElseThrow();
            require(constructor.desc.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/core/BlockPos;)V"), "Event production arguments");
            List<AbstractInsnNode> code = instructions.stream().filter(i -> i.getOpcode() >= 0).toList();
            List<AbstractInsnNode> operands = code.subList(code.indexOf(constructor) - 6, code.indexOf(constructor));
            require(operands.get(0) instanceof VarInsnNode level && level.var == 1
                    && operands.get(1) instanceof VarInsnNode parent && parent.var == 2
                    && operands.get(2) instanceof VarInsnNode result && result.var == 4
                    && operands.get(3) instanceof MethodInsnNode sub && sub.name.equals("subLevel")
                    && operands.get(4) instanceof VarInsnNode sameResult && sameResult.var == 4
                    && operands.get(5) instanceof MethodInsnNode offset && offset.name.equals("offset"), "Event must carry exact level/parent/returned result identity");
            MethodInsnNode assembly = calls.stream().filter(c -> c.name.equals("assembleFromSingleBlock")).findFirst().orElseThrow();
            MethodInsnNode changed = calls.stream().filter(c -> c.name.equals("setChanged") || c.name.equals("m_6596_")).findFirst().orElseThrow();
            MethodInsnNode post = calls.stream().filter(c -> c.owner.equals("net/minecraftforge/eventbus/api/IEventBus") && c.name.equals("post")).findFirst().orElseThrow();
            require(calls.stream().filter(c -> c.name.equals("post")).count() == 1 && instructions.indexOf(assembly) < instructions.indexOf(changed)
                    && instructions.indexOf(changed) < instructions.indexOf(constructor) && instructions.indexOf(constructor) < instructions.indexOf(post), "Assembly completion ordering");
            for (String field : List.of("lastCandidateBlocks", "lastAcceptedBlocks", "lastRejectedBlocks", "lastVisibleDeltaAfterAssembly", "lastBlockEntityCount", "lastLifecycleState", "lastFailure", "lastAssemblyFailureStage", "lastDisassemblyResult")) {
                require(instructions.stream().anyMatch(i -> i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD
                        && f.name.equals(field) && instructions.indexOf(f) < instructions.indexOf(changed)), "State must precede notification: " + field);
            }
            JumpInsnNode guard = instructions.stream().filter(i -> i instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IFNONNULL
                    && instructions.indexOf(j) > instructions.indexOf(assembly) && instructions.indexOf(j) < instructions.indexOf(changed))
                    .map(i -> (JumpInsnNode)i).findFirst().orElseThrow();
            List<AbstractInsnNode> failed = instructions.subList(instructions.indexOf(guard) + 1, instructions.indexOf(guard.label));
            require(failed.stream().anyMatch(i -> i.getOpcode() == Opcodes.RETURN) && failed.stream().noneMatch(i -> i instanceof MethodInsnNode c && c.owner.equals(EVENT)), "Null assembly returns without notification");
            require(method.tryCatchBlocks.isEmpty(), "Observer failures must not be swallowed");
            for (MethodNode other : assembler.methods) if (other != method) {
                for (AbstractInsnNode i : other.instructions) require(!(i instanceof TypeInsnNode t && t.desc.equals(EVENT)), "No failure/disassembly notification");
            }
            ClassNode event = read(jar, EVENT);
            require(event.superName.equals("net/minecraftforge/eventbus/api/Event") && event.fields.size() == 4
                    && event.fields.stream().allMatch(f -> (f.access & Opcodes.ACC_FINAL) != 0 && !f.desc.contains("command/")), "Neutral immutable production notification");
            ClassNode subscriber = read(jar, SUBSCRIBER);
            require(subscriber.visibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("Mod$EventBusSubscriber;")
                    && a.values.contains("sable") && a.values.stream().anyMatch(v -> v instanceof String[] e && e[1].equals("FORGE"))), "Automatic fixture-owned Forge subscription");
            for (String name : List.of("levelTick", "assemblyCompleted")) {
                MethodNode observer = subscriber.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
                require(observer.visibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("SubscribeEvent;")), "Listener missing: " + name);
                require(observer.tryCatchBlocks.isEmpty(), "No observer exception suppression");
                List<String> fixtures = new ArrayList<>();
                for (AbstractInsnNode i : observer.instructions) if (i instanceof MethodInsnNode c && c.owner.startsWith("dev/ryanhcode/sable/command/M")) fixtures.add(c.owner.substring(c.owner.lastIndexOf('/') + 1) + "." + c.name);
                require(fixtures.equals(name.equals("levelTick") ? List.of("M25AeronauticsCommands.tick", "M26AeronauticsPropulsionCommands.tick", "M27AerodynamicsCommands.tick")
                        : List.of("M24SimulatedSystemsCommands.onM22AssemblyCreated", "M27AerodynamicsCommands.onM22AssemblyCreated")), "Observer order/cadence changed: " + fixtures);
            }
            ClassNode aero = read(jar, "dev/eriksonn/aeronautics/forge/AeronauticsForge");
            require(aero.methods.stream().noneMatch(m -> m.name.equals("levelTick")), "Production fixture tick remains");
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        ClassNode result = new ClassNode();
        new ClassReader(jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name + ".class"))).readAllBytes()).accept(result, 0);
        return result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    public static void subscriberTests(File root, File output) throws Exception {
        Files.createDirectories(output.toPath());
        Map<String, String> stubs = new LinkedHashMap<>();
        stubs.put("dev.ryanhcode.sable.Sable", "public final class Sable { public static final String MOD_ID=\"sable\"; }");
        stubs.put("net.minecraft.world.level.Level", "public class Level { }");
        stubs.put("net.minecraft.server.level.ServerLevel", "public class ServerLevel extends net.minecraft.world.level.Level { }");
        stubs.put("dev.ryanhcode.sable.sublevel.ServerSubLevel", "public class ServerSubLevel { }");
        stubs.put("net.minecraft.core.BlockPos", "public class BlockPos { public BlockPos immutable(){return this;} }");
        stubs.put("net.minecraftforge.eventbus.api.Event", "public class Event { }");
        stubs.put("net.minecraftforge.eventbus.api.SubscribeEvent", "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface SubscribeEvent { }");
        stubs.put("net.minecraftforge.fml.common.Mod", "public @interface Mod { @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @interface EventBusSubscriber { String modid(); Bus bus(); enum Bus { FORGE,MOD } } }");
        stubs.put("net.minecraftforge.event.TickEvent", "public class TickEvent { public enum Phase{START,END} public Phase phase; public static class LevelTickEvent extends TickEvent { public net.minecraft.world.level.Level level; public LevelTickEvent(Phase p,net.minecraft.world.level.Level l){phase=p;level=l;} } }");
        stubs.put("test.Observations", "public class Observations { public static final java.util.List<String> calls=new java.util.ArrayList<>(); public static Object[] a,b; public static boolean fail; }");
        for (String fixture : List.of("M24SimulatedSystemsCommands", "M25AeronauticsCommands", "M26AeronauticsPropulsionCommands", "M27AerodynamicsCommands")) {
            stubs.put("dev.ryanhcode.sable.command." + fixture, "public class " + fixture + " { public static void tick(net.minecraft.server.level.ServerLevel l){test.Observations.calls.add(\"" + fixture + "\"); if(test.Observations.fail) throw new IllegalStateException(\"observer\");} public static void onM22AssemblyCreated(net.minecraft.server.level.ServerLevel l,net.minecraft.core.BlockPos p,dev.ryanhcode.sable.sublevel.ServerSubLevel s" + (fixture.startsWith("M27") ? ",net.minecraft.core.BlockPos o" : "") + "){test.Observations.calls.add(\"" + fixture + "\"); test.Observations." + (fixture.startsWith("M27") ? "b=new Object[]{l,p,s,o};" : "a=new Object[]{l,p,s};") + "} }");
        }
        stubs.put("test.Run", """
                public class Run {
                  public static void run() {
                    var server=new net.minecraft.server.level.ServerLevel();
                    var client=new net.minecraft.world.level.Level();
                    var sub=new dev.ryanhcode.sable.sublevel.ServerSubLevel();
                    var pos=new net.minecraft.core.BlockPos(); var offset=new net.minecraft.core.BlockPos();
                    var event=new dev.ryanhcode.sable.forge.event.ForgeSableAssemblyCompletedEvent(server,pos,sub,offset);
                    dev.ryanhcode.sable.command.M45FixtureLifecycleSubscriptions.assemblyCompleted(event);
                    if(!Observations.calls.equals(java.util.List.of("M24SimulatedSystemsCommands","M27AerodynamicsCommands")) || Observations.a[0]!=server || Observations.a[1]!=pos || Observations.a[2]!=sub || Observations.b[0]!=server || Observations.b[1]!=pos || Observations.b[2]!=sub || Observations.b[3]!=offset) throw new AssertionError("assembly order/data");
                    Observations.calls.clear();
                    dev.ryanhcode.sable.command.M45FixtureLifecycleSubscriptions.levelTick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.event.TickEvent.Phase.START,server));
                    dev.ryanhcode.sable.command.M45FixtureLifecycleSubscriptions.levelTick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.event.TickEvent.Phase.END,client));
                    if(!Observations.calls.isEmpty())throw new AssertionError("phase/side");
                    for(int i=0;i<100;i++)dev.ryanhcode.sable.command.M45FixtureLifecycleSubscriptions.levelTick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.event.TickEvent.Phase.END,server));
                    for(int i=0;i<100;i++) if(!Observations.calls.subList(i*3,i*3+3).equals(java.util.List.of("M25AeronauticsCommands","M26AeronauticsPropulsionCommands","M27AerodynamicsCommands")))throw new AssertionError("cadence/order");
                    if(Observations.calls.size()!=300)throw new AssertionError("frequency");
                    Observations.fail=true;
                    try {dev.ryanhcode.sable.command.M45FixtureLifecycleSubscriptions.levelTick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.event.TickEvent.Phase.END,server));throw new AssertionError("exception swallowed");}catch(IllegalStateException expected){}
                  }
                }
                """);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            List<JavaFileObject> units = new ArrayList<>();
            stubs.forEach((name, body) -> units.add(new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignored) { return "package " + name.substring(0, name.lastIndexOf('.')) + ";" + body; }
            }));
            manager.getJavaFileObjects(new File(root, "forge/src/main/java/" + EVENT + ".java"), new File(root, "forge/src/main/java/" + SUBSCRIBER + ".java")).forEach(units::add);
            require(compiler.getTask(null, manager, null, List.of("--release", "17", "-proc:none", "-d", output.getAbsolutePath()), null, units).call(), "Subscriber headless compilation");
        }
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{output.toURI().toURL()}, null)) {
            loader.loadClass("test.Run").getMethod("run").invoke(null);
        }
    }
}
