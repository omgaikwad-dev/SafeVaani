# Firebase Database Field Mapping Documentation

## Overview
This document provides a comprehensive mapping of all Firebase Realtime Database fields used in the Police Dashboard application. It ensures that no field is missing and all data is correctly processed.

---

## 🔴 ALERTS Structure

### Top-Level Alerts
**Path:** `/alerts/{alertId}`

Example: `/alerts/-OkUnen8V8Cs5qMkkRUk`

| Field | Type | Description | Required | Example |
|-------|------|-------------|----------|---------|
| `confidence` | number | AI detection confidence percentage | Optional | `94.2` |
| `emotion` | string | Detected emotion/threat type | **Required** | `"PANIC"` |
| `latitude` | number | GPS latitude coordinate | **Required** | `18.5204` |
| `longitude` | number | GPS longitude coordinate | **Required** | `73.8567` |
| `locationName` | string | Human-readable location | Optional | `"Senapati Bapat Road, Near ICC Trade Tower"` |
| `phone` | string | Victim's phone number | Optional | `"+91 98765 43210"` |
| `status` | string | Alert status | Optional | `"ACTIVE"` or `"RESOLVED"` |
| `threatLevel` | string | Threat severity level | Optional | `"High"`, `"Medium"`, `"Low"` |
| `timestamp` | number | Unix timestamp in milliseconds | **Required** | `1770059864899` |
| `userName` | string | Victim's name | Optional | `"Priya Sharma"` |

### Nested Alerts (User-Specific)
**Path:** `/alerts/{userId}/{alertId}`

Example: `/alerts/9nlFlZT18kcOBB0ZXbOWLUFGEG02/-Okbe7mJbFtSFV8EDO6w`

| Field | Type | Description | Required | Example |
|-------|------|-------------|----------|---------|
| `audioUrl` | string | Cloudinary URL for audio evidence | Optional | `"https://res.cloudinary.com/..."` |
| `emotion` | string | Detected emotion/threat type | **Required** | `"disgust"`, `"Voice Detection [PANIC]: High-Level Threat"` |
| `isEmergency` | boolean | Emergency flag | Optional | `true` or `false` |
| `latitude` | number | GPS latitude coordinate | **Required** | `18.4672991` |
| `longitude` | number | GPS longitude coordinate | **Required** | `73.8368202` |
| `reason` | string | AI detection reason | Optional | `"AI Detected: disgust"` |
| `timestamp` | number | Unix timestamp in milliseconds | **Required** | `1770191883514` |
| `phone` | string | Victim's phone number | Optional | `"+91 98765 43210"` |
| `userName` | string | Victim's name | Optional | `"Priya Sharma"` |
| `status` | string | Alert status | Optional | `"ACTIVE"` or `"RESOLVED"` |

### Emotion/Threat Types Detected
- `"disgust"`, `"fear"`, `"sad"`, `"anger"`
- `"Voice Detection [PANIC]: High-Level Threat / Physical Assault"`
- `"Voice Detection [HARASSMENT]: Sexual Harassment / Verbal Abuse"`
- `"Voice Detection [DOMESTIC]: Domestic Violence / Internal Conflict"`
- `"Voice Detection [MEDICAL]: Medical Emergency / Health Crisis"`
- `"Voice Detection [ROBBERY]: Theft / Mugging / Armed Robbery"`
- `"Voice Detection [ANGER]: Verbal Altercation / Confrontation"`
- `"3x Help Keywords"` or `"3x Help Keywords Detected"`
- `"Voice SOS"`
- `"Distress (Auto-Fallback)"` or `"Manual SOS (Fallback)"`

---

## 👥 USERS Structure

**Path:** `/users/{userId}`

### User Profile Fields

