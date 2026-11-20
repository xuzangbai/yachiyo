/*
 * Millisecond timer ported from LeavesHack (AGPL-3.0).
 * See META-INF/licenses/leaveshack.txt.
 */
package dev.yachiyoc.leavesmine;

public final class LeavesMineTimer {
    private long time = System.nanoTime();

    public void reset() {
        time = System.nanoTime();
    }

    public boolean passedMs(long ms) {
        return System.nanoTime() - time >= ms * 1_000_000L;
    }

    public void setMs(long ms) {
        time = System.nanoTime() - ms * 1_000_000L;
    }
}
