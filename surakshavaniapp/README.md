# 🛡️ WoShield — Next-Generation Women Safety & AI Intelligence Platform

<p align="center">
  <img src="app/src/main/res/drawable/wo.png" alt="WoShield Logo" width="120" height="120" />
</p>

<p align="center">
  <strong>Proactive, Explainable, Multi-Modal Protection and Family Guardian Network for Women</strong>
</p>

<p align="center">
  <a href="#-key-features"><img src="https://img.shields.io/badge/Platform-Android%20(API%2024--34)-brightgreen?logo=android" alt="Platform" /></a>
  <a href="#-technology-stack"><img src="https://img.shields.io/badge/Language-Java%208-orange?logo=java" alt="Language" /></a>
  <a href="#-safety-intelligence-engine"><img src="https://img.shields.io/badge/AI-SambaNova%20Llama%203.1-blueviolet" alt="AI Engine" /></a>
  <a href="#-backend--cloud-services"><img src="https://img.shields.io/badge/Backend-Firebase%20Realtime-amber?logo=firebase" alt="Firebase" /></a>
  <a href="#-evidence-vault"><img src="https://img.shields.io/badge/Media%20Vault-Cloudinary-blue?logo=cloudinary" alt="Cloudinary" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" /></a>
</p>

---

## 📖 Table of Contents

