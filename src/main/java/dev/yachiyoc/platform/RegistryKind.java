package dev.yachiyoc.platform;

public enum RegistryKind {
    ITEM("selector.yachiyoc.item"),
    BLOCK("selector.yachiyoc.block"),
    ENTITY("selector.yachiyoc.entity");

    private final String titleKey;

    RegistryKind(String titleKey) {
        this.titleKey = titleKey;
    }

    public String titleKey() {
        return titleKey;
    }
}
