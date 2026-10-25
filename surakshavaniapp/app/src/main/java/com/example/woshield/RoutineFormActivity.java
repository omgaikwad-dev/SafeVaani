package com.example.woshield;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RoutineFormActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {"College", "Office", "Gym", "Commute", "Custom"};

    private EditText etTitle, etStartLoc, etDestination;
    private Spinner spinnerCategory;
    private TextView tvStartTimeVal, tvArrivalTimeVal, tvCalculatedDuration, tvSafeStopsLabel;

    private ToggleButton tbMon, tbTue, tbWed, tbThu, tbFri, tbSat, tbSun;
    private SwitchMaterial switchReminders, switchCheckIn;

    private Routine existingRoutine;
    private RoutineRepository routineRepository;
    private SafePlaceRepository safePlaceRepository;

    private int startHour = 8, startMinute = 30;
    private int arrivalHour = 9, arrivalMinute = 15;

    private final List<SafePlace> availableSafePlaces = new ArrayList<>();
    private final List<String> selectedSafeStopIds = new ArrayList<>();
    private final List<String> selectedSafeStopNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routine_form);

        if (FirebaseAuth.getInstance().getCurrentUser() == null && !DemoDataSeeder.isDemoMode(this)) {
            finish();
            return;
        }

        routineRepository = new RoutineRepository();
        safePlaceRepository = new SafePlaceRepository();

        existingRoutine = (Routine) getIntent().getSerializableExtra("extra_routine");

        Toolbar toolbar = findViewById(R.id.toolbar_routine_form);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(existingRoutine != null ? "Edit Routine" : "New Routine");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        setupCategorySpinner();
        setupTimePickers();
        loadAvailableSafePlaces();

        if (existingRoutine != null) {
            populateExistingData(existingRoutine);
        } else {
            calculateAndDisplayDuration();
        }

        findViewById(R.id.layout_select_safe_stops).setOnClickListener(v -> showSafeStopsSelectionDialog());
        findViewById(R.id.tv_btn_manage_safe_stops).setOnClickListener(v -> startActivity(new Intent(this, SafeStopsActivity.class)));

        findViewById(R.id.btn_save_routine).setOnClickListener(v -> validateAndSaveRoutine());
        findViewById(R.id.btn_cancel_routine).setOnClickListener(v -> finish());
    }

    private void initViews() {
        etTitle = findViewById(R.id.et_routine_title);
        etStartLoc = findViewById(R.id.et_routine_start_loc);
        etDestination = findViewById(R.id.et_routine_destination);
        spinnerCategory = findViewById(R.id.spinner_routine_category);

        tvStartTimeVal = findViewById(R.id.tv_start_time_val);
        tvArrivalTimeVal = findViewById(R.id.tv_arrival_time_val);
        tvCalculatedDuration = findViewById(R.id.tv_calculated_duration);
        tvSafeStopsLabel = findViewById(R.id.tv_selected_safe_stops_label);

        tbMon = findViewById(R.id.tb_mon);
        tbTue = findViewById(R.id.tb_tue);
        tbWed = findViewById(R.id.tb_wed);
        tbThu = findViewById(R.id.tb_thu);
        tbFri = findViewById(R.id.tb_fri);
        tbSat = findViewById(R.id.tb_sat);
        tbSun = findViewById(R.id.tb_sun);

        switchReminders = findViewById(R.id.switch_reminders_enabled);
        switchCheckIn = findViewById(R.id.switch_checkin_enabled);
    }

    private void setupCategorySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        spinnerCategory.setAdapter(adapter);
    }

    private void setupTimePickers() {
        findViewById(R.id.layout_pick_start_time).setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                startHour = hourOfDay;
                startMinute = minute;
                tvStartTimeVal.setText(formatTime(startHour, startMinute));
                calculateAndDisplayDuration();
            }, startHour, startMinute, false);
            dialog.show();
        });

        findViewById(R.id.layout_pick_arrival_time).setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                arrivalHour = hourOfDay;
                arrivalMinute = minute;
                tvArrivalTimeVal.setText(formatTime(arrivalHour, arrivalMinute));
                calculateAndDisplayDuration();
            }, arrivalHour, arrivalMinute, false);
            dialog.show();
        });
    }

    private String formatTime(int hour, int minute) {
        String amPm = hour >= 12 ? "PM" : "AM";
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm);
    }

    private void calculateAndDisplayDuration() {
        int startTotal = startHour * 60 + startMinute;
        int arrivalTotal = arrivalHour * 60 + arrivalMinute;
        int diff = arrivalTotal - startTotal;
        if (diff < 0) {
            diff += 24 * 60; // Next day
        }
        tvCalculatedDuration.setText("Estimated duration: " + diff + " minutes");
    }

    private void loadAvailableSafePlaces() {
        safePlaceRepository.listenToSafePlaces(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                availableSafePlaces.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    SafePlace place = ds.getValue(SafePlace.class);
                    if (place != null) {
                        if (place.getId() == null) place.setId(ds.getKey());
                        availableSafePlaces.add(place);
                    }
                }
                updateSafeStopsLabel();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void populateExistingData(Routine routine) {
        etTitle.setText(routine.getTitle());
        etStartLoc.setText(routine.getStartLocation());
        etDestination.setText(routine.getDestination());

        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(routine.getCategory())) {
                spinnerCategory.setSelection(i);
                break;
            }
        }

        startHour = routine.getStartHour();
        startMinute = routine.getStartMinute();
        arrivalHour = routine.getArrivalHour();
        arrivalMinute = routine.getArrivalMinute();

        tvStartTimeVal.setText(routine.getStartTime());
        tvArrivalTimeVal.setText(routine.getArrivalTime());
        calculateAndDisplayDuration();

        List<String> days = routine.getDaysOfWeek();
        if (days != null) {
            tbMon.setChecked(days.contains("Mon"));
            tbTue.setChecked(days.contains("Tue"));
            tbWed.setChecked(days.contains("Wed"));
            tbThu.setChecked(days.contains("Thu"));
            tbFri.setChecked(days.contains("Fri"));
            tbSat.setChecked(days.contains("Sat"));
            tbSun.setChecked(days.contains("Sun"));
        }

        if (routine.getSafeStopIds() != null) {
            selectedSafeStopIds.addAll(routine.getSafeStopIds());
        }
        if (routine.getSafeStopNames() != null) {
            selectedSafeStopNames.addAll(routine.getSafeStopNames());
        }
        updateSafeStopsLabel();

        switchReminders.setChecked(routine.isReminderNotificationEnabled());
        switchCheckIn.setChecked(routine.isCheckInEnabled());
    }

    private void showSafeStopsSelectionDialog() {
        if (availableSafePlaces.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No Safe Places Yet")
                    .setMessage("You haven't saved any safe stops yet. Would you like to create one?")
                    .setPositiveButton("Add Safe Stop", (dialog, which) -> {
                        startActivity(new Intent(this, SafeStopsActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        String[] placeNames = new String[availableSafePlaces.size()];
        boolean[] checkedItems = new boolean[availableSafePlaces.size()];

        for (int i = 0; i < availableSafePlaces.size(); i++) {
            SafePlace p = availableSafePlaces.get(i);
            placeNames[i] = p.getName() + " (" + p.getCategory() + ")";
            checkedItems[i] = selectedSafeStopIds.contains(p.getId());
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Safe Stops")
                .setMultiChoiceItems(placeNames, checkedItems, (dialog, which, isChecked) -> {
                    SafePlace place = availableSafePlaces.get(which);
                    if (isChecked) {
                        if (!selectedSafeStopIds.contains(place.getId())) {
                            selectedSafeStopIds.add(place.getId());
                            selectedSafeStopNames.add(place.getName());
                        }
                    } else {
                        selectedSafeStopIds.remove(place.getId());
                        selectedSafeStopNames.remove(place.getName());
                    }
                })
                .setPositiveButton("Confirm", (dialog, which) -> updateSafeStopsLabel())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSafeStopsLabel() {
        if (selectedSafeStopIds.isEmpty()) {
            tvSafeStopsLabel.setText("Select Safe Stops (None chosen)");
        } else {
            tvSafeStopsLabel.setText(selectedSafeStopIds.size() + (selectedSafeStopIds.size() == 1 ? " Safe Stop Attached" : " Safe Stops Attached"));
        }
    }

    private void validateAndSaveRoutine() {
        String title = etTitle.getText().toString().trim();
        String startLoc = etStartLoc.getText().toString().trim();
        String destination = etDestination.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Routine name is required");
            return;
        }
        if (TextUtils.isEmpty(startLoc)) {
            etStartLoc.setError("Start location is required");
            return;
        }
        if (TextUtils.isEmpty(destination)) {
            etDestination.setError("Destination is required");
            return;
        }

        List<String> days = new ArrayList<>();
        if (tbMon.isChecked()) days.add("Mon");
        if (tbTue.isChecked()) days.add("Tue");
        if (tbWed.isChecked()) days.add("Wed");
        if (tbThu.isChecked()) days.add("Thu");
        if (tbFri.isChecked()) days.add("Fri");
        if (tbSat.isChecked()) days.add("Sat");
        if (tbSun.isChecked()) days.add("Sun");

        if (days.isEmpty()) {
            Toast.makeText(this, "Please select at least one scheduled day", Toast.LENGTH_SHORT).show();
            return;
        }

        int startTotal = startHour * 60 + startMinute;
        int arrivalTotal = arrivalHour * 60 + arrivalMinute;
        int diff = arrivalTotal - startTotal;
        if (diff < 0) diff += 24 * 60;

        String category = spinnerCategory.getSelectedItem().toString();
        String startTimeStr = formatTime(startHour, startMinute);
        String arrivalTimeStr = formatTime(arrivalHour, arrivalMinute);

        Routine routineToSave = existingRoutine != null ? existingRoutine : new Routine();
        routineToSave.setTitle(title);
        routineToSave.setCategory(category);
        routineToSave.setStartLocation(startLoc);
        routineToSave.setDestination(destination);
        routineToSave.setStartTime(startTimeStr);
        routineToSave.setArrivalTime(arrivalTimeStr);
        routineToSave.setStartHour(startHour);
        routineToSave.setStartMinute(startMinute);
        routineToSave.setArrivalHour(arrivalHour);
        routineToSave.setArrivalMinute(arrivalMinute);
        routineToSave.setDaysOfWeek(days);
        routineToSave.setExpectedDurationMinutes(diff);
        routineToSave.setSafeStopIds(new ArrayList<>(selectedSafeStopIds));
        routineToSave.setSafeStopNames(new ArrayList<>(selectedSafeStopNames));
        routineToSave.setReminderNotificationEnabled(switchReminders.isChecked());
        routineToSave.setCheckInEnabled(switchCheckIn.isChecked());

        routineRepository.saveRoutine(routineToSave, (error, ref) -> {
            if (error == null) {
                if (routineToSave.isEnabled() && routineToSave.isReminderNotificationEnabled()) {
                    RoutineScheduler.scheduleRoutine(RoutineFormActivity.this, routineToSave);
                } else {
                    RoutineScheduler.cancelRoutine(RoutineFormActivity.this, routineToSave);
                }
                Toast.makeText(RoutineFormActivity.this, "Routine saved successfully", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(RoutineFormActivity.this, "Failed to save: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
