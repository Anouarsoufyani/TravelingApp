package com.example.travelshare.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.models.User;
import com.example.travelshare.ui.theme.adapters.PhotoAdapter;
import com.example.travelshare.viewmodels.SharedViewModel;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME = "username";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        String username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (username == null) { finish(); return; }

        TextView btnBack          = findViewById(R.id.btn_back);
        TextView tvToolbarUsername = findViewById(R.id.tv_toolbar_username);
        TextView tvAvatarInitial  = findViewById(R.id.tv_avatar_initial);
        ImageView ivAvatar        = findViewById(R.id.iv_avatar);
        TextView tvUsername       = findViewById(R.id.tv_username);
        TextView tvBio            = findViewById(R.id.tv_bio);
        TextView tvPostCount      = findViewById(R.id.tv_post_count);
        RecyclerView rv           = findViewById(R.id.rv_user_photos);

        btnBack.setOnClickListener(v -> finish());

        tvToolbarUsername.setText(username);
        tvUsername.setText("@" + username);
        tvAvatarInitial.setText(username.substring(0, 1).toUpperCase());

        SharedViewModel viewModel = new ViewModelProvider.AndroidViewModelFactory(getApplication())
                .create(SharedViewModel.class);

        PhotoAdapter adapter = new PhotoAdapter();
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setAdapter(adapter);

        viewModel.getPublicPhotosByAuthor(username).observe(this, photos -> {
            adapter.setPhotos(photos);
            tvPostCount.setText(String.valueOf(photos != null ? photos.size() : 0));
        });

        // Charger bio + avatar depuis la BDD
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            User user = db.userDao().getUserByLogin(username);
            if (user == null) return;
            runOnUiThread(() -> {
                if (user.bio != null && !user.bio.isEmpty()) {
                    tvBio.setText(user.bio);
                    tvBio.setVisibility(View.VISIBLE);
                }
                if (user.avatarUri != null && !user.avatarUri.isEmpty()) {
                    ivAvatar.setVisibility(View.VISIBLE);
                    Glide.with(this).load(Uri.parse(user.avatarUri)).centerCrop().into(ivAvatar);
                }
            });
        });
    }
}
