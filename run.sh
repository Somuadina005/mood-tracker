#!/bin/bash
# Build and run the Mood Tracker
set -e

echo "=== Building Mood Tracker ==="
mkdir -p out/mood-tracker data

javac -d out/mood-tracker \
  src/main/java/com/moodtracker/MoodEntry.java \
  src/main/java/com/moodtracker/MoodStore.java \
  src/main/java/com/moodtracker/MoodTrackerApp.java

echo "Build successful!"
echo "Starting Mood Tracker at http://localhost:8081"
java -cp out/mood-tracker com.moodtracker.MoodTrackerApp
