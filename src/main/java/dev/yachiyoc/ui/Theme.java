package dev.yachiyoc.ui;

/**
 * Visual tokens for the "right rail / center cards" ClickGUI.
 *
 * <p>Everything is ARGB. Widgets must not hard-code colors or radii; changing
 * the theme later should only touch this file.</p>
 */
public final class Theme {
    public static final int OVERLAY_TOP = 0xD006070A;
    public static final int OVERLAY_BOTTOM = 0xA8040508;

    public static final int WINDOW = 0xF210141C;
    public static final int WINDOW_BORDER = 0x3DFFFFFF;
    public static final int SIDEBAR = 0x80171D28;
    public static final int CONTENT = 0x40171D28;

    public static final int CARD = 0xFF171C26;
    public static final int CARD_HOVER = 0xFF212938;
    public static final int CARD_ACTIVE = 0xFF1C2635;
    public static final int CARD_BORDER = 0x24FFFFFF;
    public static final int CARD_BORDER_HOVER = 0x3DFFFFFF;

    public static final int TEXT = 0xFFF3F5F8;
    public static final int TEXT_DIM = 0xFFA7ADBA;
    public static final int TEXT_MUTED = 0xFF6F7684;

    public static final int ACCENT = 0xFF63B8FF;
    public static final int ACCENT_SOFT = 0x3363B8FF;
    public static final int WIP = 0xFFFFB454;

    public static final int TRACK_OFF = 0x3DFFFFFF;
    public static final int TRACK_ON = 0xFF3F8FD6;
    public static final int KNOB = 0xFFF7FAFF;

    public static final int WINDOW_RADIUS = 20;
    public static final int PANEL_RADIUS = 14;
    public static final int CARD_RADIUS = 12;
    public static final int SMALL_RADIUS = 8;

    public static final int PADDING = 16;
    public static final int HEADER_HEIGHT = 70;
    public static final int FOOTER_HEIGHT = 26;
    public static final int SIDEBAR_WIDTH = 142;
    public static final int GAP = 14;

    public static final int TAB_HEIGHT = 26;
    public static final int TAB_GAP = 2;

    public static final int CARD_HEIGHT = 52;
    public static final int CARD_GAP = 9;
    public static final int SETTING_HEIGHT = 24;

    public static final int SEARCH_HEIGHT = 24;

    private Theme() {
    }
}
