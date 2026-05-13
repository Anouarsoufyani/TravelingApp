package com.example.travelshare.data.repository;

import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Photo;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class FirebaseRepository {

    private static FirebaseRepository instance;
    private final FirebaseFirestore db;

    private FirebaseRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static FirebaseRepository getInstance() {
        if (instance == null) instance = new FirebaseRepository();
        return instance;
    }

    // ── Photos ────────────────────────────────────────────────────────────────

    public void savePhoto(Photo photo, long roomId) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId",     roomId);
        data.put("title",      photo.getTitle());
        data.put("location",   photo.getLocation());
        data.put("author",     photo.getAuthor());
        data.put("latitude",   photo.getLatitude());
        data.put("longitude",  photo.getLongitude());
        data.put("date",       photo.getDate());
        data.put("category",   photo.getCategory());
        data.put("tags",       photo.getTags());
        data.put("visibility", photo.getVisibility());
        data.put("likes",      0);
        data.put("imageUri",   photo.getImageUri() != null ? photo.getImageUri() : "");
        data.put("timestamp",  FieldValue.serverTimestamp());

        db.collection("photos")
                .document(String.valueOf(roomId))
                .set(data)
                .addOnFailureListener(e ->
                        android.util.Log.w("FirebaseRepository", "savePhoto failed", e));
    }

    // ── Likes ─────────────────────────────────────────────────────────────────

    public void updateLikes(long photoId, int likes) {
        db.collection("photos")
                .document(String.valueOf(photoId))
                .update("likes", likes)
                .addOnFailureListener(e ->
                        android.util.Log.w("FirebaseRepository", "updateLikes failed", e));
    }

    public ListenerRegistration listenToLikes(long photoId, OnLikesChangedListener listener) {
        return db.collection("photos")
                .document(String.valueOf(photoId))
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    if (doc.getLong("likes") != null)
                        listener.onChanged(doc.getLong("likes").intValue());
                });
    }

    // ── Commentaires ──────────────────────────────────────────────────────────

    public void saveComment(long photoId, String authorName, String text, String date) {
        Map<String, Object> data = new HashMap<>();
        data.put("photoId",    photoId);
        data.put("authorName", authorName);
        data.put("text",       text);
        data.put("date",       date);
        data.put("timestamp",  FieldValue.serverTimestamp());

        db.collection("comments")
                .add(data)
                .addOnFailureListener(e ->
                        android.util.Log.w("FirebaseRepository", "saveComment failed", e));
    }

    public ListenerRegistration listenToComments(long photoId, AppDatabase localDb) {
        return db.collection("comments")
                .whereEqualTo("photoId", photoId)
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String fcAuthor = doc.getString("authorName");
                            String fcText   = doc.getString("text");
                            String fcDate   = doc.getString("date") != null ? doc.getString("date") : "";
                            if (fcAuthor == null || fcText == null) continue;
                            if (localDb.commentDao().countByContent(photoId, fcAuthor, fcText) == 0) {
                                Comment c = new Comment();
                                c.photoId    = photoId;
                                c.authorName = fcAuthor;
                                c.text       = fcText;
                                c.date       = fcDate;
                                c.userId     = -1;
                                localDb.commentDao().insertComment(c);
                            }
                        }
                    });
                });
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface OnLikesChangedListener {
        void onChanged(int newLikes);
    }
}
