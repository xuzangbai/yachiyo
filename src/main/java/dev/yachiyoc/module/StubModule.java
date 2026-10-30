package dev.yachiyoc.module;

/**
 * A registered-but-unimplemented module. It exists so the category shell and
 * module list can be developed before gameplay features. The UI marks these
 * with a {@code WIP} badge and the toggle is a no-op beyond state.
 */
public final class StubModule extends Module {
    public StubModule(String id, ModuleCategory category) {
        super(id, category, false);
    }
}
