package com.example.woshield;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class RoutineRepository {

    private final DatabaseReference mDatabase;
    private final FirebaseAuth mAuth;

    public RoutineRepository() {
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.mAuth = FirebaseAuth.getInstance();
    }

    private String getCurrentUserId() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null ? user.getUid() : DemoDataSeeder.DEMO_UID;
    }

    public DatabaseReference getRoutinesRef() {
        String uid = getCurrentUserId();
        if (uid == null) return null;
        return mDatabase.child("Users").child(uid).child("routines");
    }

    public void listenToRoutines(@NonNull ValueEventListener listener) {
        DatabaseReference ref = getRoutinesRef();
        if (ref != null) {
            ref.addValueEventListener(listener);
        }
    }

    public void removeRoutinesListener(@NonNull ValueEventListener listener) {
        DatabaseReference ref = getRoutinesRef();
        if (ref != null) {
            ref.removeEventListener(listener);
        }
    }

    public void saveRoutine(Routine routine, DatabaseReference.CompletionListener completionListener) {
        DatabaseReference ref = getRoutinesRef();
        if (ref == null) return;

        if (routine.getId() == null || routine.getId().isEmpty()) {
            String newKey = ref.push().getKey();
            routine.setId(newKey);
            routine.setUserId(getCurrentUserId());
            routine.setCreatedAt(System.currentTimeMillis());
        }
        routine.setUpdatedAt(System.currentTimeMillis());

        ref.child(routine.getId()).setValue(routine, completionListener);
    }

    public void toggleRoutineEnabled(String routineId, boolean enabled, DatabaseReference.CompletionListener completionListener) {
        DatabaseReference ref = getRoutinesRef();
        if (ref == null || routineId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("enabled", enabled);
        updates.put("updatedAt", System.currentTimeMillis());

        ref.child(routineId).updateChildren(updates, completionListener);
    }

    public void deleteRoutine(String routineId, DatabaseReference.CompletionListener completionListener) {
        DatabaseReference ref = getRoutinesRef();
        if (ref == null || routineId == null) return;

        ref.child(routineId).removeValue(completionListener);
    }
}
