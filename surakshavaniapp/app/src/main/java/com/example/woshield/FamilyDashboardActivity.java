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
                confirmRemoveMember(member);
            }

            @Override
            public void onRemoveMember(FamilyMember member, int position) {
                confirmRemoveMember(member);
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
        cleanupRealtimeListeners();
        layoutActiveAlertsContainer.removeAllViews();
        layoutActiveAlertsContainer.setVisibility(View.GONE);

        // 1. Instantly load local guardians from SharedPreferences so contacts are immediately visible
        loadLocalWardsAndContacts();

        // 2. Fetch pending invitations for me
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

        // 3. Query remote Firebase wards in the background and merge
        circleRepository.getMyProtectedWards(new FamilyCircleRepository.RepositoryCallback<List<FamilyMember>>() {
            @Override
            public void onSuccess(List<FamilyMember> wards) {
                progressDashboard.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                if (wards != null && !wards.isEmpty()) {
                    mergeRemoteWards(wards);
                }
                updateUIState();

                // Attach real-time observers to each authorized ward
                for (FamilyMember ward : protectedWardsList) {
                    attachWardObservers(ward);
                }
            }

            @Override
            public void onError(String errorMessage) {
                progressDashboard.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                updateUIState();
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
                .setPositiveButton("Add Guardian", null)
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
                    etPhone.setError("Enter a valid mobile number (min 7 digits)");
                    etPhone.requestFocus();
                    return;
                }

                if (relation.isEmpty()) {
                    relation = "Guardian";
                }

                final String finalRelation = relation;
                final String cleanPhone = phone.replaceAll("[^0-9+]", "");
                final String generatedUid = "guardian_" + System.currentTimeMillis();

                // 1. Create member and add locally IMMEDIATELY (no network wait)
                CirclePermissions permissions = new CirclePermissions(true, true, true, true, true, true, true, 0);
                FamilyMember newMember = new FamilyMember(
                        generatedUid,
                        name,
                        "",
                        cleanPhone,
                        permissions
                );
                newMember.setRelationship(finalRelation);
                newMember.setSafetyStatus(FamilyMember.STATUS_SAFE);
                newMember.setStatusDetails("Protected on Woshield Network");
                newMember.setSharingActive(true);
                newMember.setLocationTimestamp(System.currentTimeMillis());

                // Deduplicate in list by phone
                for (int i = protectedWardsList.size() - 1; i >= 0; i--) {
                    FamilyMember existing = protectedWardsList.get(i);
                    if (existing.getPhone() != null && existing.getPhone().equals(cleanPhone)) {
                        protectedWardsList.remove(i);
                    }
                }

                protectedWardsList.add(0, newMember);
                adapter.notifyDataSetChanged();
                rvFamilyMembers.scrollToPosition(0);
                updateUIState();
                attachWardObservers(newMember);

                // 2. Persist locally to SharedPreferences for SOS alerts and persistence
                addLocalEmergencyContact(name, finalRelation, cleanPhone, generatedUid);
                saveLocalWards();

                // 3. Dismiss dialog & show success toast immediately
                Toast.makeText(FamilyDashboardActivity.this, "✓ " + name + " added as Guardian!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                // 4. Asynchronously sync to Firebase Realtime Database
                circleRepository.addDirectCircleMember(name, finalRelation, cleanPhone, null);
            });
        });

        dialog.show();
    }

    private void loadLocalWardsAndContacts() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        List<FamilyMember> loaded = new ArrayList<>();

        // 1. Load from cached_family_wards
        String cachedJson = prefs.getString("cached_family_wards", "[]");
        try {
            JSONArray arr = new JSONArray(cachedJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                FamilyMember m = new FamilyMember(
                        obj.optString("uid", "member_" + i),
                        obj.optString("name", "Guardian"),
                        obj.optString("email", ""),
                        obj.optString("phone", ""),
                        new CirclePermissions(true, true, true, true, true, true, true, 0)
                );
                m.setRelationship(obj.optString("relation", "Guardian"));
                m.setSafetyStatus(obj.optString("status", FamilyMember.STATUS_SAFE));
                m.setStatusDetails(obj.optString("details", "Protected on Woshield Network"));
                m.setLatitude(obj.optDouble("latitude", 0.0));
                m.setLongitude(obj.optDouble("longitude", 0.0));
                m.setSharingActive(true);
                loaded.add(m);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached family wards", e);
        }

        // 2. Also check emergency_contacts to ensure no contact is missing
        String contactsJson = prefs.getString("emergency_contacts", "[]");
        try {
            JSONArray array = new JSONArray(contactsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String phone = obj.optString("number", obj.optString("phone", ""));
                String name = obj.optString("name", "Emergency Contact");
                String relation = obj.optString("relation", "Guardian");
                String uid = obj.optString("uid", "member_c_" + i);

                boolean alreadyInLoaded = false;
                for (FamilyMember existing : loaded) {
                    if (existing.getPhone() != null && existing.getPhone().equals(phone)) {
                        alreadyInLoaded = true;
                        break;
                    }
                }
                if (!alreadyInLoaded && !phone.isEmpty()) {
                    FamilyMember m = new FamilyMember(uid, name, "", phone, new CirclePermissions());
                    m.setRelationship(relation);
                    m.setSafetyStatus(FamilyMember.STATUS_SAFE);
                    m.setStatusDetails("Emergency Safety Contact");
                    loaded.add(m);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading emergency_contacts into wards", e);
        }

        protectedWardsList.clear();
        protectedWardsList.addAll(loaded);
        if (adapter != null) adapter.notifyDataSetChanged();
        updateUIState();
    }

    private void mergeRemoteWards(List<FamilyMember> remoteWards) {
        for (FamilyMember remote : remoteWards) {
            boolean exists = false;
            for (int i = 0; i < protectedWardsList.size(); i++) {
                FamilyMember local = protectedWardsList.get(i);
                if ((remote.getUid() != null && remote.getUid().equals(local.getUid())) ||
                    (remote.getPhone() != null && remote.getPhone().equals(local.getPhone()))) {
                    // Update live fields
                    local.setLatitude(remote.getLatitude());
                    local.setLongitude(remote.getLongitude());
                    local.setSafetyStatus(remote.getSafetyStatus());
                    local.setStatusDetails(remote.getStatusDetails());
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                protectedWardsList.add(remote);
            }
        }
        saveLocalWards();
        adapter.notifyDataSetChanged();
    }

    private void saveLocalWards() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            JSONArray arr = new JSONArray();
            for (FamilyMember m : protectedWardsList) {
                JSONObject obj = new JSONObject();
                obj.put("uid", m.getUid());
                obj.put("name", m.getDisplayName());
                obj.put("phone", m.getPhone());
                obj.put("email", m.getEmail());
                obj.put("relation", m.getRelationship());
                obj.put("status", m.getSafetyStatus());
                obj.put("details", m.getStatusDetails());
                obj.put("latitude", m.getLatitude());
                obj.put("longitude", m.getLongitude());
                arr.put(obj);
            }
            prefs.edit().putString("cached_family_wards", arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving local wards to prefs", e);
        }
    }

    private void addLocalEmergencyContact(String name, String relation, String phone, String uid) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String contactsJson = prefs.getString("emergency_contacts", "[]");
            JSONArray array = new JSONArray(contactsJson);

            // Remove any existing entry with the same number to prevent duplicates
            JSONArray cleanArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String num = obj.optString("number", obj.optString("phone", ""));
                if (!phone.equals(num)) {
                    cleanArray.put(obj);
                }
            }

            JSONObject contact = new JSONObject();
            contact.put("name", name);
            contact.put("number", phone);
            contact.put("relation", relation);
            contact.put("uid", uid);
            cleanArray.put(contact);

            prefs.edit().putString("emergency_contacts", cleanArray.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving local emergency contact", e);
        }
    }

    private void removeLocalEmergencyContact(String phone, String name) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String contactsJson = prefs.getString("emergency_contacts", "[]");
            JSONArray array = new JSONArray(contactsJson);
            JSONArray updated = new JSONArray();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String num = obj.optString("number", obj.optString("phone", ""));
                String cName = obj.optString("name", "");

                // Keep only if phone doesn't match and name doesn't match
                boolean isMatch = (phone != null && !phone.isEmpty() && phone.equals(num)) ||
                                  (name != null && !name.isEmpty() && name.equalsIgnoreCase(cName));
                if (!isMatch) {
                    updated.put(obj);
                }
            }
            prefs.edit().putString("emergency_contacts", updated.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error removing local emergency contact", e);
        }
    }

    private void confirmRemoveMember(FamilyMember member) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Guardian")
                .setMessage("Are you sure you want to remove " + member.getDisplayName() + " from your Guardians and Emergency Safety Circle?\n\nThey will no longer receive your SOS alerts.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    // 1. Remove from protected list
                    for (int i = protectedWardsList.size() - 1; i >= 0; i--) {
                        FamilyMember m = protectedWardsList.get(i);
                        if ((m.getUid() != null && m.getUid().equals(member.getUid())) ||
                            (m.getPhone() != null && m.getPhone().equals(member.getPhone()))) {
                            protectedWardsList.remove(i);
                        }
                    }

                    // 2. Remove from emergency contacts & cached wards in SharedPreferences
                    removeLocalEmergencyContact(member.getPhone(), member.getDisplayName());
                    saveLocalWards();

                    // 3. Update UI
                    adapter.notifyDataSetChanged();
                    updateUIState();

                    // 4. Remote revoke
                    if (member.getUid() != null) {
                        circleRepository.revokeMembership(member.getUid(), new FamilyCircleRepository.RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void result) {}
                            @Override public void onError(String errorMessage) {}
                        });
                    }

                    Toast.makeText(FamilyDashboardActivity.this, member.getDisplayName() + " removed from Guardians", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        String[] options = {"Open Live Location in Maps", "Call Guardian", "Remove from Guardians"};
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
                        confirmRemoveMember(member);
                    }
                })
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
