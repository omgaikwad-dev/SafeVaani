package com.example.woshield;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class SafePlaceAdapter extends RecyclerView.Adapter<SafePlaceAdapter.SafePlaceViewHolder> {

    public interface OnSafePlaceActionListener {
        void onToggleTrusted(SafePlace place);
        void onEdit(SafePlace place);
        void onDelete(SafePlace place);
        void onSelect(SafePlace place);
        void onNavigate(SafePlace place);
    }

    private final List<SafePlace> placeList;
    private final OnSafePlaceActionListener listener;
    private final boolean selectionMode;

    public SafePlaceAdapter(List<SafePlace> placeList, OnSafePlaceActionListener listener) {
        this(placeList, listener, false);
    }

    public SafePlaceAdapter(List<SafePlace> placeList, OnSafePlaceActionListener listener, boolean selectionMode) {
        this.placeList = placeList;
        this.listener = listener;
        this.selectionMode = selectionMode;
    }

    @NonNull
    @Override
    public SafePlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_safe_place, parent, false);
        return new SafePlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SafePlaceViewHolder holder, int position) {
        SafePlace place = placeList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvPlaceName.setText(place.getName());
        holder.tvPlaceCategory.setText(place.getCategory() != null ? place.getCategory().toUpperCase() : "SAFE STOP");

        // Category badge coloring
        int categoryBg = R.color.light_lavender;
        int categoryText = R.color.deep_purple;
        if ("Medical".equalsIgnoreCase(place.getCategory())) {
            categoryBg = R.color.status_safe_bg;
            categoryText = R.color.status_safe;
        } else if ("Transit".equalsIgnoreCase(place.getCategory())) {
            categoryBg = R.color.light_lavender;
            categoryText = R.color.deep_purple;
        } else if ("Family".equalsIgnoreCase(place.getCategory()) || "Home".equalsIgnoreCase(place.getCategory())) {
            categoryBg = R.color.status_warning_bg;
            categoryText = R.color.status_warning;
        }
        holder.cardPlaceCategory.setCardBackgroundColor(ContextCompat.getColor(context, categoryBg));
        holder.tvPlaceCategory.setTextColor(ContextCompat.getColor(context, categoryText));

        // Address or Coordinates
        if (place.getAddress() != null && !place.getAddress().trim().isEmpty()) {
            holder.tvPlaceAddress.setText(place.getAddress().trim());
            holder.tvPlaceAddress.setVisibility(View.VISIBLE);
        } else if (place.isHasCoordinates()) {
            holder.tvPlaceAddress.setText(String.format("Lat: %.4f, Lon: %.4f", place.getLatitude(), place.getLongitude()));
            holder.tvPlaceAddress.setVisibility(View.VISIBLE);
        } else {
            holder.tvPlaceAddress.setText("No address specified");
        }

        // Notes
        if (place.getNotes() != null && !place.getNotes().trim().isEmpty()) {
            holder.tvPlaceNotes.setText(place.getNotes().trim());
            holder.tvPlaceNotes.setVisibility(View.VISIBLE);
        } else {
            holder.tvPlaceNotes.setVisibility(View.GONE);
        }

        // Trusted Star
        if (place.isTrusted()) {
            holder.btnToggleTrusted.setImageResource(R.drawable.ic_trusted_star);
            holder.btnToggleTrusted.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_warning)));
        } else {
            holder.btnToggleTrusted.setImageResource(R.drawable.ic_trusted_star);
            holder.btnToggleTrusted.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_muted)));
        }

        holder.btnNavigatePlace.setOnClickListener(v -> {
            if (listener != null) listener.onNavigate(place);
        });

        holder.btnToggleTrusted.setOnClickListener(v -> {
            if (listener != null) listener.onToggleTrusted(place);
        });

        holder.btnEditPlace.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(place);
        });

        holder.btnDeletePlace.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(place);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                if (selectionMode) {
                    listener.onSelect(place);
                } else {
                    listener.onNavigate(place);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return placeList.size();
    }

    static class SafePlaceViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardPlaceCategory;
        TextView tvPlaceCategory, tvPlaceName, tvPlaceAddress, tvPlaceNotes;
        ImageView btnToggleTrusted, btnNavigatePlace, btnEditPlace, btnDeletePlace;

        public SafePlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPlaceCategory = itemView.findViewById(R.id.card_place_category);
            tvPlaceCategory = itemView.findViewById(R.id.tv_place_category);
            tvPlaceName = itemView.findViewById(R.id.tv_place_name);
            tvPlaceAddress = itemView.findViewById(R.id.tv_place_address);
            tvPlaceNotes = itemView.findViewById(R.id.tv_place_notes);
            btnToggleTrusted = itemView.findViewById(R.id.btn_toggle_trusted);
            btnNavigatePlace = itemView.findViewById(R.id.btn_navigate_place);
            btnEditPlace = itemView.findViewById(R.id.btn_edit_place);
            btnDeletePlace = itemView.findViewById(R.id.btn_delete_place);
        }
    }
}
