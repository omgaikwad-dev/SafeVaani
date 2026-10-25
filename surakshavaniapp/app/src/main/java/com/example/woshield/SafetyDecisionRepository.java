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
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for recording Safety Intelligence decisions and bridging
 * recommendations with Smart Check-In dispatches.
 * Target path: Users/{uid}/safety_decisions/{decisionId}
 */
public class SafetyDecisionRepository {
    private static final String TAG = "SafetyDecisionRepo";
    private static final String NODE_USERS = "Users";
    private static final String NODE_SAFETY_DECISIONS = "safety_decisions";

    private final DatabaseReference rootRef;
    private final FirebaseAuth auth;

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public SafetyDecisionRepository() {
        this.rootRef = FirebaseDatabase.getInstance().getReference();
        this.auth = FirebaseAuth.getInstance();
    }

    @Nullable
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private DatabaseReference getUserDecisionsRef(String uid) {
        return rootRef.child(NODE_USERS).child(uid).child(NODE_SAFETY_DECISIONS);
    }

    /**
     * Persists an evaluated SafetyDecision to the user's private audit tree.
     */
    public void recordDecision(@NonNull SafetyDecision decision, @Nullable RepositoryCallback<Void> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        decision.setOwnerUid(uid);
        String decisionId = decision.getDecisionId();
        if (decisionId == null || decisionId.isEmpty()) {
            decisionId = getUserDecisionsRef(uid).push().getKey();
            decision.setDecisionId(decisionId);
        }

        getUserDecisionsRef(uid).child(decisionId).setValue(decision)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to record safety decision", e);
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    /**
     * Retrieves the most recent safety decision for deduplication and home-screen indicator.
     */
    public void getRecentDecision(@NonNull RepositoryCallback<SafetyDecision> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        Query query = getUserDecisionsRef(uid).orderByChild("timestamp").limitToLast(1);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                SafetyDecision recent = null;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    recent = ds.getValue(SafetyDecision.class);
                }
                callback.onSuccess(recent);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Retrieves recent decision history (up to limit).
     */
    public void getDecisionHistory(int limit, @NonNull RepositoryCallback<List<SafetyDecision>> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onError("User not authenticated.");
            return;
        }

        Query query = getUserDecisionsRef(uid).orderByChild("timestamp").limitToLast(Math.max(1, limit));
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<SafetyDecision> list = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SafetyDecision d = ds.getValue(SafetyDecision.class);
                    if (d != null) {
                        list.add(d);
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

    /**
     * Seamlessly integrates with Smart Check-In.
     * When decision is SAFETY_CHECK_RECOMMENDED, creates a CheckIn record via CheckInRepository
     * unless one is already pending.
     */
    public void dispatchSafetyCheckInIfNeeded(@NonNull SafetyDecision decision,
                                              @NonNull CheckInRepository checkInRepo,
                                              @Nullable RepositoryCallback<CheckIn> callback) {
        if (!SafetyDecision.OUTCOME_SAFETY_CHECK_RECOMMENDED.equals(decision.getOutcome())) {
            if (callback != null) callback.onSuccess(null);
            return;
        }

        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        long deadlineAt = System.currentTimeMillis() + (5 * 60 * 1000L); // 5 min deadline
        CheckIn checkIn = new CheckIn(
                null,
                uid,
                decision.getRoutineId(),
                "Journey Safety Check",
                null,
                decision.getExplanation(),
                deadlineAt
        );

        checkInRepo.createCheckIn(checkIn, new CheckInRepository.RepositoryCallback<CheckIn>() {
            @Override
            public void onSuccess(CheckIn created) {
                decision.setActionTaken(SafetyDecision.ACTION_CHECKIN_DISPATCHED);
                recordDecision(decision, null);
                if (callback != null) callback.onSuccess(created);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed creating check-in from safety decision: " + errorMessage);
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    /**
     * Marks a decision as dismissed/resolved by user.
     */
    public void dismissDecision(@NonNull String decisionId, @Nullable RepositoryCallback<Void> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onError("User not authenticated.");
            return;
        }

        getUserDecisionsRef(uid).child(decisionId).child("dismissed").setValue(true)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }
}
