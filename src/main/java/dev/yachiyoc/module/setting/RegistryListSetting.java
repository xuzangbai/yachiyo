package dev.yachiyoc.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.yachiyoc.platform.RegistryKind;

import java.util.ArrayList;
import java.util.List;

/**
 * A registry-backed multi-select setting. The UI opens the shared
 * RegistrySelectionScreen; selected ids are stored as a JSON array.
 */
public final class RegistryListSetting extends Setting<List<String>> {
    private final RegistryKind kind;

    public RegistryListSetting(String owner, String id, RegistryKind kind, List<String> defaults) {
        super(owner, id, new ArrayList<>(defaults));
        this.kind = kind;
    }

    public RegistryKind kind() {
        return kind;
    }

    public List<String> selected() {
        return value;
    }

    public int count() {
        return value.size();
    }

    public boolean contains(String id) {
        return value.contains(id);
    }

    public void toggle(String id) {
        if (!value.remove(id)) {
            value.add(id);
        }
    }

    public void remove(String id) {
        value.remove(id);
    }

    public void clear() {
        value.clear();
    }

    @Override
    public String displayValue() {
        return Integer.toString(value.size());
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        value.forEach(array::add);
        return array;
    }

    @Override
    public void fromJson(JsonElement element) {
        value.clear();
        if (element != null && element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    value.add(entry.getAsString());
                }
            }
        } else if (element != null && element.isJsonPrimitive()) {
            // Migrate the old comma-separated StringSetting value.
            for (String part : element.getAsString().split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) {
                    value.add(id);
                }
            }
        }
    }
}
