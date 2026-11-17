package dev.yachiyoc.ui.loading;

import java.util.random.RandomGenerator;

/** Time-based playback and a shuffle bag; independent of rendering and game startup. */
public final class LoadingTimeline {
    public static final class Deck {
        private final int[] bag;
        private final RandomGenerator random;
        private int cursor, last = -1;

        public Deck(int size, RandomGenerator random) {
            if (size <= 0) throw new IllegalArgumentException("Empty loading playlist");
            bag = new int[size];
            this.random = random;
            cursor = size;
        }

        public int next() {
            if (cursor == bag.length) {
                for (int i = 0; i < bag.length; i++) bag[i] = i;
                for (int i = bag.length - 1; i > 0; i--) {
                    int other = random.nextInt(i + 1), value = bag[i];
                    bag[i] = bag[other];
                    bag[other] = value;
                }
                if (bag.length > 1 && bag[0] == last) {
                    int value = bag[0]; bag[0] = bag[1]; bag[1] = value;
                }
                cursor = 0;
            }
            return last = bag[cursor++];
        }
    }

    public record Playback(int clip, long elapsedMs, boolean changed) { }

    private final int[] durations;
    private final Deck deck;
    private int clip = -1;
    private long startedAt;

    public LoadingTimeline(int[] durations, Deck deck) {
        if (durations.length == 0) throw new IllegalArgumentException("Empty loading playlist");
        this.durations = durations.clone();
        for (int duration : durations) if (duration <= 0) throw new IllegalArgumentException("Invalid duration");
        this.deck = deck;
    }

    public Playback update(long nowMs, boolean allowNext) {
        boolean changed = false;
        if (clip == -1) {
            clip = deck.next(); startedAt = nowMs; changed = true;
        }
        if (nowMs < startedAt) startedAt = nowMs;
        // A suspended window resumes at the current cue instead of replaying stale audio.
        if (allowNext && nowMs - startedAt > 60_000) {
            clip = deck.next(); startedAt = nowMs; changed = true;
        }
        while (allowNext && nowMs - startedAt >= durations[clip]) {
            startedAt += durations[clip]; clip = deck.next(); changed = true;
        }
        return new Playback(clip, Math.min(nowMs - startedAt, durations[clip] - 1L), changed);
    }

    /** Match the original overlay's timings without extending resource loading. */
    public static float opacity(long nowMs, long completedAt, long reloadStartedAt, boolean reloading) {
        if (completedAt >= 0) return (float) Math.clamp(2 - (nowMs - completedAt) / 1000.0, 0, 1);
        if (reloading && reloadStartedAt >= 0) return (float) Math.clamp((nowMs - reloadStartedAt) / 500.0, 0, 1);
        return 1;
    }
}
