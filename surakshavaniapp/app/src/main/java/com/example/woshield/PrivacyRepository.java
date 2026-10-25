package com.example.woshield;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for managing Privacy Center state:
 * - Temporary sharing sessions (create, review, revoke, expire)
 * - Per-member granular permissions update
 * - Privacy activity audit logging
 * - Data retention & cache purging
 */
public class PrivacyRepository {
    private static final String TAG = "PrivacyRepository";

    private static final String NODE_USERS = "Users";
    private static final String NODE_SHARING_SESSIONS = "sharing_sessions";
    private static final String NODE_PRIVACY_LOGS = "privacy_logs";
    private static final String NODE_CIRCLE_MEMBERS = "circle_members";
    private static final String NODE_USER_WARDS = "user_wards";
    private static final String NODE_LIVE_LOCATIONS = "LiveLocations";

    private final DatabaseReference rootRef;
    private final FirebaseAuth auth;

    public interface PrivacyCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public PrivacyRepository() {
        this.rootRef = FirebaseDatabase.getInstance().getReference();
        this.auth = FirebaseAuth.getInstance();
    }

    @NonNull
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : DemoDataSeeder.DEMO_UID;
    }

    // =========================================================
    // 1. PRIVACY ACTIVITY LOGS
    // =========================================================

    public void logPrivacyEvent(@NonNull String eventType,
                                @NonNull String title,
                                @NonNull String description,
                                @NonNull String severity) {
        String uid = getCurrentUserId();
        if (uid == null) return;

        DatabaseReference logRef = rootRef.child(NODE_USERS).child(uid).child(NODE_PRIVACY_LOGS).push();
        String logId = logRef.getKey();

        PrivacyActivityLog event = new PrivacyActivityLog(
                logId,
                uid,
                eventType,
                title,
                description,
                severity
        );

        logRef.setValue(event).addOnFailureListener(e -> Log.w(TAG, "Failed logging privacy event", e));
    }

    public void getPrivacyLogs(@NonNull PrivacyCallback<List<PrivacyActivityLog>> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        Query query = rootRef.child(NODE_USERS).child(uid).child(NODE_PRIVACY_LOGS).limitToLast(50);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<PrivacyActivityLog> list = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    PrivacyActivityLog item = ds.getValue(PrivacyActivityLog.class);
                    if (item != null) {
                        list.add(item);
                    }
                }
                Collections.reverse(list); // Newest first
                callback.onSuccess(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    // =========================================================
    // 2. TEMPORARY SHARING SESSIONS
    // =========================================================

    public void startSharingSession(@NonNull String title,
                                    long durationMillis,
                                    @NonNull List<String> recipientUids,
                                    @NonNull List<String> recipientNames,
                                    @NonNull PrivacyCallback<SharingSession> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        DatabaseReference sessionsRef = rootRef.child(NODE_USERS).child(uid).child(NODE_SHARING_SESSIONS);
        String sessionId = sessionsRef.push().getKey();
        long expiresAt = durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0;

        SharingSession session = new SharingSession(
                sessionId,
                uid,
                title,
                recipientUids,
                recipientNames,
                expiresAt
        );

        sessionsRef.child(sessionId).setValue(session)
                .addOnSuccessListener(aVoid -> {
                    logPrivacyEvent(
                            PrivacyActivityLog.TYPE_SHARING_STARTED,
                            "Location Sharing Started",
                            "Session '" + title + "' started with " + recipientUids.size() + " recipient(s).",
                            PrivacyActivityLog.SEVERITY_INFO
                    );
                    callback.onSuccess(session);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void revokeSharingSession(@NonNull String sessionId, @NonNull PrivacyCallback<Void> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        DatabaseReference sessionRef = rootRef.child(NODE_USERS).child(uid).child(NODE_SHARING_SESSIONS).child(sessionId);
        sessionRef.child("status").setValue(SharingSession.STATUS_REVOKED);
        sessionRef.child("revokedAt").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    // Check if other active sessions remain; if none, clear LiveLocations
                    checkAndClearLiveLocationIfNoActiveSessions(uid);

                    logPrivacyEvent(
                            PrivacyActivityLog.TYPE_SHARING_REVOKED,
                            "Location Sharing Revoked",
                            "Sharing session manually ended and access denied.",
                            PrivacyActivityLog.SEVERITY_WARNING
                    );
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void stopAllSharingSessions(@NonNull PrivacyCallback<Void> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        DatabaseReference sessionsRef = rootRef.child(NODE_USERS).child(uid).child(NODE_SHARING_SESSIONS);
        sessionsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long now = System.currentTimeMillis();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String status = ds.child("status").getValue(String.class);
                    if (SharingSession.STATUS_ACTIVE.equals(status)) {
                        ds.getRef().child("status").setValue(SharingSession.STATUS_REVOKED);
                        ds.getRef().child("revokedAt").setValue(now);
                    }
                }

                // Invalidate LiveLocations node immediately
                rootRef.child(NODE_LIVE_LOCATIONS).child(uid).removeValue();

                logPrivacyEvent(
                        PrivacyActivityLog.TYPE_SHARING_REVOKED,
                        "All Location Sharing Stopped",
                        "User explicitly terminated all active sharing sessions.",
                        PrivacyActivityLog.SEVERITY_SECURITY
                );
                callback.onSuccess(null);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void getSharingSessions(@NonNull PrivacyCallback<List<SharingSession>> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        rootRef.child(NODE_USERS).child(uid).child(NODE_SHARING_SESSIONS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<SharingSession> sessions = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            SharingSession item = ds.getValue(SharingSession.class);
                            if (item != null) {
                                sessions.add(item);
                            }
                        }
                        Collections.reverse(sessions);
                        callback.onSuccess(sessions);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    private void checkAndClearLiveLocationIfNoActiveSessions(String uid) {
        rootRef.child(NODE_USERS).child(uid).child(NODE_SHARING_SESSIONS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean anyActive = false;
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            SharingSession s = ds.getValue(SharingSession.class);
                            if (s != null && s.isActive()) {
                                anyActive = true;
                                break;
                            }
                        }
                        if (!anyActive) {
                            rootRef.child(NODE_LIVE_LOCATIONS).child(uid).removeValue();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // =========================================================
    // 3. PER-MEMBER PERMISSIONS MANAGEMENT
    // =========================================================

    public void updateMemberPermissions(@NonNull String memberUid,
                                        @NonNull String memberName,
                                        @NonNull CirclePermissions permissions,
                                        @NonNull PrivacyCallback<Void> callback) {
        String myUid = getCurrentUserId();
        if (myUid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        // 1. Update in circle_members/{myUid}/{memberUid}/permissions
        rootRef.child(NODE_CIRCLE_MEMBERS).child(myUid).child(memberUid).child("permissions").setValue(permissions)
                .addOnSuccessListener(aVoid -> {
                    // 2. Reflect in user_wards/{memberUid}/{myUid}/permissions
                    rootRef.child(NODE_USER_WARDS).child(memberUid).child(myUid).child("permissions").setValue(permissions);

                    logPrivacyEvent(
                            PrivacyActivityLog.TYPE_PERMISSION_CHANGED,
                            "Permissions Updated",
                            "Updated sharing permissions for " + memberName + ".",
                            PrivacyActivityLog.SEVERITY_INFO
                    );
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getCircleMembers(@NonNull PrivacyCallback<List<FamilyMember>> callback) {
        String myUid = getCurrentUserId();
        if (myUid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        rootRef.child(NODE_CIRCLE_MEMBERS).child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<FamilyMember> members = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    FamilyMember member = ds.getValue(FamilyMember.class);
                    if (member != null) {
                        members.add(member);
                    }
                }
                callback.onSuccess(members);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    // =========================================================
    // 4. DATA RETENTION & PRIVACY PURGE TOOLS
    // =========================================================

    public static class PurgeResult {
        public int filesDeleted;
        public long bytesFreed;

        public PurgeResult(int filesDeleted, long bytesFreed) {
            this.filesDeleted = filesDeleted;
            this.bytesFreed = bytesFreed;
        }
    }

    /**
     * Clears local audio evidence files saved on device cache.
     */
    public PurgeResult clearLocalAudioCache(Context context) {
        int count = 0;
        long bytes = 0;

        File[] dirs = {context.getCacheDir(), context.getExternalCacheDir()};
        for (File dir : dirs) {
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles((d, name) -> name.startsWith("sos_manual_") && name.endsWith(".m4a"));
                if (files != null) {
                    for (File f : files) {
                        bytes += f.length();
                        if (f.delete()) {
                            count++;
                        }
                    }
                }
            }
        }

        if (count > 0) {
            logPrivacyEvent(
                    PrivacyActivityLog.TYPE_DATA_PURGED,
                    "Local Evidence Cache Cleared",
                    "Deleted " + count + " cached voice recording file(s) (" + (bytes / 1024) + " KB reclaimed).",
                    PrivacyActivityLog.SEVERITY_INFO
            );
        }

        return new PurgeResult(count, bytes);
    }

    /**
     * Purges expired sharing sessions from database.
     */
    public void purgeExpiredSessions(@NonNull PrivacyCallback<Integer> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        DatabaseReference ref = rootRef.child(NODE_USERS).child(uid).child(NODE_SHARING_SESSIONS);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int removed = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SharingSession s = ds.getValue(SharingSession.class);
                    if (s != null && (!SharingSession.STATUS_ACTIVE.equals(s.getStatus()) || s.isExpired())) {
                        ds.getRef().removeValue();
                        removed++;
                    }
                }
                if (removed > 0) {
                    logPrivacyEvent(
                            PrivacyActivityLog.TYPE_DATA_PURGED,
                            "Expired Sessions Purged",
                            "Purged " + removed + " expired sharing session records.",
                            PrivacyActivityLog.SEVERITY_INFO
                    );
                }
                callback.onSuccess(removed);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Purges privacy activity logs.
     */
    public void clearPrivacyLogs(@NonNull PrivacyCallback<Void> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        rootRef.child(NODE_USERS).child(uid).child(NODE_PRIVACY_LOGS).removeValue()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
