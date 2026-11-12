package com.myriad.character;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Server-side deadlines for charge skills: each missing charge waits for the previous one. */
public final class SequentialChargeTimers {
    private SequentialChargeTimers() {}

    public static void enqueue(Deque<Long> ready, long now, int cooldown) {
        ready.addLast(Math.max(now, ready.isEmpty() ? now : ready.peekLast()) + Math.max(1, cooldown));
    }

    public static void enqueue(List<Long> ready, long now, int cooldown) {
        ready.add(Math.max(now, ready.isEmpty() ? now : ready.getLast()) + Math.max(1, cooldown));
    }

    /** Moving the active timer also moves later deadlines so recovery remains continuous. */
    public static void moveFirstTo(Deque<Long> ready, long deadline) {
        if (ready.isEmpty()) return;
        long shift = deadline - ready.peekFirst();
        Deque<Long> moved = new ArrayDeque<>();
        for (long old : ready) moved.addLast(old + shift);
        ready.clear();
        ready.addAll(moved);
    }

    public static void moveFirstTo(List<Long> ready, long deadline) {
        if (ready.isEmpty()) return;
        long shift = deadline - ready.getFirst();
        for (int i = 0; i < ready.size(); i++) ready.set(i, ready.get(i) + shift);
    }
}
