package com.example.travelshare.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.travelshare.MainActivity;
import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Report;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.theme.adapters.CommentAdapter;
import com.example.travelshare.ui.theme.adapters.SquarePhotoAdapter;
import com.example.travelshare.ui.theme.fragments.PlanDetailFragment;
import com.example.travelshare.utils.NotificationUtil;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO_ID = "photo_id";
    public static final String EXTRA_TITLE    = "title";
    public static final String EXTRA_AUTHOR   = "author";
    public static final String EXTRA_AUTHOR_ID = "author_id";
    public static final String EXTRA_DATE     = "date";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_TAGS     = "tags";
    public static final String EXTRA_LIKES    = "likes";
    public static final String EXTRA_LAT      = "lat";
    public static final String EXTRA_LNG      = "lng";
    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_VOICE_URI = "voice_uri";

    private SharedViewModel viewModel;
    private SessionManager sessionManager;
    private ListenerRegistration likesListener;
    private ListenerRegistration commentsListener;

    private int currentLikes = 0;
    private boolean isLiked = false;
    private double photoLat, photoLng;
    private TextView tvAccess;

    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_detail);

        viewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        sessionManager = new SessionManager(this);

        Intent intent = getIntent();
        int photoId    = intent.getIntExtra(EXTRA_PHOTO_ID, -1);
        String title   = intent.getStringExtra(EXTRA_TITLE);
        String author  = intent.getStringExtra(EXTRA_AUTHOR);
        long authorId  = intent.getLongExtra(EXTRA_AUTHOR_ID, -1);
        String date    = intent.getStringExtra(EXTRA_DATE);
        String location = intent.getStringExtra(EXTRA_LOCATION);
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        String tags    = intent.getStringExtra(EXTRA_TAGS);
        int likes      = intent.getIntExtra(EXTRA_LIKES, 0);
        double lat     = intent.getDoubleExtra(EXTRA_LAT, 0);
        double lng     = intent.getDoubleExtra(EXTRA_LNG, 0);
        String imageUri = intent.getStringExtra(EXTRA_IMAGE_URI);
        String voiceUri = intent.getStringExtra(EXTRA_VOICE_URI);

        photoLat = lat; photoLng = lng;
        currentLikes = likes;

        ViewPager2 vpCarousel = findViewById(R.id.vp_detail_carousel);
        TabLayout tabIndicator = findViewById(R.id.tab_carousel_indicator);
        TextView tvTitle  = findViewById(R.id.tv_detail_title);
        TextView tvAuthorDate = findViewById(R.id.tv_detail_author_date);
        TextView tvAuthorInitial = findViewById(R.id.tv_detail_author_initial);
        TextView tvLocation = findViewById(R.id.tv_detail_location);
        TextView tvCatTags = findViewById(R.id.tv_detail_category_tags);
        TextView tvLikesCount = findViewById(R.id.tv_likes_count);
        tvAccess = findViewById(R.id.tv_detail_access);

        TextView btnLike  = findViewById(R.id.btn_detail_like);
        TextView btnRoute = findViewById(R.id.btn_detail_route);
        TextView btnReport = findViewById(R.id.btn_detail_report);

        tvTitle.setText(title);
        tvAuthorInitial.setText(author != null && !author.isEmpty()
                ? String.valueOf(author.charAt(0)).toUpperCase(Locale.getDefault())
                : "?");
        tvAuthorDate.setText("Par " + author + " • " + date);
        tvLocation.setText(location);
        tvCatTags.setText(formatCategoryTags(category, tags));
        tvLikesCount.setText(currentLikes + " personnes ont aimé");

        // Carousel Setup
        List<String> images = new ArrayList<>();
        if (imageUri != null && !imageUri.isEmpty()) {
            images.addAll(Arrays.asList(imageUri.split("\\|")));
        }
        
        CarouselAdapter carouselAdapter = new CarouselAdapter(images);
        vpCarousel.setAdapter(carouselAdapter);
        
        if (images.size() > 1) {
            new TabLayoutMediator(tabIndicator, vpCarousel, (tab, position) -> {}).attach();
            tabIndicator.setVisibility(View.VISIBLE);
        } else {
            tabIndicator.setVisibility(View.GONE);
        }

        tvAuthorDate.setOnClickListener(v -> {
            Intent profileIntent = new Intent(this, UserProfileActivity.class);
            profileIntent.putExtra(UserProfileActivity.EXTRA_USERNAME, author);
            startActivity(profileIntent);
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        fetchRouteToPhoto();

        likesListener = FirebaseRepository.getInstance().listenToLikes(photoId, newLikes -> {
            currentLikes = newLikes;
            tvLikesCount.setText(currentLikes + " personnes ont aimé");
            viewModel.updateLikes(photoId, currentLikes);
        });

        String username = sessionManager.getUsername();
        String safeUsername = (username != null) ? username.toLowerCase() : "anonyme";
        String likeKey = "liked_ids_" + safeUsername;
        android.content.SharedPreferences likePrefs = getSharedPreferences("likes", MODE_PRIVATE);
        java.util.Set<String> likedSet = new java.util.HashSet<>(
                likePrefs.getStringSet(likeKey, new java.util.HashSet<>()));
        isLiked = likedSet.contains(String.valueOf(photoId));
        btnLike.setText(isLiked ? "♥ Aimé" : "♡ J’aime");

        btnLike.setOnClickListener(v -> {
            if (!sessionManager.isLoggedIn()) {
                Toast.makeText(this, "Connectez-vous pour aimer une publication", Toast.LENGTH_SHORT).show();
                return;
            }

            isLiked = !isLiked;
            currentLikes = Math.max(0, currentLikes + (isLiked ? 1 : -1));
            btnLike.setText(isLiked ? "♥ Aimé" : "♡ J’aime");
            tvLikesCount.setText(currentLikes + " personnes ont aimé");
            viewModel.updateLikes(photoId, currentLikes);
            FirebaseRepository.getInstance().updateLikes(photoId, currentLikes);

            java.util.Set<String> ids = new java.util.HashSet<>(
                    getSharedPreferences("likes", MODE_PRIVATE).getStringSet(likeKey, new java.util.HashSet<>()));
            if (isLiked) ids.add(String.valueOf(photoId)); else ids.remove(String.valueOf(photoId));
            getSharedPreferences("likes", MODE_PRIVATE).edit().putStringSet(likeKey, ids).apply();

            if (isLiked) {
                String liker    = sessionManager.isLoggedIn() ? sessionManager.getUsername() : "Quelqu'un";
                String likeDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                AppNotification notif = new AppNotification();
                notif.targetUserId = authorId;
                notif.type    = "LIKE";
                notif.message = liker + " a aimé \"" + title + "\"";
                notif.photoId = photoId;
                notif.date    = likeDate;
                viewModel.insertAppNotification(notif);
                FirebaseRepository.getInstance().saveNotification(author, notif);
                NotificationUtil.showNotification(this, "Nouveau like", notif.message);
            }
        });

        findViewById(R.id.btn_detail_share).setOnClickListener(v -> {
            if (!sessionManager.isLoggedIn()) {
                Toast.makeText(this, "Connectez-vous pour partager", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] options = {"À un groupe", "À un ami", "Externe (Texte)"};
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Partager cette publication…")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) shareToGroup(title, location, photoId);
                        else if (which == 1) shareToFriend(title, photoId);
                        else {
                            String shareText = title + "\n📍 " + location + "\n" + category + "  ·  " + tags
                                    + "\nVia Traveling";
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                            startActivity(Intent.createChooser(shareIntent, "Partager via…"));
                        }
                    }).show();
        });

        boolean isOwner = sessionManager.isLoggedIn()
                && sessionManager.getUsername() != null
                && sessionManager.getUsername().equals(author);
        if (isOwner) {
            View layoutOwner = findViewById(R.id.layout_owner_actions);
            layoutOwner.setVisibility(View.VISIBLE);

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

        String cityForPlan = location != null ? location : "";
        findViewById(R.id.btn_detail_travelpath).setOnClickListener(v -> {
            Intent tpIntent = new Intent(this, MainActivity.class);
            tpIntent.putExtra("OPEN_TRAVELPATH", true);
            tpIntent.putExtra("TRAVELPATH_CITY", cityForPlan);
            tpIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(tpIntent);
        });

        btnReport.setOnClickListener(v -> {
            Report report = new Report();
            report.photoId = photoId;
            report.userId  = sessionManager.isLoggedIn() ? sessionManager.getUserId() : -1;
            report.date    = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            viewModel.insertReport(report);
            Toast.makeText(this, "Photo signalée. Merci.", Toast.LENGTH_SHORT).show();
            btnReport.setEnabled(false);
        });

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

        View layoutVoice = findViewById(R.id.layout_voice_note);
        TextView btnPlayVoice = findViewById(R.id.btn_play_voice);
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

        RecyclerView rvSimilar = findViewById(R.id.rv_similar_photos);
        rvSimilar.setLayoutManager(new GridLayoutManager(this, 3));
        rvSimilar.setNestedScrollingEnabled(false);
        SquarePhotoAdapter similarAdapter = new SquarePhotoAdapter(3);
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

        RecyclerView rvComments = findViewById(R.id.rv_comments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        CommentAdapter commentAdapter = new CommentAdapter();
        long currentUserId = sessionManager.isLoggedIn() ? sessionManager.getUserId() : -1;
        commentAdapter.init(currentUserId, id -> viewModel.deleteComment(id));
        rvComments.setAdapter(commentAdapter);

        viewModel.getCommentsForPhoto((long) photoId)
                .observe(this, commentAdapter::setComments);

        commentsListener = FirebaseRepository.getInstance()
                .listenToComments(photoId, AppDatabase.getInstance(this));

        TextView btnSend   = findViewById(R.id.btn_send_comment);
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

            FirebaseRepository.getInstance().saveComment(photoId, c.authorName, c.text, c.date);

            etInput.setText("");
            Toast.makeText(this, "Commentaire publié !", Toast.LENGTH_SHORT).show();

            AppNotification notif = new AppNotification();
            notif.targetUserId = authorId;
            notif.type    = "COMMENT";
            notif.message = c.authorName + " a commenté \"" + title + "\" : " + text;
            notif.photoId = photoId;
            notif.date    = c.date;
            viewModel.insertAppNotification(notif);
            FirebaseRepository.getInstance().saveNotification(author, notif);
            NotificationUtil.showNotification(this, "Nouveau commentaire", notif.message);
        });
    }

    private void shareToGroup(String title, String location, int photoId) {
        long userId = sessionManager.getUserId();
        FirebaseRepository.getInstance().getMyMemberGroups(sessionManager.getUsername(), groups -> {
            runOnUiThread(() -> {
                if (groups == null || groups.isEmpty()) {
                    Toast.makeText(this, "Vous n'appartenez à aucun groupe", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[groups.size()];
                for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).name;
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Choisir un groupe")
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
                            FirebaseRepository.getInstance().saveSharedPhotoMessage(
                                    groups.get(which).name,
                                    sessionManager.getUsername(),
                                    msg.message,
                                    photoId,
                                    msg.date);
                            Toast.makeText(this, "Partagé dans \"" + names[which] + "\"", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            });
        });
    }

    private void shareToFriend(String title, int photoId) {
        FirebaseRepository.getInstance().getFriends(sessionManager.getUsername(), friends -> {
            runOnUiThread(() -> {
                if (friends == null || friends.isEmpty()) {
                    Toast.makeText(this, "Vous n'avez pas d'amis (suivis mutuels)", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = friends.toArray(new String[0]);
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Choisir un ami")
                        .setItems(names, (d, which) -> {
                            String target = names[which];
                            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                            String msgText = "📸 " + title;
                            
                            // Send direct message
                            FirebaseRepository.getInstance().saveDirectMessage(
                                    sessionManager.getUsername(), target, msgText, date, photoId, 0, -1, null);

                            // Optional: keep notification for alert
                            AppNotification notif = new AppNotification();
                            notif.type = "SHARE_POST";
                            notif.senderUsername = sessionManager.getUsername();
                            notif.message = sessionManager.getUsername() + " vous a partagé une publication : \"" + title + "\"";
                            notif.photoId = photoId;
                            notif.date = date;
                            FirebaseRepository.getInstance().saveNotification(target, notif);
                            
                            Toast.makeText(this, "Publication partagée avec " + target, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            });
        });
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void fetchRouteToPhoto() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { if(tvAccess != null) tvAccess.setText("Maps →"); return; }
        try {
            Location last = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (last == null || l.getAccuracy() < last.getAccuracy())) last = l;
            }
            if (last == null) { if(tvAccess != null) tvAccess.setText("Maps →"); return; }
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
                                runOnUiThread(() -> { if(tvAccess != null) tvAccess.setText(label); });
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                runOnUiThread(() -> { if(tvAccess != null) tvAccess.setText("Maps →"); });
            }).start();
        } catch (SecurityException e) {
            if(tvAccess != null) tvAccess.setText("Maps →");
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

    private String formatCategoryTags(String category, String tags) {
        String safeCategory = category != null && !category.trim().isEmpty()
                ? category.trim() : "Voyage";
        String safeTags = tags != null ? tags.trim() : "";
        return safeTags.isEmpty() ? safeCategory : safeCategory + " · " + safeTags;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (likesListener != null) likesListener.remove();
        if (commentsListener != null) commentsListener.remove();
    }

    // --- CAROUSEL ADAPTER ---
    static class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.CarouselVH> {
        private final List<String> imageUrls;

        CarouselAdapter(List<String> imageUrls) { this.imageUrls = imageUrls; }

        @NonNull @Override public CarouselVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundResource(R.drawable.bg_photo_placeholder);
            return new CarouselVH(iv);
        }

        @Override public void onBindViewHolder(@NonNull CarouselVH holder, int position) {
            if (imageUrls.isEmpty()) {
                Glide.with(holder.itemView.getContext()).clear(holder.iv);
                return;
            }
            Glide.with(holder.itemView.getContext()).load(Uri.parse(imageUrls.get(position))).into(holder.iv);
        }

        @Override public int getItemCount() { return Math.max(1, imageUrls.size()); }

        static class CarouselVH extends RecyclerView.ViewHolder {
            ImageView iv;
            CarouselVH(View v) { super(v); iv = (ImageView) v; }
        }
    }
}
