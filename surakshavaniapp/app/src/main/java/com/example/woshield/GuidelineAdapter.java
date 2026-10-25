package com.example.woshield;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GuidelineAdapter extends RecyclerView.Adapter<GuidelineAdapter.ViewHolder> {
    private List<Guideline> guidelines;

    public GuidelineAdapter(List<Guideline> guidelines) {
        this.guidelines = guidelines;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guideline, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Guideline guideline = guidelines.get(position);
        holder.tvTitle.setText(guideline.getTitle());
        holder.tvDescription.setText(guideline.getDescription());
    }

    @Override
    public int getItemCount() {
        return guidelines.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
        }
    }
}
