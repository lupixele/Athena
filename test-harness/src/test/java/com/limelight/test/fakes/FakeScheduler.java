package com.limelight.test.fakes;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronous scheduler that replaces {@code android.os.Handler} in tests.
 * Runnables are collected and triggered explicitly — no threading, no timers.
 */
public class FakeScheduler {

    public record ScheduledTask(long delayMs, Runnable runnable) {}

    private final List<ScheduledTask> pending = new ArrayList<>();

    public void postDelayed(Runnable r, long delayMs) {
        pending.add(new ScheduledTask(delayMs, r));
    }

    public void post(Runnable r) {
        postDelayed(r, 0);
    }

    public void removeCallbacks(Runnable r) {
        pending.removeIf(t -> t.runnable() == r);
    }

    /** Fire all tasks whose delay <= advanceMs. */
    public void advanceTime(long advanceMs) {
        List<ScheduledTask> toRun = new ArrayList<>();
        List<ScheduledTask> toKeep = new ArrayList<>();
        for (ScheduledTask t : pending) {
            if (t.delayMs() <= advanceMs) {
                toRun.add(t);
            } else {
                toKeep.add(new ScheduledTask(t.delayMs() - advanceMs, t.runnable()));
            }
        }
        pending.clear();
        pending.addAll(toKeep);
        toRun.forEach(t -> t.runnable().run());
    }

    /** Fire ALL pending tasks regardless of delay. */
    public void drainAll() {
        List<ScheduledTask> snapshot = new ArrayList<>(pending);
        pending.clear();
        snapshot.forEach(t -> t.runnable().run());
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public int pendingCount() {
        return pending.size();
    }
}
