package com.example.cref_wss_01;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OfflineMapAreaAdapter extends RecyclerView.Adapter<OfflineMapAreaAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(OfflineMapArea area);
    }

    private List<OfflineMapArea> areas = new ArrayList<>();
    private final OnDeleteClickListener listener;

    public OfflineMapAreaAdapter(OnDeleteClickListener listener) {
        this.listener = listener;
    }

    public void setAreas(List<OfflineMapArea> areas) {
        this.areas = areas;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_offline_map_area, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OfflineMapArea area = areas.get(position);
        holder.tvName.setText(area.name);
        holder.tvDetails.setText(
                "Zoom " + area.zoomMin + "–" + area.zoomMax
                + "  ·  " + String.format(Locale.US, "%,d", area.tileCount) + " tiles"
                + "  ·  ~" + estimatedSizeMb(area.tileCount) + " MB"
        );
        holder.tvDate.setText("Downloaded " +
                new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new Date(area.downloadedAt)));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(area));
    }

    private String estimatedSizeMb(int tileCount) {
        long bytes = (long) tileCount * 15 * 1024; // ~15 KB per satellite tile
        if (bytes < 1024 * 1024) return "<1";
        return String.valueOf(bytes / (1024 * 1024));
    }

    @Override
    public int getItemCount() { return areas.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDetails, tvDate;
        Button btnDelete;

        ViewHolder(View v) {
            super(v);
            tvName    = v.findViewById(R.id.tvAreaName);
            tvDetails = v.findViewById(R.id.tvAreaDetails);
            tvDate    = v.findViewById(R.id.tvAreaDate);
            btnDelete = v.findViewById(R.id.btnDeleteArea);
        }
    }
}
