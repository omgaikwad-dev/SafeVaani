package com.example.woshield;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class RoadMapAdapter extends RecyclerView.Adapter<RoadMapAdapter.RoadMapViewHolder> {

    private final Context context;
    private List<RoadMapStep> steps;

    public RoadMapAdapter(Context context, List<RoadMapStep> steps) {
        this.context = context;
        this.steps = steps;
    }

    public void updateSteps(List<RoadMapStep> newSteps) {
        this.steps = newSteps;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoadMapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_road_map_step, parent, false);
        return new RoadMapViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoadMapViewHolder holder, int position) {
        if (steps == null || position >= steps.size()) return;
        RoadMapStep step = steps.get(position);

        holder.tvStepSeq.setText(step.stepSeq);
        holder.tvStepDistance.setText(step.distanceText);
        holder.tvRoadName.setText(step.roadName);
        holder.tvStepInstruction.setText(step.instruction);
        holder.tvStepIcon.setText(step.iconEmoji);
        holder.tvSafetyBadge.setText(step.safetyBadge);

        if (step.badgeColorRes != 0) {
            holder.tvSafetyBadge.setTextColor(ContextCompat.getColor(context, step.badgeColorRes));
        }
        if (step.badgeBgColorRes != 0) {
            holder.tvSafetyBadge.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(context, step.badgeBgColorRes)));
        }

        int roadLineColor = step.roadLineColorRes != 0 ? step.roadLineColorRes : R.color.safe_green;
        holder.viewRoadLineTop.setBackgroundColor(ContextCompat.getColor(context, roadLineColor));
        holder.viewRoadLineBottom.setBackgroundColor(ContextCompat.getColor(context, roadLineColor));

        // Hide top track for start point, hide bottom track for destination
        holder.viewRoadLineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        holder.viewRoadLineBottom.setVisibility(position == steps.size() - 1 ? View.INVISIBLE : View.VISIBLE);

        // Google Maps navigation to this specific road step
        holder.btnNavigateStep.setOnClickListener(v -> {
            try {
                double lat = step.latitude != 0.0 ? step.latitude : 21.1458;
                double lng = step.longitude != 0.0 ? step.longitude : 79.0882;
                Uri gmmIntentUri = Uri.parse(String.format(Locale.US, "google.navigation:q=%f,%f&mode=w", lat, lng));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(mapIntent);
                } else {
                    Intent fallback = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%f,%f", lat, lng)));
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(fallback);
                }
            } catch (Exception e) {
                Toast.makeText(context, "Navigating to " + step.roadName, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return steps == null ? 0 : steps.size();
    }

    static class RoadMapViewHolder extends RecyclerView.ViewHolder {
        View viewRoadLineTop, viewRoadLineBottom;
        TextView tvStepIcon, tvStepSeq, tvStepDistance, tvRoadName, tvStepInstruction, tvSafetyBadge;
        MaterialButton btnNavigateStep;

        public RoadMapViewHolder(@NonNull View itemView) {
            super(itemView);
            viewRoadLineTop = itemView.findViewById(R.id.viewRoadLineTop);
            viewRoadLineBottom = itemView.findViewById(R.id.viewRoadLineBottom);
            tvStepIcon = itemView.findViewById(R.id.tvStepIcon);
            tvStepSeq = itemView.findViewById(R.id.tvStepSeq);
            tvStepDistance = itemView.findViewById(R.id.tvStepDistance);
            tvRoadName = itemView.findViewById(R.id.tvRoadName);
            tvStepInstruction = itemView.findViewById(R.id.tvStepInstruction);
            tvSafetyBadge = itemView.findViewById(R.id.tvSafetyBadge);
            btnNavigateStep = itemView.findViewById(R.id.btnNavigateStep);
        }
    }
}
