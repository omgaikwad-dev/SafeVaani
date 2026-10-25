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
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repository for querying, managing, and acknowledging safety alerts in the Family Dashboard.
 */
public class FamilyAlertRepository {
    private static final String TAG = "FamilyAlertRepo";

    private final DatabaseReference rootRef;
    private final FirebaseAuth auth;

    public interface AlertCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public FamilyAlertRepository() {
        this.rootRef = FirebaseDatabase.getInstance().getReference();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Acknowledges an active alert, recording the family member's identity and timestamp.
     */
    public void acknowledgeAlert(@NonNull String wardUid,
                                 @NonNull String alertId,
                                 @Nullable AlertCallback<Void> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        String ackBy = currentUser != null
                ? (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : currentUser.getEmail())
                : "Family Member";

        DatabaseReference alertRef = rootRef.child("alerts").child(wardUid).child(alertId);
        long now = System.currentTimeMillis();

        alertRef.child("status").setValue(FamilyAlert.STATUS_ACKNOWLEDGED);
        alertRef.child("acknowledgedAt").setValue(now);
        alertRef.child("acknowledgedBy").setValue(ackBy)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Alert acknowledged: " + alertId + " by " + ackBy);
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed acknowledging alert", e);
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    /**
     * Resolves an alert.
     */
    public void resolveAlert(@NonNull String wardUid,
                             @NonNull String alertId,
                             @Nullable AlertCallback<Void> callback) {
        DatabaseReference alertRef = rootRef.child("alerts").child(wardUid).child(alertId);
        alertRef.child("status").setValue(FamilyAlert.STATUS_RESOLVED);
        alertRef.child("resolvedAt").setValue(System.currentTimeMillis())
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }
}
