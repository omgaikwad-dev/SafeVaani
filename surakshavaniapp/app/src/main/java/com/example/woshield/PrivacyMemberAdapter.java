package com.example.woshield;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PrivacyMemberAdapter extends RecyclerView.Adapter<PrivacyMemberAdapter.MemberViewHolder> {

    public interface OnMemberPermissionClickListener {
        void onEditPermissions(FamilyMember member);
    }

    private final Context context;
    private final List<FamilyMember> members = new ArrayList<>();
    private final OnMemberPermissionClickListener clickListener;

    public PrivacyMemberAdapter(Context context, OnMemberPermissionClickListener clickListener) {
        this.context = context;
        this.clickListener = clickListener;
    }

    public void setMembers(List<FamilyMember> newMembers) {
        this.members.clear();
        if (newMembers != null) {
            this.members.addAll(newMembers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_privacy_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        FamilyMember member = members.get(position);

        holder.tvName.setText(member.getDisplayName() != null ? member.getDisplayName() : "Circle Member");
        String relation = member.getRelationship() != null ? member.getRelationship() : "Trusted Circle";
        String contactInfo = (member.getPhone() != null && !member.getPhone().isEmpty())
                ? member.getPhone() : (member.getEmail() != null ? member.getEmail() : "");
        holder.tvRelation.setText(contactInfo.isEmpty() ? relation : (relation + " • " + contactInfo));

        CirclePermissions perms = member.getPermissions();
        if (perms != null) {
            List<String> allowed = new ArrayList<>();
            if (perms.isCanViewStatus()) allowed.add("Status");
            if (perms.isCanViewLocation()) allowed.add("Location");
            if (perms.isCanViewJourney()) allowed.add("Journeys");
            if (perms.isCanReceiveSos()) allowed.add("SOS");
            if (perms.isCanReceiveAssistance()) allowed.add("Assistance");
            if (perms.isCanReceiveMissedCheckIns()) allowed.add("Check-Ins");

            if (allowed.isEmpty()) {
                holder.tvGrantedSummary.setText("No active permissions granted (Access blocked)");
            } else {
                StringBuilder sb = new StringBuilder("Allowed: ");
                for (int i = 0; i < allowed.size(); i++) {
                    sb.append(allowed.get(i));
                    if (i < allowed.size() - 1) sb.append(", ");
                }
                holder.tvGrantedSummary.setText(sb.toString());
            }
        } else {
            holder.tvGrantedSummary.setText("Default Circle Permissions");
        }

        holder.btnEditPermissions.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onEditPermissions(member);
            }
        });
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvRelation;
        TextView tvGrantedSummary;
        MaterialButton btnEditPermissions;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_member_name);
            tvRelation = itemView.findViewById(R.id.tv_member_relation);
            tvGrantedSummary = itemView.findViewById(R.id.tv_granted_summary);
            btnEditPermissions = itemView.findViewById(R.id.btn_edit_permissions);
        }
    }
}
