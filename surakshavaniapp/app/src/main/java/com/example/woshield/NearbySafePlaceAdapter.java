package com.example.woshield;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class NearbySafePlaceAdapter extends RecyclerView.Adapter<NearbySafePlaceAdapter.SafePlaceViewHolder> {

    public interface OnSafePlaceClickListener {
        void onPlaceClicked(NearbySafePlace place);
        void onNavigateClicked(NearbySafePlace place);
        void onCallClicked(NearbySafePlace place);
    }

    private final List<NearbySafePlace> placeList;
    private final OnSafePlaceClickListener listener;
    private int selectedPosition = -1;

    public NearbySafePlaceAdapter(List<NearbySafePlace> placeList, OnSafePlaceClickListener listener) {
        this.placeList = placeList;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        int previous = selectedPosition;
        selectedPosition = position;
        if (previous >= 0 && previous < placeList.size()) notifyItemChanged(previous);
        if (selectedPosition >= 0 && selectedPosition < placeList.size()) notifyItemChanged(selectedPosition);
    }

    @NonNull
    @Override
    public SafePlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_safe_place, parent, false);
        return new SafePlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SafePlaceViewHolder holder, int position) {
        NearbySafePlace place = placeList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvPlaceName.setText(place.getName());
        holder.tvPlaceAddress.setText(place.getAddress());
        holder.tvDistance.setText(place.getDistanceFormatted());
        holder.tvCategory.setText(place.getCategory() != null ? place.getCategory().toUpperCase() : "SAFE HAVEN");
        holder.tvPlaceStatus.setText(place.getStatus() != null ? place.getStatus() : "Verified Safe Point");

        // Styling based on category
        int catBg = R.color.light_lavender;
        int catText = R.color.deep_purple;
        if ("HOSPITAL".equalsIgnoreCase(place.getCategory()) || "MEDICAL".equalsIgnoreCase(place.getCategory())) {
            catBg = R.color.status_safe_bg;
            catText = R.color.status_safe;
        } else if ("POLICE".equalsIgnoreCase(place.getCategory()) || "POLICE STATION".equalsIgnoreCase(place.getCategory())) {
            catBg = R.color.soft_violet;
            catText = R.color.deep_purple;
        } else if ("SAFE HAVEN".equalsIgnoreCase(place.getCategory()) || "PHARMACY".equalsIgnoreCase(place.getCategory())) {
            catBg = R.color.status_safe_bg;
            catText = R.color.status_safe;
        }

        holder.cardCategoryBadge.setCardBackgroundColor(ContextCompat.getColor(context, catBg));
        holder.tvCategory.setTextColor(ContextCompat.getColor(context, catText));

        // Highlight if selected
        if (position == selectedPosition) {
            holder.cardRoot.setStrokeColor(ContextCompat.getColor(context, R.color.deep_purple));
            holder.cardRoot.setStrokeWidth(4);
            holder.cardRoot.setCardElevation(6);
        } else {
            holder.cardRoot.setStrokeColor(ContextCompat.getColor(context, R.color.card_stroke));
            holder.cardRoot.setStrokeWidth(2);
            holder.cardRoot.setCardElevation(2);
        }

        // Click on entire card -> show navigate option / preview
        holder.cardRoot.setOnClickListener(v -> {
            setSelectedPosition(holder.getAdapterPosition());
            if (listener != null) {
                listener.onPlaceClicked(place);
            }
        });

        // Direct navigate button
        holder.btnNavigate.setOnClickListener(v -> {
            setSelectedPosition(holder.getAdapterPosition());
            if (listener != null) {
                listener.onNavigateClicked(place);
            }
        });

        // Call button
        if (place.getPhone() != null && !place.getPhone().trim().isEmpty()) {
            holder.btnCall.setVisibility(View.VISIBLE);
            holder.btnCall.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCallClicked(place);
                }
            });
        } else {
            holder.btnCall.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return placeList.size();
    }

    static class SafePlaceViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot, cardCategoryBadge;
        TextView tvCategory, tvDistance, tvPlaceName, tvPlaceAddress, tvPlaceStatus;
        MaterialButton btnNavigate, btnCall;

        public SafePlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.card_safe_place_item);
            cardCategoryBadge = itemView.findViewById(R.id.card_category_badge);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            tvPlaceName = itemView.findViewById(R.id.tv_place_name);
            tvPlaceAddress = itemView.findViewById(R.id.tv_place_address);
            tvPlaceStatus = itemView.findViewById(R.id.tv_place_status);
            btnNavigate = itemView.findViewById(R.id.btn_navigate);
            btnCall = itemView.findViewById(R.id.btn_call);
        }
    }
}
