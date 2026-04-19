package com.dopaminequest.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;
import com.dopaminequest.models.Task;
import com.dopaminequest.utils.AppState;

import java.util.List;

public class OverlayTaskAdapter extends RecyclerView.Adapter<OverlayTaskAdapter.VH> {

    public interface OnTaskTap { void onTap(Task task); }

    private final Context    ctx;
    private final List<Task> tasks;
    private final OnTaskTap  listener;

    public OverlayTaskAdapter(Context ctx, List<Task> tasks, OnTaskTap listener) {
        this.ctx      = ctx;
        this.tasks    = tasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_task_overlay, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Task t    = tasks.get(pos);
        boolean done = AppState.isTaskCompleted(ctx, t.id);

        h.tvNumber.setText(String.format("%02d", pos + 1));
        h.tvEmoji.setText(t.emoji);
        h.tvTitle.setText(t.title);
        h.tvMeta.setText(t.tag + "  ·  " + t.durationMins + " min  ·  +" + t.xp + " XP  ·  +" + t.accessMinutes + " min access");
        h.tvType.setText(t.verification == Task.VerificationType.PHOTO ? "PHOTO" : "TIMER");

        float alpha = done ? 0.4f : 1f;
        h.itemView.setAlpha(alpha);

        h.itemView.setOnClickListener(v -> {
            if (!done) listener.onTap(t);
        });
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNumber, tvEmoji, tvTitle, tvMeta, tvType;
        VH(View v) {
            super(v);
            tvNumber = v.findViewById(R.id.tv_number);
            tvEmoji  = v.findViewById(R.id.tv_emoji);
            tvTitle  = v.findViewById(R.id.tv_title);
            tvMeta   = v.findViewById(R.id.tv_meta);
            tvType   = v.findViewById(R.id.tv_type);
        }
    }
}
