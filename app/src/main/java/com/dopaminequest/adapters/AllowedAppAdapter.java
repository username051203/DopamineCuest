package com.dopaminequest.adapters;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;

import java.util.List;

public class AllowedAppAdapter extends RecyclerView.Adapter<AllowedAppAdapter.VH> {

    public interface OnAppTap { void onTap(String packageName); }

    public static class AppEntry {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public AppEntry(String pkg, String label, Drawable icon) {
            this.packageName = pkg;
            this.label       = label;
            this.icon        = icon;
        }
    }

    private final List<AppEntry> apps;
    private final OnAppTap       listener;

    public AllowedAppAdapter(List<AppEntry> apps, OnAppTap listener) {
        this.apps     = apps;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.item_allowed_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AppEntry app = apps.get(pos);
        h.tvLabel.setText(app.label);
        if (app.icon != null) h.ivIcon.setImageDrawable(app.icon);
        h.itemView.setOnClickListener(v -> listener.onTap(app.packageName));
    }

    @Override public int getItemCount() { return apps.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView  tvLabel;
        VH(View v) {
            super(v);
            ivIcon  = v.findViewById(R.id.iv_icon);
            tvLabel = v.findViewById(R.id.tv_label);
        }
    }
}
