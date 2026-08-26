import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SableSpawnCommandTreeVerifier {
    public static final String INPUT = "sable spawn block minecraft:stone m9_production_smoke";

    private SableSpawnCommandTreeVerifier() {
    }

    public static Result verify() throws CommandSyntaxException {
        AtomicBoolean handlerReached = new AtomicBoolean(false);
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<Object> sable = LiteralArgumentBuilder.literal("sable");
        sable.then(LiteralArgumentBuilder.literal("spawn")
                .then(LiteralArgumentBuilder.literal("block")
                        .executes((ctx) -> 7)
                        .then(namedSpawnFinale(RequiredArgumentBuilder.argument("block", new ResourceLocationTokenArgument()),
                                (ctx, name) -> {
                                    String block = ctx.getArgument("block", String.class);
                                    if (!"minecraft:stone".equals(block)) {
                                        throw new IllegalStateException("Unexpected block argument: " + block);
                                    }
                                    if (!"m9_production_smoke".equals(name)) {
                                        throw new IllegalStateException("Unexpected name argument: " + name);
                                    }
                                    handlerReached.set(true);
                                    return 1;
                                }))));
        dispatcher.register(sable);

        CommandNode<Object> terminal = child(child(child(child(child(dispatcher.getRoot(), "sable"),
                "spawn"), "block"), "block"), "name");
        if (terminal.getCommand() == null) {
            throw new IllegalStateException("Terminal /sable spawn block <block> <name> node has no command callback");
        }
        CommandNode<Object> blockArgument = child(child(child(child(dispatcher.getRoot(), "sable"),
                "spawn"), "block"), "block");
        if (blockArgument.getCommand() == null) {
            throw new IllegalStateException("/sable spawn block <block> node lost its unnamed command callback");
        }
        List<String> tree = new ArrayList<>();
        describe(dispatcher.getRoot(), "", tree);

        ParseResults<Object> parse = dispatcher.parse(INPUT, new Object());
        ImmutableStringReader reader = parse.getReader();
        if (reader.canRead()) {
            throw new IllegalStateException("Parse left unread input at cursor " + reader.getCursor()
                    + " of " + INPUT.length() + ": " + reader.getRemaining());
        }
        if (!parse.getExceptions().isEmpty()) {
            throw new IllegalStateException("Parse unexpectedly produced exceptions: " + parse.getExceptions());
        }
        int result = dispatcher.execute(parse);
        if (!handlerReached.get()) {
            throw new IllegalStateException("Parsed command executed without reaching terminal handler");
        }
        if (result != 1) {
            throw new IllegalStateException("Expected Brigadier result 1, got " + result);
        }

        return new Result(reader.getCursor(), INPUT.length(), result, tree);
    }

    private static <S, T extends ArgumentBuilder<S, T>> T namedSpawnFinale(T builder, NamedSpawnInvoker<S> invoker) {
        builder.executes((ctx) -> invoker.run(ctx, null));
        builder.then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.string())
                .executes((ctx) -> invoker.run(ctx, StringArgumentType.getString(ctx, "name"))));
        return builder;
    }

    private static CommandNode<Object> child(CommandNode<Object> node, String name) {
        CommandNode<Object> child = node.getChild(name);
        if (child == null) {
            throw new IllegalStateException("Missing command node " + name + " under " + node.getName());
        }
        return child;
    }

    private static void describe(CommandNode<Object> node, String indent, List<String> out) {
        if (!node.getName().isEmpty()) {
            out.add(indent + node.getClass().getSimpleName()
                    + " name=" + node.getName()
                    + " command=" + (node.getCommand() != null)
                    + " children=" + node.getChildren().stream().map(CommandNode::getName).toList());
        }
        for (CommandNode<Object> child : node.getChildren()) {
            describe(child, indent + "  ", out);
        }
    }

    @FunctionalInterface
    private interface NamedSpawnInvoker<S> {
        int run(CommandContext<S> ctx, String name) throws CommandSyntaxException;
    }

    private static final class ResourceLocationTokenArgument implements ArgumentType<String> {
        private static final List<String> EXAMPLES = List.of("minecraft:stone", "stone");

        @Override
        public String parse(StringReader reader) {
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
                reader.skip();
            }
            return reader.getString().substring(start, reader.getCursor());
        }

        @Override
        public Collection<String> getExamples() {
            return EXAMPLES;
        }
    }

    public record Result(int consumed, int total, int result, List<String> tree) {
        public Result {
            Objects.requireNonNull(tree, "tree");
        }
    }
}
