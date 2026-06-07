package com.moodtracker;

import java.time.LocalDate;
import java.util.UUID;

public class MoodEntry {
    private String id;
    private String mood;
    private LocalDate date;
    private String note;
    private int intensity; // 1-5

    public MoodEntry(String mood, LocalDate date, String note, int intensity) {
        this.id = UUID.randomUUID().toString();
        this.mood = mood;
        this.date = date;
        this.note = note;
        this.intensity = intensity;
    }

    public MoodEntry(String id, String mood, LocalDate date, String note, int intensity) {
        this.id = id;
        this.mood = mood;
        this.date = date;
        this.note = note;
        this.intensity = intensity;
    }

    public String getId() { return id; }
    public String getMood() { return mood; }
    public LocalDate getDate() { return date; }
    public String getNote() { return note; }
    public int getIntensity() { return intensity; }

    public String toCsv() {
        return id + "," + mood + "," + date + "," + intensity + "," + note.replace(",", ";").replace("\n", " ");
    }

    public static MoodEntry fromCsv(String line) {
        String[] parts = line.split(",", 5);
        return new MoodEntry(parts[0], parts[1], LocalDate.parse(parts[2]),
                parts.length > 4 ? parts[4] : "", Integer.parseInt(parts[3]));
    }

    @Override
    public String toString() {
        return String.format("{\"id\":\"%s\",\"mood\":\"%s\",\"date\":\"%s\",\"note\":\"%s\",\"intensity\":%d}",
                id, mood, date, note.replace("\"", "\\\""), intensity);
    }
}
