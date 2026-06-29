package com.apexpulse.apexpulse.controller;

import com.apexpulse.apexpulse.model.Patient;
import com.apexpulse.apexpulse.structure.TriageHeap;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Controller
public class TriageController {

    private TriageHeap heap = new TriageHeap();
    private final int bedsTotal = 563;

    // The dynamic list of available beds
    private final java.util.List<String> availableBeds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    // The immutable ledger of who is currently admitted to a bed
    private final java.util.concurrent.ConcurrentHashMap<String, Patient> activeRoster = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.LinkedList<Long> historicalWaitTimes = new java.util.LinkedList<>();
    long predictedWaitOneHour =  0;

    private final SimpMessagingTemplate messagingTemplate;

    public TriageController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        seedMockData();
    }

    @GetMapping("/")
    public synchronized String commandCenter(Model model) {
        List<Patient> renderList = heap.drainSorted();

        Random rand = new Random();
        for (Patient p : renderList) {
            if (p.getPriorityScore() < 85) {
                p.setHeartRate(p.getHeartRate() + (rand.nextInt(5) - 2));
                p.setSystolicBp(p.getSystolicBp() + (rand.nextInt(7) - 3));
            }
        }

        heap.rebuildHeap();

        long critical = renderList.stream().filter(p -> p.getEsi() <= 2).count();
        long currentMedianWait = 0;
        if (!renderList.isEmpty()) {
            List<Long> waitTimes = renderList.stream()
                    .map(p -> (Instant.now().getEpochSecond() - p.getArrivedAt().getEpochSecond()) / 60)
                    .sorted()
                    .toList();

            int size = waitTimes.size();
            if (size % 2 == 0) {
                currentMedianWait = (waitTimes.get(size / 2 - 1) + waitTimes.get(size / 2)) / 2;
            } else {
                currentMedianWait = waitTimes.get(size / 2);
            }
        }

        historicalWaitTimes.add(currentMedianWait);
        if (historicalWaitTimes.size() > 10) {
            historicalWaitTimes.removeFirst();
        }

        if (historicalWaitTimes.size() >= 2) {
            long oldestWait = historicalWaitTimes.getFirst();
            long newestWait = historicalWaitTimes.getLast();
            long waitDelta = newestWait - oldestWait;

            if (waitDelta > 3) waitDelta = 3;
            if (waitDelta < -3) waitDelta = -3;

            this.predictedWaitOneHour = currentMedianWait + (waitDelta * 12);
            if (this.predictedWaitOneHour < 0) this.predictedWaitOneHour = 0;
        } else {
            this.predictedWaitOneHour = currentMedianWait;
        }

        model.addAttribute("queue", renderList);
        model.addAttribute("queueSize", renderList.size());
        model.addAttribute("critical", critical);
        model.addAttribute("medianWait", currentMedianWait);
        model.addAttribute("predictedWait", this.predictedWaitOneHour);
        model.addAttribute("waitHistory", this.historicalWaitTimes);
        model.addAttribute("bedsTotal", bedsTotal);

        model.addAttribute("bedsFree", this.availableBeds.size());
        model.addAttribute("availableBeds", this.availableBeds);
        model.addAttribute("nextUp", renderList.isEmpty() ? null : renderList.get(0));
        model.addAttribute("activeRoster", this.activeRoster);
        model.addAttribute("screen", "command");

        return "command";
    }

    @GetMapping("/intake")
    public String intakeForm(Model model) {
        model.addAttribute("patient", new Patient());
        model.addAttribute("screen", "intake");
        return "intake";
    }

    @PostMapping("/intake")
    public synchronized String admitPatient(@ModelAttribute Patient patient) {
        patient.setArrivedAt(Instant.now());
        heap.insert(patient);
        return "redirect:/";
    }

    @PostMapping("/api/triage/allocate")
    @ResponseBody
    public synchronized ResponseEntity<?> allocatePatientBed(@RequestParam Map<String, String> payload) {
        try {
            String patientName = payload.get("patientName");
            String bedName = payload.get("bedName");

            List<Patient> allPatients = heap.drainSorted();
            Patient patientToAdmit = null;

            java.util.Iterator<Patient> iterator = allPatients.iterator();
            while(iterator.hasNext()){
                Patient p = iterator.next();
                if(p.getName().trim().equalsIgnoreCase(patientName.trim())){
                    patientToAdmit = p;
                    iterator.remove();
                    break;
                }
            }

            this.heap = new TriageHeap();
            for (Patient remainingPatient : allPatients) {
                this.heap.insert(remainingPatient);
            }

            if (patientToAdmit != null) {
                activeRoster.put(bedName, patientToAdmit);

                if (bedName != null && !bedName.trim().equalsIgnoreCase("Waiting Room")) {
                    this.availableBeds.remove(bedName);
                }

                messagingTemplate.convertAndSend("/topic/queue", "HEAP_UPDATED");
                return ResponseEntity.ok().body(Map.of("status", "success", "message", "Allocation confirmed"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Patient not found."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // 🧠 FEATURE: The Discharge API Endpoint
    @PostMapping("/api/triage/discharge")
    @ResponseBody
    public synchronized ResponseEntity<?> dischargePatientBed(@RequestParam Map<String, String> payload) {
        try {
            String bedName = payload.get("bedName");

            if (bedName != null && activeRoster.containsKey(bedName)) {
                // 1. Remove patient from the active roster
                activeRoster.remove(bedName);

                // 2. Add the bed back to the available pool!
                if (!bedName.trim().equalsIgnoreCase("Waiting Room")) {
                    this.availableBeds.add(bedName);
                }

                // 3. Ping the frontend to silently refresh
                messagingTemplate.convertAndSend("/topic/queue", "HEAP_UPDATED");
                return ResponseEntity.ok().body(Map.of("status", "success", "message", "Patient Discharged."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Bed is already empty."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @Scheduled(fixedRate = 30000)
    public synchronized void runWaitTimeDeteriorationEngine() {
        try {
            List<Patient> activeQueue = heap.drainSorted();
            if (activeQueue.isEmpty()) return;

            for (Patient p : activeQueue) {
                if (p.getPriorityScore() < 100) {
                    p.setHeartRate(p.getHeartRate() + 1);
                }
            }
            heap.rebuildHeap();

            this.messagingTemplate.convertAndSend("/topic/queue", "HEAP_UPDATED");

        } catch (Exception e) {
            System.out.println("Scheduler pass skipped.");
        }
    }

    private void seedMockData() {
        this.heap = new TriageHeap();
        long now = Instant.now().getEpochSecond();

        this.availableBeds.clear();
        this.availableBeds.add("Bay 1");
        this.availableBeds.add("Bay 2");
        this.availableBeds.add("Bed 12");
        this.availableBeds.add("Bed 14");
        for (int i = 101; i <= 119; i++) {
            this.availableBeds.add("Bed " + i);
        }

        heap.insert(new Patient("Okafor, Daniel",   "M", 61, "Chest pain, diaphoresis",        128, 145,  92, 89, 26, 37.0, true,  Instant.ofEpochSecond(now - 240)));
        heap.insert(new Patient("Reyes, Marisol",   "F", 54, "Unresponsive, cardiac arrest",   42, 70,  40, 84,  8, 36.1, true,  Instant.ofEpochSecond(now - 120)));
        heap.insert(new Patient("Whitlock, James",  "M", 73, "SOB, CHF exacerbation",         118, 162, 98, 92, 25, 36.8, false, Instant.ofEpochSecond(now - 660)));
        heap.insert(new Patient("Nguyen, Thanh",    "F", 48, "Palpitations, AF RVR",          146, 104, 72, 95, 20, 36.9, false, Instant.ofEpochSecond(now - 1080)));
        heap.insert(new Patient("Abara, Grace",     "F", 39, "Abdominal pain, vomiting",       104, 128, 82, 96, 18, 38.6, false, Instant.ofEpochSecond(now - 2520)));
        heap.insert(new Patient("Petrov, Anton",    "M", 56, "Hypertensive urgency",            92, 188, 104, 97, 17, 36.7, false, Instant.ofEpochSecond(now - 3300)));
        heap.insert(new Patient("Sundqvist, Lena",  "F", 31, "Laceration, left forearm",        78, 118, 76, 99, 15, 36.6, false, Instant.ofEpochSecond(now - 4080)));
        heap.insert(new Patient("Hassan, Omar",     "M", 24, "Med refill, stable",              70, 120, 78, 100, 14, 36.5, false, Instant.ofEpochSecond(now - 6000)));

        String[] firstNames = {"John", "Emma", "Robert", "Sophia", "William", "Olivia", "David", "Ava", "Joseph", "Mia"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson"};
        String[] complaints = {"Shortness of breath", "Dizziness", "Mild chest tightness", "Palpitations", "Fatigue", "Nausea"};

        Random rand = new Random(42);

        for (int i = 0; i < 30; i++) {
            String randomName = lastNames[rand.nextInt(lastNames.length)] + ", " + firstNames[rand.nextInt(firstNames.length)];
            String randomSex = rand.nextBoolean() ? "M" : "F";
            int randomAge = 18 + rand.nextInt(70);
            String randomComplaint = complaints[rand.nextInt(complaints.length)];

            int hr = 60 + rand.nextInt(80);
            int sbp = 100 + rand.nextInt(90);
            int dbp = 60 + rand.nextInt(40);
            int o2 = 88 + rand.nextInt(13);
            int rr = 12 + rand.nextInt(15);
            double temp = 36.5 + (rand.nextDouble() * 2.5);
            boolean cardiac = rand.nextDouble() > 0.85;

            long randomTimeOffset = rand.nextInt(10800);

            this.heap.insert(new Patient(
                    randomName, randomSex, randomAge, randomComplaint,
                    hr, sbp, dbp, o2, rr, temp, cardiac,
                    Instant.ofEpochSecond(now - randomTimeOffset)
            ));
        }
    }
}