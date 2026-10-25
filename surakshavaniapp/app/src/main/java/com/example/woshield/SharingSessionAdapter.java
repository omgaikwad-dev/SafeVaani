package com.example.woshield;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SharingSessionAdapter extends RecyclerView.Adapter<SharingSessionAdapter.SessionViewHolder> {

    public interface OnSessionRevokeListener {
        void onRevoke(SharingSession session);
    }

    private final Context context;
    private final List<SharingSession> sessions = new ArrayList<>();
    private final OnSessionRevokeListener revokeListener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public SharingSessionAdapter(Context context, OnSessionRevokeListener revokeListener) {
        this.context = context;
        this.revokeListener = revokeListener;
    }

    public void setSessions(List<SharingSession> newSessions) {
        this.sessions.clear();
        if (newSessions != null) {
            this.sessions.addAll(newSessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sharing_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        SharingSession session = sessions.get(position);

        holder.tvTitle.setText(session.getTitle() != null ? session.getTitle() : "Location Sharing Session");

        // Format recipient list
        if (session.getRecipientNames() != null && !session.getRecipientNames().isEmpty()) {
            StringBuilder sb = new StringBuilder("Shared with: ");
            for (int i = 0; i < session.getRecipientNames().size(); i++) {
                sb.append(session.getRecipientNames().get(i));
                if (i < session.getRecipientNames().size() - 1) sb.append(", ");
            }
            holder.tvRecipients.setText(sb.toString());
        } else {
            holder.tvRecipients.setText("Recipients: All Circle Members");
        }

        // Status badge & Expiration text
        if (session.isActive()) {
            holder.tvStatus.setText("ACTIVE");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_safe);
            holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.safe_green));
            holder.btnRevoke.setVisibility(View.VISIBLE);

            long remMinutes = session.getRemainingMinutes();
            if (session.getExpiresAt() == 0) {
                holder.tvExpiry.setText("Active until manually revoked");
            } else if (remMinutes > 0) {
                holder.tvExpiry.setText("Expires in " + remMinutes + " min(s)");
            } else {
                holder.tvExpiry.setText("Expiring shortly");
            }
        } else if (SharingSession.STATUS_REVOKED.equals(session.getStatus())) {
            holder.tvStatus.setText("REVOKED");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_emergency);
            holder.tvStatus.setTextColor(Color.parseColor("#E53935"));
            holder.btnRevoke.setVisibility(View.GONE);
            if (session.getRevokedAt() > 0) {
                holder.tvExpiry.setText("Revoked at " + timeFormat.format(new Date(session.getRevokedAt())));
            } else {
                holder.tvExpiry.setText("Revoked by owner");
            }
        } else {
            // Expired
            holder.tvStatus.setText("EXPIRED");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_routine);
            holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.btnRevoke.setVisibility(View.GONE);
            if (session.getExpiresAt() > 0) {
                holder.tvExpiry.setText("Expired at " + timeFormat.format(new Date(session.getExpiresAt())));
            } else {
                holder.tvExpiry.setText("Session concluded");
            }
        }

        holder.btnRevoke.setOnClickListener(v -> {
            if (revokeListener != null) {
                revokeListener.onRevoke(session);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvStatus;
        TextView tvRecipients;
        TextView tvExpiry;
        MaterialButton btnRevoke;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_session_title);
            tvStatus = itemView.findViewById(R.id.tv_session_status);
            tvRecipients = itemView.findViewById(R.id.tv_session_recipients);
            tvExpiry = itemView.findViewById(R.id.tv_session_expiry);
            btnRevoke = itemView.findViewById(R.id.btn_revoke_session);
        }
    }
}
