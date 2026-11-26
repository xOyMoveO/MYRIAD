package com.myriad.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Per-round continuous absences and the exact online roster authorized for one offer. */
final class EarlyRemake {
    static final int OPENING_TICKS = 90 * 20;
    static final int QUALIFY_TICKS = 20 * 20 + 1;
    static final int OFFER_TICKS = 20 * 20;

    private final Map<UUID, Long> since = new HashMap<>();
    private final Set<UUID> expired = new HashSet<>();
    private Set<UUID> lastOnline = Set.of();
    private long offerUntil = -1;
    private String offerId = "";
    private long revision;
    private transient View view = View.NONE;

    record View(long readyAt, long offerUntil, String offerId, Faction side, boolean automatic) {
        static final View NONE = new View(-1, -1, "", null, false);
        boolean active() { return readyAt >= 0 || offerUntil >= 0 || automatic; }
    }

    View view() {
        return view;
    }

    void reconnect(UUID id) {
        boolean observed = since.remove(id) != null;
        expired.remove(id);
        if (!observed && lastOnline.contains(id)) return;
        // A disconnect and reconnect can both happen between two server ticks.
        invalidateOffer();
    }

    void disconnect(UUID id, long now, long start) {
        if (now - start < OPENING_TICKS) since.putIfAbsent(id, now);
        invalidateOffer();
    }

    private void invalidateOffer() {
        offerUntil = -1;
        offerId = "";
    }

    View evaluate(UUID round, long now, long start, Map<UUID, Faction> seats, Predicate<UUID> connected, boolean objective) {
        if (objective || now - start >= OPENING_TICKS && since.isEmpty()) {
            since.clear();
            expired.clear();
            invalidateOffer();
            return view = View.NONE;
        }
        Set<UUID> online = new HashSet<>();
        int blue = 0;
        for (var seat : seats.entrySet()) {
            UUID id = seat.getKey();
            if (connected.test(id)) {
                online.add(id);
                if (seat.getValue() == Faction.GUARDIAN) blue++;
                since.remove(id);
                expired.remove(id);
            } else if (now - start < OPENING_TICKS) {
                since.putIfAbsent(id, now);
            }
        }
        if (!online.equals(lastOnline)) {
            lastOnline = online;
            invalidateOffer();
        }
        int red = online.size() - blue;
        Faction smaller = blue < red ? Faction.GUARDIAN : red < blue ? Faction.DESTRUCTION : null;
        boolean allGone = online.isEmpty();
        long readyAt = Long.MAX_VALUE;
        for (var entry : since.entrySet()) {
            if ((allGone || seats.get(entry.getKey()) == smaller) && !expired.contains(entry.getKey())) {
                readyAt = Math.min(readyAt, entry.getValue() + QUALIFY_TICKS);
            }
        }
        if (readyAt == Long.MAX_VALUE) {
            invalidateOffer();
            return view = View.NONE;
        }
        if (now < readyAt) {
            invalidateOffer();
            return view = new View(readyAt, -1, "", smaller, false);
        }
        if (blue == 0 || red == 0) return view = new View(-1, -1, "", smaller, true);
        if (offerUntil < 0) {
            offerId = round + "/" + ++revision;
            offerUntil = now + OFFER_TICKS;
        } else if (now >= offerUntil) {
            for (var entry : since.entrySet()) {
                if (seats.get(entry.getKey()) == smaller && entry.getValue() + QUALIFY_TICKS <= now)
                    expired.add(entry.getKey());
            }
            invalidateOffer();
            return view = View.NONE;
        }
        return view = new View(-1, offerUntil, offerId, smaller, false);
    }
}
