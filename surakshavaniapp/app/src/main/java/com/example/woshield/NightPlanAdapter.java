package com.example.woshield;

/**
 * NightPlanAdapter — RecyclerView adapter for the Night Plan item list.
 *
 * Renders each plan item with its confidence badge
 * (KNOWN/PROBABLE/OUTDATED/UNKNOWN) in the appropriate color.
 * Supports section headers for grouping by category.
 */

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class NightPlanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_HEADER = 0;
    private static final int VIEW_ITEM   = 1;

    private final Context context;
    private List<NightPlanEngine.PlanItem> items;

    public NightPlanAdapter(Context context, List<NightPlanEngine.PlanItem> items) {
        this.context = context;
        this.items = items;
    }

    public void updateItems(List<NightPlanEngine.PlanItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return "header".equals(items.get(position).category) ? VIEW_HEADER : VIEW_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_HEADER) {
            View v = inflater.inflate(R.layout.item_plan_section_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_night_plan_step, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NightPlanEngine.PlanItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else {
            ((ItemViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    // ── Header ViewHolder ─────────────────────────────────────────────────────
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionTitle;
        HeaderViewHolder(@NonNull View v) {
            super(v);
            tvSectionTitle = v.findViewById(R.id.tvSectionTitle);
        }
        void bind(NightPlanEngine.PlanItem item) {
            if (tvSectionTitle != null) tvSectionTitle.setText(item.title);
        }
    }

    // ── Item ViewHolder ───────────────────────────────────────────────────────
    class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmoji, tvTitle, tvDetail, tvConfidenceBadge;
        MaterialCardView cardItem;
        View viewConfidenceBar;
        View btnNavigate;

        ItemViewHolder(@NonNull View v) {
            super(v);
            tvEmoji          = v.findViewById(R.id.tvPlanEmoji);
            tvTitle          = v.findViewById(R.id.tvPlanTitle);
            tvDetail         = v.findViewById(R.id.tvPlanDetail);
            tvConfidenceBadge = v.findViewById(R.id.tvConfidenceBadge);
            cardItem         = v.findViewById(R.id.cardPlanItem);
            viewConfidenceBar = v.findViewById(R.id.viewConfidenceBar);
            btnNavigate      = v.findViewById(R.id.btnItemNavigate);
        }

        void bind(NightPlanEngine.PlanItem item) {
            if (tvEmoji != null)  tvEmoji.setText(item.emoji);
            if (tvTitle != null)  tvTitle.setText(item.title);
            if (tvDetail != null) tvDetail.setText(item.detail);

            int textColorRes = item.getConfidenceColorRes();
            int bgColorRes   = item.getConfidenceBgColorRes();

            if (tvConfidenceBadge != null) {
                tvConfidenceBadge.setText(item.getConfidenceBadge());
                tvConfidenceBadge.setTextColor(ContextCompat.getColor(context, textColorRes));
                tvConfidenceBadge.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(context, bgColorRes)));
            }

            if (viewConfidenceBar != null) {
                viewConfidenceBar.setBackgroundResource(textColorRes);
            }

            // Google Maps Navigation action
            if (btnNavigate != null) {
                btnNavigate.setOnClickListener(v -> {
                    double targetLat = item.latitude != 0.0 ? item.latitude : 18.5204;
                    double targetLng = item.longitude != 0.0 ? item.longitude : 73.8567;
                    android.net.Uri gmmIntentUri = android.net.Uri.parse("google.navigation:q=" + targetLat + "," + targetLng + "&mode=d");
                    android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    mapIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(mapIntent);
                    } catch (Exception e) {
                        android.content.Intent webIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + targetLat + "," + targetLng));
                        webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(webIntent);
                    }
                });
            }

            // Dim outdated/unknown items slightly
            float alpha = 1.0f;
            if (item.confidence == NightPlanEngine.Confidence.OUTDATED) alpha = 0.75f;
            if (item.confidence == NightPlanEngine.Confidence.UNKNOWN) alpha = 0.60f;
            itemView.setAlpha(alpha);
        }
    }
}
