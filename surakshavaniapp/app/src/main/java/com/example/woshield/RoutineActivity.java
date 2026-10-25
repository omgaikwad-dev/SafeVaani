package com.example.woshield;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class RoutineActivity extends AppCompatActivity implements RoutineAdapter.OnRoutineActionListener {

    private RecyclerView rvRoutines;
    private RoutineAdapter adapter;
    private final List<Routine> routineList = new ArrayList<>();
    private RoutineRepository repository;
    private ValueEventListener routinesListener;

    private ProgressBar progressBar;
    private MaterialCardView cardEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routine);

        if (FirebaseAuth.getInstance().getCurrentUser() == null && !DemoDataSeeder.isDemoMode(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        repository = new RoutineRepository();

        Toolbar toolbar = findViewById(R.id.toolbar_routines);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvRoutines = findViewById(R.id.rv_routines);
        progressBar = findViewById(R.id.progress_routines);
        cardEmpty = findViewById(R.id.card_empty_routines);

        rvRoutines.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RoutineAdapter(routineList, this);
        rvRoutines.setAdapter(adapter);

        findViewById(R.id.btn_nav_safe_stops).setOnClickListener(v -> {
            startActivity(new Intent(this, SafeStopsActivity.class));
        });

        findViewById(R.id.btn_view_map).setOnClickListener(v -> {
            Intent intent = new Intent(this, MapsActivity.class);
            intent.putExtra("extra_show_all_routines", true);
            startActivity(intent);
        });

        findViewById(R.id.btn_add_routine).setOnClickListener(v -> {
            startActivity(new Intent(this, RoutineFormActivity.class));
        });

        findViewById(R.id.btn_empty_create_routine).setOnClickListener(v -> {
            startActivity(new Intent(this, RoutineFormActivity.class));
        });

        loadRoutines();
    }

    private void loadRoutines() {
        progressBar.setVisibility(View.VISIBLE);
        routinesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                routineList.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Routine routine = ds.getValue(Routine.class);
                    if (routine != null) {
                        if (routine.getId() == null) routine.setId(ds.getKey());
                        routineList.add(routine);
                    }
                }

                adapter.notifyDataSetChanged();
                updateUIState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(RoutineActivity.this, "Failed to load routines: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                updateUIState();
            }
        };

        repository.listenToRoutines(routinesListener);
    }

    private void updateUIState() {
        if (routineList.isEmpty()) {
            cardEmpty.setVisibility(View.VISIBLE);
            rvRoutines.setVisibility(View.GONE);
        } else {
            cardEmpty.setVisibility(View.GONE);
            rvRoutines.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onToggleEnabled(Routine routine, boolean isEnabled) {
        routine.setEnabled(isEnabled);
        repository.toggleRoutineEnabled(routine.getId(), isEnabled, (error, ref) -> {
            if (error == null) {
                if (isEnabled) {
                    RoutineScheduler.scheduleRoutine(RoutineActivity.this, routine);
                    Toast.makeText(RoutineActivity.this, "Routine activated: " + routine.getTitle(), Toast.LENGTH_SHORT).show();
                } else {
                    RoutineScheduler.cancelRoutine(RoutineActivity.this, routine);
                    Toast.makeText(RoutineActivity.this, "Routine paused: " + routine.getTitle(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(RoutineActivity.this, "Failed to update routine: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onEdit(Routine routine) {
        Intent intent = new Intent(this, RoutineFormActivity.class);
        intent.putExtra("extra_routine", routine);
        startActivity(intent);
    }

    @Override
    public void onDelete(Routine routine) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Routine")
                .setMessage("Are you sure you want to delete '" + routine.getTitle() + "'? Scheduled reminders for this routine will also be cancelled.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    RoutineScheduler.cancelRoutine(this, routine);
                    repository.deleteRoutine(routine.getId(), (error, ref) -> {
                        if (error == null) {
                            Toast.makeText(RoutineActivity.this, "Routine deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(RoutineActivity.this, "Failed to delete: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onShowMap(Routine routine) {
        Intent intent = new Intent(this, MapsActivity.class);
        intent.putExtra("extra_routine", routine);
        startActivity(intent);
    }

    @Override
    public void onItemClick(Routine routine) {
        String[] options = {"Show Route on Map", "Start Safety Check-In Now", "Edit Routine Settings"};
        new AlertDialog.Builder(this)
                .setTitle(routine.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        onShowMap(routine);
                    } else if (which == 1) {
                        Intent checkInIntent = new Intent(this, SmartCheckInActivity.class);
                        checkInIntent.putExtra(SmartCheckInActivity.EXTRA_ROUTINE_ID, routine.getId());
                        checkInIntent.putExtra(SmartCheckInActivity.EXTRA_ROUTINE_TITLE, routine.getTitle());
                        checkInIntent.putExtra(SmartCheckInActivity.EXTRA_REASON, "Routine Checkpoint: " + routine.getTitle());
                        int duration = routine.getCheckInIntervalMinutes() > 0 ? routine.getCheckInIntervalMinutes() : 15;
                        checkInIntent.putExtra(SmartCheckInActivity.EXTRA_DURATION_MINUTES, duration);
                        startActivity(checkInIntent);
                    } else {
                        onEdit(routine);
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (repository != null && routinesListener != null) {
            repository.removeRoutinesListener(routinesListener);
        }
    }
}
