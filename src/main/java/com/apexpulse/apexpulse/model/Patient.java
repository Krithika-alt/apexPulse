package com.apexpulse.apexpulse.model;

import java.time.Instant;

public class Patient implements Comparable<Patient> {
    private String name;
    private String sex;
    private int age;
    private String complaint;
    private int heartRate;
    private int systolicBp;
    private int diastolicBp;
    private int spo2;
    private int respiratoryRate;
    private double temperature;
    private boolean cardiacFlag;
    private int cholesterol; // Added from your Kaggle dataset features
    private int active;      // Added from your Kaggle dataset features
    private Instant arrivedAt;
    private int priorityScore;

    public Patient() {}

    public Patient(String name, String sex, int age, String complaint,
                   int heartRate, int systolicBp, int diastolicBp,
                   int spo2, int respiratoryRate, double temperature,
                   boolean cardiacFlag, int cholesterol, int active, Instant arrivedAt) {
        this.name = name;
        this.sex = sex;
        this.age = age;
        this.complaint = complaint;
        this.heartRate = heartRate;
        this.systolicBp = systolicBp;
        this.diastolicBp = diastolicBp;
        this.spo2 = spo2;
        this.respiratoryRate = respiratoryRate;
        this.temperature = temperature;
        this.cardiacFlag = cardiacFlag;
        this.cholesterol = cholesterol;
        this.active = active;
        this.arrivedAt = arrivedAt;
        this.priorityScore = computeScore();
    }

    private int computeScore() {
        int score = 12; // Base scale indicator

        // 1. Oxygen Saturation (SpO2)
        if (spo2 < 90)               score += 28;
        else if (spo2 < 94)          score += 12;

        // 2. Blood Pressure (ESI/NEWS2-style severity tiers)
        if (systolicBp < 90)         score += 26; // Shock warning
        else if (systolicBp >= 180)  score += 20; // Critical Crisis
        else if (systolicBp >= 140)  score += 14; // Stage 2 Hypertension

        // 3. Heart Rate
        if (heartRate > 130)         score += 22;
        else if (heartRate >= 110)   score += 11;
        else if (heartRate < 50)     score += 22;

        // 4. Respiratory Rates & Temps
        if (respiratoryRate > 24)    score += 14;
        if (temperature >= 38.5)     score += 9;

        // 5. Acute Cardiac Markers
        if (cardiacFlag)             score += 24;

        // 6. ML Model Feature Integration (Cholesterol & Sedentary risk from Gradient Boosting analysis)
        if (cholesterol == 3)        score += 15; // High cholesterol risk tier
        else if (cholesterol == 2)   score += 8;
        if (active == 0)             score += 5;  // Inactivity risk factor

        return Math.min(99, score);
    }

    // REQUIRED FOR TRIAGE HEAP: Implements Comparable interface
    @Override
    public int compareTo(Patient other) {
        // Higher priority score bubbles to the top of the heap (root)
        return Integer.compare(other.getPriorityScore(), this.getPriorityScore());
    }

    public int getEsi() {
        if (priorityScore >= 85) return 1;
        if (priorityScore >= 60) return 2;
        if (priorityScore >= 38) return 3;
        if (priorityScore >= 20) return 4;
        return 5;
    }

    public String getEsiLabel() {
        return switch (getEsi()) {
            case 1 -> "Resuscitation";
            case 2 -> "Emergent";
            case 3 -> "Urgent";
            case 4 -> "Less urgent";
            default -> "Non-urgent";
        };
    }

    public double getWaitProgressPercentage() {
        long mins = (Instant.now().getEpochSecond() - arrivedAt.getEpochSecond()) / 60;
        long limit = switch (getEsi()) {
            case 1 -> 1;
            case 2 -> 10;
            case 3 -> 30;
            case 4 -> 60;
            default -> 120;
        };
        double percentage = ((double) mins / limit) * 100;
        return Math.min(100.0, percentage);
    }

    public String getBp() { return systolicBp + "/" + diastolicBp; }

