package com.myriad.game;

/** Same fail-fast assertions for checks that require the physical client class loader. */
final class ClientAssertions {
    private int count;
    void assertTrue(boolean passed, String message) {
        count++;
        if (!passed) throw new AssertionError(message);
    }
    int count() { return count; }
}
