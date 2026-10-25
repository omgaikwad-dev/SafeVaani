package com.example.woshield;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PrivacyCenterActivity extends AppCompatActivity {

    private PrivacyRepository privacyRepo;

    // Header views
    private ImageView ivPrivacyHeroIcon;
    private TextView tvPrivacyStatusTitle;
    private TextView tvPrivacyStatusDesc;
    private MaterialButton btnStartTempSession;
    private MaterialButton btnStopAllSharing;

    // Sessions
    private RecyclerView rvSharingSessions;
    private TextView tvEmptySessions;
    private SharingSessionAdapter sessionsAdapter;
    private final List<SharingSession> cachedSessions = new ArrayList<>();

    // Trusted Members
    private RecyclerView rvTrustedMembers;
    private TextView tvEmptyMembers;
    private PrivacyMemberAdapter membersAdapter;
    private final List<FamilyMember> cachedMembers = new ArrayList<>();

    // Audio & Mic Privacy
    private TextView tvMicPermBadge;
    private TextView tvCachedAudioInfo;
    private MaterialButton btnClearAudioCache;

    // Data Retention Controls
    private MaterialButton btnPurgeExpiredSessions;
    private MaterialButton btnClearAuditLogs;
    private TextView tvPurgeSessionsShortcut;

    // Audit logs
    private RecyclerView rvPrivacyLogs;
    private TextView tvEmptyLogs;
    private PrivacyActivityLogAdapter logsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_center);

        privacyRepo = new PrivacyRepository();

        if (privacyRepo.getCurrentUserId() == null) {
            Toast.makeText(this, "Please sign in to access Privacy Center.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupRecyclerViews();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllData();
        checkMicrophoneAndCacheStatus();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar_privacy);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivPrivacyHeroIcon = findViewById(R.id.iv_privacy_hero_icon);
        tvPrivacyStatusTitle = findViewById(R.id.tv_privacy_status_title);
        tvPrivacyStatusDesc = findViewById(R.id.tv_privacy_status_desc);
        btnStartTempSession = findViewById(R.id.btn_start_temporary_session);
        btnStopAllSharing = findViewById(R.id.btn_stop_all_sharing);

        rvSharingSessions = findViewById(R.id.rv_sharing_sessions);
        tvEmptySessions = findViewById(R.id.tv_empty_sessions);
        tvPurgeSessionsShortcut = findViewById(R.id.tv_purge_sessions_shortcut);

        rvTrustedMembers = findViewById(R.id.rv_trusted_members);
        tvEmptyMembers = findViewById(R.id.tv_empty_members);

        tvMicPermBadge = findViewById(R.id.tv_mic_perm_badge);
        tvCachedAudioInfo = findViewById(R.id.tv_cached_audio_info);
        btnClearAudioCache = findViewById(R.id.btn_clear_audio_cache);

        btnPurgeExpiredSessions = findViewById(R.id.btn_purge_expired_sessions);
        btnClearAuditLogs = findViewById(R.id.btn_clear_audit_logs);

        rvPrivacyLogs = findViewById(R.id.rv_privacy_logs);
        tvEmptyLogs = findViewById(R.id.tv_empty_logs);
    }

    private void setupRecyclerViews() {
        // Sessions
        sessionsAdapter = new SharingSessionAdapter(this, this::confirmRevokeSession);
        rvSharingSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSharingSessions.setAdapter(sessionsAdapter);

        // Members
        membersAdapter = new PrivacyMemberAdapter(this, this::showEditPermissionsDialog);
        rvTrustedMembers.setLayoutManager(new LinearLayoutManager(this));
        rvTrustedMembers.setAdapter(membersAdapter);

        // Audit Logs
        logsAdapter = new PrivacyActivityLogAdapter(this);
        rvPrivacyLogs.setLayoutManager(new LinearLayoutManager(this));
        rvPrivacyLogs.setAdapter(logsAdapter);
    }

    private void setupClickListeners() {
        btnStartTempSession.setOnClickListener(v -> showStartSharingDialog());
        btnStopAllSharing.setOnClickListener(v -> confirmStopAllSharing());

        btnClearAudioCache.setOnClickListener(v -> clearAudioCache());

        btnPurgeExpiredSessions.setOnClickListener(v -> purgeExpiredSessions());
        tvPurgeSessionsShortcut.setOnClickListener(v -> purgeExpiredSessions());

        btnClearAuditLogs.setOnClickListener(v -> confirmClearAuditLogs());
    }

    private void loadAllData() {
        loadSharingSessions();
        loadCircleMembers();
        loadPrivacyLogs();
    }

    private void loadSharingSessions() {
        privacyRepo.getSharingSessions(new PrivacyRepository.PrivacyCallback<List<SharingSession>>() {
            @Override
            public void onSuccess(List<SharingSession> sessions) {
                cachedSessions.clear();
                if (sessions != null) {
                    cachedSessions.addAll(sessions);
                }

                sessionsAdapter.setSessions(cachedSessions);

                boolean hasSessions = !cachedSessions.isEmpty();
                tvEmptySessions.setVisibility(hasSessions ? View.GONE : View.VISIBLE);
                rvSharingSessions.setVisibility(hasSessions ? View.VISIBLE : View.GONE);

                updateHeroPrivacyStatus();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PrivacyCenterActivity.this, "Failed loading sessions: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateHeroPrivacyStatus() {
        int activeCount = 0;
        for (SharingSession s : cachedSessions) {
            if (s.isActive()) activeCount++;
        }

        if (activeCount > 0) {
            tvPrivacyStatusTitle.setText("Live Location Sharing ACTIVE");
            tvPrivacyStatusTitle.setTextColor(Color.parseColor("#FB8C00"));
            tvPrivacyStatusDesc.setText(activeCount + " active session(s) transmitting real-time coordinates.");
            ivPrivacyHeroIcon.setColorFilter(Color.parseColor("#FB8C00"));
            btnStopAllSharing.setEnabled(true);
        } else {
            tvPrivacyStatusTitle.setText("Location: Completely Private");
            tvPrivacyStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvPrivacyStatusDesc.setText("No active location sharing sessions. Live tracking is idle.");
            ivPrivacyHeroIcon.setColorFilter(ContextCompat.getColor(this, R.color.vibrant_purple));
            btnStopAllSharing.setEnabled(false);
        }
    }

    private void loadCircleMembers() {
        privacyRepo.getCircleMembers(new PrivacyRepository.PrivacyCallback<List<FamilyMember>>() {
            @Override
            public void onSuccess(List<FamilyMember> members) {
                cachedMembers.clear();
                if (members != null) {
                    cachedMembers.addAll(members);
                }

                membersAdapter.setMembers(cachedMembers);

                boolean hasMembers = !cachedMembers.isEmpty();
                tvEmptyMembers.setVisibility(hasMembers ? View.GONE : View.VISIBLE);
                rvTrustedMembers.setVisibility(hasMembers ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PrivacyCenterActivity.this, "Failed loading members: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPrivacyLogs() {
        privacyRepo.getPrivacyLogs(new PrivacyRepository.PrivacyCallback<List<PrivacyActivityLog>>() {
            @Override
            public void onSuccess(List<PrivacyActivityLog> logs) {
                logsAdapter.setLogs(logs);
                boolean hasLogs = logs != null && !logs.isEmpty();
                tvEmptyLogs.setVisibility(hasLogs ? View.GONE : View.VISIBLE);
                rvPrivacyLogs.setVisibility(hasLogs ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                // Silently handle log query error
            }
        });
    }

    // =========================================================
    // TEMPORARY SHARING SESSION ACTIONS
    // =========================================================

    private void showStartSharingDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_start_sharing, null);
        TextInputEditText etTitle = dialogView.findViewById(R.id.et_session_title);
        RadioGroup rgDuration = dialogView.findViewById(R.id.rg_duration);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_session);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_start_session);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                title = "Temporary Sharing Session";
            }

            long durationMillis = 60 * 60 * 1000L; // default 1h
            int checkedId = rgDuration.getCheckedRadioButtonId();
            if (checkedId == R.id.rb_duration_30m) {
                durationMillis = 30 * 60 * 1000L;
            } else if (checkedId == R.id.rb_duration_1h) {
                durationMillis = 60 * 60 * 1000L;
            } else if (checkedId == R.id.rb_duration_4h) {
                durationMillis = 4 * 60 * 60 * 1000L;
            } else if (checkedId == R.id.rb_duration_8h) {
                durationMillis = 8 * 60 * 60 * 1000L;
            } else if (checkedId == R.id.rb_duration_manual) {
                durationMillis = 0; // manual revoke only
            }

            // Gather all circle member UIDs & Names
            List<String> recipientUids = new ArrayList<>();
            List<String> recipientNames = new ArrayList<>();
            for (FamilyMember m : cachedMembers) {
                if (m.getUid() != null) {
                    recipientUids.add(m.getUid());
                    recipientNames.add(m.getDisplayName() != null ? m.getDisplayName() : "Member");
                }
            }

            dialog.dismiss();

            privacyRepo.startSharingSession(title, durationMillis, recipientUids, recipientNames,
                    new PrivacyRepository.PrivacyCallback<SharingSession>() {
                        @Override
                        public void onSuccess(SharingSession session) {
                            Toast.makeText(PrivacyCenterActivity.this, "Sharing session started.", Toast.LENGTH_SHORT).show();
                            loadSharingSessions();
                            loadPrivacyLogs();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(PrivacyCenterActivity.this, "Error starting session: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.show();
    }

    private void confirmRevokeSession(SharingSession session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Revoke Sharing Session?")
                .setMessage("This will immediately terminate access for all authorized recipients and stop location transmission.")
                .setPositiveButton("Revoke Now", (dialog, which) -> {
                    privacyRepo.revokeSharingSession(session.getSessionId(), new PrivacyRepository.PrivacyCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(PrivacyCenterActivity.this, "Session revoked.", Toast.LENGTH_SHORT).show();
                            loadSharingSessions();
                            loadPrivacyLogs();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(PrivacyCenterActivity.this, "Error revoking session: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmStopAllSharing() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Stop All Location Sharing?")
                .setMessage("All active temporary sessions will be revoked immediately and live coordinates removed from the database.")
                .setPositiveButton("Stop Everything", (dialog, which) -> {
                    privacyRepo.stopAllSharingSessions(new PrivacyRepository.PrivacyCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(PrivacyCenterActivity.this, "All location sharing stopped.", Toast.LENGTH_SHORT).show();
                            loadSharingSessions();
                            loadPrivacyLogs();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(PrivacyCenterActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Keep Active", null)
                .show();
    }

    // =========================================================
    // PER-MEMBER PERMISSIONS DIALOG
    // =========================================================

    private void showEditPermissionsDialog(FamilyMember member) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_permissions, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_perm_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_dialog_perm_subtitle);

        SwitchMaterial swStatus = dialogView.findViewById(R.id.switch_perm_status);
        SwitchMaterial swLocation = dialogView.findViewById(R.id.switch_perm_location);
        SwitchMaterial swJourney = dialogView.findViewById(R.id.switch_perm_journey);
        SwitchMaterial swSos = dialogView.findViewById(R.id.switch_perm_sos);
        SwitchMaterial swAssistance = dialogView.findViewById(R.id.switch_perm_assistance);
        SwitchMaterial swCheckIn = dialogView.findViewById(R.id.switch_perm_missed_checkin);

        MaterialButton btnBlockAll = dialogView.findViewById(R.id.btn_revoke_all_perm);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_permissions);

        String memberName = member.getDisplayName() != null ? member.getDisplayName() : "Circle Member";
        tvTitle.setText("Permissions: " + memberName);
        tvSubtitle.setText("Control what " + memberName + " can see or receive from your device.");

        CirclePermissions currentPerms = member.getPermissions();
        if (currentPerms != null) {
            swStatus.setChecked(currentPerms.isCanViewStatus());
            swLocation.setChecked(currentPerms.isCanViewLocation());
            swJourney.setChecked(currentPerms.isCanViewJourney());
            swSos.setChecked(currentPerms.isCanReceiveSos());
            swAssistance.setChecked(currentPerms.isCanReceiveAssistance());
            swCheckIn.setChecked(currentPerms.isCanReceiveMissedCheckIns());
        } else {
            swStatus.setChecked(true);
            swLocation.setChecked(true);
            swJourney.setChecked(true);
            swSos.setChecked(true);
            swAssistance.setChecked(true);
            swCheckIn.setChecked(true);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnBlockAll.setOnClickListener(v -> {
            swStatus.setChecked(false);
            swLocation.setChecked(false);
            swJourney.setChecked(false);
            swSos.setChecked(false);
            swAssistance.setChecked(false);
            swCheckIn.setChecked(false);
        });

        btnSave.setOnClickListener(v -> {
            CirclePermissions updated = new CirclePermissions(
                    swStatus.isChecked(),
                    swLocation.isChecked(),
                    swJourney.isChecked(),
                    swSos.isChecked(),
                    swAssistance.isChecked(),
                    swCheckIn.isChecked(),
                    true, // routine updates
                    0     // no expiration
            );

            dialog.dismiss();

            privacyRepo.updateMemberPermissions(member.getUid(), memberName, updated, new PrivacyRepository.PrivacyCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(PrivacyCenterActivity.this, "Permissions saved for " + memberName, Toast.LENGTH_SHORT).show();
                    loadCircleMembers();
                    loadPrivacyLogs();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(PrivacyCenterActivity.this, "Error saving permissions: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    // =========================================================
    // AUDIO & CACHE MANAGEMENT
    // =========================================================

    private void checkMicrophoneAndCacheStatus() {
        boolean micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (micGranted) {
            tvMicPermBadge.setText("ACTIVE / GRANTED");
            tvMicPermBadge.setBackgroundResource(R.drawable.badge_safe);
            tvMicPermBadge.setTextColor(ContextCompat.getColor(this, R.color.safe_green));
        } else {
            tvMicPermBadge.setText("NOT GRANTED");
            tvMicPermBadge.setBackgroundResource(R.drawable.badge_routine);
            tvMicPermBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }

        // Check local cache files
        int count = 0;
        long bytes = 0;
        File[] dirs = {getCacheDir(), getExternalCacheDir()};
        for (File dir : dirs) {
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles((d, name) -> name.startsWith("sos_manual_") && name.endsWith(".m4a"));
                if (files != null) {
                    for (File f : files) {
                        count++;
                        bytes += f.length();
                    }
                }
            }
        }

        if (count > 0) {
            tvCachedAudioInfo.setText("Cached Evidence: " + count + " file(s) (" + (bytes / 1024) + " KB)");
            btnClearAudioCache.setEnabled(true);
        } else {
            tvCachedAudioInfo.setText("Cached Evidence: 0 files (Storage Clean)");
            btnClearAudioCache.setEnabled(false);
        }
    }

    private void clearAudioCache() {
        PrivacyRepository.PurgeResult result = privacyRepo.clearLocalAudioCache(this);
        Toast.makeText(this, "Cleared " + result.filesDeleted + " cached file(s) (" + (result.bytesFreed / 1024) + " KB reclaimed).", Toast.LENGTH_LONG).show();
        checkMicrophoneAndCacheStatus();
        loadPrivacyLogs();
    }

    private void purgeExpiredSessions() {
        privacyRepo.purgeExpiredSessions(new PrivacyRepository.PrivacyCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                Toast.makeText(PrivacyCenterActivity.this, "Purged " + count + " expired session(s).", Toast.LENGTH_SHORT).show();
                loadSharingSessions();
                loadPrivacyLogs();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PrivacyCenterActivity.this, "Error purging sessions: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmClearAuditLogs() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Privacy Audit Log?")
                .setMessage("All past security event history will be removed. This cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    privacyRepo.clearPrivacyLogs(new PrivacyRepository.PrivacyCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(PrivacyCenterActivity.this, "Audit logs cleared.", Toast.LENGTH_SHORT).show();
                            loadPrivacyLogs();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(PrivacyCenterActivity.this, "Error clearing logs: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
