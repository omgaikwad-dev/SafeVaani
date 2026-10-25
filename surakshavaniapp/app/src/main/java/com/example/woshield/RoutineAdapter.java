package com.example.woshield;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class RoutineAdapter extends RecyclerView.Adapter<RoutineAdapter.RoutineViewHolder> {

    public interface OnRoutineActionListener {
        void onToggleEnabled(Routine routine, boolean isEnabled);
        void onEdit(Routine routine);
        void onDelete(Routine routine);
        void onItemClick(Routine routine);
        void onShowMap(Routine routine);
    }

    private final List<Routine> routineList;
    private final OnRoutineActionListener listener;

    public RoutineAdapter(List<Routine> routineList, OnRoutineActionListener listener) {
        this.routineList = routineList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RoutineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_routine, parent, false);
        return new RoutineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoutineViewHolder holder, int position) {
        Routine routine = routineList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvRoutineTitle.setText(routine.getTitle());
        holder.tvRoutineCategory.setText(routine.getCategory() != null ? routine.getCategory().toUpperCase() : "COMMUTE");

        holder.tvRoutineStart.setText(routine.getStartLocation() != null ? routine.getStartLocation() : "Start");
        holder.tvRoutineDestination.setText(routine.getDestination() != null ? routine.getDestination() : "Destination");

        String timeStr = routine.getStartTime() + " → " + routine.getArrivalTime();
        holder.tvRoutineTimes.setText(timeStr);

        if (routine.getExpectedDurationMinutes() > 0) {
            holder.tvRoutineDuration.setText("(" + routine.getExpectedDurationMinutes() + " mins)");
            holder.tvRoutineDuration.setVisibility(View.VISIBLE);
        } else {
            holder.tvRoutineDuration.setVisibility(View.GONE);
        }

        holder.tvRoutineDays.setText(routine.getFormattedDays());

        int safeStopsCount = routine.getSafeStopIds() != null ? routine.getSafeStopIds().size() : 0;
        if (safeStopsCount > 0) {
            holder.tvRoutineSafeStopsCount.setText(safeStopsCount + (safeStopsCount == 1 ? " Safe Stop" : " Safe Stops"));
            holder.tvRoutineSafeStopsCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvRoutineSafeStopsCount.setVisibility(View.GONE);
        }

        // Avoid triggering listener when binding switch state
        holder.switchRoutineEnabled.setOnCheckedChangeListener(null);
        holder.switchRoutineEnabled.setChecked(routine.isEnabled());
        holder.switchRoutineEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onToggleEnabled(routine, isChecked);
            }
        });

        // Dim visual appearance slightly when disabled
        holder.itemView.setAlpha(routine.isEnabled() ? 1.0f : 0.65f);

        if (holder.btnMapRoutine != null) {
            holder.btnMapRoutine.setOnClickListener(v -> {
                if (listener != null) listener.onShowMap(routine);
            });
        }

        holder.btnEditRoutine.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(routine);
        });

        holder.btnDeleteRoutine.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(routine);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(routine);
        });
    }

    @Override
    public int getItemCount() {
        return routineList.size();
    }

    static class RoutineViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoutineCategory;
        TextView tvRoutineCategory, tvRoutineTitle, tvRoutineStart, tvRoutineDestination;
        TextView tvRoutineTimes, tvRoutineDuration, tvRoutineDays, tvRoutineSafeStopsCount;
        SwitchMaterial switchRoutineEnabled;
        ImageView btnMapRoutine, btnEditRoutine, btnDeleteRoutine;

        public RoutineViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoutineCategory = itemView.findViewById(R.id.card_routine_category);
            tvRoutineCategory = itemView.findViewById(R.id.tv_routine_category);
            tvRoutineTitle = itemView.findViewById(R.id.tv_routine_title);
            tvRoutineStart = itemView.findViewById(R.id.tv_routine_start);
            tvRoutineDestination = itemView.findViewById(R.id.tv_routine_destination);
            tvRoutineTimes = itemView.findViewById(R.id.tv_routine_times);
            tvRoutineDuration = itemView.findViewById(R.id.tv_routine_duration);
            tvRoutineDays = itemView.findViewById(R.id.tv_routine_days);
            tvRoutineSafeStopsCount = itemView.findViewById(R.id.tv_routine_safe_stops_count);
            switchRoutineEnabled = itemView.findViewById(R.id.switch_routine_enabled);
            btnMapRoutine = itemView.findViewById(R.id.btn_map_routine);
            btnEditRoutine = itemView.findViewById(R.id.btn_edit_routine);
            btnDeleteRoutine = itemView.findViewById(R.id.btn_delete_routine);
        }
    }
}
