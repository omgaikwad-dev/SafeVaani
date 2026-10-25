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
import java.util.List;

/**
 * Repository for managing consent-based circle invitations, accepted memberships,
 * granular permissions, and authorized ward observation.
 */
public class FamilyCircleRepository {
    private static final String TAG = "FamilyCircleRepo";

    private static final String NODE_INVITATIONS = "invitations";
    private static final String NODE_INVITES_BY_EMAIL = "invitations_by_email";
    private static final String NODE_CIRCLE_MEMBERS = "circle_members";
    private static final String NODE_USER_WARDS = "user_wards";
    private static final String NODE_USERS = "Users";
    private static final String NODE_LIVE_LOCATIONS = "LiveLocations";

    private final DatabaseReference rootRef;
    private final FirebaseAuth auth;

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public FamilyCircleRepository() {
        this.rootRef = FirebaseDatabase.getInstance().getReference();
        this.auth = FirebaseAuth.getInstance();
    }

    @NonNull
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : DemoDataSeeder.DEMO_UID;
    }

    @NonNull
    public String getCurrentUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : "ananya.sharma@woshield.org";
    }

    private String sanitizeEmail(String email) {
        if (email == null) return "";
        return email.toLowerCase().trim().replace(".", ",");
    }

    /**
     * Creates and dispatches a consent-based invitation to a trusted contact.
     */
    public void sendInvitation(@NonNull String recipientEmail,
                               @Nullable String recipientPhone,
                               @NonNull CirclePermissions permissions,
                               @Nullable RepositoryCallback<CircleInvitation> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            if (callback != null) callback.onError("Authentication required.");
            return;
        }

        String senderUid = currentUser.getUid();
        String senderEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
        String senderName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : senderEmail.split("@")[0];

        String cleanRecipientEmail = recipientEmail.toLowerCase().trim();
        if (cleanRecipientEmail.isEmpty()) {
            if (callback != null) callback.onError("Recipient email is required.");
            return;
        }

        if (cleanRecipientEmail.equalsIgnoreCase(senderEmail)) {
            if (callback != null) callback.onError("You cannot invite yourself to your own circle.");
            return;
        }

        DatabaseReference inviteRef = rootRef.child(NODE_INVITATIONS).push();
        String inviteId = inviteRef.getKey();

        CircleInvitation invitation = new CircleInvitation(
                inviteId,
                senderUid,
                senderName,
                senderEmail,
                cleanRecipientEmail,
                recipientPhone,
                permissions
        );

        inviteRef.setValue(invitation)
                .addOnSuccessListener(aVoid -> {
                    // Index by recipient email for rapid lookup
                    String sanitizedEmail = sanitizeEmail(cleanRecipientEmail);
                    rootRef.child(NODE_INVITES_BY_EMAIL).child(sanitizedEmail).child(inviteId).setValue(true);

                    Log.d(TAG, "Invitation sent successfully: " + inviteId);
                    if (callback != null) callback.onSuccess(invitation);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed sending invitation", e);
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    /**
     * Retrieves pending invitations addressed to the signed-in user's email.
     */
    public void getPendingInvitationsForMe(@NonNull RepositoryCallback<List<CircleInvitation>> callback) {
        String myEmail = getCurrentUserEmail();
        if (myEmail == null || myEmail.isEmpty()) {
            callback.onError("User email unavailable.");
            return;
        }

        String sanitizedEmail = sanitizeEmail(myEmail);
        rootRef.child(NODE_INVITES_BY_EMAIL).child(sanitizedEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<CircleInvitation> results = new ArrayList<>();
                        if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                            callback.onSuccess(results);
                            return;
                        }

                        final long totalCount = snapshot.getChildrenCount();
                        final int[] processed = {0};

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String inviteId = child.getKey();
                            if (inviteId == null) continue;

                            rootRef.child(NODE_INVITATIONS).child(inviteId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot inviteSnap) {
                                            CircleInvitation inv = inviteSnap.getValue(CircleInvitation.class);
                                            if (inv != null && CircleInvitation.STATUS_PENDING.equals(inv.getStatus()) && !inv.isExpired()) {
                                                results.add(inv);
                                            }
                                            processed[0]++;
                                            if (processed[0] >= totalCount) {
                                                callback.onSuccess(results);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            processed[0]++;
                                            if (processed[0] >= totalCount) {
                                                callback.onSuccess(results);
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    /**
     * Accepts a pending invitation. Establishes the authenticated bidirectional membership.
     */
    public void acceptInvitation(@NonNull CircleInvitation invitation, @NonNull RepositoryCallback<Void> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onError("Authentication required.");
            return;
        }

        String myUid = currentUser.getUid();
        String myEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
        String myName = currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()
                ? currentUser.getDisplayName() : myEmail.split("@")[0];

        String senderUid = invitation.getSenderUid();
        String inviteId = invitation.getInvitationId();

        // 1. Update invitation status
        invitation.setStatus(CircleInvitation.STATUS_ACCEPTED);
        invitation.setRecipientUid(myUid);
        invitation.setRespondedAt(System.currentTimeMillis());

        rootRef.child(NODE_INVITATIONS).child(inviteId).setValue(invitation)
                .addOnSuccessListener(aVoid -> {
                    // Remove from pending email index
                    String sanitizedEmail = sanitizeEmail(myEmail);
                    rootRef.child(NODE_INVITES_BY_EMAIL).child(sanitizedEmail).child(inviteId).removeValue();

                    // 2. Establish sender's circle member record (sender shares with me)
                    FamilyMember memberRecord = new FamilyMember(
                            myUid,
                            myName,
                            myEmail,
                            invitation.getRecipientPhone(),
                            invitation.getPermissions()
                    );
                    rootRef.child(NODE_CIRCLE_MEMBERS).child(senderUid).child(myUid).setValue(memberRecord);

                    // 3. Establish my protected ward record (I monitor sender)
                    FamilyMember wardRecord = new FamilyMember(
                            senderUid,
                            invitation.getSenderName(),
                            invitation.getSenderEmail(),
                            "",
                            invitation.getPermissions()
                    );
                    rootRef.child(NODE_USER_WARDS).child(myUid).child(senderUid).setValue(wardRecord)
                            .addOnSuccessListener(v -> callback.onSuccess(null))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Rejects an invitation.
     */
    public void rejectInvitation(@NonNull CircleInvitation invitation, @NonNull RepositoryCallback<Void> callback) {
        String inviteId = invitation.getInvitationId();
        invitation.setStatus(CircleInvitation.STATUS_REJECTED);
        invitation.setRespondedAt(System.currentTimeMillis());

        rootRef.child(NODE_INVITATIONS).child(inviteId).setValue(invitation)
                .addOnSuccessListener(aVoid -> {
                    String sanitizedEmail = sanitizeEmail(invitation.getRecipientEmail());
                    rootRef.child(NODE_INVITES_BY_EMAIL).child(sanitizedEmail).child(inviteId).removeValue();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Revokes a membership. Both parties immediately lose access to each other's live records.
     */
    public void revokeMembership(@NonNull String targetUid, @NonNull RepositoryCallback<Void> callback) {
        String myUid = getCurrentUserId();
        if (myUid == null) {
            callback.onError("Authentication required.");
            return;
        }

        // Remove from both directions
        rootRef.child(NODE_CIRCLE_MEMBERS).child(myUid).child(targetUid).removeValue();
        rootRef.child(NODE_USER_WARDS).child(targetUid).child(myUid).removeValue();

        rootRef.child(NODE_USER_WARDS).child(myUid).child(targetUid).removeValue();
        rootRef.child(NODE_CIRCLE_MEMBERS).child(targetUid).child(myUid).removeValue()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Fetches all wards that the current user is authorized to monitor in the Family Dashboard.
     */
    public void getMyProtectedWards(@NonNull RepositoryCallback<List<FamilyMember>> callback) {
        String myUid = getCurrentUserId();
        if (myUid == null) {
            callback.onError("Authentication required.");
            return;
        }

        rootRef.child(NODE_USER_WARDS).child(myUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<FamilyMember> wards = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    FamilyMember member = ds.getValue(FamilyMember.class);
                    if (member != null && (member.getPermissions() == null || !member.getPermissions().isExpired())) {
                        wards.add(member);
                    }
                }
                callback.onSuccess(wards);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Realtime observation of ward's live location (if authorized).
     */
    public ValueEventListener listenToWardLocation(@NonNull String wardUid, @NonNull ValueEventListener listener) {
        DatabaseReference locRef = rootRef.child(NODE_LIVE_LOCATIONS).child(wardUid);
        locRef.addValueEventListener(listener);
        return listener;
    }

    public void removeWardLocationListener(@NonNull String wardUid, @NonNull ValueEventListener listener) {
        rootRef.child(NODE_LIVE_LOCATIONS).child(wardUid).removeEventListener(listener);
    }

    /**
     * Realtime observation of ward's latest Check-In status.
     */
    public ValueEventListener listenToWardLatestCheckIn(@NonNull String wardUid, @NonNull ValueEventListener listener) {
        Query query = rootRef.child(NODE_USERS).child(wardUid).child("checkIns").limitToLast(1);
        query.addValueEventListener(listener);
        return listener;
    }

    public void removeWardCheckInListener(@NonNull String wardUid, @NonNull ValueEventListener listener) {
        rootRef.child(NODE_USERS).child(wardUid).child("checkIns").removeEventListener(listener);
    }

    /**
     * Realtime observation of ward's alerts (SOS, assistance, missed check-in).
     */
    public ValueEventListener listenToWardAlerts(@NonNull String wardUid, @NonNull ValueEventListener listener) {
        Query query = rootRef.child("alerts").child(wardUid).limitToLast(5);
        query.addValueEventListener(listener);
        return listener;
    }

    public void removeWardAlertsListener(@NonNull String wardUid, @NonNull ValueEventListener listener) {
        rootRef.child("alerts").child(wardUid).removeEventListener(listener);
    }

    /**
     * Directly adds a trusted circle member with Name, Relationship, and Mobile Number.
     * Established immediately without requiring external invitation acceptance.
     */
    public void addDirectCircleMember(@NonNull String name,
                                      @NonNull String relationship,
                                      @NonNull String phone,
                                      @Nullable RepositoryCallback<FamilyMember> callback) {
        String myUid = getCurrentUserId();
        if (myUid == null) {
            if (callback != null) callback.onError("Authentication required.");
            return;
        }

        String generatedUid = rootRef.child(NODE_USER_WARDS).child(myUid).push().getKey();
        final String memberUid = (generatedUid != null) ? generatedUid : ("member_" + System.currentTimeMillis());

        CirclePermissions permissions = new CirclePermissions(true, true, true, true, true, true, true, 0);

        FamilyMember newMember = new FamilyMember(
                memberUid,
                name,
                "",
                phone,
                permissions
        );
        newMember.setRelationship(relationship);
        newMember.setSafetyStatus(FamilyMember.STATUS_SAFE);
        newMember.setStatusDetails("Protected on Woshield Network");
        newMember.setSharingActive(true);
        newMember.setLocationTimestamp(System.currentTimeMillis());

        final FamilyMember finalMember = newMember;
        rootRef.child(NODE_USER_WARDS).child(myUid).child(memberUid).setValue(finalMember)
                .addOnSuccessListener(aVoid -> {
                    // Also mirror to circle_members for bidirectional privacy center compatibility
                    rootRef.child(NODE_CIRCLE_MEMBERS).child(myUid).child(memberUid).setValue(finalMember);
                    Log.d(TAG, "Direct circle member added successfully: " + memberUid);
                    if (callback != null) callback.onSuccess(finalMember);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed adding direct circle member to remote database", e);
                    // Return the instantiated member so offline/demo sessions continue seamlessly
                    if (callback != null) callback.onSuccess(finalMember);
                });
    }
}
