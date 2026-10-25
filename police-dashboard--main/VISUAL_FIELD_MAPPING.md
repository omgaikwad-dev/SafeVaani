# 🎨 Visual Field Mapping Guide

## Quick Reference: Where Each Firebase Field Appears

This guide shows exactly where each Firebase field is displayed in the UI.

---

## 🚨 ALERT FIELDS

### Top-Level Alert Structure
```
/alerts/{alertId}
```

| Firebase Field | Type | Component | Location | Visual Style |
|---------------|------|-----------|----------|--------------|
| `confidence` | number | Dashboard | Latest Alert → AI Confidence box | Emerald card, shows "94.2%" |
| `confidence` | number | AlertsView | Alert Card → AI Confidence box | Emerald card, shows "94.2%" |
| `emotion` | string | All | Alert header, titles, labels | Bold text, primary identifier |
| `latitude` | number | All | GPS Coordinates, Maps | "18.5204" format |
| `longitude` | number | All | GPS Coordinates, Maps | "73.8567" format |
| `locationName` | string | Dashboard | Latest Alert → Location box | White text on rose background |
| `locationName` | string | AlertsView | Alert Card → Location Name box | Indigo card |
| `locationName` | string | DangerZoneView | Zone list sidebar | Small text with pin icon |
| `phone` | string | AlertsView | Alert Card → Phone section | Clickable tel: link |
| `status` | string | All | Alert badge, status indicators | "ACTIVE" or "RESOLVED" |
| `threatLevel` | string | Dashboard | Latest Alert → Threat Level box | Color-coded (rose/amber/slate) |
| `threatLevel` | string | AlertsView | Alert Card → Threat Level box | Color-coded (rose/amber/slate) |
| `timestamp` | number | All | Time displays | "2:30 PM" or full date |
| `userName` | string | All | Victim name displays | Bold, large text |

### Nested Alert Structure
```
/alerts/{userId}/{alertId}
```

| Firebase Field | Type | Component | Location | Visual Style |
|---------------|------|-----------|----------|--------------|
| `audioUrl` | string | Dashboard | Latest Alert → Evidence button | White button with play icon |
| `audioUrl` | string | AlertsView | Alert Card → Audio Evidence button | Indigo button with play icon |
| `audioUrl` | string | MembersView | History → Play Evidence button | Small indigo button |
| `audioUrl` | string | RecentActivity | Event icon | Audio icon indicator |
| `emotion` | string | All | Same as top-level | Same as top-level |
| `isEmergency` | boolean | RecentActivity | Event title | 🚨 emoji if true |
| `isEmergency` | boolean | AlertsView | Alert status | Red background if true |
| `latitude` | number | All | Same as top-level | Same as top-level |
| `longitude` | number | All | Same as top-level | Same as top-level |
| `reason` | string | Dashboard | Latest Alert → Detection Reason box | White text on rose background |
| `reason` | string | AlertsView | Alert Card → Detection Reason box | Slate card |
| `reason` | string | RecentActivity | Event description | In parentheses after emotion |
| `timestamp` | number | All | Same as top-level | Same as top-level |

---

## 👥 USER FIELDS

### User Profile
```
/users/{userId}
```

| Firebase Field | Type | Component | Location | Visual Style |
|---------------|------|-----------|----------|--------------|
| `name` | string | MembersView | Profile header, user list | Large bold text |
| `displayName` | string | MembersView | Profile header (police users) | Large bold text |
| `email` | string | MembersView | Below name in profile | Medium gray text |
| `phone` | string | MembersView | Call button | Clickable button with phone icon |
| `role` | string | MembersView | Badge next to name | Small colored badge (indigo/slate) |
| `batteryLevel` | number | MembersView | User list item | Battery icon + percentage |
| `dangerZoneActive` | boolean | MembersView | User avatar, profile | Red pulsing dot if true |
| `lastLatitude` | number | MembersView | Last Known Location section | "LAT: 18.4673" |
| `lastLongitude` | number | MembersView | Last Known Location section | "LNG: 73.8368" |
| `lastUpdateTimestamp` | number | MembersView | Activity tracking | Used for sorting |
| `lastLogin` | number | MembersView | Activity tracking | Used for sorting |
| `uniqueId` | string | MembersView | Profile header top-right | Monospace font, indigo background |
| `station` | string | MembersView | Profile info (police users) | Below email |
| `timestamp` | number | MembersView | Account info | Used for creation date |

### User Contacts
```
/users/{userId}/contacts/{contactId}
```

