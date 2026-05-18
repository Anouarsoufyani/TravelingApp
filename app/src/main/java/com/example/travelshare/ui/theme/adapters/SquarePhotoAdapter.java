package com.example.travelshare.ui.theme.adapters;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.PhotoDetailActivity;

import java.util.ArrayList;
import java.util.List;

public class SquarePhotoAdapter extends RecyclerView.Adapter<SquarePhotoAdapter.PVH> {
    private final int maxItems;
    private List<Photo> photos = new ArrayList<>();

    public SquarePhotoAdapter() {
        this(0);
    }

    public SquarePhotoAdapter(int maxItems) {
        this.maxItems = maxItems;
    }

    static class PVH extends RecyclerView.ViewHolder {
        View placeholder;
        ImageView image;

        PVH(View v) {
            super(v);
            placeholder = v.findViewById(R.id.profile_post_placeholder);
            image = v.findViewById(R.id.iv_profile_post);
        }
    }

    @NonNull
    @Override
    public PVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_post_square, parent, false);
        return new PVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PVH h, int position) {
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

        h.itemView.setOnClickListener(v -> {
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
        });
    }

    @Override
    public int getItemCount() {
        return maxItems > 0 ? Math.min(photos.size(), maxItems) : photos.size();
    }

    public void setPhotos(List<Photo> photos) {
        this.photos = photos != null ? new ArrayList<>(photos) : new ArrayList<>();
        notifyDataSetChanged();
    }
}
