package com.example.woshield;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class FamilyDashboardAdapter extends RecyclerView.Adapter<FamilyDashboardAdapter.MemberViewHolder> {

    public interface OnMemberActionListener {
        void onMemberClick(FamilyMember member);
        void onRevokeAccess(FamilyMember member);
    }

    private final List<FamilyMember> memberList;
    private final OnMemberActionListener listener;

    public FamilyDashboardAdapter(List<FamilyMember> memberList, OnMemberActionListener listener) {
        this.memberList = memberList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_family_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        FamilyMember member = memberList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvMemberName.setText(member.getDisplayName());

        String relation = member.getRelationship();
        String phone = member.getPhone();
        String email = member.getEmail();

        if (relation != null && !relation.trim().isEmpty() && phone != null && !phone.trim().isEmpty()) {
            holder.tvMemberEmail.setText(relation + " • " + phone);
        } else if (relation != null && !relation.trim().isEmpty()) {
            holder.tvMemberEmail.setText(relation);
        } else if (phone != null && !phone.trim().isEmpty()) {
            holder.tvMemberEmail.setText(phone);
        } else if (email != null && !email.trim().isEmpty()) {
            holder.tvMemberEmail.setText(email);
        } else {
            holder.tvMemberEmail.setText("Family Member");
        }

        CirclePermissions perms = member.getPermissions();
        boolean canViewLocation = perms != null && perms.isCanViewLocation();
        boolean canViewStatus = perms != null && perms.isCanViewStatus();
        boolean canViewJourney = perms != null && perms.isCanViewJourney();

        // 1. Status Badge & Details
        if (!canViewStatus) {
            holder.badgeSafetyStatus.setText("PROTECTED");
            holder.badgeSafetyStatus.setBackgroundResource(R.drawable.badge_routine);
            holder.badgeSafetyStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.tvStatusDetails.setText("Status sharing not authorized by member.");
        } else {
            String status = member.getSafetyStatus() != null ? member.getSafetyStatus() : FamilyMember.STATUS_SAFE;
            bindStatusBadge(holder, context, status);
            holder.tvStatusDetails.setText(member.getStatusDetails() != null ? member.getStatusDetails() : "Active on Shield Network");
        }

        // 2. Routine Info
        if (canViewJourney && member.getActiveRoutineTitle() != null && !member.getActiveRoutineTitle().isEmpty()) {
            holder.layoutRoutineInfo.setVisibility(View.VISIBLE);
            holder.tvRoutineTitle.setText("Routine: " + member.getActiveRoutineTitle());
            if (member.getExpectedArrival() != null && !member.getExpectedArrival().isEmpty()) {
                holder.tvExpectedArrival.setText("ETA " + member.getExpectedArrival());
                holder.tvExpectedArrival.setVisibility(View.VISIBLE);
            } else {
                holder.tvExpectedArrival.setVisibility(View.GONE);
            }
        } else {
            holder.layoutRoutineInfo.setVisibility(View.GONE);
        }

        // 3. Location Section
        if (!canViewLocation) {
            holder.layoutLocationSection.setVisibility(View.VISIBLE);
            holder.tvLocationLabel.setText("Location sharing restricted by member.");
            holder.tvLocationTime.setText("");
            holder.tvStaleWarning.setVisibility(View.GONE);
            holder.btnViewMap.setEnabled(false);
            holder.btnViewMap.setAlpha(0.5f);
        } else if (member.getLatitude() != 0.0 && member.getLongitude() != 0.0) {
            holder.layoutLocationSection.setVisibility(View.VISIBLE);
            String locText = String.format(Locale.getDefault(), "Location: %.4f, %.4f", member.getLatitude(), member.getLongitude());
            holder.tvLocationLabel.setText(locText);
            holder.btnViewMap.setEnabled(true);
            holder.btnViewMap.setAlpha(1.0f);

            long ageMins = member.getLocationAgeMinutes();
            if (member.isLocationStale()) {
                holder.tvLocationTime.setText("Stale (" + ageMins + "m ago)");
                holder.tvLocationTime.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
                holder.tvStaleWarning.setVisibility(View.VISIBLE);
                holder.tvStaleWarning.setText("⚠️ Location is stale (" + ageMins + "m old). Device may be offline or stopped sharing.");
            } else {
                holder.tvLocationTime.setText("Updated " + (ageMins <= 1 ? "just now" : ageMins + "m ago"));
                holder.tvLocationTime.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                holder.tvStaleWarning.setVisibility(View.GONE);
            }
        } else {
            holder.layoutLocationSection.setVisibility(View.VISIBLE);
            holder.tvLocationLabel.setText("No active location transmitted.");
            holder.tvLocationTime.setText("Offline");
            holder.tvStaleWarning.setVisibility(View.GONE);
            holder.btnViewMap.setEnabled(false);
            holder.btnViewMap.setAlpha(0.5f);
        }

        // 4. Action buttons
        holder.btnCallMember.setOnClickListener(v -> {
            if (member.getPhone() != null && !member.getPhone().isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + member.getPhone()));
                context.startActivity(callIntent);
            } else {
                Toast.makeText(context, "No phone number available for " + member.getDisplayName(), Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnViewMap.setOnClickListener(v -> {
            if (member.getLatitude() != 0.0 && member.getLongitude() != 0.0) {
                String uri = String.format(Locale.ENGLISH, "geo:%f,%f?q=%f,%f(%s)",
                        member.getLatitude(), member.getLongitude(),
                        member.getLatitude(), member.getLongitude(),
                        Uri.encode(member.getDisplayName() + "'s Location"));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                mapIntent.setPackage("com.google.android.apps.maps");
                try {
                    context.startActivity(mapIntent);
                } catch (Exception e) {
                    Intent webMap = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + member.getLatitude() + "," + member.getLongitude()));
                    context.startActivity(webMap);
                }
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMemberClick(member);
        });
    }

    private void bindStatusBadge(MemberViewHolder holder, Context context, String status) {
        if (FamilyMember.STATUS_SAFE.equalsIgnoreCase(status)) {
            holder.badgeSafetyStatus.setText("SAFE");
            holder.badgeSafetyStatus.setBackgroundResource(R.drawable.badge_safe);
            holder.badgeSafetyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_safe));
        } else if (FamilyMember.STATUS_PENDING_CHECKIN.equalsIgnoreCase(status)) {
            holder.badgeSafetyStatus.setText("CHECK-IN DUE");
            holder.badgeSafetyStatus.setBackgroundResource(R.drawable.badge_routine);
            holder.badgeSafetyStatus.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
        } else if (FamilyMember.STATUS_ASSISTANCE_NEEDED.equalsIgnoreCase(status)) {
            holder.badgeSafetyStatus.setText("ASSISTANCE");
            holder.badgeSafetyStatus.setBackgroundResource(R.drawable.badge_routine);
            holder.badgeSafetyStatus.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
        } else if (FamilyMember.STATUS_SOS_ACTIVE.equalsIgnoreCase(status)) {
            holder.badgeSafetyStatus.setText("🚨 SOS ACTIVE");
            holder.badgeSafetyStatus.setBackgroundResource(R.drawable.badge_emergency);
            holder.badgeSafetyStatus.setTextColor(ContextCompat.getColor(context, R.color.error_red));
        } else {
            holder.badgeSafetyStatus.setText("PROTECTED");
            holder.badgeSafetyStatus.setBackgroundResource(R.drawable.badge_safe);
            holder.badgeSafetyStatus.setTextColor(ContextCompat.getColor(context, R.color.status_safe));
        }
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMemberAvatar, ivLocationIcon;
        TextView tvMemberName, tvMemberEmail, badgeSafetyStatus, tvStatusDetails;
        LinearLayout layoutRoutineInfo, layoutLocationSection;
        TextView tvRoutineTitle, tvExpectedArrival, tvLocationLabel, tvLocationTime, tvStaleWarning;
        MaterialButton btnCallMember, btnViewMap;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMemberAvatar = itemView.findViewById(R.id.iv_member_avatar);
            ivLocationIcon = itemView.findViewById(R.id.iv_location_icon);
            tvMemberName = itemView.findViewById(R.id.tv_member_name);
            tvMemberEmail = itemView.findViewById(R.id.tv_member_email);
            badgeSafetyStatus = itemView.findViewById(R.id.badge_safety_status);
            tvStatusDetails = itemView.findViewById(R.id.tv_status_details);

            layoutRoutineInfo = itemView.findViewById(R.id.layout_routine_info);
            tvRoutineTitle = itemView.findViewById(R.id.tv_routine_title);
            tvExpectedArrival = itemView.findViewById(R.id.tv_expected_arrival);

            layoutLocationSection = itemView.findViewById(R.id.layout_location_section);
            tvLocationLabel = itemView.findViewById(R.id.tv_location_label);
            tvLocationTime = itemView.findViewById(R.id.tv_location_time);
            tvStaleWarning = itemView.findViewById(R.id.tv_stale_warning);

            btnCallMember = itemView.findViewById(R.id.btn_call_member);
            btnViewMap = itemView.findViewById(R.id.btn_view_map);
        }
    }
}