| Firebase Field | Type | Component | Location | Visual Style |
|---------------|------|-----------|----------|--------------|
| `name` | string | MembersView | Emergency Contacts → Contact card | Bold text |
| `phone` | string | MembersView | Emergency Contacts → Contact card | Clickable link, right side |
| `relationship` | string | MembersView | Emergency Contacts → Contact card | Small gray text, capitalized |

### User History
```
/users/{userId}/history/{historyId}
```

| Firebase Field | Type | Component | Location | Visual Style |
|---------------|------|-----------|----------|--------------|
| `emotion` | string | MembersView | History card header | Bold text |
| `emotion` | string | RecentActivity | Event title | Bold text |
| `latitude` | number | MembersView | View Location button | Opens in Google Maps |
| `longitude` | number | MembersView | View Location button | Opens in Google Maps |
| `status` | string | MembersView | History card | Small gray text |
| `status` | string | RecentActivity | Event description | After user name |
| `timestamp` | number | MembersView | History card | Small gray text, right side |
| `timestamp` | number | RecentActivity | Event time | "2:30 PM" format |
| `audioUrl` | string | MembersView | Play Evidence button | Indigo button with play icon |
| `audioUrl` | string | RecentActivity | Event icon | Audio icon if exists |
| `reason` | string | MembersView | History card | Not currently displayed |
| `reason` | string | RecentActivity | Event description | In parentheses |

---

## 🎨 Color Coding Reference

### Threat Level Colors
- **High**: Rose (`bg-rose-50`, `text-rose-600`, `border-rose-100`)
- **Medium**: Amber (`bg-amber-50`, `text-amber-600`, `border-amber-100`)
- **Low**: Slate (`bg-slate-50`, `text-slate-600`, `border-slate-100`)

### Field Type Colors
- **Confidence**: Emerald (`bg-emerald-50`, `text-emerald-600`)
- **Location Name**: Indigo (`bg-indigo-50`, `text-indigo-600`)
- **Detection Reason**: Slate (`bg-slate-50`, `text-slate-600`)
- **Audio Evidence**: Indigo (`bg-indigo-600`, `text-white`)

### Status Colors
- **ACTIVE/Emergency**: Rose (`bg-rose-500`, `text-white`)
- **RESOLVED**: Slate (`bg-slate-400`, `text-white`)
- **Completed**: Indigo (`bg-indigo-100`, `text-indigo-600`)

---

## 📱 Component Layout Examples

### Dashboard - Latest Critical Alert

```
┌─────────────────────────────────────────────────────────────┐
│ 🚨 LATEST CRITICAL ALERT                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Victim: {userName}                                          │
│ Threat Type: {emotion}                                      │
│                                                             │
│ Detection Reason: {reason}              [if exists]         │
│ Location: {locationName}                [if exists]         │
│                                                             │
│ ┌─────────────────┬─────────────────┐                      │
│ │ AI Confidence   │ Threat Level    │                      │
│ │ {confidence}%   │ {threatLevel}   │  [if exist]          │
│ └─────────────────┴─────────────────┘                      │
│                                                             │
│ ┌─────────────────┬─────────────────┐                      │
│ │ GPS Coordinates │ Time            │                      │
│ │ {lat}, {lng}    │ {timestamp}     │                      │
│ └─────────────────┴─────────────────┘                      │
│                                                             │
│ [Navigate Now]  [Evidence]              [if audioUrl]      │
└─────────────────────────────────────────────────────────────┘
```

### AlertsView - Alert Card

