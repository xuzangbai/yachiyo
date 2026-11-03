package dev.yachiyoc.ui;

/**
 * Visual tokens for the "right rail / center cards" ClickGUI.
 *
 * <p>Everything is ARGB. Widgets must not hard-code colors or radii; changing
 * the theme later should only touch this file.</p>
 */
public final class Theme {
    /**
     * Lavender / sakura preset. All UI colors go through these tokens so a
     * future "client settings -> theme" option can swap palettes without
     * touching widgets.
     */
    public static final String PRESET = "lavender";
    public static int OVERLAY_TOP = 0xCC150F2A;
    public static int OVERLAY_BOTTOM = 0x99150F2A;

    public static int SURFACE_LOW = 0xFFF2EAFE;
    public static int SURFACE_HIGH = 0xFFFDF9FF;
    public static int OUTLINE = 0xFFD8C8F0;
    public static int OUTLINE_STRONG = 0xFFB9A4DD;
    public static int PRIMARY_CONTAINER = 0xFFE9DDFF;
    public static int SECONDARY = 0xFFD98CA8;
    public static int SECONDARY_CONTAINER = 0xFFFFE3EE;
    public static int STATE_LAYER = 0x148B5CF6;
    public static int STATE_LAYER_STRONG = 0x228B5CF6;

    public static int WINDOW = 0xFFF7F1FF;
    public static int WINDOW_BORDER = 0xFFC9B6EA;
    public static int SIDEBAR = 0xFFEFE5FF;
    public static int CONTENT = 0xFFF4EDFF;

    public static int CARD = 0xFFFBF8FF;
    public static int CARD_HOVER = 0xFFFFE8F2;
    public static int CARD_ACTIVE = 0xFFF0E2FF;
    public static int CARD_BORDER = 0x40A78BFA;
    public static int CARD_BORDER_HOVER = 0x80A78BFA;

    public static int TEXT = 0xFF3B3155;
    public static int TEXT_DIM = 0xFF6F6088;
    public static int TEXT_MUTED = 0xFF9B8DAF;

    public static int ACCENT = 0xFF8B5CF6;
    public static int ACCENT_SOFT = 0x338B5CF6;
    public static int WIP = 0xFFE88AA8;

    public static int TRACK_OFF = 0x339B8DAF;
    public static int TRACK_ON = 0xFFA78BFA;
    public static int KNOB = 0xFFFFFDF7;

    public static int SEPARATOR = 0x287C6AAE;
    public static int ROW_HOVER = 0x148B5CF6;
    public static int FIELD = 0xFFF8F2FF;
    public static int FIELD_HOVER = 0xFFFFEAF3;
    public static int SEARCH_BORDER = 0xFFD8C8F0;
    public static int SEARCH_BORDER_FOCUS = 0xFF8B5CF6;
    public static int SEARCH_FILL = 0xFFFBF8FF;
    public static int SEARCH_FILL_FOCUS = 0xFFFFFFFF;
    public static int TAB_HOVER = 0x14A78BFA;
    public static int TAB_ACTIVE = 0x22F3A6C2;
    public static int SHADOW = 0x33200030;
    public static int DANGER = 0xFFD85C7A;

    public static int WINDOW_RADIUS = 28;
    public static final int PANEL_RADIUS = 22;
    public static int CARD_RADIUS = 20;
    public static final int SMALL_RADIUS = 12;

    public static final int PADDING = 16;
    public static final int HEADER_HEIGHT = 70;
    public static final int FOOTER_HEIGHT = 26;
    public static int SIDEBAR_WIDTH = 142;
    public static final int GAP = 14;

    public static final int TAB_HEIGHT = 26;
    public static final int TAB_GAP = 2;

    public static int CARD_HEIGHT = 52;
    public static int CARD_GAP = 9;
    public static final int SETTING_HEIGHT = 24;

    public static final int SEARCH_HEIGHT = 24;

    private Theme() {
    }
}
