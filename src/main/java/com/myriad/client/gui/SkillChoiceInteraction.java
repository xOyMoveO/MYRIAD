package com.myriad.client.gui;

import java.util.List;

/** Selection and double-click contract, independent of client rendering and packets. */
public final class SkillChoiceInteraction {
    public static int stepFocus(List<String> ids, String focused, boolean backwards) {
        int current = ids.indexOf(focused);
        if (current < 0) return backwards ? ids.size() - 1 : 0;
        return Math.floorMod(current + (backwards ? -1 : 1), ids.size());
    }

    // Minecraft can report a double click even if the pointer moved to a different action.
    public static boolean sameActionTwice(String previous, String current, boolean twice) {
        return twice && previous.equals(current);
    }

    public static final class SubmissionGate {
        private Object connection;
        private long next, pending, submitted;
        public void connect(Object current) {
            if (connection == current) return;
            connection = current;
            next = pending = 0;
        }
        public boolean pending() { return pending != 0; }
        public boolean cooling(long now) { return next > 0 && now - submitted < 1_000_000_000L; }
        public long begin(long now) {
            if (connection == null || pending() || cooling(now)) return 0;
            submitted = now;
            pending = ++next;
            return pending;
        }
        public boolean receive(long sequence) {
            if (sequence == 0 || pending != sequence) return false;
            pending = 0;
            return true;
        }
    }

    private String firstClick = "";
    private long clickTime;
    public void clearPair() { firstClick = ""; }
    public boolean pair(String id, long now) {
        if (id.equals(firstClick) && now - clickTime <= 350_000_000L) {
            clearPair();
            return true;
        }
        firstClick = id;
        clickTime = now;
        return false;
    }

    private String selected="";
    private boolean doubleConsumed;
    public String selected(){return selected;}
    public void reset(){selected="";doubleConsumed=false;clearPair();}
    /** A first click only selects. A double click on that same item may activate once. */
    public boolean select(String id,boolean twice,boolean eligible){
        boolean same=selected.equals(id);
        if(!twice||!same)doubleConsumed=false;
        selected=id;
        if(twice&&same&&!doubleConsumed){doubleConsumed=true;return eligible;}
        return false;
    }
    /** The second event in a double-click on the explicit button must not buy twice. */
    public boolean confirm(String id,boolean twice,boolean eligible){return !twice&&eligible&&selected.equals(id);}
}