```
┌─────────────────────────────────────────────────────────────┐
│ 🚨 EMERGENCY ALERT                                          │
│ Victim: {userName}                                          │
│ {emotion}                                                   │
│                                     [ACTIVE]                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Detection Reason                        [slate card]        │
│ {reason}                                                    │
│                                                             │
│ Location Name                           [indigo card]       │
│ {locationName}                                              │
│                                                             │
│ ┌─────────────────┬─────────────────┐                      │
│ │ AI Confidence   │ Threat Level    │                      │
│ │ [emerald card]  │ [color-coded]   │                      │
│ │ {confidence}%   │ {threatLevel}   │                      │
│ └─────────────────┴─────────────────┘                      │
│                                                             │
│ 📍 GPS: {latitude}, {longitude}                            │
│ 📞 {phone}                                                  │
│ 🕐 {timestamp}                                              │
│                                                             │
│ [Get Directions]  [View on Map]                            │
│ [Play Audio Evidence]                   [if audioUrl]      │
│                                                             │
│ ┌─────────────────────────────────┐                        │
│ │         [Map Display]           │                        │
│ │                                 │                        │
│ └─────────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### MembersView - User Profile

```
┌─────────────────────────────────────────────────────────────┐
│ [Avatar]  {name}                        Unique ID           │
│           {email}                       {uniqueId}          │
│           [Call {phone}] [Danger Zone]  [if active]         │
├─────────────────────────────────────────────────────────────┤
│ Incident History        │  Emergency Contacts               │
│                         │                                   │
│ ┌─────────────────────┐ │  ┌──────────────────────────┐    │
│ │ {emotion}           │ │  │ {contact.name}           │    │
│ │ {status}            │ │  │ {contact.relationship}   │    │
│ │ {timestamp}         │ │  │ {contact.phone}          │    │
│ │ [View Location]     │ │  └──────────────────────────┘    │
│ │ [Play Evidence]     │ │                                   │
│ └─────────────────────┘ │  Last Known Location              │
│                         │  ┌──────────────────────────┐    │
│                         │  │     [Map Pin Icon]       │    │
│                         │  │ LAT: {lastLatitude}      │    │
│                         │  │ LNG: {lastLongitude}     │    │
│                         │  └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### RecentActivity - Event Feed

```
┌─────────────────────────────────────────────────────────────┐
│ Recent Events                                               │
├─────────────────────────────────────────────────────────────┤
│ [🚨] 🚨 Emergency Alert                          2:30 PM    │
│      {userName} - {emotion} ({reason})                      │
│                                                             │
│ [📍] Alert Triggered                             2:25 PM    │
│      Citizen - {emotion}                                    │
│                                                             │
│ [🔊] {emotion}                                   2:20 PM    │
│      {user.name} - {status} ({reason})                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔍 Field Presence Logic

### Always Required
These fields MUST exist for the component to work:
- `emotion` - Primary identifier for all alerts
- `latitude` - Required for map display
- `longitude` - Required for map display
- `timestamp` - Required for sorting and time display

### Conditionally Displayed
These fields are shown only if they exist in Firebase:
- `confidence` - Shows AI confidence box
- `threatLevel` - Shows threat level badge
- `locationName` - Shows location name card
- `reason` - Shows detection reason box
- `audioUrl` - Shows audio evidence button
- `isEmergency` - Changes visual style (🚨 emoji, red background)
- `phone` - Shows phone number link
- `userName` - Shows victim name (fallback to "Citizen")

### Optional with Fallbacks
These fields have default values if missing:
- `status` - Defaults to "ACTIVE" if `isEmergency` is true
- `userName` - Defaults to "Citizen in Distress"
- `batteryLevel` - Defaults to -1 (shows "N/A")
- `lastLatitude/lastLongitude` - Defaults to 0 (shows "0.0000")

---

## 📊 Data Flow Diagram

```
Firebase Realtime Database
         │
         ├─ /alerts
         │    ├─ {alertId} (Top-level)
         │    │    ├─ confidence ──────────┐
         │    │    ├─ emotion ─────────────┤
         │    │    ├─ latitude ────────────┤
         │    │    ├─ longitude ───────────┤
         │    │    ├─ locationName ────────┤
         │    │    ├─ phone ───────────────┤
         │    │    ├─ status ──────────────┤
         │    │    ├─ threatLevel ─────────┤
         │    │    ├─ timestamp ───────────┤
         │    │    └─ userName ────────────┤
         │    │                            │
         │    └─ {userId} (Nested)         │
         │         └─ {alertId}            │
         │              ├─ audioUrl ───────┤
         │              ├─ emotion ────────┤
         │              ├─ isEmergency ────┤
         │              ├─ latitude ───────┤
         │              ├─ longitude ──────┤
         │              ├─ reason ─────────┤
         │              └─ timestamp ──────┤
         │                                 │
         └─ /users                         │
              └─ {userId}                  │
                   ├─ Profile Fields ──────┤
                   ├─ /contacts ───────────┤
                   └─ /history ────────────┤
                                           │
                                           ▼
                                    App.tsx (Firebase Listeners)
                                           │
                                           ▼
                                    DashboardData State
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
                    ▼                      ▼                      ▼
              Dashboard.tsx         AlertsView.tsx        MembersView.tsx
                    │                      │                      │
                    ├─ SafetyRadar        ├─ Alert Cards         ├─ User Profiles
                    └─ RecentActivity     └─ Map Display         ├─ Contacts
                                                                  └─ History
```

---

*Last Updated: 2026-02-11*
*Version: 1.0.0*
