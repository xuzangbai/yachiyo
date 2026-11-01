package dev.yachiyoc.ui;

/**
 * Visual tokens for the "right rail / center cards" ClickGUI.
 *
 * <p>Everything is ARGB. Widgets must not hard-code colors or radii; changing
 * the theme later should only touch this file.</p>
 */
public final class Theme {
    /**
     * Cream preset. All UI colors go through these tokens so a future
     * "client settings -> theme" option can swap palettes without touching
     * widgets.
     */
    public static final String PRESET = "cream";
    public static int OVERLAY_TOP = 0xCC000000;
    public static int OVERLAY_BOTTOM = 0x99000000;

    public static int WINDOW = 0xFFFFFDD0;
    public static int WINDOW_BORDER = 0xFFD8C79A;
    public static int SIDEBAR = 0xFFF3E7BE;
    public static int CONTENT = 0xFFFBF3D2;

    public static int CARD = 0xFFFFF9E2;
    public static int CARD_HOVER = 0xFFFFF0B5;
    public static int CARD_ACTIVE = 0xFFFFE8A3;
    public static int CARD_BORDER = 0x406B5F4B;
    public static int CARD_BORDER_HOVER = 0x806B5F4B;

    public static int TEXT = 0xFF3B3428;
    public static int TEXT_DIM = 0xFF65594A;
    public static int TEXT_MUTED = 0xFF8C7C62;

    public static int ACCENT = 0xFFB07A3A;
    public static int ACCENT_SOFT = 0x33B07A3A;
    public static int WIP = 0xFFB5651D;

    public static int TRACK_OFF = 0x336B5F4B;
    public static int TRACK_ON = 0xFFB07A3A;
    public static int KNOB = 0xFFFFFFFF;

    public static int SEPARATOR = 0x286B5F4B;
    public static int ROW_HOVER = 0x146B5F4B;
    public static int FIELD = 0xFFFFF6D8;
    public static int FIELD_HOVER = 0xFFFFEFC0;
    public static int SEARCH_BORDER = 0xFFD5C49A;
    public static int SEARCH_BORDER_FOCUS = 0xFFB07A3A;
    public static int SEARCH_FILL = 0xFFFFF7D8;
    public static int SEARCH_FILL_FOCUS = 0xFFFFFFFF;
    public static int TAB_HOVER = 0x146B5F4B;
    public static int TAB_ACTIVE = 0x22B07A3A;
    public static int SHADOW = 0x33000000;
    public static int DANGER = 0xFFC84F4F;

    public static int WINDOW_RADIUS = 20;
    public static final int PANEL_RADIUS = 14;
    public static int CARD_RADIUS = 12;
    public static final int SMALL_RADIUS = 8;

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
