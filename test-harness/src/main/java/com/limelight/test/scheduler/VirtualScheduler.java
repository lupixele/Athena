package com.limelight.test.scheduler;

import java.util.Objects;
import java.util.PriorityQueue;

public class VirtualScheduler {

    public static final class ScheduledTask implements Comparable<ScheduledTask> {
        private final Object owner;
        private final long deadline;
        private final long sequence;
        private final Runnable runnable;
        private final Object token;

        public ScheduledTask(Object owner, long deadline, long sequence, Runnable runnable, Object token) {
            this.owner = owner;
            this.deadline = deadline;
            this.sequence = sequence;
            this.runnable = runnable;
            this.token = token;
        }

        public Object owner() { return owner; }
        public long deadline() { return deadline; }
        public long sequence() { return sequence; }
        public Runnable runnable() { return runnable; }
        public Object token() { return token; }

        @Override
        public int compareTo(ScheduledTask o) {
            int cmp = Long.compare(this.deadline, o.deadline);
            if (cmp != 0) return cmp;
            return Long.compare(this.sequence, o.sequence);
        }
    }

    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>();
    private long currentTime = 0;
    private long seqCounter = 0;

    public synchronized void post(Runnable r) {
        postDelayed(null, r, 0);
    }

    public synchronized void postDelayed(Runnable r, long delayMs) {
        postDelayed(null, r, delayMs);
    }

    public synchronized void post(Object owner, Runnable r) {
        postDelayed(owner, r, 0);
    }

    public synchronized void postDelayed(Object owner, Runnable r, long delayMs) {
        if (r == null) return;
        long deadline = currentTime + Math.max(0, delayMs);
        queue.add(new ScheduledTask(owner, deadline, seqCounter++, r, null));
    }

    public synchronized void removeCallbacks(Runnable r) {
        removeCallbacks(null, r);
    }

    public synchronized void removeCallbacks(Object owner, Runnable r) {
        if (r == null) return;
        queue.removeIf(task -> {
            if (task.runnable != r) return false;
            return owner == null || Objects.equals(task.owner, owner);
        });
    }

    public synchronized void removeCallbacksAndMessages(Object token) {
        removeCallbacksAndMessages(null, token);
    }

    public synchronized void removeCallbacksAndMessages(Object owner, Object token) {
        queue.removeIf(task -> {
            if (owner != null && !Objects.equals(task.owner, owner)) {
                return false;
            }
            if (token == null) {
                return true;
            }
            return Objects.equals(task.token, token);
        });
    }

    public synchronized void advanceTime(long advanceMs) {
        if (advanceMs < 0) return;
        long targetTime = currentTime + advanceMs;
        while (!queue.isEmpty() && queue.peek().deadline <= targetTime) {
            ScheduledTask next = queue.poll();
            currentTime = next.deadline;
            next.runnable.run();
        }
        currentTime = targetTime;
    }

    public synchronized void drainAll() {
        while (!queue.isEmpty()) {
            ScheduledTask next = queue.poll();
            currentTime = next.deadline;
            next.runnable.run();
        }
    }

    public synchronized void reset() {
        queue.clear();
        currentTime = 0;
        seqCounter = 0;
    }

    public synchronized long getCurrentTime() {
        return currentTime;
    }

    public synchronized boolean hasPending() {
        return !queue.isEmpty();
    }

    public synchronized boolean hasPending(Object owner) {
        return queue.stream().anyMatch(task -> Objects.equals(task.owner, owner));
    }

    public synchronized int pendingCount() {
        return queue.size();
    }

    public synchronized int pendingCount(Object owner) {
        return (int) queue.stream().filter(task -> Objects.equals(task.owner, owner)).count();
    }
}