- [Executive Summary](#-executive-summary)
- [System Architecture](#-system-architecture)
- [Core Safety Pillars & Key Features](#-core-safety-pillars--key-features)
  - [1. Safety Intelligence Engine](#1-safety-intelligence-engine)
  - [2. Suraksha Voice AI & Acoustic Threat Detection](#2-suraksha-voice-ai--acoustic-threat-detection)
  - [3. Multi-Modal Emergency SOS Escalation](#3-multi-modal-emergency-sos-escalation)
  - [4. Emergency Siren Buzzer & Countdown Timer](#4-emergency-siren-buzzer--countdown-timer)
  - [5. Smart Check-Ins & Virtual Escort (Walk-With-Me)](#5-smart-check-ins--virtual-escort-walk-with-me)
  - [6. Daily Routines, Corridor Tracking & Safe Stops](#6-daily-routines-corridor-tracking--safe-stops)
  - [7. Family Guardian Dashboard & Safety Circle](#7-family-guardian-dashboard--safety-circle)
  - [8. Sovereign Privacy Center & Data Control](#8-sovereign-privacy-center--data-control)
  - [9. AI Safety Companion (SambaNova Cloud)](#9-ai-safety-companion-sambanova-cloud)
  - [10. Discreet Exit Tools (Fake Call & Helplines)](#10-discreet-exit-tools-fake-call--helplines)
  - [11. Community Danger Zones & Admin Governance](#11-community-danger-zones--admin-governance)
- [Project Directory Structure](#-project-directory-structure)
- [Technology Stack & Dependencies](#-technology-stack--dependencies)
- [Database Schema & Data Models](#-database-schema--data-models)
- [Setup & Installation Guide](#-setup--installation-guide)
- [Hardware & System Permissions](#-hardware--system-permissions)
- [Demo Mode & Live Simulation](#-demo-mode--live-simulation)
- [Contributing Guidelines](#-contributing-guidelines)
- [License](#-license)

---

## 🌟 Executive Summary

**WoShield** is an advanced women safety ecosystem designed to bridge the gap between reactive emergency responses and proactive situational awareness. Traditional safety apps require manual interaction in panic situations; WoShield integrates **on-device acoustic intelligence, deterministic journey monitoring, sensor-fusion panic triggers (power button, shake gestures), and cryptographic evidence retention**.

Built with a **privacy-first, consent-driven architecture**, WoShield guarantees that users retain full sovereignty over their location and telemetry data through granular, revocable member permissions and time-to-live (TTL) sharing sessions.

```
                    ┌────────────────────────────────────────────────────────┐
                    │                   WoShield Ecosystem                   │
                    └────────────────────────────────────────────────────────┘
                                                 │
         ┌────────────────────────┬──────────────┴───────────────┬────────────────────────┐
         ▼                        ▼                              ▼                        ▼
 🎙️ Acoustic AI            🧠 Safety Rules               🚨 Multi-Modal SOS        👨‍👩‍👧 Family Guardian
 Continuous voice         Deterministic route           Power button, shake,       Real-time telemetry,
 keyword monitoring       deviation & check-in          tap/hold audio & video     granular permissions
 & tamper-proof vault     deadline engine               auto-dispatch              & urgent alerts
```

---

## 🏗️ System Architecture

WoShield is structured into distinct layers ensuring high availability, zero latency during emergencies, and strict offline-fallback resilience:

```mermaid
graph TD
    subgraph Client Layer [Android Client Application]
        UI[Material Design 3 UI & Dashboards]
        SIE[Safety Intelligence Engine]
        SVAI[Suraksha Voice AI Service]
        PBS[PowerButtonService & ShakeDetector]
        BTS[BuzzerTimer Foreground Service]
    end

    subgraph Intelligence & Processing [AI & Decision Engine]
        SIE -->|Evaluate Movement & Delays| SD[Safety Decision Repository]
        SVAI -->|Speech Recognition| ThreatTier[4-Tier Threat Classifier]
        LLM[SambaNova Meta-Llama-3.1-8B-Instruct]
    end

    subgraph Media & Evidence [Secure Media Vault]
        Cloudinary[Cloudinary Cloud Storage (Audio Evidence)]
        FBStorage[Firebase Cloud Storage (Video Evidence)]
    end

    subgraph Backend & Network [Firebase & Telephony]
        Auth[Firebase Authentication (Google / Email / FB)]
        RTDB[Firebase Realtime Database]
        SMS[Android Telephony / SMS Manager]
    end

    subgraph Guardians [Family & Admin Dashboards]
        WardDash[Family Guardian Dashboard]
        AdminDash[Admin Danger Zone Governance]
    end

    UI --> SIE
    SVAI -->|Upload Evidence| Cloudinary
    PBS -->|Video Auto-Record| FBStorage
    SIE -->|Emergency Escalation| SMS
    SIE -->|Dual-Log Telemetry| RTDB
    RTDB --> WardDash
    RTDB --> AdminDash
    UI <--> LLM
```

---

## 🚀 Core Safety Pillars & Key Features

### 1. Safety Intelligence Engine
- **Deterministic, Non-Downgrading Hierarchy**: Explicit SOS signals and missed check-ins cannot be suppressed or delayed by low-priority signals.
- **Sensor Uncertainty & GPS Jitter Filtering**: Eliminates false positives by evaluating satellite accuracy tolerances and requiring consecutive sustained samples before flagging route corridors.
- **Configurable Delay Grace Periods**: Automatically detects transit anomalies when transit time exceeds scheduled arrival plus user grace margins.
- **Explainable Decisions**: Every evaluation produces a human-readable diagnosis (`SafetyDecision`) with raw signal parameters.

### 2. Suraksha Voice AI & Acoustic Threat Detection
- **Continuous Background Microphone Monitoring**: Implemented via a resilient Foreground Service (`SurakshaService`).
- **4-Tier Threat Taxonomy**:
  1. *Tier 1: Immediate Distress* (`help`, `save me`, `call the police`, `stop following me`)
  2. *Tier 2: Physical/Verbal Assault* (`don't touch me`, `he's attacking me`, `i will hurt you`)
  3. *Tier 3: Kidnapping/Abduction* (`kidnap`, `let me go`, `get in the car`)
  4. *Tier 4: Domestic Abuse* (`he is abusing me`, `she is hurting me`)
- **Automated Evidence Upload**: When threats are matched, high-fidelity audio is captured and uploaded instantly to Cloudinary (`CloudinaryHelper`), generating a tamper-proof shareable audio link.

### 3. Multi-Modal Emergency SOS Escalation
- **Emergency Button (Tap vs. Hold)**:
  - *Quick Tap*: Dispatches high-accuracy GPS coordinates to all configured emergency contacts via SMS and Firebase.
  - *2-Second Hold*: Records 128kbps AAC high-resolution ambient voice evidence, uploads to Cloudinary, and attaches evidence links to emergency SMS broadcasts.
- **Hardware Power Button Trigger (`PowerButtonService`)**:
  - Triple-tapping the device power button automatically invokes `VideoRecordingService`.
  - Captures 10 seconds of background camera evidence and uploads directly to Firebase Storage (`evidence/{timestamp}.mp4`).
- **Accelerometer Shake Detector (`ShakeDetector`)**:
  - Detects rapid physical shake gestures (>12m/s²) to trigger quick emergency escalations hands-free.

### 4. Emergency Siren Buzzer & Countdown Timer
- **Foreground Safety Countdown (`BuzzerTimerService`)**:
  - Set custom safety countdowns (e.g., 5 min walk through a dark alley).
  - Maintains system `WakeLock` to guarantee execution when screen is off.
- **High-Decibel Siren**: Plays looping hardware alarm (`siren.wav`) overriding silent modes upon zero.
- **Fail-Safe Escalation**: If the user fails to disarm the countdown, full SOS dispatch triggers automatically.

### 5. Smart Check-Ins & Virtual Escort (Walk-With-Me)
- **Time-Bound Check-Ins (`CheckInRepository`)**:
  - Schedule trip check-ins with deadline notifications.
  - Automatic escalation triggers `CheckInDeadlineReceiver` if confirmation is missed.
- **Walk-With-Me Real-Time Escort**:
  - Broadcasts live telemetry and heading to designated trusted guardians until safe arrival is confirmed.

### 6. Daily Routines, Corridor Tracking & Safe Stops
- **Routine Management (`RoutineActivity`)**:
  - Configure recurring commutes (Office, College, Gym) with start/end waypoints, departure times, and allowed detour corridors.
- **Google Maps Integration (`MapsActivity`)**:
  - Real-time route polylines, boundary rendering, and danger zone overlays.
- **Safe Stops Directory (`SafeStopsActivity`)**:
  - Pre-mapped and user-contributed safe havens: police stations, 24/7 pharmacies, hospitals, and verified women help desks.

### 7. Family Guardian Dashboard & Safety Circle
- **Consent-Based Circle Invitations**:
  - Secure invitations via email/phone with cryptographically unique IDs.
- **Granular Member Permission Matrix (`CirclePermissions`)**:
  - Users toggle access per guardian: View Live Location, View Travel History, Receive SOS Alerts, Receive Missed Check-Ins, or Receive Route Deviations.
- **Multi-Ward Telemetry**: Guardians view real-time battery status, reverse-geocoded addresses, transit speeds, and active alerts.

### 8. Sovereign Privacy Center & Data Control
- **Temporary Sharing Sessions (`SharingSession`)**:
  - Create self-expiring sharing links with custom TTL (e.g., 30 mins, 2 hours).
- **Instant Revocation**: Terminate guardian visibility with a single click.
- **Privacy Audit Trail (`PrivacyActivityLog`)**:
  - Complete chronological log of who viewed location data, when sessions expired, and which permissions changed.
- **Data Retention & Cache Purging**: One-tap local cache clearing and server log redaction.

### 9. AI Safety Companion (SambaNova Cloud)
- **Llama 3.1 8B/70B Integration**:
  - Direct integration with SambaNova Cloud API (`SambaNovaService`).
  - Provides real-time guidance on self-defense laws, crisis de-escalation, local police jurisdiction navigation, and panic management.

### 10. Discreet Exit Tools (Fake Call & Helplines)
- **Fake Call Simulation (`FakeCallActivity`)**:
  - Realistic incoming Android phone call UI with animated accept/reject sliders to gracefully disengage from uncomfortable encounters.
- **Emergency Helpline Directory (`Helpline`)**:
  - Direct-dial access to National Emergency (112), Women Helpline (1091), Police (100), Domestic Abuse Assistance, and Ambulance (102).

### 11. Community Danger Zones & Admin Governance
- **Crowdsourced Hazard Reporting (`BookActivity`)**:
  - Users flag poorly lit areas, harassment hotspots, or unpatrolled zones with GPS tags and descriptions.
- **Admin Approval Portal (`AdminDashboard`, `DangerZoneApprovalActivity`)**:
  - Verified administrators review and approve danger zones before broadcasting them to all users' navigation maps.

---

## 📂 Project Directory Structure

```
woshield2/
├── app/
│   ├── build.gradle                          # Dependencies, SDK targets (minSdk 24, targetSdk 34)
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml           # Permissions, services, receivers, activities
│   │   │   ├── java/com/example/woshield/
│   │   │   │   ├── core/
│   │   │   │   │   ├── WoShieldApplication.java       # Base application container
│   │   │   │   │   ├── DemoDataSeeder.java            # Multi-layer mock data generator
│   │   │   │   │   └── EmergencyHelper.java           # Central SOS dispatch engine
│   │   │   │   ├── intelligence/
│   │   │   │   │   ├── SafetyIntelligenceEngine.java  # Deterministic decision rules
│   │   │   │   │   ├── SafetyContext.java             # Telemetry & state wrapper
│   │   │   │   │   ├── SafetyDecision.java            # Explainable outcome model
│   │   │   │   │   ├── SafetyRuleConfig.java          # Thresholds & grace periods
│   │   │   │   │   └── SafetySignal.java              # Sensor signal descriptor
│   │   │   │   ├── voice/
│   │   │   │   │   ├── SurakshaService.java           # Foreground voice recognition
│   │   │   │   │   ├── SurakshaVoiceAI.java           # 4-tier threat taxonomy classifier
│   │   │   │   │   ├── AudioThreatDetector.java       # Audio amplitude & pattern monitor
│   │   │   │   │   └── BackgroundVoiceService.java    # Voice service stub
│   │   │   │   ├── hardware/
│   │   │   │   │   ├── PowerButtonService.java        # Triple-click screen-off detector
│   │   │   │   │   ├── VideoRecordingService.java     # Automated background video recorder
│   │   │   │   │   ├── ShakeDetector.java             # Accelerometer shake listener
│   │   │   │   │   ├── BuzzerTimerService.java        # Countdown alarm foreground service
│   │   │   │   │   └── BuzzerAlarmReceiver.java       # Exact alarm escalation receiver
│   │   │   │   ├── circle/
│   │   │   │   │   ├── FamilyCircleRepository.java    # Circle invitations & ward telemetry
│   │   │   │   │   ├── FamilyAlertRepository.java     # Realtime emergency alert pipeline
│   │   │   │   │   ├── FamilyDashboardActivity.java   # Guardian overview screen
│   │   │   │   │   ├── FamilyDashboardAdapter.java    # Ward card recyclerview adapter
│   │   │   │   │   ├── FamilyMember.java              # Ward model
│   │   │   │   │   ├── CircleInvitation.java          # Invitation model
│   │   │   │   │   └── CirclePermissions.java         # Granular permission matrix
│   │   │   │   ├── checkin/
│   │   │   │   │   ├── SmartCheckInActivity.java      # Check-in interface & timers
│   │   │   │   │   ├── CheckInRepository.java         # Atomic check-in state manager
│   │   │   │   │   ├── CheckInScheduler.java          # AlarmManager scheduler
│   │   │   │   │   ├── CheckInDeadlineReceiver.java   # Timeout escalation worker
│   │   │   │   │   └── CheckIn.java                   # Check-in state model
│   │   │   │   ├── privacy/
│   │   │   │   │   ├── PrivacyCenterActivity.java     # Data sovereignty dashboard
│   │   │   │   │   ├── PrivacyRepository.java         # Sessions, logs & retention
│   │   │   │   │   ├── SharingSession.java            # TTL sharing session model
│   │   │   │   │   └── PrivacyActivityLog.java        # Privacy audit log model
│   │   │   │   ├── ai/
│   │   │   │   │   ├── SambaNovaService.java          # Retrofit client for Llama 3.1
│   │   │   │   │   ├── SambaNovaApiService.java       # REST endpoints
│   │   │   │   │   ├── SambaNovaRequest.java          # Payload model
│   │   │   │   │   ├── SambaNovaResponse.java         # Response model
│   │   │   │   │   └── ChatBotActivity.java           # Chat companion UI
│   │   │   │   ├── maps/
│   │   │   │   │   ├── MapsActivity.java              # Google Maps corridor & danger zone view
│   │   │   │   │   ├── SafeStopsActivity.java         # Safe haven discovery
│   │   │   │   │   ├── GeofenceBroadcastReceiver.java # Perimeter entry/exit detector
│   │   │   │   │   └── Nearby_Safe.java               # Categorized emergency places
│   │   │   │   ├── routines/
│   │   │   │   │   ├── RoutineActivity.java           # Daily routine list
│   │   │   │   │   ├── RoutineFormActivity.java       # Create/edit routine form
│   │   │   │   │   ├── RoutineRepository.java         # Routine persistence
│   │   │   │   │   └── RoutineScheduler.java          # Routine reminder worker
│   │   │   │   └── ui/
│   │   │   │       ├── HomePage.java                  # Main safety command center
│   │   │   │       ├── SplashActivity.java            # Animated startup gateway
│   │   │   │       ├── LoginActivity.java             # Auth (Email, Google, FB)
│   │   │   │       ├── SignupActivity.java            # Account registration
│   │   │   │       ├── FakeCallActivity.java          # Realistic fake incoming call
│   │   │   │       ├── BuzzerActivity.java            # Manual siren & countdown timer
│   │   │   │       ├── AdminDashboard.java            # Admin governance hub
│   │   │   │       └── DangerZoneApprovalActivity.java# Danger zone verification
│   │   │   ├── res/
│   │   │   │   ├── layout/                            # 35+ Material XML layout definitions
│   │   │   │   ├── drawable/                          # Vector graphics, gradients & icons
│   │   │   │   ├── raw/siren.wav                      # Hardware emergency siren audio
│   │   │   │   └── values/                            # Colors, strings, themes & styles
│   └── proguard-rules.pro
├── build.gradle                                       # Top-level Gradle configuration
├── settings.gradle                                    # Project settings & repositories
└── README.md                                          # Master documentation
```

---

## 💻 Technology Stack & Dependencies

| Layer | Technology | Details |
|---|---|---|
| **Platform** | Android Native | Java 8 (`minSdk 24`, `compileSdk 34`, `targetSdk 34`) |
| **Design System** | Google Material Design 3 | MaterialCardView, BottomNavigationView, ExtendedFAB, ConstraintLayout |
| **Authentication** | Firebase Auth | Email/Password, Google Sign-In (`androidx.credentials`), Facebook SDK (`16.1.3`) |
| **Realtime Database** | Firebase Realtime DB | Offline persistence enabled, atomic transactions for status transitions |
| **Cloud Storage** | Firebase Storage | Automated emergency video uploads (`evidence/*.mp4`) |
| **Media Vault** | Cloudinary Android SDK | Encrypted multi-part audio upload with secure URL dispatch |
| **AI LLM Engine** | SambaNova Cloud API | `Meta-Llama-3.1-8B-Instruct` / `Meta-Llama-3.1-70B-Instruct` |
| **HTTP & REST** | Retrofit 2 + OkHttp 3 | Gson serialization, logging interceptors, 60s timeout handling |
| **Mapping & Location**| Google Play Services | Google Maps SDK (`18.2.0`), FusedLocationProviderClient (`21.2.0`), Places (`3.3.0`) |
| **Image Loading** | Bumptech Glide | Image caching, circular avatars, fallback placeholders (`4.12.0`) |
| **Telephony** | Android SMS Manager | Multi-recipient SOS dispatch with fallback to offline SMS |

---

## 🗄️ Database Schema & Data Models

Firebase Realtime Database is structured into indexed nodes:

```json
{
  "Users": {
    "{uid}": {
      "name": "Ananya Sharma",
      "email": "ananya.sharma@woshield.org",
      "role": "user",
      "profilePhotoUrl": "https://...",
      "checkIns": {
        "{checkInId}": {
          "status": "pending | confirmed_safe | missed | sos_triggered",
          "deadlineAt": 1729532400000,
          "routineTitle": "Evening Commute Home",
          "latitude": 18.46225,
          "longitude": 73.864972
        }
      },
      "routines": {
        "{routineId}": {
          "title": "College to Home",
          "startLocation": "College Campus",
          "destination": "Greenfield Residency",
          "departureTime": "18:00",
          "gracePeriodMinutes": 15
        }
      },
      "sharing_sessions": {
        "{sessionId}": {
          "granteeUid": "{guardianUid}",
          "active": true,
          "expiresAt": 1729539600000
        }
      },
      "privacy_logs": {
        "{logId}": {
          "eventType": "SESSION_REVOKED",
          "timestamp": 1729525200000,
          "severity": "NORMAL"
        }
      }
    }
  },
  "alerts": {
    "{uid}": {
      "{alertId}": {
        "timestamp": "1729525200000",
        "type": "Manual SOS with Audio Evidence",
        "latitude": 18.46225,
        "longitude": 73.864972,
        "audioUrl": "https://res.cloudinary.com/.../audio.m4a"
      }
    }
  },
  "DangerZones": {
    "{zoneId}": {
      "areaName": "Katraj Ghat Bypass",
      "latitude": 18.4485,
      "longitude": 73.8588,
      "radius": 400,
      "status": "approved | pending",
      "description": "Poor lighting and deserted stretch post 9 PM"
    }
  },
  "circle_members": {
    "{ownerUid}": {
      "{guardianUid}": {
        "relationship": "Father",
        "safetyStatus": "safe",
        "permissions": {
          "canViewLocation": true,
          "canReceiveSos": true
        }
      }
    }
  },
  "LiveLocations": {
    "{uid}": {
      "latitude": 18.46225,
      "longitude": 73.864972,
      "batteryLevel": 88,
      "speedKmph": 24.5,
      "lastUpdated": 1729525200000
    }
  }
}
```

---

## ⚙️ Setup & Installation Guide

### Prerequisites
- **Android Studio**: Iguana (2023.2.1), Jellyfish, or Koala
- **JDK**: Java Development Kit 17 (recommended) or Java 8+
- **Android SDK**: API level 34 build tools
- **Physical Device**: Required for testing microphone background services, power button taps, and SMS dispatch

### Step-by-Step Configuration

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/DhangarRohit18/woshield.git
   cd woshield
   ```

2. **Configure Firebase**:
   - Create a project on the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android App with package name: `com.example.woshield`.
   - Download `google-services.json` and place it in the `app/` directory:
     ```
     app/google-services.json
     ```
   - Enable **Firebase Authentication** (Email/Password, Google).
   - Enable **Firebase Realtime Database** in test/production mode.
   - Enable **Firebase Cloud Storage** for emergency evidence.

3. **Configure Google Maps API Key**:
   - Generate an API Key in the [Google Cloud Console](https://console.cloud.google.com/) with **Maps SDK for Android** enabled.
   - Update `app/src/main/AndroidManifest.xml`:
     ```xml
     <meta-data
         android:name="com.google.android.geo.API_KEY"
         android:value="YOUR_GOOGLE_MAPS_API_KEY" />
     ```

4. **Configure Cloudinary (Audio Evidence)**:
   - Provide your Cloudinary credentials in `CloudinaryHelper.java`:
     ```java
     config.put("cloud_name", "your_cloud_name");
     config.put("api_key", "your_api_key");
     config.put("api_secret", "your_api_secret");
     ```

5. **Configure SambaNova AI API Key**:
   - Provide your key in `SambaNovaService.java`:
     ```java
     private static final String API_KEY = "your_sambanova_api_key";
     ```

6. **Build & Run**:
   - In Android Studio, click **Sync Project with Gradle Files**.
   - Select your target device and click **Run (Shift + F10)**.

---

## 🔐 Hardware & System Permissions

WoShield declares the following permissions in `AndroidManifest.xml`:

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Continuous speech recognition in `SurakshaService` and high-fidelity audio evidence capture |
| `ACCESS_FINE_LOCATION` | Pinpoint GPS coordinates for route corridor evaluation and emergency SOS dispatch |
| `ACCESS_BACKGROUND_LOCATION` | Background journey tracking and geofence boundary monitoring |
| `CAMERA` | 10-second front/back evidence video recording via `VideoRecordingService` |
| `SEND_SMS` | Dispatches emergency distress text messages to configured contacts |
| `READ_CONTACTS` | Allows one-tap selection of trusted contacts from the phonebook |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ compliant foreground service for acoustic monitoring |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ emergency safety buzzer & siren countdown timer |
| `WAKE_LOCK` | Guarantees siren audio and countdown execution even when device is locked/sleeping |
| `SCHEDULE_EXACT_ALARM` | Accurate dispatch of check-in deadlines and routine reminders |

---

## ⚡ Demo Mode & Live Simulation

To facilitate testing without a full production backend, WoShield includes an authoritative **`DemoDataSeeder`** anchored to real-world Pune coordinates (`18°27'44.1"N, 73°51'53.9"E`):

### Available Interactive Simulations

| Feature | How to Trigger in App | Expected Result |
|---|---|---|
| **Voice Threat Detection** | Turn on *Voice Shield* switch on HomePage; speak *"Help me"* or *"Danger"* | Immediate distress notification, audio evidence recording, and emergency SMS broadcast |
| **Quick SOS** | Tap the central red SOS button once | Acquires GPS coordinates and immediately broadcasts emergency alerts to contacts |
| **Evidence SOS** | Hold the red SOS button for 2 seconds | Pulses red, records audio, uploads to Cloudinary, and attaches link to alert |
| **Triple-Click Power SOS** | Quickly press the physical Power Button 3 times | Triggers `PowerButtonService` → records 10s video evidence → uploads to Firebase Storage |
| **Siren Buzzer Countdown** | Navigate to *Buzzer*, set a 30-second timer, let it reach zero | Screaming siren plays via `MediaPlayer`, screen wakes up, emergency contacts notified |
| **Discreet Escape** | Navigate to *Fake Call* | Incoming full-screen phone call ring screen with custom caller name |
| **Guardian Dashboard** | Tap *Circle* in bottom navigation | View simulated ward family members with live battery levels, addresses, and status badges |
| **AI Companion** | Tap *AI Chatbot* | Chat with Llama 3.1 for situational guidance and legal rights |

---

## 🤝 Contributing Guidelines

Contributions from developers and safety advocates are welcome!

1. Fork the Project (`https://github.com/DhangarRohit18/woshield.git`).
2. Create your Feature Branch:
   ```bash
   git checkout -b feature/SafetyInnovation
   ```
3. Commit your Changes with descriptive messages:
   ```bash
   git commit -m 'feat: Add Bluetooth mesh offline distress signaling'
   ```
4. Push to the Branch:
   ```bash
   git push origin feature/SafetyInnovation
   ```
5. Open a Pull Request for review.

---

## 📄 License

This project is open-source and licensed under the **MIT License**. See the [LICENSE](LICENSE) file for more details.

---

<p align="center">
  <strong>WoShield — Because Every Woman Deserves Absolute Safety and Complete Control.</strong>
</p>