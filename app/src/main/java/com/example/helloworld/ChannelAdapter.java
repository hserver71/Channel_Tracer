package com.example.helloworld;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {

    interface Listener {
        void onChannelClick(Channel channel);

        void onChannelCheckedChanged(Channel channel, boolean checked);
    }

    private final List<Channel> allItems = new ArrayList<>();
    private final List<Channel> visibleItems = new ArrayList<>();
    private final Listener listener;
    private int selectedStreamId = -1;
    private String filter = "";

    ChannelAdapter(Listener listener) {
        this.listener = listener;
    }

    void setItems(List<Channel> items) {
        allItems.clear();
        allItems.addAll(items);
        applyFilter();
    }

    void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase();
        applyFilter();
    }

    void setSelectedStreamId(int selectedStreamId) {
        this.selectedStreamId = selectedStreamId;
        notifyDataSetChanged();
    }

    List<Channel> getSelectedChannels() {
        List<Channel> selected = new ArrayList<>();
        for (Channel channel : allItems) {
            if (channel.selected) {
                selected.add(channel);
            }
        }
        return selected;
    }

    private void applyFilter() {
        visibleItems.clear();
        for (Channel item : allItems) {
            if (filter.isEmpty()
                    || item.name.toLowerCase().contains(filter)
                    || String.valueOf(item.streamId).contains(filter)) {
                visibleItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Channel item = visibleItems.get(position);
        holder.id.setText(String.valueOf(item.streamId));
        holder.name.setText(item.name);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(item.selected);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.selected = isChecked;
            listener.onChannelCheckedChanged(item, isChecked);
        });

        boolean selected = item.streamId == selectedStreamId;
        if (selected) {
            holder.row.setBackgroundResource(R.drawable.row_selected_bg);
            int white = ContextCompat.getColor(holder.itemView.getContext(), R.color.row_selected_text);
            holder.id.setTextColor(white);
            holder.name.setTextColor(white);
        } else {
            holder.row.setBackgroundColor(Color.TRANSPARENT);
            holder.id.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_muted));
            holder.name.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
        }

        holder.itemView.setOnClickListener(v -> listener.onChannelClick(item));
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final CheckBox check;
        final TextView id;
        final TextView name;

        Holder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.channelRow);
            check = itemView.findViewById(R.id.channelCheck);
            id = itemView.findViewById(R.id.channelId);
            name = itemView.findViewById(R.id.channelName);
        }
    }
}