    public String getWaitLabel() {
        long mins = (Instant.now().getEpochSecond() - arrivedAt.getEpochSecond()) / 60;
        if (mins < 60) return mins + "m";
        return (mins / 60) + "h" + String.format("%02d", mins % 60) + "m";
    }

    public String getDemographics() { return sex + " · " + age; }

    // Vital flag helpers for template coloring
    public String getHrColor()   { return heartRate > 130 || heartRate < 50 ? "#FF3B4E" : heartRate >= 110 ? "#FF8A33" : "inherit"; }
    public String getBpColor()   { return systolicBp < 90 ? "#FF3B4E" : systolicBp >= 180 ? "#FF8A33" : "inherit"; }
    public String getSpo2Color() { return spo2 < 90 ? "#FF3B4E" : spo2 < 94 ? "#FF8A33" : "inherit"; }
    public String getRrColor()   { return respiratoryRate > 24 ? "#FF8A33" : "inherit"; }
    public String getEsiCssVar() {
        return switch (getEsi()) {
            case 1 -> "var(--esi1)";
            case 2 -> "var(--esi2)";
            case 3 -> "var(--esi3)";
            case 4 -> "var(--esi4)";
            default -> "var(--esi5)";
        };
    }

    // Getters
    public String getName()          { return name; }
    public String getSex()           { return sex; }
    public int getAge()              { return age; }
    public String getComplaint()     { return complaint; }
    public int getHeartRate()        { return heartRate; }
    public int getSystolicBp()       { return systolicBp; }
    public int getDiastolicBp()      { return diastolicBp; }
    public int getSpo2()             { return spo2; }
    public int getRespiratoryRate()  { return respiratoryRate; }
    public double getTemperature()   { return temperature; }
    public boolean isCardiacFlag()   { return cardiacFlag; }
    public int getCholesterol()      { return cholesterol; }
    public int getActive()           { return active; }
    public Instant getArrivedAt()    { return arrivedAt; }

    public int getPriorityScore() {
        long minutesWaiting = (Instant.now().getEpochSecond() - arrivedAt.getEpochSecond()) / 60;
        int ageBonus = (int) (minutesWaiting / 10);
        return Math.min(99, this.priorityScore + ageBonus);
    }

    public String getRecommendedAllocation() {
        if (this.getEsi() == 1 || this.getHeartRate() > 140 || this.getSystolicBp() < 80) {
            return "Bay 1";
        } else if (this.getEsi() == 2 || this.getSystolicBp() > 180) {
            return "Bay 2";
        } else if (this.getEsi() == 3 || this.getSpo2() < 94) {
            return "Bed 12";
        } else {
            return "Waiting Room";
        }
    }

    // Setters
    public void setName(String name)                  { this.name = name; }
    public void setSex(String sex)                    { this.sex = sex; }
    public void setAge(int age)                       { this.age = age; }
    public void setComplaint(String complaint)        { this.complaint = complaint; }
    public void setHeartRate(int heartRate)           { this.heartRate = heartRate; this.priorityScore = computeScore(); }
    public void setSystolicBp(int sbp)               { this.systolicBp = sbp; this.priorityScore = computeScore(); }
    public void setDiastolicBp(int dbp)              { this.diastolicBp = dbp; this.priorityScore = computeScore(); }
    public void setSpo2(int spo2)                    { this.spo2 = spo2; this.priorityScore = computeScore(); }
    public void setRespiratoryRate(int rr)           { this.respiratoryRate = rr; this.priorityScore = computeScore(); }
    public void setTemperature(double temp)          { this.temperature = temp; this.priorityScore = computeScore(); }
    public void setCardiacFlag(boolean flag)         { this.cardiacFlag = flag; this.priorityScore = computeScore(); }
    public void setCholesterol(int cholesterol)      { this.cholesterol = cholesterol; this.priorityScore = computeScore(); }
    public void setActive(int active)                { this.active = active; this.priorityScore = computeScore(); }
    public void setArrivedAt(Instant arrivedAt)      { this.arrivedAt = arrivedAt; }
}