# 🌙 NightShift Navigator — Night-Time Decision Engine for Shift Workers

> **Theme 1 · Problem 1: "The City That Sleeps While You Work"**  
> *A real-time, night-time decision engine that understands the changing conditions of a city throughout the night for millions of shift workers (nurses, factory workers, security guards, delivery workers, and sanitation staff).*

---

## 📌 Context & The Challenge

A city is designed around people who work during the day, but millions of workers live on a completely different schedule. At **2:30 AM**, a worker finishing a shift may need:
- Safe transportation home when regular public transit is closed or infrequent
- Open 24/7 essential services (medicines, food, fuel, restrooms)
- A safe route that avoids dark corridors, waterlogging, or historical incident hotspots
- Proactive emergency safety monitoring and automated check-ins
- **Crucially:** An engine that **understands the reality of nighttime data** — where feeds go silent, schedules are erratic, and GPS signals drop. Rather than confidently hallucinating or failing, the engine must make data uncertainty explicit and provide resilient fallbacks.

Given:  
> *"I finish work at 2:15 AM and need to get home safely, find an open pharmacy, and know which route is safe."*

**NightShift Navigator** solves this end-to-end.

---

## ⚡ Core Innovations & Architecture

```
                          ┌─────────────────────────────────────┐
                          │     Shift Worker Context Input      │
                          │  (Shift End: 02:15 AM, Destination,  │
                          │     Role: ICU Nurse / Factory)      │
                          └──────────────────┬──────────────────┘
                                             │
                                             ▼
                      ┌──────────────────────────────────────────────┐
                      │          NightPlanEngine (Core Engine)       │
                      │  • Evaluates 4-Tier Uncertainty Matrix       │
                      │  • Multi-modal transit routing & fallbacks   │
                      │  • 24/7 Essential Service hours verification │
                      │  • Street lighting & incident zone scoring   │
                      │  • Weather & night road closure overrides    │
                      └──────────────────────┬───────────────────────┘
                                             │
               ┌─────────────────────────────┼─────────────────────────────┐
               ▼                             ▼                             ▼
    ┌──────────────────────┐      ┌──────────────────────┐      ┌──────────────────────┐
    │  Confidence Matrix   │      │ Dynamic Re-Evaluator │      │  Journey Guardian    │
    │  • KNOWN (Live)      │      │  • Auto-refresh 3min │      │  • Check-in deadline │
    │  • PROBABLE (Pattern)│      │  • Silence detection │      │  • Silent escalation │
    │  • OUTDATED (Stale)  │      │  • Offline caching   │      │  • Ambient audio SOS │
    │  • UNKNOWN (Explicit)│      │  • SMS fallback      │      │  • Cloudinary backup │
    └──────────────────────┘      └──────────────────────┘      └──────────────────────┘
```

---

## 🛡️ 1. The 4-Tier Explicit Uncertainty Model

Traditional navigation systems treat night as just another timestamp and invent ETA predictions even when buses aren't running. **NightShift Navigator never guesses blindly.** Every recommendation carries an explicit confidence level:

| Level | Badge | Meaning | System Behavior |
|:------|:-----:|:--------|:----------------|
| **KNOWN** | `KNOWN ✓` | Verified within the last 15 minutes (live GPS, active transit dispatch, open pharmacy confirmation) | Act on with full confidence. Primary recommendation. |
| **PROBABLE** | `PROBABLE ~` | Inferred from 30-day historical patterns (e.g. night auto stand usually active, 85% availability) | Highlighted with pattern probability; secondary recommendation. |
| **OUTDATED** | `OUTDATED ⏰` | Feed went silent or telemetry is >2 hours stale | Explicitly flagged in amber/red with alternate contingency plan. |
| **UNKNOWN** | `UNKNOWN ?` | No verified data available for this sector/hour | The engine explicitly says *"No live data"*, avoids sending workers into the unknown, and suggests verified safe hubs nearby. |

---

## 🚀 Key Feature Matrix

