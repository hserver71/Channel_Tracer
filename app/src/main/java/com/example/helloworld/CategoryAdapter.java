package com.example.helloworld;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

    interface Listener {
        void onCategoryClick(Category category);
    }

    private final List<Category> allItems = new ArrayList<>();
    private final List<Category> visibleItems = new ArrayList<>();
    private final Listener listener;
    private String selectedId = "";
    private String filter = "";

    CategoryAdapter(Listener listener) {
        this.listener = listener;
    }

    void setItems(List<Category> items) {
        allItems.clear();
        allItems.addAll(items);
        applyFilter();
    }

    void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase();
        applyFilter();
    }

    void setSelectedId(String selectedId) {
        this.selectedId = selectedId == null ? "" : selectedId;
        notifyDataSetChanged();
    }

    void updateCount(String categoryId, int count) {
        for (Category item : allItems) {
            if (item.id.equals(categoryId)) {
                item.count = count;
                break;
            }
        }
        notifyDataSetChanged();
    }

    private void applyFilter() {
        visibleItems.clear();
        for (Category item : allItems) {
            if (filter.isEmpty()
                    || item.name.toLowerCase().contains(filter)
                    || item.id.contains(filter)) {
                visibleItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Category item = visibleItems.get(position);
        holder.name.setText(item.name);
        holder.type.setText("live");
        holder.count.setText(item.count >= 0 ? String.valueOf(item.count) : "—");

        boolean selected = item.id.equals(selectedId);
        if (selected) {
            holder.row.setBackgroundResource(R.drawable.row_selected_bg);
            int white = ContextCompat.getColor(holder.itemView.getContext(), R.color.row_selected_text);
            holder.name.setTextColor(white);
            holder.type.setTextColor(white);
            holder.count.setTextColor(white);
        } else {
            holder.row.setBackgroundColor(Color.TRANSPARENT);
            holder.name.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
            holder.type.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_muted));
            holder.count.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_muted));
        }

        holder.itemView.setOnClickListener(v -> listener.onCategoryClick(item));
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView name;
        final TextView type;
        final TextView count;

        Holder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.categoryRow);
            name = itemView.findViewById(R.id.categoryName);
            type = itemView.findViewById(R.id.categoryType);
            count = itemView.findViewById(R.id.categoryCount);
        }
    }
}
