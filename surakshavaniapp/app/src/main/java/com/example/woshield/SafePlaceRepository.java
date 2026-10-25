package com.example.woshield;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SafePlaceRepository {

    private final DatabaseReference mDatabase;
    private final FirebaseAuth mAuth;

    public SafePlaceRepository() {
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.mAuth = FirebaseAuth.getInstance();
    }

    private String getCurrentUserId() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null ? user.getUid() : DemoDataSeeder.DEMO_UID;
    }

    public DatabaseReference getSafePlacesRef() {
        String uid = getCurrentUserId();
        if (uid == null) return null;
        return mDatabase.child("Users").child(uid).child("safePlaces");
    }

    public void listenToSafePlaces(@NonNull ValueEventListener listener) {
        DatabaseReference ref = getSafePlacesRef();
        if (ref != null) {
            ref.addValueEventListener(listener);
        }
    }

    public void removeSafePlacesListener(@NonNull ValueEventListener listener) {
        DatabaseReference ref = getSafePlacesRef();
        if (ref != null) {
            ref.removeEventListener(listener);
        }
    }

    public void saveSafePlace(SafePlace place, DatabaseReference.CompletionListener completionListener) {
        DatabaseReference ref = getSafePlacesRef();
        if (ref == null) return;

        if (place.getId() == null || place.getId().isEmpty()) {
            String newKey = ref.push().getKey();
            place.setId(newKey);
            place.setUserId(getCurrentUserId());
            place.setCreatedAt(System.currentTimeMillis());
        }
        place.setUpdatedAt(System.currentTimeMillis());

        ref.child(place.getId()).setValue(place, completionListener);
    }

    public void toggleTrusted(String placeId, boolean trusted, DatabaseReference.CompletionListener completionListener) {
        DatabaseReference ref = getSafePlacesRef();
        if (ref == null || placeId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("trusted", trusted);
        updates.put("updatedAt", System.currentTimeMillis());

        ref.child(placeId).updateChildren(updates, completionListener);
    }

    public void deleteSafePlace(String placeId, DatabaseReference.CompletionListener completionListener) {
        DatabaseReference ref = getSafePlacesRef();
        if (ref == null || placeId == null) return;

        ref.child(placeId).removeValue(completionListener);
    }
}
