package com.example.travelshare.ui.theme.adapters;

import android.content.Intent;
import android.graphics.Color;
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

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private static final int[][] GRADIENTS = {
        {0xFF2D9CDB, 0xFF5BE7C4},
        {0xFF102027, 0xFF2F5D62},
        {0xFF5267D8, 0xFF2D9CDB},
        {0xFF4BAF8F, 0xFFD8B45C},
        {0xFF172126, 0xFF66757D},
        {0xFF2F5D62, 0xFF4BAF8F},
    };

    private List<Photo> photos = new ArrayList<>();

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        View gradientBg;
        ImageView ivPhoto;
        TextView tvLocation, tvTitle, tvAuthor, tvCatTags, tvLikes, tvBadge;

        public PhotoViewHolder(View v) {
            super(v);
            gradientBg  = v.findViewById(R.id.photo_gradient_bg);
            ivPhoto     = v.findViewById(R.id.photo_image);
            tvLocation  = v.findViewById(R.id.photo_location);
            tvTitle     = v.findViewById(R.id.photo_title);
            tvAuthor    = v.findViewById(R.id.photo_author);
            tvCatTags   = v.findViewById(R.id.photo_cat_tags);
            tvLikes     = v.findViewById(R.id.photo_likes);
            tvBadge     = v.findViewById(R.id.tv_multi_photo_badge);
        }
    }

    @NonNull @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder h, int position) {
        Photo p = photos.get(position);

        int[] colors = GRADIENTS[position % GRADIENTS.length];
        h.gradientBg.setBackgroundColor(colors[0]);

        List<String> uris = p.getImageUriList();
        String firstUri = !uris.isEmpty() ? uris.get(0) : null;
        
        if (h.tvBadge != null) {
            if (uris.size() > 1) {
                h.tvBadge.setVisibility(View.VISIBLE);
                h.tvBadge.setText("1/" + uris.size());
            } else {
                h.tvBadge.setVisibility(View.GONE);
            }
        }
        if (firstUri != null) {
            h.ivPhoto.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                    .load(Uri.parse(firstUri))
                    .centerCrop()
                    .into(h.ivPhoto);
        } else {
            h.ivPhoto.setVisibility(View.GONE);
            Glide.with(h.itemView.getContext()).clear(h.ivPhoto);
        }

        h.tvLocation.setText(p.getLocation());
        h.tvTitle.setText(p.getTitle());
        h.tvAuthor.setText("@" + (p.getAuthor() != null ? p.getAuthor() : ""));

        String catTags = "";
        if (p.getCategory() != null && !p.getCategory().isEmpty()) catTags = p.getCategory();
        if (p.getTags() != null && !p.getTags().isEmpty())
            catTags += catTags.isEmpty() ? p.getTags() : "  ·  " + p.getTags();
        h.tvCatTags.setText(catTags);

        h.tvLikes.setText(p.getLikes() + " likes");

        h.tvAuthor.setOnClickListener(v -> {
            String author = p.getAuthor();
            if (author != null && !author.isEmpty()) {
                Intent intent = new Intent(v.getContext(), UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USERNAME, author);
                v.getContext().startActivity(intent);
            }
        });

        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), PhotoDetailActivity.class);
            intent.putExtra(PhotoDetailActivity.EXTRA_PHOTO_ID,  p.getId());
            intent.putExtra(PhotoDetailActivity.EXTRA_TITLE,     p.getTitle());
            intent.putExtra(PhotoDetailActivity.EXTRA_AUTHOR,    p.getAuthor());
            intent.putExtra(PhotoDetailActivity.EXTRA_DATE,      p.getDate());
            intent.putExtra(PhotoDetailActivity.EXTRA_LOCATION,  p.getLocation());
            intent.putExtra(PhotoDetailActivity.EXTRA_CATEGORY,  p.getCategory());
            intent.putExtra(PhotoDetailActivity.EXTRA_TAGS,      p.getTags());
            intent.putExtra(PhotoDetailActivity.EXTRA_LIKES,     p.getLikes());
            intent.putExtra(PhotoDetailActivity.EXTRA_LAT,       p.getLatitude());
            intent.putExtra(PhotoDetailActivity.EXTRA_LNG,       p.getLongitude());
            intent.putExtra(PhotoDetailActivity.EXTRA_IMAGE_URI, p.getImageUri());
            intent.putExtra(PhotoDetailActivity.EXTRA_VOICE_URI, p.getVoiceUri());
            v.getContext().startActivity(intent);
        });
    }

    @Override public int getItemCount() { return photos.size(); }

    public void setPhotos(List<Photo> photos) {
        this.photos = photos != null ? new ArrayList<>(photos) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void appendPhotos(List<Photo> more) {
        if (more == null || more.isEmpty()) return;
        int start = this.photos.size();
        this.photos.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }
}
