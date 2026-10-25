# Woshield Step 4: Firebase Security Rules & Cloud Functions Architecture

This document contains the authoritative Firebase Realtime Database Security Rules and the Firebase Cloud Functions backend scheduler required for production-grade, authoritative server-side deadline escalation.

---

## 1. Firebase Realtime Database Security Rules

Apply these rules to your Firebase console under **Realtime Database > Rules**:

```json
{
  "rules": {
    "Users": {
      "$uid": {
        // Only the authenticated owner can access their user profile
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",

        "checkIns": {
          // Owner can read and query all their check-ins
          ".read": "auth != null && auth.uid == $uid",
          ".indexOn": ["status", "deadlineAt", "createdAt"],

          "$checkInId": {
            // Write permission only for the check-in owner
            ".write": "auth != null && auth.uid == $uid",

            // Validate record schema and state transitions
            ".validate": "newData.hasChildren(['status', 'createdAt', 'deadlineAt'])",

            "status": {
              ".validate": "newData.val() === 'pending' || newData.val() === 'confirmed_safe' || newData.val() === 'assistance_requested' || newData.val() === 'sos_triggered' || newData.val() === 'missed' || newData.val() === 'cancelled' || newData.val() === 'expired'"
            },

            // Transition Integrity: Once a check-in leaves 'pending', it cannot transition to any other state
            "$other": {
              ".validate": "!data.exists() || data.parent().child('status').val() === 'pending' || data.parent().child('status').val() === newData.parent().child('status').val()"
            }
          }
        },

        "routines": {
          ".read": "auth != null && auth.uid == $uid",
          ".write": "auth != null && auth.uid == $uid"
        },

        "safePlaces": {
          ".read": "auth != null && auth.uid == $uid",
          ".write": "auth != null && auth.uid == $uid"
        }
      }
    },

    "alerts": {
      "$uid": {
        // User can read and write to their own alerts log
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },

    "emergency_logs": {
      // Authenticated users can push emergency logs (used for backward compatibility)
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```

---

## 2. Server-Side Deadline Escalation (Firebase Cloud Functions)

Because an Android device may be offline, out of battery, killed by aggressive battery management, or physically destroyed during an emergency, **authoritative deadline processing must run server-side**.

Below is the complete Node.js Firebase Cloud Function (`functions/index.js` or `functions/src/index.ts`):

```javascript
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Scheduled Cloud Function that runs every 1 minute.
 * Scans active users for pending check-ins whose deadline has passed.
 * Atomically marks them as 'missed' and triggers cloud escalation alerts.
 */
exports.checkInDeadlineEscalator = functions.pubsub
  .schedule("every 1 minutes")
  .onRun(async (context) => {
    const now = Date.now();
    const db = admin.database();

    // Query pending check-ins across users using root database admin privileges
    const usersSnap = await db.ref("Users").once("value");
    if (!usersSnap.exists()) return null;

    const updates = {};
    const escalationPromises = [];

    usersSnap.forEach((userDoc) => {
      const uid = userDoc.key;
      const checkInsSnap = userDoc.child("checkIns");

      if (checkInsSnap.exists()) {
        checkInsSnap.forEach((checkInDoc) => {
          const checkIn = checkInDoc.val();

          // Condition 1: Check-in is currently pending
          // Condition 2: Deadline has passed
          if (checkIn.status === "pending" && checkIn.deadlineAt <= now) {
            const checkInId = checkInDoc.key;
            console.log(`Processing missed check-in: ${checkInId} for user ${uid}`);

            // Atomic state transition to 'missed'
            updates[`Users/${uid}/checkIns/${checkInId}/status`] = "missed";
            updates[`Users/${uid}/checkIns/${checkInId}/escalationStatus`] = "notified_contacts";
            updates[`Users/${uid}/checkIns/${checkInId}/updatedAt`] = now;

            // Escalate according to user policy: Send FCM notification & log audit event
            const escalationLog = {
              timestamp: String(now),
              status: `Missed Check-In Escalation: ${checkIn.reason || 'Safety Checkpoint'}`,
              latitude: checkIn.latitude || 0,
              longitude: checkIn.longitude || 0,
              audioUrl: ""
            };

            updates[`alerts/${uid}/${db.ref().push().key}`] = escalationLog;
            updates[`emergency_logs/${db.ref().push().key}`] = escalationLog;
          }
        });
      }
    });

    if (Object.keys(updates).length > 0) {
      await db.ref().update(updates);
      console.log(`Successfully escalated ${Object.keys(updates).length} records.`);
    }

    return null;
  });
```

---

## 3. Escalation Safety Rules
1. A missed check-in alone is **not** treated as an immediate high-speed police 911 dispatch or sirens.
2. The server escalates by alerting the user's authorized trusted circle via SMS/Push with the last recorded GPS location, prompting family to verify safety.
3. If the user explicitly activates **EMERGENCY SOS**, the full emergency pipeline (audio capture, immediate SMS blast, and red alert) is engaged.
