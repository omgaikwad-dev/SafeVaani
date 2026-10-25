package com.example.woshield;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data access and atomic state-transition repository for Woshield Check-In records.
 * Target path: Users/{uid}/checkIns/{checkInId}
 */
public class CheckInRepository {
    private static final String TAG = "CheckInRepository";
    private static final String NODE_USERS = "Users";
    private static final String NODE_CHECK_INS = "checkIns";

    private final DatabaseReference rootRef;
    private final FirebaseAuth auth;

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public CheckInRepository() {
        this.rootRef = FirebaseDatabase.getInstance().getReference();
        this.auth = FirebaseAuth.getInstance();
    }

    @NonNull
    private String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : DemoDataSeeder.DEMO_UID;
    }

    private DatabaseReference getUserCheckInsRef(String uid) {
        return rootRef.child(NODE_USERS).child(uid).child(NODE_CHECK_INS);
    }

    /**
     * Creates a new CheckIn record.
     * Prevents duplicate active check-ins for the same routine/event.
     */
    public void createCheckIn(@NonNull CheckIn checkIn, @Nullable RepositoryCallback<CheckIn> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        checkIn.setOwnerUid(uid);

        // Check if a pending non-expired check-in already exists
        DatabaseReference userCheckIns = getUserCheckInsRef(uid);
        Query pendingQuery = userCheckIns.orderByChild("status").equalTo(CheckIn.STATUS_PENDING);

        pendingQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // If a pending check-in is already active and unexpired, prevent duplicate
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CheckIn existing = ds.getValue(CheckIn.class);
                    if (existing != null && !existing.isExpired()) {
                        // If it belongs to same routine, reuse existing to avoid duplicates
                        if ((existing.getRoutineId() != null && existing.getRoutineId().equals(checkIn.getRoutineId()))
                                || (checkIn.getRoutineId() == null && existing.getRoutineId() == null)) {
                            Log.d(TAG, "Reusing existing pending check-in: " + existing.getCheckInId());
                            if (callback != null) callback.onSuccess(existing);
                            return;
                        }
                    }
                }

                // Proceed with creating new check-in
                String id = checkIn.getCheckInId();
                if (id == null || id.isEmpty()) {
                    id = userCheckIns.push().getKey();
                    checkIn.setCheckInId(id);
                }

                checkIn.setCreatedAt(System.currentTimeMillis());
                checkIn.setUpdatedAt(System.currentTimeMillis());

                userCheckIns.child(id).setValue(checkIn)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Check-in created: " + checkIn.getCheckInId());
                            if (callback != null) callback.onSuccess(checkIn);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to create check-in", e);
                            if (callback != null) callback.onError(e.getMessage());
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (callback != null) callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Atomically transitions a check-in response (I'M SAFE, ASSISTANCE, SOS).
     * Prevents race conditions with deadline timeout workers or concurrent taps.
     */
    public void respondCheckIn(@NonNull String checkInId,
                               @NonNull String targetStatus,
                               @NonNull String responseNote,
                               double latitude,
                               double longitude,
                               @Nullable RepositoryCallback<CheckIn> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        DatabaseReference itemRef = getUserCheckInsRef(uid).child(checkInId);

        itemRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                CheckIn current = currentData.getValue(CheckIn.class);
                if (current == null) {
                    return Transaction.success(currentData);
                }

                // Validate transition: ONLY valid from STATUS_PENDING
                if (!current.canTransitionTo(targetStatus)) {
                    Log.w(TAG, "Invalid state transition requested from " + current.getStatus() + " to " + targetStatus);
                    return Transaction.abort();
                }

                // Apply update
                current.setStatus(targetStatus);
                current.setResponse(responseNote);
                current.setRespondedAt(System.currentTimeMillis());
                if (latitude != 0.0 || longitude != 0.0) {
                    current.setLatitude(latitude);
                    current.setLongitude(longitude);
                }
                current.setUpdatedAt(System.currentTimeMillis());

                currentData.setValue(current);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    Log.e(TAG, "Transaction error responding to check-in", error.toException());
                    if (callback != null) callback.onError(error.getMessage());
                    return;
                }

                if (!committed) {
                    Log.w(TAG, "Transaction aborted: check-in was already resolved or does not exist.");
                    if (callback != null) callback.onError("Check-in has already been resolved or expired.");
                    return;
                }

                if (currentData != null) {
                    CheckIn updated = currentData.getValue(CheckIn.class);
                    if (callback != null) callback.onSuccess(updated);
                } else {
                    if (callback != null) callback.onError("Updated record unavailable.");
                }
            }
        });
    }

    /**
     * Atomically marks a check-in as missed when deadline expires.
     */
    public void markMissed(@NonNull String checkInId,
                           @NonNull String escalationStatus,
                           @Nullable RepositoryCallback<CheckIn> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        DatabaseReference itemRef = getUserCheckInsRef(uid).child(checkInId);

        itemRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                CheckIn current = currentData.getValue(CheckIn.class);
                if (current == null) {
                    return Transaction.success(currentData);
                }

                // Cannot mark as missed if already resolved or cancelled
                if (!CheckIn.STATUS_PENDING.equals(current.getStatus())) {
                    return Transaction.abort();
                }

                // Verify deadline actually passed (allow small 1000ms clock skew buffer)
                if (System.currentTimeMillis() < current.getDeadlineAt() - 1000) {
                    return Transaction.abort();
                }

                current.setStatus(CheckIn.STATUS_MISSED);
                current.setEscalationStatus(escalationStatus);
                current.setUpdatedAt(System.currentTimeMillis());

                currentData.setValue(current);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    if (callback != null) callback.onError(error.getMessage());
                    return;
                }
                if (!committed) {
                    if (callback != null) callback.onError("Check-in was already resolved before deadline.");
                    return;
                }
                if (currentData != null) {
                    CheckIn updated = currentData.getValue(CheckIn.class);
                    if (callback != null) callback.onSuccess(updated);
                }
            }
        });
    }

    /**
     * Fetches current pending check-in if one exists.
     */
    public void getPendingCheckIn(@NonNull RepositoryCallback<CheckIn> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        Query pendingQuery = getUserCheckInsRef(uid).orderByChild("status").equalTo(CheckIn.STATUS_PENDING);
        pendingQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                CheckIn latestPending = null;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    CheckIn item = ds.getValue(CheckIn.class);
                    if (item != null) {
                        latestPending = item;
                    }
                }
                callback.onSuccess(latestPending);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Fetches a specific check-in by ID.
     */
    public void getCheckInById(@NonNull String checkInId, @NonNull RepositoryCallback<CheckIn> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        getUserCheckInsRef(uid).child(checkInId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                CheckIn item = snapshot.getValue(CheckIn.class);
                if (item != null) {
                    callback.onSuccess(item);
                } else {
                    callback.onError("Check-in not found.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Attaches a real-time listener to a check-in.
     */
    public ValueEventListener attachCheckInListener(@NonNull String checkInId, @NonNull ValueEventListener listener) {
        String uid = getCurrentUserId();
        if (uid == null) return listener;
        DatabaseReference itemRef = getUserCheckInsRef(uid).child(checkInId);
        itemRef.addValueEventListener(listener);
        return listener;
    }

    public void removeCheckInListener(@NonNull String checkInId, @NonNull ValueEventListener listener) {
        String uid = getCurrentUserId();
        if (uid == null) return;
        getUserCheckInsRef(uid).child(checkInId).removeEventListener(listener);
    }
}
