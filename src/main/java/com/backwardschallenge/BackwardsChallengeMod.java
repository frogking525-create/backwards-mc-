package com.backwardschallenge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Beat The Game Backwards" - server-side challenge mod.
 *
 * NOTE ON MAPPINGS: Minecraft 26.2 ships unobfuscated with Mojang's own class names. This file
 * was iteratively fixed against real 26.2 compiler errors, so most of it is confirmed-correct.
 * A couple of vanilla accessor names (getting the MinecraftServer from a player, and checking
 * a player's bed/respawn-anchor state) could not be confirmed after repeated attempts, so this
 * version deliberately avoids needing them at all - see the comments below for what that
 * trades away and how to add it back if you find the right accessor names in your IDE.
 */
public class BackwardsChallengeMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();

    // Players whose "first join" teleport is still pending. We deliberately do NOT teleport
    // directly inside the JOIN event - on some setups vanilla finishes placing the player into
    // its normal initial position on the tick right after JOIN fires, which can silently
    // override a teleport done during the event itself. Queuing it and applying it on the next
    // server tick sidesteps that race entirely.
    private static final Set<UUID> PENDING_INITIAL_SPAWN = new HashSet<>();

    // FIX: same idea as PENDING_INITIAL_SPAWN above, now also used for respawn. Teleporting the
    // player across dimensions synchronously from inside AFTER_RESPAWN was leaving clients stuck
    // on the "Loading terrain..." screen - the client is still mid-processing the vanilla
    // respawn dimension-change packet at that point, and stacking a second cross-dimension
    // teleport on top of it in the same tick was the cause. Queuing it here and applying it on
    // the next server tick (processPendingRespawnSpawns, below) fixes it the same way the
    // join-race was already avoided above.
    private static final Set<UUID> PENDING_RESPAWN_SPAWN = new HashSet<>();

    @Override
    public void onInitialize() {
        LOGGER.info("[BackwardsChallenge] Initializing backwards challenge mod.");

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                handleJoin(handler.getPlayer()));

        ServerPlayerEvents.AFTER_RESPAWN.register(this::handleRespawn);

        ServerTickEvents.END_SERVER_TICK.register(this::watchForTrueEnding);
        ServerTickEvents.END_SERVER_TICK.register(this::processPendingInitialSpawns);
        // FIX: drains PENDING_RESPAWN_SPAWN every tick, same pattern as processPendingInitialSpawns.
        ServerTickEvents.END_SERVER_TICK.register(this::processPendingRespawnSpawns);

        // Tracks "has the dragon been killed" ourselves via a death event, instead of reading
        // vanilla's internal dragon-fight object (whose exact class name/package could not be
        // confirmed against 26.2 source).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof EnderDragon && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.setAttached(ModAttachments.DRAGON_DEFEATED, Boolean.TRUE);
                LOGGER.info("[BackwardsChallenge] Ender Dragon defeated - portals can be lit safely from now on.");
            }
        });
    }

    // ---------------------------------------------------------------------------------------
    // 1. Initial spawn
    // ---------------------------------------------------------------------------------------

    private void handleJoin(ServerPlayer player) {
        LOGGER.info("[BackwardsChallenge] {} joined, initialized={}.", player.getName().getString(),
                player.getAttachedOrElse(ModAttachments.INITIALIZED, Boolean.FALSE));

        if (Boolean.TRUE.equals(player.getAttachedOrElse(ModAttachments.INITIALIZED, Boolean.FALSE))) {
            return;
        }
        player.setAttached(ModAttachments.INITIALIZED, Boolean.TRUE);
        PENDING_INITIAL_SPAWN.add(player.getUUID());
    }

    private void processPendingInitialSpawns(MinecraftServer server) {
        if (PENDING_INITIAL_SPAWN.isEmpty()) {
            return;
        }
        ServerLevel endWorld = server.getLevel(Level.END);
        if (endWorld == null) {
            LOGGER.warn("[BackwardsChallenge] End world unavailable, could not redirect pending spawns.");
            PENDING_INITIAL_SPAWN.clear();
            return;
        }

        var iterator = PENDING_INITIAL_SPAWN.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue; // disconnected before we got to them
            }
            LOGGER.info("[BackwardsChallenge] Redirecting {} to the End spawn platform now.", player.getName().getString());
            sendPlayerToEndSpawn(player, endWorld);
            player.sendSystemMessage(Component.literal(
                    "You are starting this world backwards: find the Ender Dragon, then look for a way out."));
        }
    }

    // ---------------------------------------------------------------------------------------
    // 2. Death respawn
    // ---------------------------------------------------------------------------------------

    /**
     * Simplification: redirects EVERY death to the End platform until the challenge is
     * complete, rather than only bed-less deaths. In practice a player cannot have a legitimate
     * Overworld bed before finishing this challenge anyway (the only route to the Overworld at
     * all is the self-built portal that immediately completes the challenge), so the only case
     * this simplification actually overrides is a respawn anchor charged in the Nether during
     * the escape leg. If you want that case respected, add back a check here for the player's
     * bed/respawn-anchor state - search your IDE's autocomplete on ServerPlayer/Player for
     * "respawn" to find the current accessor name for your exact build.
     */
    private void handleRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (alive) {
            return; // only intercept genuine deaths, not dimension-transition "respawns"
        }
        if (Boolean.TRUE.equals(newPlayer.getAttachedOrElse(ModAttachments.TRUE_ENDING_DONE, Boolean.FALSE))) {
            return; // challenge already completed - normal respawn rules apply from here on
        }
        // FIX: don't teleport across dimensions here. The client is still processing the
        // vanilla respawn dimension-change packet at this exact point, and immediately firing a
        // second cross-dimension teleport on top of it was what left players stuck on the
        // "Loading terrain..." screen. Queue it and resolve it on the next server tick instead
        // (see processPendingRespawnSpawns), the same way initial spawns already work above.
        PENDING_RESPAWN_SPAWN.add(newPlayer.getUUID());
    }

    private void processPendingRespawnSpawns(MinecraftServer server) {
        if (PENDING_RESPAWN_SPAWN.isEmpty()) {
            return;
        }
        ServerLevel endWorld = server.getLevel(Level.END);
        if (endWorld == null) {
            LOGGER.warn("[BackwardsChallenge] End world unavailable, could not redirect a pending respawn.");
            PENDING_RESPAWN_SPAWN.clear();
            return;
        }

        var iterator = PENDING_RESPAWN_SPAWN.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue; // disconnected before we got to them
            }
            sendPlayerToEndSpawn(player, endWorld);
            player.sendSystemMessage(Component.literal("Back to the End with you."));
        }
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

        BlockPos standPos = PlatformUtil.buildEndIsland(endWorld, platformCenter, ModConstants.END_PLATFORM_RADIUS);
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
            if (!Boolean.TRUE.equals(player.getAttachedOrElse(ModAttachments.AWAITING_TRUE_CREDITS, Boolean.FALSE))) {
                continue;
            }
            if (Boolean.TRUE.equals(player.getAttachedOrElse(ModAttachments.TRUE_ENDING_DONE, Boolean.FALSE))) {
                continue;
            }

            player.setAttached(ModAttachments.AWAITING_TRUE_CREDITS, Boolean.FALSE);
            player.setAttached(ModAttachments.TRUE_ENDING_DONE, Boolean.TRUE);
            playTrueEndingSequence(player);
        }
    }

    private void playTrueEndingSequence(ServerPlayer player) {
        LOGGER.info("[BackwardsChallenge] {} completed the backwards challenge - showing credits.", player.getName().getString());

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
        if (!Boolean.TRUE.equals(player.getAttachedOrElse(ModAttachments.TRUE_ENDING_DONE, Boolean.FALSE))) {
            player.setAttached(ModAttachments.AWAITING_TRUE_CREDITS, Boolean.TRUE);
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

    private static void teleportAcrossDimensions(ServerPlayer player, ServerLevel targetWorld, BlockPos standPos) {
        player.teleportTo(targetWorld,
                standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
    }
}
