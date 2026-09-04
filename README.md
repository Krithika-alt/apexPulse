# ApexPulse

A real-time ER triage command center: patients are prioritized by a custom max-heap keyed on a computed acuity score, routed to beds through a rule-based allocation engine, and the whole dashboard updates live over WebSockets as vitals, wait times, and bed occupancy change.

## Features

- **Priority queue backed by a hand-rolled binary max-heap** (`TriageHeap`) — insert, extract-max, peek, and a non-destructive `drainSorted()` snapshot for rendering without disturbing heap state.
- **Acuity scoring** — each patient's vitals (heart rate, systolic/diastolic BP, SpO₂, respiratory rate, temperature, cardiac flag) are combined into a 0–99 priority score and mapped to a 5-level ESI (Emergency Severity Index) classification, with a wait-time aging bonus so patients don't stall indefinitely in the queue.
- **Patient intake form** with live vital sliders — the ESI/priority preview is computed client-side in JS, mirroring the server's scoring logic, so a nurse sees the triage outcome before submitting.
- **Smart bed allocation** — a rule-based recommendation engine suggests the appropriate bay (Resus, Trauma, Acute Care, or Waiting Room) based on ESI and vital thresholds, pre-selected in the allocation modal.
- **Discharge workflow** with a permanent audit ledger, freeing the bed back into the available pool.
- **Live dashboard** — pushes updates to every connected client over STOMP/SockJS whenever the queue, beds, or roster change, so the UI refreshes silently with no polling or full page reload.
- **Wait-time telemetry** — tracks a rolling window of median wait times and renders a short-horizon trend forecast on a Chart.js sparkline.

## Tech stack

- Java 17, Spring Boot 3.3.5
- Thymeleaf server-side templates
- Spring WebSocket (STOMP over SockJS) for live push updates
- Chart.js for the wait-time sparkline
- Gradle (wrapper included)

## Running it

```bash
./gradlew bootRun
```

Then open `http://localhost:8080`. The app seeds itself with a mock patient roster and bed layout on startup (`TriageController#seedMockData`) — no database or external services required.

## Project layout

```
src/main/java/com/apexpulse/apexpulse/
  controller/TriageController.java   page routes + REST endpoints (allocate/discharge)
  model/Patient.java                 vitals + derived clinical fields (ESI, score, recommended bed)
  structure/TriageHeap.java          the priority queue
  config/WebSocketConfig.java        STOMP broker + /ws-triage endpoint
src/main/resources/templates/
  command.html                       live dashboard
  intake.html                        patient intake form
```

## Testing

```bash
./gradlew test
```

Covers the acuity scoring boundaries in `Patient` (hypoxia, shock vs. hypertensive crisis, tachy/bradycardia, capping, wait-time aging) and the heap's ordering invariants (`peekMax`, `extractMax` drain order, non-destructive `drainSorted`, `rebuildHeap` after external score changes).

## Notes

The acuity score is a hand-tuned heuristic modeled loosely on standard ESI/NEWS2-style severity thresholds — it is **not** a trained or validated clinical model, and the thresholds haven't been reviewed by a clinician. This project is a full-stack/real-time-systems exercise (heap data structure, WebSocket push, Spring MVC), not a research submission — treat the scoring as illustrative, not medically authoritative.
