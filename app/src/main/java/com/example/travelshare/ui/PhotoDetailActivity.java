package com.example.travelshare.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.travelshare.data.AppDatabase;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.travelshare.MainActivity;
import com.example.travelshare.R;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Report;
import com.example.travelshare.ui.UserProfileActivity;
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
    public static final String EXTRA_VOICE_URI = "voice_uri";

    private SessionManager sessionManager;
    private SharedViewModel viewModel;
    private int photoId;
    private int currentLikes;
    private boolean isLiked = false;
    private long authorId = -1;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private ActivityResultLauncher<String> locationPermLauncher;
    private double photoLat, photoLng;
    private TextView tvAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_detail);

        sessionManager = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(SharedViewModel.class);

        locationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) fetchRouteToPhoto(); });


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
        photoLat = lat; photoLng = lng;
        String imageUri  = intent.getStringExtra(EXTRA_IMAGE_URI);
        String voiceUri  = intent.getStringExtra(EXTRA_VOICE_URI);


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
        TextView tvAuthorDate = findViewById(R.id.tv_detail_author_date);
        tvAuthorDate.setText(date + " · par @" + author);
        tvAuthorDate.setOnClickListener(v -> {
            if (finalAuthor != null && !finalAuthor.isEmpty()) {
                Intent profileIntent = new Intent(this, UserProfileActivity.class);
                profileIntent.putExtra(UserProfileActivity.EXTRA_USERNAME, finalAuthor);
                startActivity(profileIntent);
            }
        });
        ((TextView) findViewById(R.id.tv_detail_category_tags)).setText(category + "  ·  " + tags);
        ((TextView) findViewById(R.id.tv_detail_location)).setText("📍 " + location);

        // Info blocks
        boolean isApprox = lat != 0 && Math.abs(lat - Math.round(lat * 10.0) / 10.0) < 0.001
                && Math.abs(lat % 0.1) < 0.001;
        ((TextView) findViewById(R.id.tv_detail_coord)).setText(isApprox
                ? String.format(java.util.Locale.getDefault(), "≈ %.1f° N", lat)
                : String.format(java.util.Locale.getDefault(), "%.4f° N", lat));
        ((TextView) findViewById(R.id.tv_detail_period)).setText(date != null && date.length() >= 7
                ? date.substring(0, 7) : date);
        tvAccess = findViewById(R.id.tv_detail_access);
        tvAccess.setText("📍 Calcul...");
        if (lat != 0 && lng != 0) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fetchRouteToPhoto();
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        } else {
            tvAccess.setText("Maps →");
        }

        // Bouton retour
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        Button btnLike   = findViewById(R.id.btn_detail_like);
        Button btnRoute  = findViewById(R.id.btn_detail_route);
        Button btnReport = findViewById(R.id.btn_detail_report);
        TextView tvLikesCount = findViewById(R.id.tv_likes_count);
        tvLikesCount.setText(currentLikes + " personnes ont aimé");

        // Restaurer état like depuis SharedPreferences
        android.content.SharedPreferences likePrefs = getSharedPreferences("likes", MODE_PRIVATE);
        java.util.Set<String> likedSet = new java.util.HashSet<>(
                likePrefs.getStringSet("liked_ids", new java.util.HashSet<>()));
        isLiked = likedSet.contains(String.valueOf(photoId));
        btnLike.setText(isLiked ? "♥ Aimé" : "♡ Like");

        // ── Like / Unlike persisté (accessible en mode anonyme) ───────────
        btnLike.setOnClickListener(v -> {
            isLiked = !isLiked;
            currentLikes += isLiked ? 1 : -1;
            btnLike.setText(isLiked ? "♥ Aimé" : "♡ Like");
            tvLikesCount.setText(currentLikes + " personnes ont aimé");
            viewModel.updateLikes(photoId, currentLikes);

            // Sauvegarder dans SharedPreferences
            java.util.Set<String> ids = new java.util.HashSet<>(
                    getSharedPreferences("likes", MODE_PRIVATE).getStringSet("liked_ids", new java.util.HashSet<>()));
            if (isLiked) ids.add(String.valueOf(photoId)); else ids.remove(String.valueOf(photoId));
            getSharedPreferences("likes", MODE_PRIVATE).edit().putStringSet("liked_ids", ids).apply();

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

        // ── Partager dans un groupe ───────────────────────────────────────
        findViewById(R.id.btn_share_to_group).setOnClickListener(v -> {
            if (!sessionManager.isLoggedIn()) {
                Toast.makeText(this, "Connectez-vous pour partager dans un groupe", Toast.LENGTH_SHORT).show();
                return;
            }
            long userId = sessionManager.getUserId();
            AppDatabase.databaseWriteExecutor.execute(() -> {
                java.util.List<com.example.travelshare.data.models.Group> groups =
                        AppDatabase.getInstance(this).groupDao().getGroupsForUserSync(userId);
                runOnUiThread(() -> {
                    if (groups == null || groups.isEmpty()) {
                        Toast.makeText(this, "Vous n'appartenez à aucun groupe", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[groups.size()];
                    for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).name;
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Partager dans…")
                            .setItems(names, (d, which) -> {
                                com.example.travelshare.data.models.GroupMessage msg =
                                        new com.example.travelshare.data.models.GroupMessage();
                                msg.groupId    = groups.get(which).id;
                                msg.userId     = userId;
                                msg.authorName = sessionManager.getUsername();
                                msg.message    = "📸 " + title + " — 📍 " + location;
                                msg.photoId    = photoId;
                                msg.date       = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                                viewModel.sendGroupMessage(msg);
                                Toast.makeText(this, "Partagé dans \"" + names[which] + "\"", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Annuler", null)
                            .show();
                });
            });
        });

        // ── Partage externe ───────────────────────────────────────────────
        findViewById(R.id.btn_detail_share).setOnClickListener(v -> {
            String text = title + "\n📍 " + location + "\n" + category + "  ·  " + tags
                    + "\nVia Traveling";
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(shareIntent, "Partager via…"));
        });

        // ── Actions propriétaire ──────────────────────────────────────────
        boolean isOwner = sessionManager.isLoggedIn()
                && sessionManager.getUsername() != null
                && sessionManager.getUsername().equals(author);
        if (isOwner) {
            View layoutOwner = findViewById(R.id.layout_owner_actions);
            layoutOwner.setVisibility(View.VISIBLE);

            TextView tvTitle = findViewById(R.id.tv_detail_title);

            findViewById(R.id.btn_edit_title).setOnClickListener(v -> {
                android.app.AlertDialog.Builder dlg = new android.app.AlertDialog.Builder(this);
                dlg.setTitle("Modifier le titre");
                android.widget.EditText etNew = new android.widget.EditText(this);
                etNew.setText(tvTitle.getText());
                dlg.setView(etNew);
                dlg.setPositiveButton("Sauvegarder", (d, w) -> {
                    String newTitle = etNew.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        viewModel.updatePhotoTitle(photoId, newTitle);
                        tvTitle.setText(newTitle);
                        Toast.makeText(this, "Titre modifié", Toast.LENGTH_SHORT).show();
                    }
                });
                dlg.setNegativeButton("Annuler", null);
                dlg.show();
            });

            findViewById(R.id.btn_delete_post).setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Supprimer cette photo ?")
                        .setMessage("Cette action est irréversible.")
                        .setPositiveButton("Supprimer", (d, w) -> {
                            viewModel.deletePhoto(photoId);
                            Toast.makeText(this, "Photo supprimée", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            });
        }

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

        // ── Note vocale ───────────────────────────────────────────────────
        View layoutVoice = findViewById(R.id.layout_voice_note);
        Button btnPlayVoice = findViewById(R.id.btn_play_voice);
        if (voiceUri != null && !voiceUri.isEmpty()) {
            layoutVoice.setVisibility(View.VISIBLE);
            btnPlayVoice.setOnClickListener(v -> {
                if (isPlaying && mediaPlayer != null) {
                    mediaPlayer.pause();
                    isPlaying = false;
                    btnPlayVoice.setText("▶ Écouter");
                } else {
                    try {
                        if (mediaPlayer == null) {
                            mediaPlayer = new MediaPlayer();
                            mediaPlayer.setDataSource(this, Uri.parse(voiceUri));
                            mediaPlayer.prepare();
                            mediaPlayer.setOnCompletionListener(mp -> {
                                isPlaying = false;
                                btnPlayVoice.setText("▶ Écouter");
                            });
                        }
                        mediaPlayer.start();
                        isPlaying = true;
                        btnPlayVoice.setText("⏸ Pause");
                    } catch (Exception e) {
                        Toast.makeText(this, "Impossible de lire la note vocale", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // ── Photos similaires (tags puis catégorie) ───────────────────────
        RecyclerView rvSimilar = findViewById(R.id.rv_similar_photos);
        rvSimilar.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        PhotoAdapter similarAdapter = new PhotoAdapter();
        rvSimilar.setAdapter(similarAdapter);

        String firstTag = extractFirstTag(tags);
        if (firstTag != null) {
            viewModel.getPhotosByTag(firstTag).observe(this, tagPhotos -> {
                java.util.List<com.example.travelshare.data.models.Photo> result = new java.util.ArrayList<>();
                if (tagPhotos != null) {
                    for (com.example.travelshare.data.models.Photo p : tagPhotos) {
                        if (p.getId() != photoId) result.add(p);
                    }
                }
                if (result.isEmpty() && category != null && !category.isEmpty()) {
                    viewModel.getPhotosByCategory(category).observe(this, catPhotos -> {
                        java.util.List<com.example.travelshare.data.models.Photo> catResult = new java.util.ArrayList<>();
                        if (catPhotos != null) {
                            for (com.example.travelshare.data.models.Photo p : catPhotos) {
                                if (p.getId() != photoId) catResult.add(p);
                            }
                        }
                        similarAdapter.setPhotos(catResult);
                    });
                } else {
                    similarAdapter.setPhotos(result);
                }
            });
        } else if (category != null && !category.isEmpty()) {
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
        long currentUserId = sessionManager.isLoggedIn() ? sessionManager.getUserId() : -1;
        commentAdapter.init(currentUserId, id -> viewModel.deleteComment(id));
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

    private void fetchRouteToPhoto() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { tvAccess.setText("Maps →"); return; }
        try {
            Location last = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (last == null || l.getAccuracy() < last.getAccuracy())) last = l;
            }
            if (last == null) { tvAccess.setText("Maps →"); return; }
            final double userLat = last.getLatitude();
            final double userLng = last.getLongitude();
            new Thread(() -> {
                try {
                    String url = String.format(java.util.Locale.US,
                            "https://router.project-osrm.org/route/v1/foot/%.6f,%.6f;%.6f,%.6f?overview=false",
                            userLng, userLat, photoLng, photoLat);
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection)
                            new java.net.URL(url).openConnection();
                    con.setRequestProperty("User-Agent", "TravelingApp/1.0");
                    con.setConnectTimeout(6000); con.setReadTimeout(6000);
                    if (con.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(con.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                        if ("Ok".equals(root.optString("code"))) {
                            org.json.JSONObject route = root.getJSONArray("routes").getJSONObject(0);
                            double durationSec = route.optDouble("duration", -1);
                            double distanceM   = route.optDouble("distance", -1);
                            if (durationSec > 0) {
                                int min = (int) Math.round(durationSec / 60.0);
                                String dist = distanceM > 1000
                                        ? String.format(java.util.Locale.getDefault(), "%.1f km", distanceM / 1000)
                                        : ((int) distanceM) + " m";
                                String label = "🚶 " + (min < 60 ? min + " min" : (min/60) + "h" + String.format("%02d", min%60))
                                        + " (" + dist + ")";
                                runOnUiThread(() -> tvAccess.setText(label));
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                runOnUiThread(() -> tvAccess.setText("Maps →"));
            }).start();
        } catch (SecurityException e) {
            tvAccess.setText("Maps →");
        }
    }

    private String extractFirstTag(String tags) {
        if (tags == null || tags.trim().isEmpty()) return null;
        String[] parts = tags.split("[,\\s]+");
        for (String part : parts) {
            String t = part.replace("#", "").trim();
            if (t.length() > 2) return t;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
