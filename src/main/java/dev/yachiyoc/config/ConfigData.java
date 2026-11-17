package dev.yachiyoc.config;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plain data model persisted as {@code config/yachiyoc.json}.
 * Keep this class free of Minecraft types so it stays stable across versions.
 */
public final class ConfigData {
    public String prefix = "--";
    public int openKey = 265; // GLFW_KEY_UP
    /** Normalized ClickGUI position; negative means centered. */
    public float guiX = -1.0F;
    public float guiY = -1.0F;
    public float guiWidth = 760.0F;
    public float guiHeight = 440.0F;
    /** auto | en_us | zh_cn */
    public String language = "zh_cn";
    /** --debug 开着的功能名，默认空（全关）。 */
    public Set<String> debugFeatures = new LinkedHashSet<>();

    public Map<String, Boolean> moduleStates = new LinkedHashMap<>();
    public Map<String, Map<String, JsonElement>> moduleSettings = new LinkedHashMap<>();
    public java.util.List<HudData> hudElements = new java.util.ArrayList<>();
    public boolean hudInitialized;

    public static final class HudData {
        public String type;
        public float x;
        public float y;
        public Map<String, JsonElement> settings = new LinkedHashMap<>();
    }
}
