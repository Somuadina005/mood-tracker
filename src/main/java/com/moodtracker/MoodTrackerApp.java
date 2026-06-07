package com.moodtracker;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class MoodTrackerApp {

    private static MoodStore store;

    public static void main(String[] args) throws IOException {
        String dataFile = "data/moods.csv";
        new File("data").mkdirs();
        store = new MoodStore(dataFile);
        store.seedData();

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/", MoodTrackerApp::handleIndex);
        server.createContext("/api/moods", MoodTrackerApp::handleMoods);
        server.createContext("/api/stats", MoodTrackerApp::handleStats);
        server.start();

        System.out.println("Mood Tracker running at http://localhost:8081");
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        sendResponse(ex, 200, "text/html", getHtml());
    }

    private static void handleMoods(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        if (method.equals("GET")) {
            String query = ex.getRequestURI().getQuery();
            List<MoodEntry> result;
            if (query != null && query.startsWith("mood=")) {
                String mood = query.substring(5);
                result = store.getByMood(mood);
            } else {
                result = store.getAll();
            }
            result.sort(Comparator.comparing(MoodEntry::getDate).reversed());
            String json = "[" + result.stream().map(MoodEntry::toString).collect(Collectors.joining(",")) + "]";
            sendResponse(ex, 200, "application/json", json);

        } else if (method.equals("POST")) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseJson(body);
            try {
                String mood = params.getOrDefault("mood", "Calm");
                LocalDate date = LocalDate.parse(params.getOrDefault("date", LocalDate.now().toString()));
                String note = params.getOrDefault("note", "");
                int intensity = Integer.parseInt(params.getOrDefault("intensity", "3"));
                MoodEntry e = new MoodEntry(mood, date, note, intensity);
                store.add(e);
                sendResponse(ex, 201, "application/json", e.toString());
            } catch (Exception e) {
                sendResponse(ex, 400, "application/json", "{\"error\":\"Invalid data\"}");
            }

        } else if (method.equals("DELETE")) {
            String path = ex.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            boolean ok = store.delete(id);
            sendResponse(ex, ok ? 200 : 404, "application/json", ok ? "{\"ok\":true}" : "{\"error\":\"Not found\"}");

        } else {
            sendResponse(ex, 405, "text/plain", "Method not allowed");
        }
    }

    private static void handleStats(HttpExchange ex) throws IOException {
        Map<String, Long> freq = store.getMoodFrequency();
        Map<String, Map<String, Long>> byWeek = store.getMoodByWeek();
        String dominant = store.getMostFrequentMood();
        long total = store.getAll().size();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"total\":").append(total).append(",");
        sb.append("\"dominant\":\"").append(dominant).append("\",");
        sb.append("\"frequency\":{");
        sb.append(freq.entrySet().stream().map(e -> "\"" + e.getKey() + "\":" + e.getValue()).collect(Collectors.joining(",")));
        sb.append("},\"byWeek\":{");
        sb.append(byWeek.entrySet().stream().map(e -> {
            String inner = e.getValue().entrySet().stream()
                    .map(m -> "\"" + m.getKey() + "\":" + m.getValue())
                    .collect(Collectors.joining(","));
            return "\"" + e.getKey() + "\":{" + inner + "}";
        }).collect(Collectors.joining(",")));
        sb.append("}}");

        sendResponse(ex, 200, "application/json", sb.toString());
    }

    private static void sendResponse(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim().replaceAll("[{}\"]", "");
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) map.put(kv[0].trim(), kv[1].trim());
        }
        return map;
    }

    private static String getHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Mood Tracker</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Segoe UI', system-ui, sans-serif; background: #f0f4f8; color: #1a202c; }
  header { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 20px 32px; display: flex; align-items: center; gap: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.15); }
  header h1 { font-size: 1.6rem; font-weight: 700; }
  .container { max-width: 1200px; margin: 0 auto; padding: 24px; }
  .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 24px; }
  .stat-card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); border-left: 4px solid #f093fb; }
  .stat-card .label { font-size: 0.8rem; color: #718096; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 6px; }
  .stat-card .value { font-size: 1.5rem; font-weight: 700; color: #2d3748; }
  .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }
  @media(max-width: 768px) { .grid2 { grid-template-columns: 1fr; } }
  .card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
  .card h2 { font-size: 1.1rem; font-weight: 600; color: #4a5568; margin-bottom: 16px; }
  .mood-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px; }
  .mood-btn { padding: 12px 8px; border: 2px solid #e2e8f0; border-radius: 10px; cursor: pointer; text-align: center; transition: all 0.2s; background: white; font-size: 0.85rem; font-weight: 600; }
  .mood-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
  .mood-btn.selected { border-width: 2.5px; transform: scale(1.05); }
  .mood-btn .emoji { font-size: 1.6rem; display: block; margin-bottom: 4px; }
  .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
  .form-group label { font-size: 0.85rem; font-weight: 600; color: #4a5568; }
  .form-group input, .form-group textarea { padding: 10px 12px; border: 1.5px solid #e2e8f0; border-radius: 8px; font-size: 0.95rem; outline: none; transition: border-color 0.2s; font-family: inherit; }
  .form-group input:focus, .form-group textarea:focus { border-color: #f093fb; box-shadow: 0 0 0 3px rgba(240,147,251,0.15); }
  .intensity-row { display: flex; gap: 8px; }
  .int-btn { flex: 1; padding: 8px; border: 1.5px solid #e2e8f0; border-radius: 8px; cursor: pointer; text-align: center; font-weight: 600; font-size: 0.9rem; background: white; transition: all 0.15s; }
  .int-btn.selected { background: linear-gradient(135deg, #f093fb, #f5576c); color: white; border-color: transparent; }
  .btn { padding: 11px 24px; border: none; border-radius: 8px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
  .btn-primary { background: linear-gradient(135deg, #f093fb, #f5576c); color: white; width: 100%; margin-top: 8px; }
  .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(240,147,251,0.4); }
  .btn-danger { background: #fed7d7; color: #c53030; padding: 4px 10px; font-size: 0.8rem; border-radius: 6px; border: none; cursor: pointer; }
  .filter-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
  .filter-btn { padding: 5px 14px; border-radius: 20px; border: 1.5px solid #e2e8f0; background: white; cursor: pointer; font-size: 0.83rem; font-weight: 600; transition: all 0.15s; }
  .filter-btn.active { background: linear-gradient(135deg, #f093fb, #f5576c); color: white; border-color: transparent; }
  table { width: 100%; border-collapse: collapse; }
  th { padding: 10px 12px; text-align: left; font-size: 0.8rem; text-transform: uppercase; color: #718096; background: #f7fafc; }
  td { padding: 11px 12px; border-bottom: 1px solid #edf2f7; font-size: 0.88rem; }
  tr:last-child td { border-bottom: none; }
  .mood-tag { display: inline-flex; align-items: center; gap: 5px; padding: 3px 10px; border-radius: 20px; font-size: 0.8rem; font-weight: 600; }
  .stars { color: #f093fb; letter-spacing: 1px; }
  .empty { text-align: center; color: #a0aec0; padding: 40px; }
  .toast { position: fixed; bottom: 20px; right: 20px; background: #48bb78; color: white; padding: 12px 20px; border-radius: 8px; font-weight: 600; opacity: 0; transition: opacity 0.3s; pointer-events: none; z-index: 1000; }
  .toast.show { opacity: 1; }
</style>
</head>
<body>
<header><span>🌈</span><h1>Daily Mood Tracker</h1></header>
<div class="container">
  <div class="stats-grid">
    <div class="stat-card"><div class="label">Total Entries</div><div class="value" id="statTotal">0</div></div>
    <div class="stat-card" style="border-color:#f5576c"><div class="label">Dominant Mood</div><div class="value" id="statDominant">—</div></div>
    <div class="stat-card" style="border-color:#48bb78"><div class="label">This Week</div><div class="value" id="statWeek">0</div></div>
    <div class="stat-card" style="border-color:#ed8936"><div class="label">Mood Variety</div><div class="value" id="statVariety">0</div></div>
  </div>
  <div class="grid2">
    <div class="card">
      <h2>📊 Mood Distribution</h2>
      <canvas id="freqChart" height="220"></canvas>
    </div>
    <div class="card">
      <h2>📈 Weekly Mood Trend</h2>
      <canvas id="trendChart" height="220"></canvas>
    </div>
  </div>
  <div class="grid2">
    <div class="card">
      <h2>✨ Log Today's Mood</h2>
      <div class="mood-grid" id="moodGrid">
        <button class="mood-btn" onclick="selectMood('Happy',this)"><span class="emoji">😊</span>Happy</button>
        <button class="mood-btn" onclick="selectMood('Sad',this)"><span class="emoji">😢</span>Sad</button>
        <button class="mood-btn" onclick="selectMood('Anxious',this)"><span class="emoji">😰</span>Anxious</button>
        <button class="mood-btn" onclick="selectMood('Calm',this)"><span class="emoji">😌</span>Calm</button>
        <button class="mood-btn" onclick="selectMood('Excited',this)"><span class="emoji">🤩</span>Excited</button>
        <button class="mood-btn" onclick="selectMood('Angry',this)"><span class="emoji">😡</span>Angry</button>
        <button class="mood-btn" onclick="selectMood('Tired',this)"><span class="emoji">😴</span>Tired</button>
        <button class="mood-btn" onclick="selectMood('Grateful',this)"><span class="emoji">🙏</span>Grateful</button>
      </div>
      <div class="form-group">
        <label>Intensity (1 = mild, 5 = strong)</label>
        <div class="intensity-row" id="intensityRow">
          <button class="int-btn" onclick="selectIntensity(1,this)">1</button>
          <button class="int-btn" onclick="selectIntensity(2,this)">2</button>
          <button class="int-btn selected" onclick="selectIntensity(3,this)">3</button>
          <button class="int-btn" onclick="selectIntensity(4,this)">4</button>
          <button class="int-btn" onclick="selectIntensity(5,this)">5</button>
        </div>
      </div>
      <div class="form-group">
        <label>Date</label>
        <input type="date" id="date">
      </div>
      <div class="form-group">
        <label>Note (optional)</label>
        <textarea id="note" rows="2" placeholder="How are you feeling? What's on your mind?"></textarea>
      </div>
      <button class="btn btn-primary" onclick="logMood()">Log Mood</button>
    </div>
    <div class="card" style="overflow-x:auto">
      <h2>📋 Mood Log</h2>
      <div class="filter-bar" id="filterBar">
        <button class="filter-btn active" onclick="filterMood('all',this)">All</button>
        <button class="filter-btn" onclick="filterMood('Happy',this)">😊 Happy</button>
        <button class="filter-btn" onclick="filterMood('Sad',this)">😢 Sad</button>
        <button class="filter-btn" onclick="filterMood('Anxious',this)">😰 Anxious</button>
        <button class="filter-btn" onclick="filterMood('Calm',this)">😌 Calm</button>
        <button class="filter-btn" onclick="filterMood('Excited',this)">🤩 Excited</button>
      </div>
      <table>
        <thead><tr><th>Date</th><th>Mood</th><th>Intensity</th><th>Note</th><th></th></tr></thead>
        <tbody id="moodTable"><tr><td colspan="5" class="empty">Loading...</td></tr></tbody>
      </table>
    </div>
  </div>
</div>
<div class="toast" id="toast"></div>

<script>
const MOOD_COLORS = {Happy:'#f6d860',Sad:'#74c0fc',Anxious:'#ff8787',Calm:'#69db7c',Excited:'#f093fb',Angry:'#ff6b6b',Tired:'#adb5bd',Grateful:'#ffa94d'};
const MOOD_EMOJI = {Happy:'😊',Sad:'😢',Anxious:'😰',Calm:'😌',Excited:'🤩',Angry:'😡',Tired:'😴',Grateful:'🙏'};
let selectedMood = null, selectedIntensity = 3, freqChart, trendChart;
let currentFilter = 'all', allEntries = [];

document.getElementById('date').value = new Date().toISOString().split('T')[0];

function selectMood(mood, btn) {
  selectedMood = mood;
  document.querySelectorAll('.mood-btn').forEach(b => b.classList.remove('selected'));
  btn.classList.add('selected');
  btn.style.borderColor = MOOD_COLORS[mood] || '#f093fb';
  btn.style.background = (MOOD_COLORS[mood] || '#f093fb') + '22';
}

function selectIntensity(val, btn) {
  selectedIntensity = val;
  document.querySelectorAll('.int-btn').forEach(b => b.classList.remove('selected'));
  btn.classList.add('selected');
}

async function load() {
  const [entryRes, statRes] = await Promise.all([fetch('/api/moods'), fetch('/api/stats')]);
  allEntries = await entryRes.json();
  const stats = await statRes.json();

  document.getElementById('statTotal').textContent = stats.total;
  document.getElementById('statDominant').textContent = (MOOD_EMOJI[stats.dominant] || '') + ' ' + stats.dominant;
  document.getElementById('statVariety').textContent = Object.keys(stats.frequency).length + ' moods';

  const weekEntries = allEntries.filter(e => {
    const d = new Date(e.date), now = new Date();
    return (now - d) / 86400000 <= 7;
  });
  document.getElementById('statWeek').textContent = weekEntries.length;

  renderTable(allEntries);
  renderFreqChart(stats.frequency);
  renderTrendChart(stats.byWeek);
}

function renderTable(entries) {
  const filtered = currentFilter === 'all' ? entries : entries.filter(e => e.mood === currentFilter);
  const tbody = document.getElementById('moodTable');
  if (!filtered.length) { tbody.innerHTML = '<tr><td colspan="5" class="empty">No entries found</td></tr>'; return; }
  tbody.innerHTML = filtered.slice(0, 20).map(e => {
    const stars = '★'.repeat(e.intensity) + '☆'.repeat(5 - e.intensity);
    const color = MOOD_COLORS[e.mood] || '#a0aec0';
    return `<tr>
      <td>${e.date}</td>
      <td><span class="mood-tag" style="background:${color}33;color:${color}">${MOOD_EMOJI[e.mood]||''} ${e.mood}</span></td>
      <td><span class="stars">${stars}</span></td>
      <td style="color:#718096;max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${e.note}">${e.note || '—'}</td>
      <td><button class="btn-danger" onclick="deleteEntry('${e.id}')">✕</button></td>
    </tr>`;
  }).join('');
}

function filterMood(mood, btn) {
  currentFilter = mood;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderTable(allEntries);
}

function renderFreqChart(data) {
  const labels = Object.keys(data);
  const values = Object.values(data);
  const ctx = document.getElementById('freqChart').getContext('2d');
  if (freqChart) freqChart.destroy();
  freqChart = new Chart(ctx, { type: 'doughnut', data: {
    labels: labels.map(l => (MOOD_EMOJI[l]||'') + ' ' + l),
    datasets: [{ data: values, backgroundColor: labels.map(l => MOOD_COLORS[l] || '#a0aec0'), borderWidth: 2, borderColor: '#fff' }]
  }, options: { plugins: { legend: { position: 'right', labels: { font: { size: 11 } } } }, cutout: '55%' }});
}

function renderTrendChart(byWeek) {
  const weeks = Object.keys(byWeek);
  const moods = [...new Set(Object.values(byWeek).flatMap(w => Object.keys(w)))];
  const ctx = document.getElementById('trendChart').getContext('2d');
  if (trendChart) trendChart.destroy();
  trendChart = new Chart(ctx, { type: 'bar', data: {
    labels: weeks,
    datasets: moods.map(mood => ({
      label: (MOOD_EMOJI[mood]||'') + ' ' + mood,
      data: weeks.map(w => byWeek[w][mood] || 0),
      backgroundColor: (MOOD_COLORS[mood] || '#a0aec0') + 'bb',
      borderColor: MOOD_COLORS[mood] || '#a0aec0',
      borderWidth: 1, borderRadius: 4
    }))
  }, options: { plugins: { legend: { labels: { font: { size: 10 } } } },
    scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true } } }});
}

async function logMood() {
  if (!selectedMood) { showToast('Please select a mood first', '#fc8181'); return; }
  const date = document.getElementById('date').value;
  const note = document.getElementById('note').value;
  if (!date) { showToast('Please select a date', '#fc8181'); return; }
  await fetch('/api/moods', { method: 'POST', headers: {'Content-Type':'application/json'},
    body: JSON.stringify({mood: selectedMood, date, note, intensity: selectedIntensity})});
  document.getElementById('note').value = '';
  selectedMood = null;
  document.querySelectorAll('.mood-btn').forEach(b => { b.classList.remove('selected'); b.style.borderColor = ''; b.style.background = ''; });
  showToast('Mood logged! 🌟', '#48bb78');
  load();
}

async function deleteEntry(id) {
  await fetch('/api/moods/' + id, { method: 'DELETE' });
  showToast('Entry deleted', '#ed8936');
  load();
}

function showToast(msg, bg) {
  const t = document.getElementById('toast');
  t.textContent = msg; t.style.background = bg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 2500);
}

load();
</script>
</body>
</html>
""";
    }
}
