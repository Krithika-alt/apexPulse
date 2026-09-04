package com.apexpulse.apexpulse.structure;

import com.apexpulse.apexpulse.model.Patient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TriageHeapTest {

    private static Patient vitals(String name, int hr, int sbp, int dbp, int spo2, int rr, double temp, boolean cardiac) {
        return new Patient(name, "M", 40, "Test complaint",
                hr, sbp, dbp, spo2, rr, temp, cardiac, Instant.now());
    }

    @Test
    void emptyHeapReturnsNullOnPeekAndExtract() {
        TriageHeap heap = new TriageHeap();
        assertNull(heap.peekMax());
        assertNull(heap.extractMax());
        assertEquals(0, heap.size());
    }

    @Test
    void peekMaxAlwaysReturnsHighestPriorityScore() {
        TriageHeap heap = new TriageHeap();
        heap.insert(vitals("Stable, Patient", 72, 120, 78, 98, 16, 36.6, false));
        heap.insert(vitals("Critical, Patient", 150, 70, 40, 80, 30, 39.5, true));
        heap.insert(vitals("Moderate, Patient", 72, 150, 78, 98, 16, 36.6, false));

        assertEquals("Critical, Patient", heap.peekMax().getName());
    }

    @Test
    void extractMaxDrainsInDescendingPriorityOrder() {
        TriageHeap heap = new TriageHeap();
        heap.insert(vitals("Low", 72, 120, 78, 98, 16, 36.6, false));
        heap.insert(vitals("High", 150, 70, 40, 80, 30, 39.5, true));
        heap.insert(vitals("Mid", 72, 150, 78, 98, 16, 36.6, false));

        int previous = Integer.MAX_VALUE;
        int extracted = 0;
        Patient p;
        while ((p = heap.extractMax()) != null) {
            assertTrue(p.getPriorityScore() <= previous, "extractMax must yield non-increasing priority scores");
            previous = p.getPriorityScore();
            extracted++;
        }
        assertEquals(3, extracted);
        assertEquals(0, heap.size());
    }

    @Test
    void drainSortedReturnsSnapshotWithoutMutatingHeap() {
        TriageHeap heap = new TriageHeap();
        heap.insert(vitals("Low", 72, 120, 78, 98, 16, 36.6, false));
        heap.insert(vitals("High", 150, 70, 40, 80, 30, 39.5, true));
        heap.insert(vitals("Mid", 72, 150, 78, 98, 16, 36.6, false));

        List<Patient> sorted = heap.drainSorted();

        assertEquals(3, sorted.size());
        assertEquals("High", sorted.get(0).getName());
        assertEquals("Low", sorted.get(2).getName());
        assertEquals(3, heap.size(), "drainSorted must not destroy the underlying heap");
        assertEquals("High", heap.peekMax().getName(), "heap invariant must still hold after drainSorted");
    }

    @Test
    void rebuildHeapRestoresInvariantAfterExternalScoreChange() {
        TriageHeap heap = new TriageHeap();
        Patient risingStar = vitals("RisingStar", 72, 120, 78, 98, 16, 36.6, false);
        heap.insert(risingStar);
        heap.insert(vitals("AlreadyHigh", 150, 70, 40, 80, 30, 39.5, true));

        assertEquals("AlreadyHigh", heap.peekMax().getName());

        risingStar.setCardiacFlag(true);
        risingStar.setHeartRate(150);
        risingStar.setSystolicBp(70);
        risingStar.setSpo2(80);
        risingStar.setRespiratoryRate(30);
        risingStar.setTemperature(39.5);

        heap.rebuildHeap();

        assertEquals(99, heap.peekMax().getPriorityScore());
    }
}