| Field | Type | Description | Required | Example |
|-------|------|-------------|----------|---------|
| `name` | string | User's full name | Optional | `"Rohit Dhangar"` |
| `displayName` | string | Display name (for police users) | Optional | `"Head Officer"` |
| `email` | string | User's email address | **Required** | `"rohit2006@gmail.com"` |
| `phone` | string | User's phone number | **Required** | `"9975819201"` |
| `role` | string | User role | Optional | `"admin"`, `"member"`, `"police"`, `"parent"` |
| `batteryLevel` | number | Device battery percentage | Optional | `85` or `-1` (unknown) |
| `dangerZoneActive` | boolean | Danger zone activation status | Optional | `true` or `false` |
| `lastLatitude` | number | Last known GPS latitude | Optional | `18.4672991` |
| `lastLongitude` | number | Last known GPS longitude | Optional | `73.8368202` |
| `lastUpdateTimestamp` | number | Last location update timestamp | Optional | `1770191883514` |
| `lastLogin` | number | Last login timestamp | Optional | `1770066129523` |
| `uniqueId` | string | Unique user identifier | Optional | `"HH39K3"` |
| `station` | string | Police station (for police users) | Optional | `"Sentinel Central Command"` |
| `timestamp` | number | User creation timestamp | Optional | `1770107362087` |

### Contacts Sub-Collection
**Path:** `/users/{userId}/contacts/{contactId}`

| Field | Type | Description | Required | Example |
|-------|------|-------------|----------|---------|
| `name` | string | Contact's name | **Required** | `"Atharva Raut"` |
| `phone` | string | Contact's phone number | **Required** | `"8591443012"` |
| `relationship` | string | Relationship to user | **Required** | `"Friend From Mumbai"` |

### History Sub-Collection
**Path:** `/users/{userId}/history/{historyId}`

| Field | Type | Description | Required | Example |
|-------|------|-------------|----------|---------|
| `emotion` | string | Detected emotion/event type | **Required** | `"Voice SOS"`, `"sad"` |
| `latitude` | number | GPS latitude coordinate | **Required** | `18.4672991` |
| `longitude` | number | GPS longitude coordinate | **Required** | `73.8368202` |
| `status` | string | Event status | **Required** | `"Evidence Secured"`, `"Recording Completed"` |
| `timestamp` | number | Unix timestamp in milliseconds | **Required** | `1770191883514` |
| `audioUrl` | string | Cloudinary URL for audio evidence | Optional | `"https://res.cloudinary.com/..."` |
| `reason` | string | Detection reason | Optional | `"AI Detected: Voice Detection [PANIC]"` |

---

## 🎯 Data Processing Logic

### Alert Processing
The application handles **two alert structures**:

1. **Flat Structure** (Top-Level Alerts):
   ```typescript
   alerts: {
     "-OkUnen8V8Cs5qMkkRUk": {
       emotion: "PANIC",
       latitude: 18.5204,
       // ... other fields
     }
   }
   ```

2. **Nested Structure** (User-Specific Alerts):
   ```typescript
   alerts: {
     "9nlFlZT18kcOBB0ZXbOWLUFGEG02": {
       "-Okbe7mJbFtSFV8EDO6w": {
         emotion: "disgust",
         isEmergency: true,
         // ... other fields
       }
     }
   }
   ```

### Processing Steps
1. **Iterate through all alerts** in `/alerts`
2. **Check if alert has `emotion` field** → Top-level alert
3. **If no `emotion` field** → Nested structure, iterate through nested alerts
4. **Extract all fields** including optional ones
5. **Display in UI** with appropriate formatting

### User Data Processing
1. **Load all users** from `/users`
2. **Extract contacts** from `/users/{userId}/contacts`
3. **Extract history** from `/users/{userId}/history`
4. **Match alerts to users** by phone number or location proximity
5. **Display user profiles** with all available data

### Active Alerts Detection Logic

The application uses **intelligent logic** to determine if an alert is active:

#### Classification Rules:
An alert is considered **ACTIVE** if ANY of the following conditions are true:

1. **Explicit ACTIVE Status**: `status === "ACTIVE"`
   - Example: Top-level alert with `"status": "ACTIVE"`
   
2. **Emergency Flag Set**: `isEmergency === true`
   - Example: Nested alert with `"isEmergency": true`
   
3. **Default Active State**: No `status` field AND no `isEmergency` field
   - Treats alerts as active by default if not explicitly marked
   - This handles alerts that don't have status tracking

#### Exclusion Rule:
An alert is **NEVER** considered active if:
- `status === "RESOLVED"` (explicitly resolved)

#### Implementation:
```typescript
const isActive = alert.status === 'ACTIVE' || 
                alert.isEmergency === true || 
                (!alert.status && !alert.hasOwnProperty('isEmergency'));

const isActiveAlert = isActive && alert.status !== 'RESOLVED';
```

#### Examples from Your Firebase Data:

