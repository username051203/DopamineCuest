package com.dopaminequest.adapters;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectAdapter extends RecyclerView.Adapter<AppSelectAdapter.VH> {

    public static class AppItem {
        public final String          packageName;
        public final String          label;
        public final ApplicationInfo info;
        public       boolean         selected;

        public AppItem(String packageName, String label, ApplicationInfo info, boolean selected) {
            this.packageName = packageName;
            this.label       = label;
            this.info        = info;
            this.selected    = selected;
        }
    }

    public interface OnSelectionChanged { void onChanged(int selectedCount); }

    private final List<AppItem>        items;
    private final OnSelectionChanged   listener;

    public AppSelectAdapter(List<AppItem> items, OnSelectionChanged listener) {
        this.items    = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppItem item = items.get(position);
        holder.tvLabel.setText(item.label);
        holder.tvPkg.setText(item.packageName);
        holder.checkbox.setChecked(item.selected);

        // Load icon
        try {
            PackageManager pm = holder.itemView.getContext().getPackageManager();
            Drawable icon = pm.getApplicationIcon(item.info);
            holder.ivIcon.setImageDrawable(icon);
        } catch (Exception e) {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        holder.itemView.setOnClickListener(v -> {
            item.selected = !item.selected;
            holder.checkbox.setChecked(item.selected);
            notifySelectionChanged();
        });
        holder.checkbox.setOnCheckedChangeListener((cb, checked) -> {
            if (item.selected != checked) {
                item.selected = checked;
                notifySelectionChanged();
            }
        });
    }

    private void notifySelectionChanged() {
        int count = 0;
        for (AppItem i : items) if (i.selected) count++;
        if (listener != null) listener.onChanged(count);
    }

    public List<String> getSelectedPackages() {
        List<String> result = new ArrayList<>();
        for (AppItem i : items) if (i.selected) result.add(i.packageName);
        return result;
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView  tvLabel, tvPkg;
        CheckBox  checkbox;
        VH(View v) {
            super(v);
            ivIcon   = v.findViewById(R.id.iv_icon);
            tvLabel  = v.findViewById(R.id.tv_label);
            tvPkg    = v.findViewById(R.id.tv_pkg);
            checkbox = v.findViewById(R.id.checkbox);
        }
    }
}
