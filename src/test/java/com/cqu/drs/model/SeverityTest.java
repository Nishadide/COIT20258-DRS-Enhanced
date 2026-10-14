package com.cqu.drs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests T01 to T04: the mapping from an assessed severity to a response
 * priority. The four severities form a closed set, so every one is tested.
 */
@DisplayName("Severity to Priority mapping")
class SeverityTest {

    @Test
    @DisplayName("T01: SEVERE maps to P1")
    void toPriorityReturnsP1ForSevere() {
        assertEquals(Priority.P1, Severity.SEVERE.toPriority());
    }

    @Test
    @DisplayName("T02: HIGH maps to P2")
    void toPriorityReturnsP2ForHigh() {
        assertEquals(Priority.P2, Severity.HIGH.toPriority());
    }

    @Test
    @DisplayName("T03: MEDIUM maps to P3")
    void toPriorityReturnsP3ForMedium() {
        assertEquals(Priority.P3, Severity.MEDIUM.toPriority());
    }

    @Test
    @DisplayName("T04: LOW maps to P4")
    void toPriorityReturnsP4ForLow() {
        assertEquals(Priority.P4, Severity.LOW.toPriority());
    }

    @Test
    @DisplayName("Priority ranks order P1 ahead of P4")
    void priorityRanksAreOrdered() {
        assertEquals(1, Priority.P1.getRank());
        assertEquals(4, Priority.P4.getRank());
    }
}