**✅ ACTIVE Alert Examples:**
```json
// Example 1: Top-level with ACTIVE status
{
  "status": "ACTIVE",
  "emotion": "PANIC",
  "threatLevel": "High"
}
// Result: ACTIVE (status === "ACTIVE")

// Example 2: Nested with isEmergency flag
{
  "isEmergency": true,
  "emotion": "disgust",
  "reason": "AI Detected: disgust"
}
// Result: ACTIVE (isEmergency === true)

// Example 3: No status field (default active)
{
  "emotion": "fear",
  "latitude": 18.4672991,
  "longitude": 73.8368202
}
// Result: ACTIVE (no status, no isEmergency → default active)
```

**❌ INACTIVE Alert Examples:**
```json
// Example 1: Explicitly resolved
{
  "status": "RESOLVED",
  "emotion": "PANIC"
}
// Result: INACTIVE (status === "RESOLVED")

// Example 2: isEmergency false and no status
{
  "isEmergency": false,
  "emotion": "sad"
}
// Result: INACTIVE (isEmergency === false, no ACTIVE status)
```

#### Where This Logic Is Used:
1. **App.tsx** - `getActiveAlertCount()` for badge count
2. **Dashboard.tsx** - `latestCriticalAlert` and `activeAlerts` filtering
3. **AlertsView.tsx** - `activeAlerts` filtering for display
4. **Alert Cards** - Visual styling (red for active, gray for inactive)

---

## ✅ Field Validation Checklist

### Alerts
- [x] `confidence` - Displayed in alert details
- [x] `emotion` - Primary alert identifier
- [x] `latitude` - Used for map display
- [x] `longitude` - Used for map display
- [x] `locationName` - Shown in alert cards
- [x] `phone` - Used for victim identification
- [x] `status` - Determines alert state (ACTIVE/RESOLVED)
- [x] `threatLevel` - Color-coded display
- [x] `timestamp` - Sorting and time display
- [x] `userName` - Victim name display
- [x] `audioUrl` - Audio evidence playback
- [x] `isEmergency` - Emergency flag
- [x] `reason` - AI detection reason

### Users
- [x] `name` - User profile display
- [x] `displayName` - Police user display
- [x] `email` - User identification
- [x] `phone` - Contact information
- [x] `role` - Access control
- [x] `batteryLevel` - Device monitoring
- [x] `dangerZoneActive` - Safety status
- [x] `lastLatitude` - Location tracking
- [x] `lastLongitude` - Location tracking
- [x] `lastUpdateTimestamp` - Activity tracking
- [x] `lastLogin` - Session tracking
- [x] `uniqueId` - User identification
- [x] `station` - Police station info
- [x] `timestamp` - Account creation
- [x] `contacts` - Emergency contacts
- [x] `history` - Incident history

---

## 🔧 Component Field Usage

### Dashboard.tsx
- Uses: `emotion`, `latitude`, `longitude`, `timestamp`, `userName`, `reason`, `audioUrl`, `isEmergency`, `status`
- Displays: Latest critical alert, stats, threat radar, recent activity

### AlertsView.tsx
- Uses: All alert fields
- Displays: Complete alert details with maps, audio evidence, navigation

### MembersView.tsx
- Uses: All user fields, contacts, history
- Displays: User profiles, emergency contacts, incident history

### DangerZoneView.tsx
- Uses: `emotion`, `latitude`, `longitude`, `timestamp`, `userName`, `locationName`
- Displays: Interactive map with danger zones

### SafetyRadar.tsx
- Uses: `emotion`, `timestamp`
- Displays: Threat categorization radar chart

### RecentActivity.tsx
- Uses: All alert and history fields
- Displays: Chronological event feed

---

## 📊 Statistics

- **Total Alert Fields:** 13
- **Total User Fields:** 15
- **Total Contact Fields:** 3
- **Total History Fields:** 7
- **Total Emotion Types:** 15+

---

## 🚀 Implementation Status

✅ **All fields are correctly mapped and processed**
✅ **Both alert structures (flat and nested) are handled**
✅ **All optional fields have fallback handling**
✅ **Type safety with TypeScript interfaces**
✅ **Real-time Firebase listeners active**
✅ **No missing fields in the database structure**

---

*Last Updated: 2026-02-11*
*Version: 1.0.0*
