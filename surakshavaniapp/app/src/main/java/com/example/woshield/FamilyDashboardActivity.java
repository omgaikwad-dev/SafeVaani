package com.example.woshield;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FamilyDashboardActivity extends AppCompatActivity {
    private static final String TAG = "FamilyDashboardAct";

    // UI Views
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutActiveAlertsContainer;
    private MaterialCardView cardPendingInvitesBanner;
    private TextView tvPendingInvitesCount;
    private MaterialButton btnReviewInvites;
    private TextView tvProtectedCountBadge;
    private ProgressBar progressDashboard;
    private RecyclerView rvFamilyMembers;
    private MaterialCardView cardEmptyDashboard;
    private MaterialButton btnEmptyInvite;
    private MaterialButton btnManageMySharing;
    private ExtendedFloatingActionButton fabInviteMember;

    // Repositories & Data
    private FamilyCircleRepository circleRepository;
    private FamilyAlertRepository alertRepository;
    private FamilyDashboardAdapter adapter;
    private final List<FamilyMember> protectedWardsList = new ArrayList<>();
    private final List<CircleInvitation> pendingInvitationsList = new ArrayList<>();

    // Realtime Listeners Map for clean lifecycle management
    private final Map<String, ValueEventListener> locationListeners = new HashMap<>();
    private final Map<String, ValueEventListener> checkInListeners = new HashMap<>();
    private final Map<String, ValueEventListener> alertsListeners = new HashMap<>();

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            if (!DemoDataSeeder.isDemoMode(this)) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_family_dashboard);

        circleRepository = new FamilyCircleRepository();
        alertRepository = new FamilyAlertRepository();

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupActions();

        loadDashboardData();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        layoutActiveAlertsContainer = findViewById(R.id.layout_active_alerts_container);
        cardPendingInvitesBanner = findViewById(R.id.card_pending_invites_banner);
        tvPendingInvitesCount = findViewById(R.id.tv_pending_invites_count);
        btnReviewInvites = findViewById(R.id.btn_review_invites);
        tvProtectedCountBadge = findViewById(R.id.tv_protected_count_badge);
        progressDashboard = findViewById(R.id.progress_dashboard);
        rvFamilyMembers = findViewById(R.id.rv_family_members);
        cardEmptyDashboard = findViewById(R.id.card_empty_dashboard);
        btnEmptyInvite = findViewById(R.id.btn_empty_invite);
        btnManageMySharing = findViewById(R.id.btn_manage_my_sharing);
        fabInviteMember = findViewById(R.id.fab_invite_member);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_family_dashboard);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new FamilyDashboardAdapter(protectedWardsList, new FamilyDashboardAdapter.OnMemberActionListener() {
            @Override
            public void onMemberClick(FamilyMember member) {
                showMemberOptionsDialog(member);
            }

            @Override
            public void onRevokeAccess(FamilyMember member) {
                confirmRevokeMembership(member);
            }
        });
        rvFamilyMembers.setLayoutManager(new LinearLayoutManager(this));
        rvFamilyMembers.setAdapter(adapter);
    }

    private void setupActions() {
        swipeRefresh.setOnRefreshListener(this::loadDashboardData);
        fabInviteMember.setOnClickListener(v -> showInviteMemberDialog());
        btnEmptyInvite.setOnClickListener(v -> showInviteMemberDialog());
        btnReviewInvites.setOnClickListener(v -> showPendingInvitationsDialog());
        cardPendingInvitesBanner.setOnClickListener(v -> showPendingInvitationsDialog());
        btnManageMySharing.setOnClickListener(v -> showManageSharingDialog());
    }

    private void loadDashboardData() {
        progressDashboard.setVisibility(View.VISIBLE);
        cleanupRealtimeListeners();
        layoutActiveAlertsContainer.removeAllViews();
        layoutActiveAlertsContainer.setVisibility(View.GONE);

        // 1. Fetch pending invitations for me
        circleRepository.getPendingInvitationsForMe(new FamilyCircleRepository.RepositoryCallback<List<CircleInvitation>>() {
            @Override
            public void onSuccess(List<CircleInvitation> invites) {
                pendingInvitationsList.clear();
                pendingInvitationsList.addAll(invites);
                updateInvitationsBanner();
            }

            @Override
            public void onError(String errorMessage) {
                cardPendingInvitesBanner.setVisibility(View.GONE);
            }
        });

        // 2. Fetch authorized protected wards
        circleRepository.getMyProtectedWards(new FamilyCircleRepository.RepositoryCallback<List<FamilyMember>>() {
            @Override
            public void onSuccess(List<FamilyMember> wards) {
                progressDashboard.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                protectedWardsList.clear();
                protectedWardsList.addAll(wards);
                adapter.notifyDataSetChanged();
                updateUIState();

                // Attach real-time observers to each authorized ward
                for (FamilyMember ward : wards) {
                    attachWardObservers(ward);
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressDashboard.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                updateUIState();
                Toast.makeText(FamilyDashboardActivity.this, "Failed to load circle members: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateInvitationsBanner() {
        if (!pendingInvitationsList.isEmpty()) {
            cardPendingInvitesBanner.setVisibility(View.VISIBLE);
            tvPendingInvitesCount.setText("You have " + pendingInvitationsList.size() + " pending circle invitation(s) to review.");
        } else {
            cardPendingInvitesBanner.setVisibility(View.GONE);
        }
    }

    private void updateUIState() {
        int count = protectedWardsList.size();
        tvProtectedCountBadge.setText(count + (count == 1 ? " Member" : " Members"));

        if (count == 0) {
            cardEmptyDashboard.setVisibility(View.VISIBLE);
            rvFamilyMembers.setVisibility(View.GONE);
        } else {
            cardEmptyDashboard.setVisibility(View.GONE);
            rvFamilyMembers.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Attaches granular realtime listeners to an authorized ward.
     */
    private void attachWardObservers(FamilyMember ward) {
        String wardUid = ward.getUid();
        if (wardUid == null || wardUid.isEmpty()) return;

        CirclePermissions perms = ward.getPermissions();

        // 1. Live Location Listener (if authorized)
        if (perms != null && perms.isCanViewLocation()) {
            ValueEventListener locListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isFinishing() || isDestroyed()) return;
                    if (snapshot.exists()) {
                        Double lat = snapshot.child("latitude").getValue(Double.class);
                        Double lon = snapshot.child("longitude").getValue(Double.class);
                        if (lat != null && lon != null) {
                            ward.setLatitude(lat);
                            ward.setLongitude(lon);
                            ward.setSharingActive(true);
                            ward.setLocationTimestamp(System.currentTimeMillis());
                            adapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Location listener cancelled for: " + wardUid);
                }
            };
            circleRepository.listenToWardLocation(wardUid, locListener);
            locationListeners.put(wardUid, locListener);
        }

        // 2. Check-In Status Listener (if authorized)
        if (perms != null && perms.isCanViewStatus()) {
            ValueEventListener checkInListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isFinishing() || isDestroyed()) return;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        CheckIn checkIn = child.getValue(CheckIn.class);
                        if (checkIn != null) {
                            applyCheckInToWard(ward, checkIn);
                            adapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Check-in listener cancelled for: " + wardUid);
                }
            };
            circleRepository.listenToWardLatestCheckIn(wardUid, checkInListener);
            checkInListeners.put(wardUid, checkInListener);
        }

        // 3. Alerts Listener (if authorized for SOS or Assistance)
        if (perms != null && (perms.isCanReceiveSos() || perms.isCanReceiveAssistance() || perms.isCanReceiveMissedCheckIns())) {
            ValueEventListener alertListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (isFinishing() || isDestroyed()) return;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String alertId = child.getKey();
                        String statusMsg = child.child("status").getValue(String.class);
                        String ackStatus = child.child("ackStatus").getValue(String.class);

                        // If unacknowledged emergency or recent alert
                        if (statusMsg != null && !FamilyAlert.STATUS_ACKNOWLEDGED.equals(ackStatus)) {
                            Double lat = child.child("latitude").getValue(Double.class);
                            Double lon = child.child("longitude").getValue(Double.class);
                            String audioUrl = child.child("audioUrl").getValue(String.class);

                            FamilyAlert alert = new FamilyAlert(
                                    alertId,
                                    wardUid,
                                    ward.getDisplayName(),
                                    statusMsg.contains("SOS") ? FamilyAlert.TYPE_SOS : FamilyAlert.TYPE_ASSISTANCE,
                                    statusMsg,
                                    lat != null ? lat : 0,
                                    lon != null ? lon : 0,
                                    audioUrl
                            );
                            displayActiveEmergencyAlert(alert);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Alerts listener cancelled for: " + wardUid);
                }
            };
            circleRepository.listenToWardAlerts(wardUid, alertListener);
            alertsListeners.put(wardUid, alertListener);
        }
    }

    private void applyCheckInToWard(FamilyMember ward, CheckIn checkIn) {
        String status = checkIn.getStatus();
        if (CheckIn.STATUS_PENDING.equals(status)) {
            ward.setSafetyStatus(FamilyMember.STATUS_PENDING_CHECKIN);
            ward.setStatusDetails("Safety Check-In Due: " + checkIn.getReason());
        } else if (CheckIn.STATUS_CONFIRMED_SAFE.equals(status)) {
            ward.setSafetyStatus(FamilyMember.STATUS_SAFE);
            ward.setStatusDetails("Confirmed Safe (" + timeFormat.format(new Date(checkIn.getRespondedAt())) + ")");
        } else if (CheckIn.STATUS_ASSISTANCE_REQUESTED.equals(status)) {
            ward.setSafetyStatus(FamilyMember.STATUS_ASSISTANCE_NEEDED);
            ward.setStatusDetails("⚠️ Assistance Requested: " + checkIn.getReason());
        } else if (CheckIn.STATUS_SOS_TRIGGERED.equals(status)) {
            ward.setSafetyStatus(FamilyMember.STATUS_SOS_ACTIVE);
            ward.setStatusDetails("🚨 EMERGENCY SOS DISPATCHED");
        } else if (CheckIn.STATUS_MISSED.equals(status)) {
            ward.setSafetyStatus(FamilyMember.STATUS_ASSISTANCE_NEEDED);
            ward.setStatusDetails("⚠️ Missed Check-In Deadline: " + checkIn.getReason());
        }

        if (checkIn.getRoutineTitle() != null && !checkIn.getRoutineTitle().isEmpty()) {
            ward.setActiveRoutineTitle(checkIn.getRoutineTitle());
            ward.setExpectedArrival(timeFormat.format(new Date(checkIn.getDeadlineAt())));
        }
    }

    private void displayActiveEmergencyAlert(FamilyAlert alert) {
        layoutActiveAlertsContainer.setVisibility(View.VISIBLE);

        View alertView = LayoutInflater.from(this).inflate(R.layout.item_family_alert, layoutActiveAlertsContainer, false);
        TextView tvTitle = alertView.findViewById(R.id.tv_alert_title);
        TextView tvWardName = alertView.findViewById(R.id.tv_alert_ward_name);
        TextView tvMessage = alertView.findViewById(R.id.tv_alert_message);
        TextView tvTime = alertView.findViewById(R.id.tv_alert_time);
        MaterialButton btnMap = alertView.findViewById(R.id.btn_alert_map);
        MaterialButton btnAck = alertView.findViewById(R.id.btn_acknowledge_alert);

        tvTitle.setText(alert.isEmergency() ? "🚨 EMERGENCY SOS ACTIVE" : "⚠️ ASSISTANCE REQUESTED");
        tvWardName.setText("From: " + alert.getWardName());
        tvMessage.setText(alert.getMessage());
        tvTime.setText(timeFormat.format(new Date(alert.getCreatedAt())));

        btnMap.setOnClickListener(v -> {
            if (alert.getLatitude() != 0 && alert.getLongitude() != 0) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + alert.getLatitude() + "," + alert.getLongitude()));
                startActivity(intent);
            }
        });

        btnAck.setOnClickListener(v -> {
            alertRepository.acknowledgeAlert(alert.getWardUid(), alert.getAlertId(), new FamilyAlertRepository.AlertCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(FamilyDashboardActivity.this, "Alert Acknowledged", Toast.LENGTH_SHORT).show();
                    layoutActiveAlertsContainer.removeView(alertView);
                    if (layoutActiveAlertsContainer.getChildCount() == 0) {
                        layoutActiveAlertsContainer.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(FamilyDashboardActivity.this, "Failed acknowledging: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        layoutActiveAlertsContainer.removeAllViews();
        layoutActiveAlertsContainer.addView(alertView);
    }

    /**
     * Dialog to directly add a trusted circle member with Name, Relation, and Mobile Number.
     */
    private void showInviteMemberDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_invite_member, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_invite_name);
        AutoCompleteTextView actvRelation = dialogView.findViewById(R.id.actv_invite_relation);
        TextInputEditText etPhone = dialogView.findViewById(R.id.et_invite_phone);

        String[] commonRelations = new String[]{
                "Mother", "Father", "Sister", "Brother", "Spouse",
                "Daughter", "Son", "Friend", "Guardian", "Relative"
        };
        ArrayAdapter<String> relationAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, commonRelations
        );
        actvRelation.setAdapter(relationAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Add Member", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveBtn.setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                String relation = actvRelation.getText() != null ? actvRelation.getText().toString().trim() : "";
                String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

                if (name.isEmpty()) {
                    etName.setError("Name is required");
                    etName.requestFocus();
                    return;
                }

                if (phone.isEmpty() || phone.length() < 7) {
                    etPhone.setError("Enter a valid mobile number");
                    etPhone.requestFocus();
                    return;
                }

                if (relation.isEmpty()) {
                    relation = "Family Member";
                }

                final String finalRelation = relation;
                progressDashboard.setVisibility(View.VISIBLE);

                circleRepository.addDirectCircleMember(name, finalRelation, phone, new FamilyCircleRepository.RepositoryCallback<FamilyMember>() {
                    @Override
                    public void onSuccess(FamilyMember member) {
                        progressDashboard.setVisibility(View.GONE);
                        // Add directly to local list if not already present
                        boolean alreadyPresent = false;
                        for (FamilyMember m : protectedWardsList) {
                            if (m.getUid() != null && m.getUid().equals(member.getUid())) {
                                alreadyPresent = true;
                                break;
                            }
                        }
                        if (!alreadyPresent) {
                            protectedWardsList.add(0, member);
                            adapter.notifyItemInserted(0);
                            rvFamilyMembers.scrollToPosition(0);
                            updateUIState();
                            attachWardObservers(member);
                        }

                        // Also save to emergency contacts in SharedPreferences so SOS & Buzzer immediately protect this number
                        addLocalEmergencyContact(name, finalRelation, phone);

                        Toast.makeText(FamilyDashboardActivity.this, name + " directly added to your circle!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progressDashboard.setVisibility(View.GONE);
                        Toast.makeText(FamilyDashboardActivity.this, "Failed adding member: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        dialog.show();
    }

    private void addLocalEmergencyContact(String name, String relation, String phone) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String contactsJson = prefs.getString("emergency_contacts", "[]");
            JSONArray array = new JSONArray(contactsJson);

            boolean exists = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (phone.equals(obj.optString("number"))) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                JSONObject contact = new JSONObject();
                String displayName = name + (relation != null && !relation.isEmpty() ? " (" + relation + ")" : "");
                contact.put("name", displayName);
                contact.put("number", phone);
                array.put(contact);
                prefs.edit().putString("emergency_contacts", array.toString()).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving local emergency contact", e);
        }
    }

    /**
     * Dialog to review and accept/reject pending invitations.
     */
    private void showPendingInvitationsDialog() {
        if (pendingInvitationsList.isEmpty()) {
            Toast.makeText(this, "No pending invitations.", Toast.LENGTH_SHORT).show();
            return;
        }

        CircleInvitation invitation = pendingInvitationsList.get(0);

        String message = "From: " + invitation.getSenderName() + " (" + invitation.getSenderEmail() + ")\n\n"
                + "Inviting you to join their Trusted Safety Circle.\n"
                + "Permissions: View Status, Live Location, and receive Emergency Alerts.";

        new AlertDialog.Builder(this)
                .setTitle("Circle Invitation")
                .setMessage(message)
                .setPositiveButton("Accept & Monitor", (dialog, which) -> {
                    circleRepository.acceptInvitation(invitation, new FamilyCircleRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(FamilyDashboardActivity.this, "Invitation Accepted! Circle established.", Toast.LENGTH_LONG).show();
                            loadDashboardData();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(FamilyDashboardActivity.this, "Error accepting: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Decline", (dialog, which) -> {
                    circleRepository.rejectInvitation(invitation, new FamilyCircleRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(FamilyDashboardActivity.this, "Invitation Declined", Toast.LENGTH_SHORT).show();
                            loadDashboardData();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(FamilyDashboardActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNeutralButton("Later", null)
                .show();
    }

    private void showMemberOptionsDialog(FamilyMember member) {
        String[] options = {"Open Live Location in Maps", "Call Member", "Revoke Circle Access"};
        new AlertDialog.Builder(this)
                .setTitle(member.getDisplayName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (member.getLatitude() != 0 && member.getLongitude() != 0) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + member.getLatitude() + "," + member.getLongitude()));
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "Location unavailable for " + member.getDisplayName(), Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 1) {
                        if (member.getPhone() != null && !member.getPhone().isEmpty()) {
                            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + member.getPhone())));
                        } else {
                            Toast.makeText(this, "Phone number unavailable.", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 2) {
                        confirmRevokeMembership(member);
                    }
                })
                .show();
    }

    private void confirmRevokeMembership(FamilyMember member) {
        new AlertDialog.Builder(this)
                .setTitle("Revoke Access")
                .setMessage("Are you sure you want to revoke circle access for " + member.getDisplayName() + "? All shared location and status updates will stop immediately.")
                .setPositiveButton("Revoke", (dialog, which) -> {
                    circleRepository.revokeMembership(member.getUid(), new FamilyCircleRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(FamilyDashboardActivity.this, "Access Revoked for " + member.getDisplayName(), Toast.LENGTH_LONG).show();
                            loadDashboardData();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(FamilyDashboardActivity.this, "Failed to revoke: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManageSharingDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Manage Safety Circle")
                .setMessage("To add a new family member, tap '+ Invite Member'. To revoke an active member's access, tap on their card and select 'Revoke Circle Access'.")
                .setPositiveButton("Invite New", (dialog, which) -> showInviteMemberDialog())
                .setNegativeButton("Close", null)
                .show();
    }

    private void cleanupRealtimeListeners() {
        for (Map.Entry<String, ValueEventListener> entry : locationListeners.entrySet()) {
            circleRepository.removeWardLocationListener(entry.getKey(), entry.getValue());
        }
        locationListeners.clear();

        for (Map.Entry<String, ValueEventListener> entry : checkInListeners.entrySet()) {
            circleRepository.removeWardCheckInListener(entry.getKey(), entry.getValue());
        }
        checkInListeners.clear();

        for (Map.Entry<String, ValueEventListener> entry : alertsListeners.entrySet()) {
            circleRepository.removeWardAlertsListener(entry.getKey(), entry.getValue());
        }
        alertsListeners.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupRealtimeListeners();
    }
}
