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

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {


    private static final int[][] GRADIENTS = {
        {0xFF1B3A5C, 0xFF2D5480},  // navy
        {0xFF2A5C3A, 0xFF3A7C50},  // forest
        {0xFF7A3E0A, 0xFFA8601A},  // maroc
        {0xFF2A4A6B, 0xFF4A6A8B},  // oslo
        {0xFF5C2A6B, 0xFF8A4A98},  // plum
        {0xFF3A4A2A, 0xFF5A6A3A},  // sage
        {0xFF2A7D6F, 0xFF3FA090},  // teal
        {0xFFC4603A, 0xFFD4804A},  // terracotta
    };

    private List<Photo> photos = new ArrayList<>();

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        View gradientBg;
        ImageView ivPhoto;
        TextView tvLocation, tvTitle, tvAuthor, tvCatTags, tvLikes;

        public PhotoViewHolder(View v) {
            super(v);
            gradientBg  = v.findViewById(R.id.photo_gradient_bg);
            ivPhoto     = v.findViewById(R.id.photo_image);
            tvLocation  = v.findViewById(R.id.photo_location);
            tvTitle     = v.findViewById(R.id.photo_title);
            tvAuthor    = v.findViewById(R.id.photo_author);
            tvCatTags   = v.findViewById(R.id.photo_cat_tags);
            tvLikes     = v.findViewById(R.id.photo_likes);
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

        // Couleur de fond déterministe selon la position (fallback si pas d'image)
        int[] colors = GRADIENTS[position % GRADIENTS.length];
        h.gradientBg.setBackgroundColor(colors[0]);

        // Charger l'image si disponible
        String imageUri = p.getImageUri();
        if (imageUri != null && !imageUri.isEmpty()) {
            h.ivPhoto.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                    .load(Uri.parse(imageUri))
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
            v.getContext().startActivity(intent);
        });
    }

    @Override public int getItemCount() { return photos.size(); }

    public void setPhotos(List<Photo> photos) {
        this.photos = photos != null ? photos : new ArrayList<>();
        notifyDataSetChanged();
    }
}
