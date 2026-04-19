package com.dopaminequest.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dopaminequest.R;
import com.dopaminequest.activities.TaskDetailActivity;
import com.dopaminequest.adapters.OverlayTaskAdapter;
import com.dopaminequest.models.Task;

public class TasksFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = view.findViewById(R.id.rv_tasks);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        OverlayTaskAdapter adapter = new OverlayTaskAdapter(
            requireContext(), Task.all(), task -> {
                Intent i = new Intent(requireContext(), TaskDetailActivity.class);
                i.putExtra("task_id", task.id);
                i.putExtra("from_overlay", false);
                startActivity(i);
            });
        rv.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh to show completed state
        RecyclerView rv = requireView().findViewById(R.id.rv_tasks);
        if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
    }
}
