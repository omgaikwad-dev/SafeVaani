# Firebase Privacy, Sharing Sessions, and Security Rules

## 1. Executive Summary

This specification enforces the privacy, granular permission, and temporary sharing session constraints defined in **Woshield Step 6: Privacy & Security Center**. 

It eliminates client-only security assumptions and provides authoritative Firebase Realtime Database Security Rules that ensure:
1. **Owner-Only Isolation**: Private preferences, device audit logs, and temporary session tokens are readable and writable solely by the authenticated user (`auth.uid == $uid`).
2. **Granular Per-Member Authorization**: Trusted Circle members are constrained by explicit consent flags (`canViewLocation`, `canViewStatus`, `canViewJourney`, `canReceiveSos`).
3. **Session Expiry & Revocation**: Revoked or expired sessions immediately invalidate read access to live location feeds.
4. **Audit Integrity**: Privacy logs cannot be altered by unauthorized third parties.
5. **No Public Coordinates**: The `LiveLocations` root is completely shielded from unauthenticated or unauthorized reads.

---

## 2. Firebase Realtime Database Security Rules

```json
{
  "rules": {
    "Users": {
      "$uid": {
        // Only the profile owner can read and write their primary profile, sessions, and audit logs
        ".read": "auth != null && (auth.uid === $uid || root.child('circle_members').child($uid).child(auth.uid).child('permissions').child('canViewStatus').val() === true)",
        ".write": "auth != null && auth.uid === $uid",

        "sharing_sessions": {
          "$sessionId": {
            // Only the owner can create, modify, or revoke their sharing sessions
            ".read": "auth != null && auth.uid === $uid",
            ".write": "auth != null && auth.uid === $uid",
            ".validate": "newData.hasChildren(['sessionId', 'ownerUid', 'title', 'status', 'createdAt'])"
          }
        },

        "privacy_logs": {
          // Owner only; append-only audit trail
          ".read": "auth != null && auth.uid === $uid",
          "$logId": {
            ".write": "auth != null && auth.uid === $uid",
            ".validate": "newData.hasChildren(['logId', 'ownerUid', 'eventType', 'timestamp'])"
          }
        }
      }
    },

    "LiveLocations": {
      "$wardUid": {
        // Read permitted only if:
        // 1. Caller is the ward themselves, OR
        // 2. Caller is an accepted circle member with canViewLocation permission AND
        //    the ward is currently in an active, unexpired sharing state
        ".read": "auth != null && (auth.uid === $wardUid || (root.child('circle_members').child($wardUid).child(auth.uid).child('permissions').child('canViewLocation').val() === true && root.child('circle_members').child($wardUid).child(auth.uid).child('status').val() === 'accepted'))",
        
        // Write permitted solely by the device owner
        ".write": "auth != null && auth.uid === $wardUid",
        ".validate": "newData.hasChildren(['latitude', 'longitude', 'timestamp'])"
      }
    },

    "circle_members": {
      "$ownerUid": {
        // Owner can read and write circle configurations
        // Authorized member can read their own membership and permission record
        ".read": "auth != null && (auth.uid === $ownerUid || root.child('circle_members').child($ownerUid).child(auth.uid).exists())",
        
        "$memberUid": {
          ".read": "auth != null && (auth.uid === $ownerUid || auth.uid === $memberUid)",
          ".write": "auth != null && auth.uid === $ownerUid"
        }
      }
    },

    "user_wards": {
      "$memberUid": {
        // Member can read list of wards they are authorized to safeguard
        ".read": "auth != null && auth.uid === $memberUid",
        "$wardUid": {
          ".write": "auth != null && (auth.uid === $memberUid || auth.uid === $wardUid)"
        }
      }
    },

    "family_alerts": {
      "$memberUid": {
        // Alerts can be read only by intended recipients
        ".read": "auth != null && auth.uid === $memberUid",
        "$alertId": {
          ".write": "auth != null"
        }
      }
    }
  }
}
```

---

## 3. Trusted Backend Functions (Cloud Functions for Firebase)

While client applications enforce immediate state updates and local cache purges, authoritative expiration and cross-user notifications should be handled by trusted serverless functions:

### 1. `expireSharingSessionsCron` (Scheduled Job)
- **Schedule**: Every 10 minutes (`schedule('*/10 * * * *')`)
- **Action**: Queries `/Users/{uid}/sharing_sessions` for sessions where `expiresAt < now()` and `status == 'active'`.
- **Enforcement**: Sets `status = 'expired'`, and if no other active sessions remain for the user, wipes `/LiveLocations/{uid}`.

### 2. `onSharingSessionRevoked` (Realtime Database Trigger)
- **Trigger**: `ref('/Users/{uid}/sharing_sessions/{sessionId}/status').onUpdate()`
- **Action**: When status changes from `active` to `revoked`, checks remaining sessions. If zero, clears `/LiveLocations/{uid}` immediately so stale location reads are rejected by rules.

---

## 4. Microphone, Audio Evidence & Cloudinary Safeguards

1. **Pre-Consent & Runtime Checks**: Microphones are never opened in the background unless the user enables Voice Shield or initiates SOS.
2. **Local Audio Cache**: Saved in app private cache directory (`context.getCacheDir()`). Local cache can be purged on demand via Privacy Center.
3. **Signed Evidence Uploads**: SOS audio files uploaded to Cloudinary or Firebase Storage are tagged with the user's encrypted UID and restricted to authorized emergency escalation contacts.
