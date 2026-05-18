package com.example.travelshare.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.MainActivity;
import com.example.travelshare.R;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.theme.adapters.SquarePhotoAdapter;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.android.material.button.MaterialButton;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME = "username";
    
    private String targetUsername;
    private String currentUsername;
    private boolean isFollowing = false;
    private MaterialButton btnFollow, btnMessage;
    private TextView tvFollowers, tvFollowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        targetUsername = getIntent().getStringExtra(EXTRA_USERNAME);
        if (targetUsername == null) { finish(); return; }
        
        SessionManager session = new SessionManager(this);
        currentUsername = session.getUsername();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView tvToolbarUsername = findViewById(R.id.tv_toolbar_username);
        TextView tvAvatarInitial  = findViewById(R.id.tv_avatar_initial);
        ImageView ivAvatar        = findViewById(R.id.iv_avatar);
        TextView tvUsername       = findViewById(R.id.tv_username);
        TextView tvBio            = findViewById(R.id.tv_bio);
        TextView tvPostCount      = findViewById(R.id.tv_post_count);
        tvFollowers               = findViewById(R.id.tv_follower_count);
        tvFollowing               = findViewById(R.id.tv_following_count);
        btnFollow                 = findViewById(R.id.btn_follow_action);
        btnMessage                = findViewById(R.id.btn_message_action);
        RecyclerView rv           = findViewById(R.id.rv_user_photos);

        tvToolbarUsername.setText(targetUsername);
        tvUsername.setText("@" + targetUsername);
        tvAvatarInitial.setText(targetUsername.isEmpty() ? "?" : targetUsername.substring(0, 1).toUpperCase());

        if (targetUsername.equalsIgnoreCase(currentUsername)) {
            btnFollow.setVisibility(View.GONE);
            btnMessage.setVisibility(View.GONE);
        }

        SharedViewModel viewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        SquarePhotoAdapter adapter = new SquarePhotoAdapter();
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setAdapter(adapter);

        viewModel.getPublicPhotosByAuthor(targetUsername).observe(this, photos -> {
            adapter.setPhotos(photos);
            tvPostCount.setText(String.valueOf(photos != null ? photos.size() : 0));
        });

        // Load Bio and Avatar
        FirebaseRepository.getInstance().getUserProfile(targetUsername, doc -> {
            if (doc != null && doc.exists()) {
                String bio = doc.getString("bio");
                String avatar = doc.getString("avatarUri");
                runOnUiThread(() -> {
                    if (bio != null && !bio.isEmpty()) {
                        tvBio.setText(bio);
                        tvBio.setVisibility(View.VISIBLE);
                    }
                    if (avatar != null && !avatar.isEmpty()) {
                        ivAvatar.setVisibility(View.VISIBLE);
                        Glide.with(this).load(Uri.parse(avatar)).centerCrop().into(ivAvatar);
                    }
                });
            }
        });

        refreshSocialStats();
        checkFollowStatus();
        checkFriendStatus();

        btnFollow.setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(this, "Connectez-vous pour suivre cet utilisateur", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isFollowing) {
                FirebaseRepository.getInstance().unfollowUser(currentUsername, targetUsername);
                isFollowing = false;
            } else {
                FirebaseRepository.getInstance().followUser(currentUsername, targetUsername);
                isFollowing = true;
            }
            updateFollowButton();
            refreshSocialStats();
            checkFriendStatus();
        });

        btnMessage.setOnClickListener(v -> {
            androidx.fragment.app.Fragment chatFrag = com.example.travelshare.ui.theme.fragments.DirectChatFragment.newInstance(targetUsername);
            
            // To start a fragment from an Activity, we might need a container or a dedicated Activity.
            // Assuming MainActivity is the host and we can use it.
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.putExtra("OPEN_DIRECT_CHAT", true);
            mainIntent.putExtra("DIRECT_CHAT_USER", targetUsername);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(mainIntent);
        });
    }

    private void checkFriendStatus() {
        if (currentUsername == null || targetUsername.equalsIgnoreCase(currentUsername)) return;
        FirebaseRepository.getInstance().getFriends(currentUsername, friends -> {
            boolean isFriend = friends != null && friends.contains(targetUsername);
            runOnUiThread(() -> btnMessage.setVisibility(isFriend ? View.VISIBLE : View.GONE));
        });
    }

    private void checkFollowStatus() {
        if (currentUsername == null) return;
        FirebaseRepository.getInstance().isFollowing(currentUsername, targetUsername, status -> {
            isFollowing = status;
            runOnUiThread(this::updateFollowButton);
        });
    }

    private void updateFollowButton() {
        if (isFollowing) {
            btnFollow.setText("Suivi ✓");
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.surface_field, null)));
            btnFollow.setTextColor(getResources().getColor(R.color.default_white, null));
        } else {
            btnFollow.setText("Suivre");
            btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.sand, null)));
            btnFollow.setTextColor(getResources().getColor(R.color.navy, null));
        }
    }

    private void refreshSocialStats() {
        FirebaseRepository.getInstance().getFollowers(targetUsername, list -> 
                runOnUiThread(() -> tvFollowers.setText(String.valueOf(list.size()))));
        FirebaseRepository.getInstance().getFollowing(targetUsername, list -> 
                runOnUiThread(() -> tvFollowing.setText(String.valueOf(list.size()))));
    }
}
