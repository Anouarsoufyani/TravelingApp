package com.example.travelshare.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.travelshare.MainActivity;
import com.example.travelshare.R;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Report;
import com.example.travelshare.ui.theme.adapters.CommentAdapter;
import com.example.travelshare.ui.theme.adapters.PhotoAdapter;
import com.example.travelshare.utils.NotificationUtil;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PhotoDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO_ID = "photo_id";
    public static final String EXTRA_TITLE    = "title";
    public static final String EXTRA_AUTHOR   = "author";
    public static final String EXTRA_DATE     = "date";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_TAGS     = "tags";
    public static final String EXTRA_LIKES    = "likes";
    public static final String EXTRA_LAT       = "lat";
    public static final String EXTRA_LNG       = "lng";
    public static final String EXTRA_IMAGE_URI = "image_uri";

    private SessionManager sessionManager;
    private SharedViewModel viewModel;
    private int photoId;
    private int currentLikes;
    private boolean isLiked = false;
    private long authorId = -1; // userId de l'auteur du post, résolu en background

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_detail);

        sessionManager = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        Intent intent = getIntent();
        photoId      = intent.getIntExtra(EXTRA_PHOTO_ID, -1);
        String title    = intent.getStringExtra(EXTRA_TITLE);
        String author   = intent.getStringExtra(EXTRA_AUTHOR);
        String date     = intent.getStringExtra(EXTRA_DATE);
        String location = intent.getStringExtra(EXTRA_LOCATION);
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        String tags     = intent.getStringExtra(EXTRA_TAGS);
        currentLikes    = intent.getIntExtra(EXTRA_LIKES, 0);
        double lat      = intent.getDoubleExtra(EXTRA_LAT, 0.0);
        double lng      = intent.getDoubleExtra(EXTRA_LNG, 0.0);
        String imageUri = intent.getStringExtra(EXTRA_IMAGE_URI);


        final String finalAuthor = author;
        if (finalAuthor != null) {
            viewModel.getUserByLogin(finalAuthor, user -> {
                if (user != null) authorId = (long) user.id;
            });
        }

        // Hero image
        ImageView ivHero = findViewById(R.id.iv_detail_hero);
        if (imageUri != null && !imageUri.isEmpty()) {
            Glide.with(this).load(Uri.parse(imageUri)).centerCrop().into(ivHero);
        }

        ((TextView) findViewById(R.id.tv_detail_title)).setText(title);
        ((TextView) findViewById(R.id.tv_detail_author_date)).setText(date + " · par @" + author);
        ((TextView) findViewById(R.id.tv_detail_category_tags)).setText(category + "  ·  " + tags);
        ((TextView) findViewById(R.id.tv_detail_location)).setText("📍 " + location);

        // Info blocks
        ((TextView) findViewById(R.id.tv_detail_coord)).setText(
                String.format(java.util.Locale.getDefault(), "%.3f° N", lat));
        ((TextView) findViewById(R.id.tv_detail_period)).setText(date != null && date.length() >= 7
                ? date.substring(0, 7) : date);
        ((TextView) findViewById(R.id.tv_detail_access)).setText("Maps →");

        // Bouton retour
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        Button btnLike   = findViewById(R.id.btn_detail_like);
        Button btnRoute  = findViewById(R.id.btn_detail_route);
        Button btnReport = findViewById(R.id.btn_detail_report);
        TextView tvLikesCount = findViewById(R.id.tv_likes_count);
        tvLikesCount.setText(currentLikes + " personnes ont aimé");

        // ── Like / Unlike persisté (accessible en mode anonyme) ───────────
        btnLike.setOnClickListener(v -> {
            isLiked = !isLiked;
            currentLikes += isLiked ? 1 : -1;
            btnLike.setText(isLiked ? "♥ Aimé" : "♡ Like");
            tvLikesCount.setText(currentLikes + " personnes ont aimé");
            viewModel.updateLikes(photoId, currentLikes);

            if (isLiked) {
                String liker    = sessionManager.isLoggedIn() ? sessionManager.getUsername() : "Quelqu'un";
                String likeDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                AppNotification notif = new AppNotification();
                notif.targetUserId = authorId; // notif envoyée à l'auteur du post
                notif.type    = "LIKE";
                notif.message = liker + " a aimé \"" + title + "\"";
                notif.photoId = photoId;
                notif.date    = likeDate;
                viewModel.insertAppNotification(notif);
                NotificationUtil.showNotification(this, "Nouveau like", notif.message);
            }
        });

        // ── Passerelle TravelPath ─────────────────────────────────────────
        String cityForPlan = location != null ? location : "";
        findViewById(R.id.btn_detail_travelpath).setOnClickListener(v -> {
            Intent tpIntent = new Intent(this, MainActivity.class);
            tpIntent.putExtra("OPEN_TRAVELPATH", true);
            tpIntent.putExtra("TRAVELPATH_CITY", cityForPlan);
            tpIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(tpIntent);
        });

        // ── Signalement persisté (accessible en mode anonyme) ─────────────
        btnReport.setOnClickListener(v -> {
            Report report = new Report();
            report.photoId = photoId;
            report.userId  = sessionManager.isLoggedIn() ? sessionManager.getUserId() : -1;
            report.date    = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            viewModel.insertReport(report);
            Toast.makeText(this, "Photo signalée. Merci.", Toast.LENGTH_SHORT).show();
            btnReport.setEnabled(false);
        });

        // ── Itinéraire Google Maps ─────────────────────────────────────────
        btnRoute.setOnClickListener(v -> {
            String uri = "geo:" + lat + "," + lng + "?q=" + lat + "," + lng
                    + "(" + Uri.encode(location) + ")";
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            }
        });

        // ── Photos similaires (même catégorie) ────────────────────────────
        RecyclerView rvSimilar = findViewById(R.id.rv_similar_photos);
        rvSimilar.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        PhotoAdapter similarAdapter = new PhotoAdapter();
        rvSimilar.setAdapter(similarAdapter);
        if (category != null && !category.isEmpty()) {
            viewModel.getPhotosByCategory(category).observe(this, photos -> {
                if (photos != null) {
                    java.util.List<com.example.travelshare.data.models.Photo> filtered = new java.util.ArrayList<>();
                    for (com.example.travelshare.data.models.Photo p : photos) {
                        if (p.getId() != photoId) filtered.add(p);
                    }
                    similarAdapter.setPhotos(filtered);
                }
            });
        }

        // ── Commentaires ───────────────────────────────────────────────────
        RecyclerView rvComments = findViewById(R.id.rv_comments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        CommentAdapter commentAdapter = new CommentAdapter();
        rvComments.setAdapter(commentAdapter);

        viewModel.getCommentsForPhoto((long) photoId)
                .observe(this, commentAdapter::setComments);

        Button btnSend   = findViewById(R.id.btn_send_comment);
        EditText etInput = findViewById(R.id.et_comment_input);

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) return;

            Comment c = new Comment();
            c.photoId    = photoId;
            c.userId     = sessionManager.isLoggedIn() ? sessionManager.getUserId() : -1;
            c.authorName = sessionManager.isLoggedIn() ? sessionManager.getUsername() : "Anonyme";
            c.text       = text;
            c.date       = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

            viewModel.insertComment(c);
            etInput.setText("");
            Toast.makeText(this, "Commentaire publié !", Toast.LENGTH_SHORT).show();

            // Notification in-app + système → envoyée à l'auteur du post
            AppNotification notif = new AppNotification();
            notif.targetUserId = authorId;
            notif.type    = "COMMENT";
            notif.message = c.authorName + " a commenté \"" + title + "\" : " + text;
            notif.photoId = photoId;
            notif.date    = c.date;
            viewModel.insertAppNotification(notif);
            NotificationUtil.showNotification(this, "Nouveau commentaire", notif.message);
        });
    }
}
