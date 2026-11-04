package dev.yachiyoc.rotation;

/** Per-module override: FOLLOW means use the client-wide setting. */
public enum RotationChoice {
    FOLLOW,
    NONE,
    DELAY_MOVEMENT,
    MOVEMENT_POST,
    LEGACY_SILENT,
    SILENT_MOVEMENT
}
