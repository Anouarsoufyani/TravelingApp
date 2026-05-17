package com.example.travelshare.data.repository;

import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.models.GroupMessage;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.models.TravelPlan;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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

    public void updateLikes(long photoId, int likes) {
        Map<String, Object> data = new HashMap<>();
        data.put("likes", likes);
        db.collection("photos")
                .document(String.valueOf(photoId))
                .set(data, com.google.firebase.firestore.SetOptions.merge())
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

    public void deleteComment(long photoId, String authorName, String text) {
        db.collection("comments")
                .whereEqualTo("photoId",    photoId)
                .whereEqualTo("authorName", authorName)
                .whereEqualTo("text",       text)
                .get()
                .addOnSuccessListener(query -> {
                    for (QueryDocumentSnapshot doc : query) doc.getReference().delete();
                })
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "deleteComment failed", e));
    }

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

    public void syncPublicPhotosToRoom(AppDatabase localDb, Runnable onDone) {
        db.collection("photos")
                .whereEqualTo("visibility", "PUBLIC")
                .get()
                .addOnSuccessListener(query -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            Long roomIdL = doc.getLong("roomId");
                            if (roomIdL == null) continue;
                            int roomId = roomIdL.intValue();
                            if (localDb.photoDao().countById(roomId) > 0) continue;

                            String title    = str(doc, "title");
                            String location = str(doc, "location");
                            String author   = str(doc, "author");
                            int likes       = doc.getLong("likes") != null ? doc.getLong("likes").intValue() : 0;
                            double lat      = doc.getDouble("latitude")  != null ? doc.getDouble("latitude")  : 0;
                            double lng      = doc.getDouble("longitude") != null ? doc.getDouble("longitude") : 0;
                            String date     = str(doc, "date");
                            String category = str(doc, "category");
                            String tags     = str(doc, "tags");

                            Photo p = new Photo(title, location, author, likes, lat, lng, date, category, tags, "PUBLIC");
                            p.setId(roomId);
                            p.setImageUri(str(doc, "imageUri"));
                            localDb.photoDao().insertPhotoOrIgnore(p);
                        }
                        if (onDone != null)
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
                    });
                })
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "syncPhotos failed", e));
    }

    private String str(QueryDocumentSnapshot doc, String key) {
        String v = doc.getString(key);
        return v != null ? v : "";
    }

    public void saveUserProfile(String username, String bio) {
        Map<String, Object> data = new HashMap<>();
        data.put("username",  username);
        data.put("bio",       bio != null ? bio : "");
        data.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users")
                .document(username)
                .set(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveUserProfile failed", e));
    }

    public void loadUserProfile(String username, Consumer<Map<String, Object>> callback) {
        db.collection("users")
                .document(username)
                .get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists() ? doc.getData() : null))
                .addOnFailureListener(e -> callback.accept(null));
    }

    public void saveGroup(String name, String description, String creatorUsername) {
        Map<String, Object> data = new HashMap<>();
        data.put("name",            name);
        data.put("description",     description != null ? description : "");
        data.put("creatorUsername", creatorUsername);
        data.put("timestamp",       FieldValue.serverTimestamp());

        db.collection("groups")
                .document(name)
                .set(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveGroup failed", e));
    }

    public void syncGroupsToRoom(AppDatabase localDb, Runnable onDone) {
        db.collection("groups")
                .get()
                .addOnSuccessListener(query -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String name = doc.getString("name");
                            if (name == null || name.isEmpty()) continue;
                            if (localDb.groupDao().countByName(name) > 0) continue;

                            Group g = new Group();
                            g.name        = name;
                            g.description = doc.getString("description") != null ? doc.getString("description") : "";
                            g.creatorId   = 0;
                            localDb.groupDao().insertGroupOrIgnore(g);
                        }
                        if (onDone != null)
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
                    });
                })
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "syncGroups failed", e));
    }

    public void saveGroupMember(String groupName, String username, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupName", groupName);
        data.put("username",  username);
        data.put("status",    status);
        data.put("timestamp", FieldValue.serverTimestamp());

        String docId = sanitize(groupName) + "_" + sanitize(username);
        db.collection("group_members")
                .document(docId)
                .set(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveGroupMember failed", e));
    }

    public void deleteGroupMember(String groupName, String username) {
        String docId = sanitize(groupName) + "_" + sanitize(username);
        db.collection("group_members")
                .document(docId)
                .delete()
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "deleteGroupMember failed", e));
    }

    public ListenerRegistration listenToPendingMembers(String groupName, AppDatabase localDb, long localGroupId) {
        return db.collection("group_members")
                .whereEqualTo("groupName", groupName)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String username = doc.getString("username");
                            if (username == null || username.isEmpty()) continue;
                            if (localDb.groupMemberDao().countByUserName(localGroupId, username) > 0) continue;

                            GroupMember m = new GroupMember();
                            m.groupId  = localGroupId;
                            m.userId   = Math.abs((long) username.hashCode());
                            m.userName = username;
                            m.status   = "PENDING";
                            localDb.groupMemberDao().joinGroup(m);
                        }
                    });
                });
    }

    public void syncMyMemberStatuses(String username, AppDatabase localDb, Runnable onDone) {
        db.collection("group_members")
                .whereEqualTo("username", username)
                .whereEqualTo("status", "MEMBER")
                .get()
                .addOnSuccessListener(query -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String groupName = doc.getString("groupName");
                            if (groupName == null) continue;
                            Group g = localDb.groupDao().getGroupByName(groupName);
                            if (g == null) continue;
                            localDb.groupMemberDao().acceptRequestByUserName(g.id, username);
                        }
                        if (onDone != null)
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
                    });
                })
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "syncMyMemberStatuses failed", e));
    }

    private String sanitize(String s) {
        return s != null ? s.replaceAll("[^a-zA-Z0-9_-]", "_") : "unknown";
    }

    public void saveMessage(String groupName, String authorName, String message, String date) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupName",   groupName);
        data.put("authorName",  authorName);
        data.put("message",     message);
        data.put("date",        date != null ? date : "");
        data.put("photoId",     0);
        data.put("timestamp",   FieldValue.serverTimestamp());

        db.collection("group_messages")
                .add(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveMessage failed", e));
    }

    public ListenerRegistration listenToMessages(String groupName, AppDatabase localDb, long localGroupId) {
        return db.collection("group_messages")
                .whereEqualTo("groupName", groupName)
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String author  = doc.getString("authorName");
                            String text    = doc.getString("message");
                            String date    = doc.getString("date");
                            if (author == null || text == null) continue;
                            if (localDb.groupMessageDao().countByContent(localGroupId, author, text) > 0) continue;

                            GroupMessage msg = new GroupMessage();
                            msg.groupId    = localGroupId;
                            msg.userId     = -1;
                            msg.authorName = author;
                            msg.message    = text;
                            msg.date       = date != null ? date : "";
                            msg.photoId    = 0;
                            localDb.groupMessageDao().insertOrIgnore(msg);
                        }
                    });
                });
    }

    public void deletePhoto(long photoId) {
        db.collection("photos")
                .document(String.valueOf(photoId))
                .delete()
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "deletePhoto failed", e));
    }

    public void updatePhotoTitle(long photoId, String title) {
        db.collection("photos")
                .document(String.valueOf(photoId))
                .update("title", title)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "updatePhotoTitle failed", e));
    }

    public void saveNotification(String targetUsername, AppNotification notif) {
        Map<String, Object> data = new HashMap<>();
        data.put("targetUsername", targetUsername);
        data.put("type",    notif.type    != null ? notif.type    : "");
        data.put("message", notif.message != null ? notif.message : "");
        data.put("photoId", notif.photoId);
        data.put("groupId", notif.groupId);
        data.put("date",    notif.date    != null ? notif.date    : "");
        data.put("isRead",  false);
        data.put("timestamp", FieldValue.serverTimestamp());

        db.collection("notifications")
                .add(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveNotification failed", e));
    }

    public ListenerRegistration listenToNotifications(String username, AppDatabase localDb, long localUserId) {
        return db.collection("notifications")
                .whereEqualTo("targetUsername", username)
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String type    = doc.getString("type");
                            String message = doc.getString("message");
                            if (type == null || message == null) continue;
                            if (localDb.appNotificationDao().countByContent(localUserId, type, message) > 0) continue;

                            AppNotification n = new AppNotification();
                            n.targetUserId = localUserId;
                            n.type    = type;
                            n.message = message;
                            n.date    = doc.getString("date") != null ? doc.getString("date") : "";
                            Long pid  = doc.getLong("photoId");
                            Long gid  = doc.getLong("groupId");
                            n.photoId = pid != null ? pid.intValue() : 0;
                            n.groupId = gid != null ? gid : 0;
                            n.isRead  = false;
                            localDb.appNotificationDao().insert(n);
                        }
                    });
                });
    }

    public void saveNotificationPreference(String username, String type, String value) {
        Map<String, Object> data = new HashMap<>();
        data.put("username",  username);
        data.put("type",      type);
        data.put("value",     value);
        data.put("timestamp", FieldValue.serverTimestamp());

        db.collection("notification_prefs")
                .document(sanitize(username) + "_" + sanitize(type) + "_" + sanitize(value))
                .set(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveNotifPref failed", e));
    }

    public void deleteNotificationPreference(String username, String type, String value) {
        db.collection("notification_prefs")
                .document(sanitize(username) + "_" + sanitize(type) + "_" + sanitize(value))
                .delete()
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "deleteNotifPref failed", e));
    }

    public void syncNotificationPreferences(String username, AppDatabase localDb, long localUserId, Runnable onDone) {
        db.collection("notification_prefs")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(query -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (QueryDocumentSnapshot doc : query) {
                            String type  = doc.getString("type");
                            String value = doc.getString("value");
                            if (type == null || value == null) continue;
                            if (localDb.notificationPreferenceDao().countByTypeValue(localUserId, type, value) > 0) continue;

                            NotificationPreference pref = new NotificationPreference();
                            pref.userId = localUserId;
                            pref.type   = type;
                            pref.value  = value;
                            localDb.notificationPreferenceDao().insertPreference(pref);
                        }
                        if (onDone != null)
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
                    });
                })
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "syncNotifPrefs failed", e));
    }

    public void savePlan(String username, TravelPlan plan) {
        Map<String, Object> data = new HashMap<>();
        data.put("username",      username);
        data.put("city",          plan.city       != null ? plan.city       : "");
        data.put("type",          plan.type       != null ? plan.type       : "");
        data.put("activities",    plan.activities != null ? plan.activities : "");
        data.put("budgetEur",     plan.budgetEur);
        data.put("durationHours", plan.durationHours);
        data.put("effort",        plan.effort     != null ? plan.effort     : "");
        data.put("date",          plan.date       != null ? plan.date       : "");
        data.put("liked",         plan.liked);
        data.put("saved",         plan.saved);
        data.put("timestamp",     FieldValue.serverTimestamp());

        db.collection("plans")
                .document(username + "_" + plan.id)
                .set(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "savePlan failed", e));
    }

    public interface OnLikesChangedListener {
        void onChanged(int newLikes);
    }
}
