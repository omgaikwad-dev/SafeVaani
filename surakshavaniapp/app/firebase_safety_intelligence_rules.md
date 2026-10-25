# Firebase AI Safety Intelligence Security Rules & Architecture

## 1. Executive Summary

This specification enforces the data access controls, privacy guarantees, and serverless background execution model for **Woshield Step 7: AI Safety Intelligence**.

The intelligence engine evaluates routine status, GPS corridor deviation, and emergency escalation without claiming danger where evidence is uncertain. Decision records are scoped strictly to the authenticated user and scrubbed of raw GPS trails or audio artifacts.

---

## 2. Firebase Realtime Database Security Rules

Add the following rule fragment under `/Users/{$uid}/` to govern safety decisions:

```json
{
  "rules": {
    "Users": {
      "$uid": {
        "safety_decisions": {
          // Only the profile owner can read their decision logs
          ".read": "auth != null && auth.uid === $uid",

          "$decisionId": {
            // Only the owner or a trusted backend service can write a decision
            ".write": "auth != null && auth.uid === $uid",
            ".validate": "newData.hasChildren(['decisionId', 'ownerUid', 'outcome', 'triggeringRule', 'timestamp']) && newData.child('ownerUid').val() === $uid"
          }
        }
      }
    }
  }
}
```

### Key Security Safeguards:
1. **Owner-Only Read Access**: A family member or contact cannot directly inspect private safety decision evaluations without explicit authorization.
2. **Strict UID Binding**: Writing to `safety_decisions` requires `auth.uid === $uid` and `ownerUid === $uid` to prevent cross-account injection.
3. **No Raw Coordinates Stored**: `SafetyDecision` records store categorical metric distances (e.g., `distanceFromRouteMeters: 320.0`), not continuous GPS coordinate streams.

---

## 3. Serverless Background Integration (Cloud Functions)

To prevent client battery drain and provide continuous monitoring during deep sleep, the AI Safety Intelligence rules can run as an authoritative Cloud Function:

### 1. `evaluateJourneyCorridorOnLocationUpdate` (Database Trigger)
- **Trigger**: `ref('/LiveLocations/{uid}').onUpdate()`
- **Logic**:
  - Compares new location against active routine route corridor stored in `/Users/{uid}/routines`.
  - Applies `SafetyRuleConfig` (accuracy filter, sustained sample check).
  - Writes authoritative `SafetyDecision` to `/Users/{uid}/safety_decisions`.
  - If `OUTCOME_SAFETY_CHECK_RECOMMENDED`, invokes `CheckInRepository` logic server-side and sends FCM push notification.

### 2. `evaluateJourneyDelayScheduled` (Cron Job)
- **Schedule**: Every 5 minutes (`schedule('*/5 * * * *')`)
- **Logic**:
  - Checks in-transit users whose `expectedArrivalMillis + gracePeriod < now()`.
  - Deduplicates against existing unresolved decisions within the 15-minute window.
  - Automatically dispatches Smart Check-In.

---

## 4. Model-Based Extension Assessment

- **Finding**: No trained or validated TensorFlow Lite / ONNX models exist in the repository.
- **Decision**: The intelligence engine remains strictly transparent, deterministic, and rule-based.
- **Justification**: Deploying arbitrary heuristics as "AI risk predictions" violates safety-critical engineering ethics and IEEE Ideathon guidelines. Real-time deterministic logic provides 100% auditability and eliminates dangerous false-negative hallucinations.
