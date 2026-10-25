package com.example.woshield;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PrivacyActivityLogAdapter extends RecyclerView.Adapter<PrivacyActivityLogAdapter.LogViewHolder> {

    private final Context context;
    private final List<PrivacyActivityLog> logs = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

    public PrivacyActivityLogAdapter(Context context) {
        this.context = context;
    }

    public void setLogs(List<PrivacyActivityLog> newLogs) {
        this.logs.clear();
        if (newLogs != null) {
            this.logs.addAll(newLogs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_privacy_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        PrivacyActivityLog log = logs.get(position);

        holder.tvTitle.setText(log.getTitle() != null ? log.getTitle() : "Privacy Event");
        holder.tvDesc.setText(log.getDescription() != null ? log.getDescription() : "");
        holder.tvTime.setText(dateFormat.format(new Date(log.getTimestamp())));

        // Style icon and tint according to event type or severity
        int iconRes = R.drawable.ic_shield_lock;
        int tintColor = ContextCompat.getColor(context, R.color.deep_purple);

        if (PrivacyActivityLog.TYPE_DATA_PURGED.equals(log.getEventType())) {
            iconRes = R.drawable.ic_delete_sweep;
            tintColor = Color.parseColor("#E53935"); // Red for purge
        } else if (PrivacyActivityLog.TYPE_SHARING_REVOKED.equals(log.getEventType()) ||
                PrivacyActivityLog.TYPE_MEMBER_REVOKED.equals(log.getEventType())) {
            tintColor = Color.parseColor("#FB8C00"); // Warning orange
        } else if (PrivacyActivityLog.SEVERITY_SECURITY.equals(log.getSeverity())) {
            tintColor = Color.parseColor("#E53935");
        } else if (PrivacyActivityLog.TYPE_SHARING_STARTED.equals(log.getEventType())) {
            tintColor = ContextCompat.getColor(context, R.color.vibrant_purple);
        }

        holder.ivIcon.setImageResource(iconRes);
        holder.ivIcon.setColorFilter(tintColor);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvDesc;
        TextView tvTime;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_log_icon);
            tvTitle = itemView.findViewById(R.id.tv_log_title);
            tvDesc = itemView.findViewById(R.id.tv_log_desc);
            tvTime = itemView.findViewById(R.id.tv_log_time);
        }
    }
}
