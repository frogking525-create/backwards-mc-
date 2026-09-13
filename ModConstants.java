package com.backwardschallenge;

import net.minecraft.core.BlockPos;

/**
 * Central place for every tunable number and tag name used by the challenge.
 * Change coordinates here if you re-roll the world seed.
 */
public final class ModConstants {

    private ModConstants() {
    }

    public static final String MOD_ID = "backwardschallenge";

    // ---- Entity tags (persisted automatically with the player, survive logout/relog) ----
    public static final String TAG_INITIALIZED = "bc_initialized";
    public static final String TAG_AWAITING_TRUE_CREDITS = "bc_awaiting_credits";
    public static final String TAG_TRUE_ENDING_DONE = "bc_true_ending_done";

    // ---- End spawn ----
    public static final BlockPos END_SEARCH_CENTER = new BlockPos(1500, 70, 1500);
    public static final int END_SEARCH_RADIUS = 200;
    public static final int END_SEARCH_Y_MIN = 40;
    public static final int END_SEARCH_Y_MAX = 100;
    public static final int PEARL_GAP_DISTANCE = 42;
    public static final int END_PLATFORM_RADIUS = 7; // 15x15 platform

    // ---- Nether landing (after the exit-fountain redirect) ----
    public static final BlockPos NETHER_TARGET = new BlockPos(0, 70, 0);
    public static final int NETHER_PLATFORM_RADIUS = 5;
    public static final int NETHER_SEARCH_Y_MIN = 32;
    public static final int NETHER_SEARCH_Y_MAX = 120;

    // ---- Portal-before-dragon guard ----
    public static final int PORTAL_SCAN_RADIUS = 12;
}
