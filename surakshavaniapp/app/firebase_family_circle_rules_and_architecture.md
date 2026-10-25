# Woshield Step 5: Family Dashboard & Trusted Safety Circle Security Rules

This document outlines the authoritative Firebase Realtime Database Security Rules and architecture for the Trusted Safety Circle and Family Dashboard.

---

## 1. Firebase Realtime Database Security Rules

Apply these rules in the Firebase console under **Realtime Database > Rules**:

```json
{
  "rules": {
    "Users": {
      "$uid": {
        // Only the authenticated user can read or write their private profile
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",

        "checkIns": {
          // Owner or explicitly authorized circle members can read check-ins
          ".read": "auth != null && (auth.uid == $uid || root.child('circle_members').child($uid).child(auth.uid).child('permissions/canViewStatus').val() === true)",
          ".write": "auth != null && auth.uid == $uid"
        },

        "routines": {
          ".read": "auth != null && (auth.uid == $uid || root.child('circle_members').child($uid).child(auth.uid).child('permissions/canViewJourney').val() === true)",
          ".write": "auth != null && auth.uid == $uid"
        }
      }
    },

    "invitations": {
      "$invitationId": {
        // Read allowed for sender or intended recipient
        ".read": "auth != null && (data.child('senderUid').val() === auth.uid || data.child('recipientEmail').val() === auth.token.email)",
        // Create allowed only if authenticated sender UID matches
        ".write": "auth != null && (!data.exists() && newData.child('senderUid').val() === auth.uid || data.exists() && (data.child('senderUid').val() === auth.uid || data.child('recipientEmail').val() === auth.token.email))",
        ".validate": "newData.hasChildren(['senderUid', 'recipientEmail', 'status', 'createdAt'])"
      }
    },

    "invitations_by_email": {
      "$sanitizedEmail": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },

    "circle_members": {
      "$ownerUid": {
        // Owner and participating circle members can view the circle roster
        ".read": "auth != null && (auth.uid == $ownerUid || data.child(auth.uid).exists())",
        // Only the owner can add, modify, or revoke permissions
        ".write": "auth != null && (auth.uid == $ownerUid || newData.parent().key === auth.uid)"
      }
    },

    "user_wards": {
      "$memberUid": {
        // Only the member can view their personal dashboard of protected wards
        ".read": "auth != null && auth.uid == $memberUid",
        ".write": "auth != null"
      }
    },

    "LiveLocations": {
      "$wardUid": {
        // Only the device owner can write their live coordinates
        ".write": "auth != null && auth.uid == $wardUid",
        // Read permitted only if authorized in the ward's circle with active location permission
        ".read": "auth != null && (auth.uid == $wardUid || root.child('circle_members').child($wardUid).child(auth.uid).child('permissions/canViewLocation').val() === true)"
      }
    },

    "alerts": {
      "$wardUid": {
        // Read permitted if recipient has emergency/assistance permission
        ".read": "auth != null && (auth.uid == $wardUid || root.child('circle_members').child($wardUid).child(auth.uid).exists())",
        ".write": "auth != null"
      }
    },

    "emergency_logs": {
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```

---

## 2. Consent & Revocation Architecture

1. **Explicit Consent**:
   - Two users are never connected automatically.
   - User A invites User B with an explicit list of granted permissions (`canViewStatus`, `canViewLocation`, `canViewJourney`, `canReceiveSos`, `canReceiveAssistance`, `canReceiveMissedCheckIns`).
   - User B must sign in and explicitly accept the invitation.
2. **Instant Revocation**:
   - When User A revokes User B's access, the `circle_members` record is removed atomically.
   - Because the Firebase Security Rules check `root.child('circle_members').child($wardUid).child(auth.uid)`, User B's client immediately loses the ability to read `LiveLocations`, `checkIns`, and private alerts.
   - Local realtime listeners immediately detach.
3. **Stale Location Warning**:
   - If a device stops transmitting location updates (e.g. sharing deactivated, out of range, or app closed), the Family Dashboard detects that the timestamp is older than 15 minutes and displays an explicit amber warning: *"⚠️ Location is stale (over 15 min old). Device may be offline."*
   - Stale locations are never falsely presented as live.
