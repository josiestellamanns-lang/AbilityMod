package com.abilitymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class AbilityCommand {
    private AbilityCommand() {}

    private static final SuggestionProvider<ServerCommandSource> SUGGEST = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (Ability a : Ability.values()) names.add(a.id);
        names.add("all");
        return CommandSource.suggestMatching(names, builder);
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("ability")
                    .requires(s -> s.hasPermissionLevel(2))
                    .then(CommandManager.literal("give")
                            .then(CommandManager.argument("targets", EntityArgumentType.players())
                                    .then(CommandManager.argument("ability", StringArgumentType.word())
                                            .suggests(SUGGEST)
                                            .executes(c -> change(c, true)))))
                    .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("targets", EntityArgumentType.players())
                                    .then(CommandManager.argument("ability", StringArgumentType.word())
                                            .suggests(SUGGEST)
                                            .executes(c -> change(c, false)))))
                    .then(CommandManager.literal("list")
                            .then(CommandManager.argument("targets", EntityArgumentType.player())
                                    .executes(AbilityCommand::list)));

            LiteralCommandNode<ServerCommandSource> node = dispatcher.register(root);
            // alias so the typo "/abitity" works too
            dispatcher.register(CommandManager.literal("abitity")
                    .requires(s -> s.hasPermissionLevel(2))
                    .redirect(node));
        });
    }

    private static int change(CommandContext<ServerCommandSource> ctx, boolean grant) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
        String name = StringArgumentType.getString(ctx, "ability");
        int bits;
        if (name.equalsIgnoreCase("all")) {
            bits = Ability.allBits();
        } else {
            Ability a = Ability.byId(name);
            if (a == null) {
                ctx.getSource().sendError(Text.literal("Unknown ability: " + name));
                return 0;
            }
            bits = a.bit();
        }

        AbilityData data = AbilityData.get(ctx.getSource().getServer());
        for (ServerPlayerEntity p : targets) {
            int old = data.getMask(p.getUuid());
            int now = grant ? (old | bits) : (old & ~bits);
            data.setMask(p.getUuid(), now);
            if (!grant && (bits & Ability.BLOCK_MORPH.bit()) != 0) AbilityActions.clearMorph(p);
            AbilityActions.sync(p, grant ? (now & ~old) : 0);
        }

        final String label = name.toLowerCase();
        final int count = targets.size();
        ctx.getSource().sendFeedback(() -> Text.literal((grant ? "Gave " : "Removed ") + label
                + (grant ? " to " : " from ") + count + " player(s)"), true);
        return count;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "targets");
        int mask = AbilityData.get(ctx.getSource().getServer()).getMask(p.getUuid());
        List<String> names = new ArrayList<>();
        for (Ability a : Ability.values()) if ((mask & a.bit()) != 0) names.add(a.id);
        String text = p.getName().getString() + " has: " + (names.isEmpty() ? "no abilities" : String.join(", ", names));
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return names.size();
    }
}
