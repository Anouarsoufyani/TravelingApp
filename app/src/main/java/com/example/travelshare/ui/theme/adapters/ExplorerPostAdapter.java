package com.example.travelshare.ui.theme.adapters;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.ui.UserProfileActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExplorerPostAdapter extends RecyclerView.Adapter<ExplorerPostAdapter.PostVH> {

    public interface Listener {
        void onLike(Photo photo);
        void onShare(Photo photo);
    }

    private final Listener listener;
    private final Set<Integer> likedIds = new HashSet<>();
    private List<Photo> photos = new ArrayList<>();

    public ExplorerPostAdapter(Listener listener) {
        this.listener = listener;
    }

    static class PostVH extends RecyclerView.ViewHolder {
        View placeholder;
        ImageView image;
        TextView badge;
        TextView location;
        TextView title;
        TextView author;
        TextView tags;
        TextView likes;
        MaterialButton likeButton;
        MaterialButton shareButton;

        PostVH(View v) {
            super(v);
            placeholder = v.findViewById(R.id.explorer_post_placeholder);
            image = v.findViewById(R.id.iv_explorer_post);
            badge = v.findViewById(R.id.tv_explorer_multi_badge);
            location = v.findViewById(R.id.tv_explorer_location);
            title = v.findViewById(R.id.tv_explorer_title);
            author = v.findViewById(R.id.tv_explorer_author);
            tags = v.findViewById(R.id.tv_explorer_tags);
            likes = v.findViewById(R.id.tv_explorer_likes);
            likeButton = v.findViewById(R.id.btn_explorer_like);
            shareButton = v.findViewById(R.id.btn_explorer_share);
        }
    }

    @NonNull
    @Override
    public PostVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_explorer_post_square, parent, false);
        return new PostVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PostVH h, int position) {
        Photo photo = photos.get(position);
        List<String> uris = photo.getImageUriList();
        String firstUri = !uris.isEmpty() ? uris.get(0) : null;

        if (firstUri != null) {
            h.image.setVisibility(View.VISIBLE);
            h.placeholder.setVisibility(View.GONE);
            Glide.with(h.itemView.getContext())
                    .load(Uri.parse(firstUri))
                    .centerCrop()
                    .into(h.image);
        } else {
            Glide.with(h.itemView.getContext()).clear(h.image);
            h.image.setVisibility(View.GONE);
            h.placeholder.setVisibility(View.VISIBLE);
        }

        if (uris.size() > 1) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText("1/" + uris.size());
        } else {
            h.badge.setVisibility(View.GONE);
        }

        h.location.setText(photo.getLocation() != null && !photo.getLocation().isEmpty()
                ? photo.getLocation()
                : "Lieu non renseigné");
        h.title.setText(photo.getTitle() != null && !photo.getTitle().isEmpty()
                ? photo.getTitle()
                : "Sans titre");
        String safeAuthor = photo.getAuthor() != null ? photo.getAuthor() : "";
        h.author.setText("@" + safeAuthor);
        h.tags.setText(formatTags(photo));
        h.likes.setText(String.valueOf(Math.max(0, photo.getLikes())));
        bindLikeState(h, likedIds.contains(photo.getId()));

        h.itemView.setOnClickListener(v -> openDetail(v, photo));
        h.author.setOnClickListener(v -> {
            if (!safeAuthor.isEmpty()) {
                Intent intent = new Intent(v.getContext(), UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USERNAME, safeAuthor);
                v.getContext().startActivity(intent);
            }
        });
        h.likeButton.setOnClickListener(v -> {
            if (listener != null) listener.onLike(photo);
        });
        h.shareButton.setOnClickListener(v -> {
            if (listener != null) listener.onShare(photo);
        });
    }

    private void bindLikeState(PostVH h, boolean liked) {
        int color = h.itemView.getContext().getResources().getColor(
                liked ? R.color.coral : R.color.default_white, null);
        h.likeButton.setIconTint(ColorStateList.valueOf(color));
    }

    private String formatTags(Photo photo) {
        String category = photo.getCategory() != null ? photo.getCategory().trim() : "";
        String tags = photo.getTags() != null ? photo.getTags().trim() : "";
        if (category.isEmpty()) return tags;
        if (tags.isEmpty()) return category;
        return category + "  ·  " + tags;
    }

    private void openDetail(View v, Photo photo) {
        Intent intent = new Intent(v.getContext(), PhotoDetailActivity.class);
        intent.putExtra(PhotoDetailActivity.EXTRA_PHOTO_ID, photo.getId());
        intent.putExtra(PhotoDetailActivity.EXTRA_TITLE, photo.getTitle());
        intent.putExtra(PhotoDetailActivity.EXTRA_AUTHOR, photo.getAuthor());
        intent.putExtra(PhotoDetailActivity.EXTRA_DATE, photo.getDate());
        intent.putExtra(PhotoDetailActivity.EXTRA_LOCATION, photo.getLocation());
        intent.putExtra(PhotoDetailActivity.EXTRA_CATEGORY, photo.getCategory());
        intent.putExtra(PhotoDetailActivity.EXTRA_TAGS, photo.getTags());
        intent.putExtra(PhotoDetailActivity.EXTRA_LIKES, photo.getLikes());
        intent.putExtra(PhotoDetailActivity.EXTRA_LAT, photo.getLatitude());
        intent.putExtra(PhotoDetailActivity.EXTRA_LNG, photo.getLongitude());
        intent.putExtra(PhotoDetailActivity.EXTRA_IMAGE_URI, photo.getImageUri());
        intent.putExtra(PhotoDetailActivity.EXTRA_VOICE_URI, photo.getVoiceUri());
        v.getContext().startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    public void setPhotos(List<Photo> photos) {
        this.photos = photos != null ? new ArrayList<>(photos) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void appendPhotos(List<Photo> more) {
        if (more == null || more.isEmpty()) return;
        int start = photos.size();
        photos.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    public void setLikedIds(Set<Integer> ids) {
        likedIds.clear();
        if (ids != null) likedIds.addAll(ids);
        notifyDataSetChanged();
    }
}
