package com.moodtracker;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class MoodStore {
    private final List<MoodEntry> entries = new ArrayList<>();
    private final String dataFile;

    public MoodStore(String dataFile) {
        this.dataFile = dataFile;
        load();
    }

    public synchronized void add(MoodEntry e) {
        entries.add(e);
        save();
    }

    public synchronized boolean delete(String id) {
        boolean removed = entries.removeIf(e -> e.getId().equals(id));
        if (removed) save();
        return removed;
    }

    public synchronized List<MoodEntry> getAll() {
        return new ArrayList<>(entries);
    }

    public synchronized List<MoodEntry> getByMood(String mood) {
        return entries.stream().filter(e -> e.getMood().equalsIgnoreCase(mood)).collect(Collectors.toList());
    }

    public synchronized List<MoodEntry> getByDateRange(LocalDate from, LocalDate to) {
        return entries.stream()
                .filter(e -> !e.getDate().isBefore(from) && !e.getDate().isAfter(to))
                .sorted(Comparator.comparing(MoodEntry::getDate))
                .collect(Collectors.toList());
    }

    public synchronized Map<String, Long> getMoodFrequency() {
        return entries.stream().collect(Collectors.groupingBy(MoodEntry::getMood, Collectors.counting()));
    }

    public synchronized Map<String, Long> getWeeklyMoodCounts() {
        // last 4 weeks
        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        for (int w = 3; w >= 0; w--) {
            LocalDate start = now.minusDays((long)(w + 1) * 7);
            LocalDate end = now.minusDays((long) w * 7);
            String label = "Week " + (4 - w);
            long count = entries.stream().filter(e -> !e.getDate().isBefore(start) && e.getDate().isBefore(end)).count();
            result.put(label, count);
        }
        return result;
    }

    public synchronized Map<String, Map<String, Long>> getMoodByWeek() {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        for (int w = 3; w >= 0; w--) {
            LocalDate start = now.minusDays((long)(w + 1) * 7);
            LocalDate end = now.minusDays((long) w * 7);
            String label = "Week " + (4 - w);
            Map<String, Long> moodCount = entries.stream()
                    .filter(e -> !e.getDate().isBefore(start) && e.getDate().isBefore(end))
                    .collect(Collectors.groupingBy(MoodEntry::getMood, Collectors.counting()));
            result.put(label, moodCount);
        }
        return result;
    }

    public synchronized String getMostFrequentMood() {
        return getMoodFrequency().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("N/A");
    }

    private void load() {
        File f = new File(dataFile);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) entries.add(MoodEntry.fromCsv(line));
            }
        } catch (IOException e) {
            System.err.println("Error loading: " + e.getMessage());
        }
    }

    private void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(dataFile))) {
            for (MoodEntry e : entries) pw.println(e.toCsv());
        } catch (IOException e) {
            System.err.println("Error saving: " + e.getMessage());
        }
    }

    public synchronized void seedData() {
        if (!entries.isEmpty()) return;
        LocalDate now = LocalDate.now();
        String[] moods = {"Happy", "Sad", "Anxious", "Calm", "Excited", "Angry", "Tired"};
        String[] notes = {"Had a great day!", "Feeling low today", "Work stress", "Peaceful morning", "New project!", "Traffic was awful", "Long week"};
        int[] intensities = {5, 2, 3, 4, 5, 2, 3, 4, 4, 5, 2, 3, 3, 4, 2, 5, 3, 4, 1, 5, 2, 4, 3, 5, 3, 2, 5, 4};
        String[] seedMoods = {"Happy","Anxious","Calm","Happy","Excited","Sad","Tired","Happy","Calm","Excited",
                "Anxious","Happy","Sad","Calm","Angry","Happy","Tired","Happy","Anxious","Excited",
                "Calm","Happy","Sad","Happy","Excited","Anxious","Happy","Calm"};
        for (int i = 0; i < 28; i++) {
            String mood = seedMoods[i % seedMoods.length];
            String note = notes[i % notes.length];
            int intensity = intensities[i % intensities.length];
            add(new MoodEntry(mood, now.minusDays(i), note, intensity));
        }
    }
}
