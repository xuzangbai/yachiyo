package dev.yachiyoc.config;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain data model persisted as {@code config/yachiyoc.json}.
 * Keep this class free of Minecraft types so it stays stable across versions.
 */
public final class ConfigData {
    public String prefix = "--";
    public int openKey = 265; // GLFW_KEY_UP
    /** auto | en_us | zh_cn */
    public String language = "zh_cn";

    public Map<String, Boolean> moduleStates = new LinkedHashMap<>();
    public Map<String, Map<String, JsonElement>> moduleSettings = new LinkedHashMap<>();
    public java.util.List<HudData> hudElements = new java.util.ArrayList<>();

    public static final class HudData {
        public String type;
        public float x;
        public float y;
        public Map<String, JsonElement> settings = new LinkedHashMap<>();
    }
}
