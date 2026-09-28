package dev.denis.playerbots;

import dev.denis.playerbots.bot.BotManager;
import dev.denis.playerbots.network.PlayerBotsNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PlayerBotsMod implements ModInitializer {
    public static final String MOD_ID = "playerbots";
    public static final Item CONFIGURATOR = Registry.register(Registries.ITEM, id("bot_configurator"), new Item(new FabricItemSettings().maxCount(1)));
    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, id("main"), FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.playerbots.main"))
            .icon(() -> new ItemStack(CONFIGURATOR))
            .entries((context, entries) -> entries.add(CONFIGURATOR))
            .build());

    private static BotManager manager;

    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }
    public static BotManager manager() { return manager; }

    @Override
    public void onInitialize() {
        PlayerBotsNetworking.registerServer();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { manager = new BotManager(server); manager.load(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (manager != null) manager.save(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> manager = null);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (manager == null) return;
            manager.possession().releaseController(handler.player.getUuid());
            manager.possession().releaseIfControlled(handler.player.getUuid());
        });
        registerCommands();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBots(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (manager == null) return builder.buildFuture();
        return CommandSource.suggestMatching(
                manager.bots().stream().map(p -> p.getGameProfile().getName()), builder);
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("bot")
                .then(literal("list").executes(ctx -> {
                    var source = ctx.getSource();
                    if (manager == null) return 0;
                    source.sendFeedback(() -> Text.literal(manager.bots().stream().map(p -> p.getGameProfile().getName()).toList().toString()), false);
                    return manager.bots().size();
                }))
                .then(literal("create").then(argument("name", word()).executes(ctx -> {
                    var source = ctx.getSource();
                    var player = source.getPlayer();
                    var bot = player != null
                            ? manager.create(player, getString(ctx, "name"))
                            : manager.createAt(getString(ctx, "name"), source.getWorld(),
                                    source.getPosition().x, source.getPosition().y, source.getPosition().z,
                                    source.getRotation().y, source.getRotation().x);
                    if (bot == null) { source.sendError(Text.literal("Invalid or duplicate bot name")); return 0; }
                    source.sendFeedback(() -> Text.literal("Created bot " + bot.getGameProfile().getName()), false);
                    if (player != null) PlayerBotsNetworking.sendSnapshot(player);
                    return 1;
                })))
                .then(literal("remove").then(argument("name", word()).suggests((ctx, builder) -> suggestBots(builder)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrThrow(); var bot = manager.getByName(getString(ctx, "name"));
                    if (bot == null || !manager.remove(player, bot.getUuid())) return 0;
                    PlayerBotsNetworking.sendSnapshot(player); return 1;
                })))
                .then(literal("rename").then(argument("name", word()).suggests((ctx, builder) -> suggestBots(builder)).then(argument("newName", word()).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrThrow(); var bot = manager.getByName(getString(ctx, "name"));
                    if (bot == null || manager.rename(player, bot.getUuid(), getString(ctx, "newName")) == null) return 0;
                    PlayerBotsNetworking.sendSnapshot(player); return 1;
                }))))
                .then(literal("control").then(argument("name", word()).suggests((ctx, builder) -> suggestBots(builder)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrThrow(); var bot = manager.getByName(getString(ctx, "name"));
                    if (bot == null || !manager.possession().control(player, bot)) return 0;
                    PlayerBotsNetworking.sendSnapshot(manager.possession().deliveryPlayer(manager.possession().controllerOf(player))); return 1;
                })))
                .then(literal("release").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrThrow(); boolean changed = manager.possession().release(player);
                    PlayerBotsNetworking.sendSnapshot(manager.possession().controllerOf(player)); return changed ? 1 : 0;
                }))
                .then(literal("tp").then(argument("name", word()).suggests((ctx, builder) -> suggestBots(builder)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrThrow(); var bot = manager.getByName(getString(ctx, "name"));
                    if (bot == null || !manager.teleportControllerTo(player, bot.getUuid())) return 0;
                    PlayerBotsNetworking.sendSnapshot(manager.possession().controllerOf(player)); return 1;
                })))));
    }
}
