package com.example.travelshare.ui.theme.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.models.Comment;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> comments = new ArrayList<>();

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthor, tvDate, tvText;
        public CommentViewHolder(View v) {
            super(v);
            tvAuthor = v.findViewById(R.id.tv_comment_author);
            tvDate   = v.findViewById(R.id.tv_comment_date);
            tvText   = v.findViewById(R.id.tv_comment_text);
        }
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment c = comments.get(position);
        holder.tvAuthor.setText(c.authorName);
        holder.tvDate.setText(c.date);
        holder.tvText.setText(c.text);
    }

    @Override
    public int getItemCount() { return comments.size(); }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
        notifyDataSetChanged();
    }
}
