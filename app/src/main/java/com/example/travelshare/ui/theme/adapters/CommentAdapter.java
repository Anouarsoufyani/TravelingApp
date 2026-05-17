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
import java.util.function.Consumer;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private List<Comment> comments = new ArrayList<>();
    private long currentUserId = -1;
    private Consumer<Long> deleteCallback;

    public void init(long currentUserId, Consumer<Long> deleteCallback) {
        this.currentUserId = currentUserId;
        this.deleteCallback = deleteCallback;
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthor, tvDate, tvText, btnDelete;
        public CommentViewHolder(View v) {
            super(v);
            tvAuthor  = v.findViewById(R.id.tv_comment_author);
            tvDate    = v.findViewById(R.id.tv_comment_date);
            tvText    = v.findViewById(R.id.tv_comment_text);
            btnDelete = v.findViewById(R.id.btn_delete_comment);
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

        holder.tvAuthor.setOnClickListener(v -> {
            if (c.authorName != null) {
                android.content.Intent intent = new android.content.Intent(v.getContext(), com.example.travelshare.ui.UserProfileActivity.class);
                intent.putExtra(com.example.travelshare.ui.UserProfileActivity.EXTRA_USERNAME, c.authorName);
                v.getContext().startActivity(intent);
            }
        });

        if (currentUserId >= 0 && c.userId == currentUserId && deleteCallback != null) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> deleteCallback.accept(c.id));
        } else {
            holder.btnDelete.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return comments.size(); }

    public void setComments(List<Comment> comments) {
        this.comments = comments != null ? comments : new ArrayList<>();
        notifyDataSetChanged();
    }
}