### 1. Multi-Modal Night Transit Routing
- Real-time awareness of night-owl buses, late-night metro last-train times, verified prepaid auto stands, and 24/7 cab availability.
- Automatically computes **Primary Plan** (e.g. Night Bus #212 departing 02:28 AM) + **Verified Contingency Plan** (24/7 Auto Stand 350m away).

### 2. 24/7 Open Essential Services
- Curated, live-verified database of late-night services:
  - 24/7 Pharmacies (Apollo, MedPlus) with security guards and CCTV
  - Emergency Trauma Centers & Government Hospitals
  - 24/7 Well-lit Fuel Stations & Safe Rest Stops
  - Police Helpdesks & Women Safety Booths

### 3. Route Safety & Night Incident Heatmaps
- Historical incident scoring overlaid with real-time street lighting telemetry.
- Dynamic avoidance of dark alleys, isolated bridges, and unlit construction stretches after midnight.
- Integrated **Walk With Me** audio escort and live GPS breadcrumb sharing with guardians.

### 4. Weather & Night Road Closures
- Real-time weather detection (e.g., late-night downpours, waterlogged underpasses, nighttime repair work).
- Proactively reroutes around active nighttime municipal closures.

### 5. Automated Smart Check-Ins & Journey Monitoring
- Set departure time from work (e.g. 02:15 AM) and expected arrival time (e.g. 02:50 AM).
- If the worker does not check in by the deadline, the engine automatically escalates:
  1. Soft push notification prompt (2-minute grace period)
  2. Automated SMS alert with last known GPS coordinates sent to emergency contacts
  3. Ambient audio recording triggered and backed up securely to Cloudinary

### 6. Night AI Companion (Groq-Powered)
- Built-in conversational AI assistant tailored specifically for night-shift workers.
- Answers late-night queries with honest confidence badges (*"Is the bus running at 2:30 AM?", "Where is the nearest open pharmacy with a guard?"*).

---

## 📱 Mobile App Architecture (`surakshavaniapp`)

The Android application is written in Java and Android SDK (API 34/35 compatible):

- **[`NightPlanEngine.java`](surakshavaniapp/app/src/main/java/com/example/woshield/NightPlanEngine.java)**: The core decision engine implementing the 4-tier confidence system, fallback generator, offline pattern cache, and Firebase Realtime Database sync.
- **[`NightPlanActivity.java`](surakshavaniapp/app/src/main/java/com/example/woshield/NightPlanActivity.java)**: Worker command center for generating shift plans, live ETA countdown, and journey monitoring.
- **[`NightPlanAdapter.java`](surakshavaniapp/app/src/main/java/com/example/woshield/NightPlanAdapter.java)**: RecyclerView adapter rendering plan items with color-coded confidence chips.
- **[`HomePage.java`](surakshavaniapp/app/src/main/java/com/example/woshield/HomePage.java)**: Night-themed dashboard with one-tap access to Night Plan, Safe Map, 24/7 Stops, and SOS.
- **[`ChatBotActivity.java`](surakshavaniapp/app/src/main/java/com/example/woshield/ChatBotActivity.java)**: AI companion with night-safety system prompts and confidence labeling.
- **[`DemoDataSeeder.java`](surakshavaniapp/app/src/main/java/com/example/woshield/DemoDataSeeder.java)**: 1-Click authoritative seed data (centered on 18.46225° N, 73.864972° E) including night-shift ICU nurse routines, open pharmacies, and transit nodes.

---

## 🌐 Web Platform & Decision Engine Dashboard

The root web application (`index.html`) serves as the comprehensive web portal:
- Interactive live Night Plan generator demo
- Visual Confidence Matrix explainer (`KNOWN`, `PROBABLE`, `OUTDATED`, `UNKNOWN`)
- Shift worker persona walk-throughs (Nurses, Security Guards, Delivery Workers, Factory Staff)
- Live city conditions simulator with 02:30 AM scenario playback

---

## 🛠️ Getting Started

### Web Application
Start any local HTTP server in the repository root:
```bash
# Python
python -m http.server 8080

# Or Node.js
npx serve .
```
Open [http://localhost:8080](http://localhost:8080) in your browser.

### Android Application
1. Open Android Studio.
2. Select **Open** and choose the `surakshavaniapp` directory.
3. Sync Gradle and build the project (`app` module).
4. Run on an emulator or physical device (Android 8.0+ / API 26+).
5. On the login screen, click **"1-Click Demo Login"** or use the credentials:
   - **User Mode**: Auto-logs in as Ananya Sharma (Night Shift Staff) with pre-seeded night routines and safe stops.
   - **Admin Mode**: `admin@woshield.org` / `admin123` for governance and incident zone approvals.

---

## 📄 License
© 2026 NightShift Navigator. Built for the city that never truly sleeps.
