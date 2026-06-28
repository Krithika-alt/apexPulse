package com.apexpulse.apexpulse.controller;

import com.apexpulse.apexpulse.model.Patient;
import com.apexpulse.apexpulse.structure.TriageHeap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Controller
public class TriageController {

    private final TriageHeap heap = new TriageHeap();

    public TriageController() {
        seedMockData();
    }

    @GetMapping("/")
    public String commandCenter(Model model) {
        heap.rebuildHeap();
        List<Patient> queue = heap.drainSorted();
        long critical = queue.stream().filter(p -> p.getEsi() <= 2).count();
        long waitSum  = queue.stream().mapToLong(p ->
            (Instant.now().getEpochSecond() - p.getArrivedAt().getEpochSecond()) / 60
        ).sum();
        long medianWait = queue.isEmpty() ? 0 : waitSum / queue.size();

        model.addAttribute("queue",       queue);
        model.addAttribute("queueSize",   queue.size());
        model.addAttribute("critical",    critical);
        model.addAttribute("medianWait",  medianWait);
        model.addAttribute("bedsTotal",   22);
        model.addAttribute("bedsFree",    4);
        model.addAttribute("nextUp",      queue.isEmpty() ? null : queue.get(0));
        model.addAttribute("screen",      "command");
        return "command";
    }

    @GetMapping("/intake")
    public String intakeForm(Model model) {
        model.addAttribute("patient", new Patient());
        model.addAttribute("screen", "intake");
        return "intake";
    }

    @PostMapping("/intake")
    public String admitPatient(@ModelAttribute Patient patient) {
        patient.setArrivedAt(Instant.now());
        heap.insert(patient);
        return "redirect:/";
    }

    private void seedMockData() {
        long now = Instant.now().getEpochSecond();

        // Keep her original 8 high-fidelity clinical corner-cases
        heap.insert(new Patient("Okafor, Daniel",   "M", 61, "Chest pain, diaphoresis",        128, 145,  92, 89, 26, 37.0, true,  Instant.ofEpochSecond(now - 240)));
        heap.insert(new Patient("Reyes, Marisol",   "F", 54, "Unresponsive, ?cardiac arrest",   42, 70,  40, 84,  8, 36.1, true,  Instant.ofEpochSecond(now - 120)));
        heap.insert(new Patient("Whitlock, James",  "M", 73, "SOB, ?CHF exacerbation",         118, 162, 98, 92, 25, 36.8, false, Instant.ofEpochSecond(now - 660)));
        heap.insert(new Patient("Nguyen, Thanh",    "F", 48, "Palpitations, ?AF RVR",          146, 104, 72, 95, 20, 36.9, false, Instant.ofEpochSecond(now - 1080)));
        heap.insert(new Patient("Abara, Grace",     "F", 39, "Abdominal pain, vomiting",       104, 128, 82, 96, 18, 38.6, false, Instant.ofEpochSecond(now - 2520)));
        heap.insert(new Patient("Petrov, Anton",    "M", 56, "Hypertensive urgency",            92, 188, 104, 97, 17, 36.7, false, Instant.ofEpochSecond(now - 3300)));
        heap.insert(new Patient("Sundqvist, Lena",  "F", 31, "Laceration, left forearm",        78, 118, 76, 99, 15, 36.6, false, Instant.ofEpochSecond(now - 4080)));
        heap.insert(new Patient("Hassan, Omar",     "M", 24, "Med refill, stable",              70, 120, 78, 100, 14, 36.5, false, Instant.ofEpochSecond(now - 6000)));

        // Programmatically generate 30 more randomized clinical profiles to load test your Max-Heap sorting!
        String[] firstNames = {"John", "Emma", "Robert", "Sophia", "William", "Olivia", "David", "Ava", "Joseph", "Mia"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson"};
        String[] complaints = {"Shortness of breath", "Dizziness", "Mild chest tightness", "Palpitations", "Fatigue", "Nausea"};

        java.util.Random rand = new java.util.Random(42); // Seeded for strict reproducibility rules

        for (int i = 0; i < 30; i++) {
            String randomName = lastNames[rand.nextInt(lastNames.length)] + ", " + firstNames[rand.nextInt(firstNames.length)];
            String randomSex = rand.nextBoolean() ? "M" : "F";
            int randomAge = 18 + rand.nextInt(70);
            String randomComplaint = complaints[rand.nextInt(complaints.length)];

            // Generate varying distributions of medical vitals
            int hr = 60 + rand.nextInt(80);          // 60 - 140 bpm
            int sbp = 100 + rand.nextInt(90);        // 100 - 190 mmHg
            int dbp = 60 + rand.nextInt(40);         // 60 - 100 mmHg
            int o2 = 88 + rand.nextInt(13);          // 88 - 100% SpO2
            int rr = 12 + rand.nextInt(15);          // 12 - 27 breaths/min
            double temp = 36.5 + (rand.nextDouble() * 2.5); // 36.5 - 39.0 C
            boolean cardiac = rand.nextDouble() > 0.85; // 15% chance of severe acute flag

            // Randomize arrival timestamp spanning the past 3 hours
            long randomTimeOffset = rand.nextInt(10800);

            heap.insert(new Patient(
                    randomName, randomSex, randomAge, randomComplaint,
                    hr, sbp, dbp, o2, rr, temp, cardiac,
                    Instant.ofEpochSecond(now - randomTimeOffset)
            ));
        }
    }
}
