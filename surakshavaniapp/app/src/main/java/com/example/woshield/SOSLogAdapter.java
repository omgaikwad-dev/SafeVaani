package com.example.woshield;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SOSLogAdapter extends RecyclerView.Adapter<SOSLogAdapter.LogViewHolder> {

    private List<SOSLog> logList;
    private OnLogClickListener listener;

    public interface OnLogClickListener {
        void onLocationClick(SOSLog log);
    }

    public SOSLogAdapter(List<SOSLog> logList, OnLogClickListener listener) {
        this.logList = logList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        SOSLog log = logList.get(position);
        
        String dateString = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                .format(new Date(Long.parseLong(log.timestamp)));
        
        holder.tvTimestamp.setText(dateString);
        holder.tvStatus.setText("Reason: " + log.status);
        holder.tvLocation.setText(String.format(Locale.getDefault(), "Coords: %.4f, %.4f", log.latitude, log.longitude));
        
        holder.itemView.setOnClickListener(v -> listener.onLocationClick(log));
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimestamp, tvStatus, tvLocation;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tv_log_timestamp);
            tvStatus = itemView.findViewById(R.id.tv_log_status);
            tvLocation = itemView.findViewById(R.id.tv_log_location);
        }
    }
}
