package com.backwardschallenge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Beat The Game Backwards" - server-side challenge mod.
 *
 * NOTE ON MAPPINGS: Minecraft 26.2 ships unobfuscated with Mojang's own class names, and this
 * was written using those official names to the best of the author's knowledge. A handful of
 * lines are marked "MAPPING CHECKPOINT" - those are the only ones with real risk of being
 * slightly off (exact overload/signature). If the build fails on one of them, open the
 * relevant vanilla class in your IDE (or https://mcsrc.dev) and adjust just that line; the
 * surrounding logic does not need to change.
 */
public class BackwardsChallengeMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("[BackwardsChallenge] Initializing backwards challenge mod.");

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                handleJoin(handler.getPlayer(), server));

        ServerPlayerEvents.AFTER_RESPAWN.register(this::handleRespawn);

        ServerTickEvents.END_SERVER_TICK.register(this::watchForTrueEnding);
    }

    // ---------------------------------------------------------------------------------------
    // 1. Initial spawn
    // ---------------------------------------------------------------------------------------

    private void handleJoin(ServerPlayer player, MinecraftServer server) {
        if (player.getTags().contains(ModConstants.TAG_INITIALIZED)) {
            return;
        }
        player.getTags().add(ModConstants.TAG_INITIALIZED);

        ServerLevel endWorld = server.getLevel(Level.END);
        if (endWorld == null) {
            LOGGER.warn("[BackwardsChallenge] End world unavailable on first join, could not redirect spawn.");
            return;
        }

        sendPlayerToEndSpawn(player, endWorld);
        player.sendSystemMessage(Component.literal(
                "You are starting this world backwards: find the Ender Dragon, then look for a way out."));
    }

    // ---------------------------------------------------------------------------------------
    // 2. Bed-less death respawn
    // ---------------------------------------------------------------------------------------

    private void handleRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) {
            return; // only intercept genuine deaths, not dimension-transition "respawns"
        }
        if (newPlayer.getTags().contains(ModConstants.TAG_TRUE_ENDING_DONE)) {
            return; // challenge already completed - normal bed/anchor rules apply from here on
        }
        // MAPPING CHECKPOINT: a null respawn position means no valid bed/anchor was set, so
        // vanilla would otherwise put the player back at the Overworld's default spawn.
        boolean hadValidRespawnPoint = oldPlayer.getRespawnPosition() != null;
        if (hadValidRespawnPoint) {
            return;
        }

        MinecraftServer server = newPlayer.getServer();
        if (server == null) {
            return;
        }
        ServerLevel endWorld = server.getLevel(Level.END);
        if (endWorld == null) {
            return;
        }

        sendPlayerToEndSpawn(newPlayer, endWorld);
        newPlayer.sendSystemMessage(Component.literal("No bed or anchor set - back to the End with you."));
    }

    private void sendPlayerToEndSpawn(ServerPlayer player, ServerLevel endWorld) {
        BlockPos island = PlatformUtil.findNearestEndIsland(
                endWorld,
                ModConstants.END_SEARCH_CENTER,
                ModConstants.END_SEARCH_RADIUS,
                ModConstants.END_SEARCH_Y_MIN,
                ModConstants.END_SEARCH_Y_MAX);

        BlockPos platformCenter;
        if (island != null) {
            double dx = ModConstants.END_SEARCH_CENTER.getX() - island.getX();
            double dz = ModConstants.END_SEARCH_CENTER.getZ() - island.getZ();
            double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
            int px = island.getX() + (int) Math.round(dx / len * ModConstants.PEARL_GAP_DISTANCE);
            int pz = island.getZ() + (int) Math.round(dz / len * ModConstants.PEARL_GAP_DISTANCE);
            platformCenter = new BlockPos(px, island.getY() + 2, pz);
            LOGGER.info("[BackwardsChallenge] Found End island near {}, placing platform at {}.", island, platformCenter);
        } else {
            platformCenter = ModConstants.END_SEARCH_CENTER;
            LOGGER.warn("[BackwardsChallenge] No End island found within {} blocks of {}, using fallback coordinates.",
                    ModConstants.END_SEARCH_RADIUS, ModConstants.END_SEARCH_CENTER);
        }

        BlockPos standPos = PlatformUtil.buildFlatPlatform(endWorld, platformCenter, ModConstants.END_PLATFORM_RADIUS);
        teleportAcrossDimensions(player, endWorld, standPos);
    }

    // ---------------------------------------------------------------------------------------
    // 4. Detect the real ending: Nether -> Overworld via a self-built portal
    // ---------------------------------------------------------------------------------------

    private void watchForTrueEnding(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> current = player.level().dimension();
            ResourceKey<Level> previous = LAST_DIMENSION.get(player.getUUID());
            LAST_DIMENSION.put(player.getUUID(), current);

            if (previous == null) {
                continue;
            }
            boolean crossedNetherToOverworld = previous == Level.NETHER && current == Level.OVERWORLD;
            if (!crossedNetherToOverworld) {
                continue;
            }
            if (!player.getTags().contains(ModConstants.TAG_AWAITING_TRUE_CREDITS)) {
                continue;
            }
            if (player.getTags().contains(ModConstants.TAG_TRUE_ENDING_DONE)) {
                continue;
            }

            player.getTags().remove(ModConstants.TAG_AWAITING_TRUE_CREDITS);
            player.getTags().add(ModConstants.TAG_TRUE_ENDING_DONE);
            playTrueEndingSequence(player);
        }
    }

    private void playTrueEndingSequence(ServerPlayer player) {
        LOGGER.info("[BackwardsChallenge] {} completed the backwards challenge - showing credits.", player.getName().getString());

        // MAPPING CHECKPOINT (highest risk line in the whole mod): vanilla's own "show end
        // credits" packet/reason. If ClientboundGameEventPacket.WIN_GAME doesn't exist under
        // that name, delete this try block - the chat message below still gives a clear
        // "you won" moment on its own.
        try {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0F));
        } catch (Throwable t) {
            LOGGER.warn("[BackwardsChallenge] Could not send vanilla credits packet, falling back to a plain message.", t);
        }

        player.sendSystemMessage(Component.literal("========================================"));
        player.sendSystemMessage(Component.literal("You escaped the Nether and beat the game... backwards."));
        player.sendSystemMessage(Component.literal("========================================"));
    }

    public static void markAwaitingTrueCredits(ServerPlayer player) {
        if (!player.getTags().contains(ModConstants.TAG_TRUE_ENDING_DONE)) {
            player.getTags().add(ModConstants.TAG_AWAITING_TRUE_CREDITS);
        }
    }

    // ---------------------------------------------------------------------------------------
    // 3. Nether landing after the End exit fountain redirect (no portal generated, ever)
    // ---------------------------------------------------------------------------------------

    public static void sendPlayerToNetherLanding(ServerPlayer player, ServerLevel netherWorld) {
        int safeY = PlatformUtil.findSafeNetherY(
                netherWorld,
                ModConstants.NETHER_TARGET.getX(),
                ModConstants.NETHER_TARGET.getZ(),
                ModConstants.NETHER_SEARCH_Y_MIN,
                ModConstants.NETHER_SEARCH_Y_MAX,
                ModConstants.NETHER_TARGET.getY());

        BlockPos platformCenter = new BlockPos(ModConstants.NETHER_TARGET.getX(), safeY, ModConstants.NETHER_TARGET.getZ());
        BlockPos standPos = PlatformUtil.buildFlatPlatform(netherWorld, platformCenter, ModConstants.NETHER_PLATFORM_RADIUS);

        teleportAcrossDimensions(player, netherWorld, standPos);

        player.sendSystemMessage(Component.literal(
                "The fountain didn't send you home - you're in the Nether now. Build a portal to get out."));
    }

    /**
     * MAPPING CHECKPOINT: cross-dimension teleport signature for ServerPlayer. This is a raw
     * teleport (not portal-linking), matching the exact overload/arg count is the main risk
     * here. If it doesn't compile, look at ServerPlayer#teleportTo in your IDE and adjust the
     * call below to match - the intent (move this player to this level/position, no relative
     * flags) stays the same regardless of the exact parameter list.
     */
    private static void teleportAcrossDimensions(ServerPlayer player, ServerLevel targetWorld, BlockPos standPos) {
        player.teleportTo(targetWorld,
                standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }
}
