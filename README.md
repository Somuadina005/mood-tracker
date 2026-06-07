# Mood Tracker

A lightweight Java web application for logging and analyzing daily moods.

## Features
- 😊 Log mood from 8 types: Happy, Sad, Anxious, Calm, Excited, Angry, Tired, Grateful
- ⭐ Intensity scale 1–5 per entry
- 📝 Optional free-text note per entry
- 📊 Doughnut chart of mood distribution
- 📈 Stacked weekly bar chart showing mood patterns over 4 weeks
- 🔍 Filter log by mood type
- 🌱 Auto-seeded with 28 days of sample data on first run
- 💾 Persistent CSV storage

## How to Run

```bash
# 1. Compile
mkdir -p out/mood-tracker data
javac -d out/mood-tracker \
  src/main/java/com/moodtracker/MoodEntry.java \
  src/main/java/com/moodtracker/MoodStore.java \
  src/main/java/com/moodtracker/MoodTrackerApp.java

# 2. Run
java -cp out/mood-tracker com.moodtracker.MoodTrackerApp

# 3. Open browser
# http://localhost:8081
```

Or use the helper script:
```bash
chmod +x run.sh && ./run.sh
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/moods` | Get all entries |
| GET | `/api/moods?mood=Happy` | Filter by mood type |
| POST | `/api/moods` | Log new mood entry |
| DELETE | `/api/moods/{id}` | Delete an entry |
| GET | `/api/stats` | Get frequency, weekly breakdown, dominant mood |

### POST /api/moods body
```json
{
  "mood": "Happy",
  "date": "2026-06-05",
  "note": "Had a great day!",
  "intensity": 4
}
```

## Project Structure

```
mood-tracker/
├── run.sh                          # Build + run script
├── data/
│   └── moods.csv                   # Persistent storage (auto-created)
└── src/main/java/com/moodtracker/
    ├── MoodEntry.java              # Data model + CSV serialization
    ├── MoodStore.java              # In-memory store + file persistence + analytics
    └── MoodTrackerApp.java         # HTTP server + REST API + HTML/JS UI
```
