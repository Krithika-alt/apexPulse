package com.apexpulse.apexpulse.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientTest {

    private static Patient vitals(int hr, int sbp, int dbp, int spo2, int rr, double temp, boolean cardiac) {
        return new Patient("Test, Patient", "M", 40, "Test complaint",
                hr, sbp, dbp, spo2, rr, temp, cardiac, Instant.now());
    }

    @Test
    void normalVitalsScoreBaseline() {
        Patient p = vitals(72, 120, 78, 98, 16, 36.6, false);
        assertEquals(12, p.getPriorityScore());
        assertEquals(5, p.getEsi());
    }

    @Test
    void lowSpo2AddsHypoxiaPoints() {
        Patient p = vitals(72, 120, 78, 88, 16, 36.6, false);
        assertEquals(12 + 28, p.getPriorityScore());
    }

    @Test
    void borderlineSpo2AddsSmallerPenalty() {
        Patient p = vitals(72, 120, 78, 92, 16, 36.6, false);
        assertEquals(12 + 12, p.getPriorityScore());
    }

    @Test
    void hypotensiveShockOutranksHypertensiveCrisis() {
        Patient shock = vitals(72, 85, 78, 98, 16, 36.6, false);
        Patient crisis = vitals(72, 190, 78, 98, 16, 36.6, false);
        assertEquals(12 + 26, shock.getPriorityScore());
        assertEquals(12 + 20, crisis.getPriorityScore());
        assertTrue(shock.getPriorityScore() > crisis.getPriorityScore());
    }

    @Test
    void stage2HypertensionAddsModeratePoints() {
        Patient p = vitals(72, 150, 78, 98, 16, 36.6, false);
        assertEquals(12 + 14, p.getPriorityScore());
    }

    @Test
    void bradycardiaAndTachycardiaBothPenalized() {
        Patient brady = vitals(42, 120, 78, 98, 16, 36.6, false);
        Patient tachy = vitals(140, 120, 78, 98, 16, 36.6, false);
        assertEquals(12 + 22, brady.getPriorityScore());
        assertEquals(12 + 22, tachy.getPriorityScore());
    }

    @Test
    void cardiacFlagAndCriticalVitalsCapAtNinetyNine() {
        Patient p = vitals(150, 70, 40, 80, 30, 39.5, true);
        assertEquals(99, p.getPriorityScore());
        assertEquals(1, p.getEsi());
    }

    @Test
    void esiBoundariesMatchPriorityScoreThresholds() {
        assertEquals(1, vitals(150, 70, 40, 80, 30, 39.5, true).getEsi());
        assertEquals(4, vitals(72, 150, 78, 98, 16, 36.6, false).getEsi());
        assertEquals(5, vitals(72, 120, 78, 98, 16, 36.6, false).getEsi());
    }

    @Test
    void agingIncreasesPriorityScoreOverTime() {
        Patient waited = new Patient("Test, Patient", "M", 40, "Test complaint",
                72, 120, 78, 98, 16, 36.6, false, Instant.now().minusSeconds(25 * 60));
        assertEquals(12 + 2, waited.getPriorityScore());
    }
}