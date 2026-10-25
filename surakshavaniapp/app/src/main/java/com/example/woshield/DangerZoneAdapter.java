package com.example.woshield;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DangerZoneAdapter extends RecyclerView.Adapter<DangerZoneAdapter.ViewHolder> {
    private List<Dataholder> dangerZones;
    private List<String> keys;
    private OnActionClickListener listener;

    public interface OnActionClickListener {
        void onApprove(String key);
        void onReject(String key);
    }

    public DangerZoneAdapter(List<Dataholder> dangerZones, List<String> keys, OnActionClickListener listener) {
        this.dangerZones = dangerZones;
        this.keys = keys;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_danger_zone, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Dataholder zone = dangerZones.get(position);
        String key = keys.get(position);

        holder.tvEmail.setText(zone.getEmail());
        holder.tvDesc.setText(zone.getDescription());
        holder.tvCoords.setText("Lat: " + zone.getLatitude() + ", Lng: " + zone.getLongitude());

        holder.btnApprove.setOnClickListener(v -> listener.onApprove(key));
        holder.btnReject.setOnClickListener(v -> listener.onReject(key));
    }

    @Override
    public int getItemCount() {
        return dangerZones.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmail, tvDesc, tvCoords;
        Button btnApprove, btnReject;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvReporterEmail);
            tvDesc = itemView.findViewById(R.id.tvZoneDescription);
            tvCoords = itemView.findViewById(R.id.tvLocationCoords);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}
