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
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    public void getUserProfile(String username, Consumer<com.google.firebase.firestore.DocumentSnapshot> callback) {
        db.collection("users").document(username).get()
                .addOnSuccessListener(callback::accept)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "getUserProfile failed", e));
    }

    public void saveUserProfile(String username, String bio, String avatarUri) {
        Map<String, Object> data = new HashMap<>();
        data.put("username",  username);
        data.put("bio",       bio != null ? bio : "");
        data.put("avatarUri", avatarUri != null ? avatarUri : "");
        data.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users")
                .document(username)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveUserProfile failed", e));
    }

    public void saveUserAccount(String username, String email, String nom, String prenom,
                                String telephone, String centresInteret) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username != null ? username : "");
        data.put("email", email != null ? email : "");
        data.put("nom", nom != null ? nom : "");
        data.put("prenom", prenom != null ? prenom : "");
        data.put("telephone", telephone != null ? telephone : "");
        data.put("centresInteret", centresInteret != null ? centresInteret : "");
        data.put("userId", stableUserId(username));
        data.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users")
                .document(username)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveUserAccount failed", e));
    }

    public void loadUserAccountByEmail(String email, Consumer<Map<String, Object>> callback) {
        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query == null || query.isEmpty()) {
                        callback.accept(null);
                        return;
                    }
                    callback.accept(query.getDocuments().get(0).getData());
                })
                .addOnFailureListener(e -> callback.accept(null));
    }

    public void loadUserProfile(String username, Consumer<Map<String, Object>> callback) {
        db.collection("users")
                .document(username)
                .get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists() ? doc.getData() : null))
                .addOnFailureListener(e -> callback.accept(null));
    }

    public void saveGroup(String name, String description, String creatorUsername) {
        saveGroup(name, description, creatorUsername, stableUserId(creatorUsername), Group.VISIBILITY_PRIVATE);
    }

    public void saveGroup(String name, String description, String creatorUsername,
                          long creatorId, String visibility) {
        String safeVisibility = Group.VISIBILITY_PUBLIC.equals(visibility)
                ? Group.VISIBILITY_PUBLIC : Group.VISIBILITY_PRIVATE;
        Map<String, Object> data = new HashMap<>();
        data.put("name",            name);
        data.put("description",     description != null ? description : "");
        data.put("creatorUsername", creatorUsername);
        data.put("creatorId",       creatorId);
        data.put("groupId",         stableGroupId(name));
        data.put("visibility",      safeVisibility);
        data.put("timestamp",       FieldValue.serverTimestamp());

        db.collection("groups")
                .document(sanitize(name))
                .set(data)
                .addOnSuccessListener(unused -> saveGroupMember(name, creatorUsername, "MEMBER"))
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveGroup failed", e));
    }

    public ListenerRegistration listenToGroups(Consumer<List<Group>> callback) {
        return db.collection("groups")
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) {
                        callback.accept(new ArrayList<>());
                        return;
                    }
                    List<Group> groups = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Group group = groupFromDoc(doc);
                        if (group.name != null && !group.name.isEmpty()) groups.add(group);
                    }
                    callback.accept(groups);
                });
    }

    public ListenerRegistration listenToMyGroupMemberships(String username,
            Consumer<Map<String, String>> callback) {
        return db.collection("group_members")
                .whereEqualTo("username", username)
                .addSnapshotListener((query, e) -> {
                    Map<String, String> statuses = new LinkedHashMap<>();
                    if (e == null && query != null) {
                        for (QueryDocumentSnapshot doc : query) {
                            String groupName = doc.getString("groupName");
                            String status = doc.getString("status");
                            if (groupName != null && status != null) statuses.put(groupName, status);
                        }
                    }
                    callback.accept(statuses);
                });
    }

    public void getMyMemberGroups(String username, Consumer<List<Group>> callback) {
        db.collection("group_members")
                .whereEqualTo("username", username)
                .whereEqualTo("status", "MEMBER")
                .get()
                .addOnSuccessListener(members -> db.collection("groups")
                        .get()
                        .addOnSuccessListener(groupsQuery -> {
                            Map<String, Group> allGroups = new LinkedHashMap<>();
                            for (QueryDocumentSnapshot doc : groupsQuery) {
                                Group group = groupFromDoc(doc);
                                allGroups.put(group.name, group);
                            }

                            Map<String, Group> result = new LinkedHashMap<>();
                            for (QueryDocumentSnapshot memberDoc : members) {
                                String groupName = memberDoc.getString("groupName");
                                Group group = allGroups.get(groupName);
                                if (group != null) result.put(group.name, group);
                            }
                            for (Group group : allGroups.values()) {
                                if (group.creatorUsername != null
                                        && group.creatorUsername.equalsIgnoreCase(username)) {
                                    result.put(group.name, group);
                                }
                            }
                            callback.accept(new ArrayList<>(result.values()));
                        })
                        .addOnFailureListener(e -> callback.accept(new ArrayList<>())))
                .addOnFailureListener(e -> callback.accept(new ArrayList<>()));
    }

    public void getGroupMembershipStatus(String groupName, String username,
            Consumer<String> callback) {
        db.collection("group_members")
                .document(sanitize(groupName) + "_" + sanitize(username))
                .get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists() ? doc.getString("status") : null))
                .addOnFailureListener(e -> callback.accept(null));
    }

    private Group groupFromDoc(QueryDocumentSnapshot doc) {
        Group g = new Group();
        g.name = str(doc, "name");
        if (g.name.isEmpty()) g.name = doc.getId();
        g.description = str(doc, "description");
        g.creatorUsername = str(doc, "creatorUsername");
        Long creatorId = doc.getLong("creatorId");
        g.creatorId = creatorId != null ? creatorId : stableUserId(g.creatorUsername);
        Long groupId = doc.getLong("groupId");
        g.id = groupId != null ? groupId : stableGroupId(g.name);
        String visibility = doc.getString("visibility");
        g.visibility = Group.VISIBILITY_PUBLIC.equals(visibility)
                ? Group.VISIBILITY_PUBLIC : Group.VISIBILITY_PRIVATE;
        return g;
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
                            Long creatorId = doc.getLong("creatorId");
                            g.creatorId   = creatorId != null ? creatorId : stableUserId(doc.getString("creatorUsername"));
                            g.creatorUsername = doc.getString("creatorUsername");
                            String visibility = doc.getString("visibility");
                            g.visibility = Group.VISIBILITY_PUBLIC.equals(visibility)
                                    ? Group.VISIBILITY_PUBLIC : Group.VISIBILITY_PRIVATE;
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
        data.put("groupId",   stableGroupId(groupName));
        data.put("username",  username);
        data.put("userId",    stableUserId(username));
        data.put("status",    status);
        data.put("timestamp", FieldValue.serverTimestamp());

        String docId = sanitize(groupName) + "_" + sanitize(username);
        db.collection("group_members")
                .document(docId)
                .set(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveGroupMember failed", e));
    }

    public void sendGroupInvitation(String groupName, long groupId, String inviterUsername,
                                    String targetUsername, String date) {
        long gid = groupId > 0 ? groupId : stableGroupId(groupName);
        Map<String, Object> data = new HashMap<>();
        data.put("groupName", groupName);
        data.put("groupId", gid);
        data.put("username", targetUsername);
        data.put("userId", stableUserId(targetUsername));
        data.put("status", "INVITED");
        data.put("invitedBy", inviterUsername);
        data.put("timestamp", FieldValue.serverTimestamp());

        String docId = sanitize(groupName) + "_" + sanitize(targetUsername);
        db.collection("group_members")
                .document(docId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    // Send notification
                    AppNotification notif = new AppNotification();
                    notif.type = "GROUP_INVITE";
                    notif.message = inviterUsername + " vous invite à rejoindre \"" + groupName + "\"";
                    notif.groupId = gid;
                    notif.groupName = groupName;
                    notif.date = date != null ? date : "";
                    saveNotification(targetUsername, notif);

                    // Send direct message
                    saveDirectMessage(inviterUsername, targetUsername, 
                                     "✉️ Invitation à rejoindre le groupe \"" + groupName + "\"", 
                                     date, 0, 0, gid, groupName);
                })
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "sendGroupInvitation failed", e));
    }

    public void acceptGroupInvitation(String groupName, String username) {
        saveGroupMember(groupName, username, "MEMBER");
    }

    public void declineGroupInvitation(String groupName, String username) {
        deleteGroupMember(groupName, username);
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

    public ListenerRegistration listenToPendingMembers(String groupName,
            Consumer<List<GroupMember>> callback) {
        return db.collection("group_members")
                .whereEqualTo("groupName", groupName)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((query, e) -> {
                    List<GroupMember> members = new ArrayList<>();
                    if (e == null && query != null) {
                        for (QueryDocumentSnapshot doc : query) {
                            String username = doc.getString("username");
                            if (username == null || username.isEmpty()) continue;
                            GroupMember m = new GroupMember();
                            Long groupId = doc.getLong("groupId");
                            Long userId = doc.getLong("userId");
                            m.groupId = groupId != null ? groupId : stableGroupId(groupName);
                            m.userId = userId != null ? userId : stableUserId(username);
                            m.userName = username;
                            m.status = "PENDING";
                            members.add(m);
                        }
                    }
                    callback.accept(members);
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

    public long stableGroupId(String groupName) {
        return Math.abs((long) sanitize(groupName).toLowerCase(java.util.Locale.ROOT).hashCode());
    }

    public long stableUserId(String username) {
        return Math.abs((long) sanitize(username).toLowerCase(java.util.Locale.ROOT).hashCode());
    }

    public void saveMessage(String groupName, String authorName, String message, String date) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupName",   groupName);
        data.put("authorName",  authorName);
        data.put("message",     message);
        data.put("date",        date != null ? date : "");
        data.put("photoId",     0);
        data.put("planId",      0);
        data.put("timestamp",   FieldValue.serverTimestamp());

        db.collection("group_messages")
                .add(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveMessage failed", e));
    }

    public void saveSharedPhotoMessage(String groupName, String authorName, String message,
                                       long photoId, String date) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupName",   groupName);
        data.put("authorName",  authorName);
        data.put("message",     message);
        data.put("date",        date != null ? date : "");
        data.put("photoId",     photoId);
        data.put("planId",      0);
        data.put("timestamp",   FieldValue.serverTimestamp());

        db.collection("group_messages")
                .add(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveSharedPhotoMessage failed", e));
    }

    public void saveSharedPlanMessage(String groupName, String authorName, String message,
                                      long planId, String date) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupName",   groupName);
        data.put("authorName",  authorName);
        data.put("message",     message);
        data.put("date",        date != null ? date : "");
        data.put("photoId",     0);
        data.put("planId",      planId);
        data.put("timestamp",   FieldValue.serverTimestamp());

        db.collection("group_messages")
                .add(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveSharedPlanMessage failed", e));
    }

    // DIRECT MESSAGES LOGIC
    public void saveDirectMessage(String sender, String receiver, String text, String date, 
                                  long photoId, long planId, long invGroupId, String invGroupName) {
        Map<String, Object> data = new HashMap<>();
        data.put("sender",    sender);
        data.put("receiver",  receiver);
        data.put("message",   text);
        data.put("date",      date);
        data.put("photoId",   photoId);
        data.put("planId",    planId);
        data.put("invitationGroupId",   invGroupId);
        data.put("invitationGroupName", invGroupName != null ? invGroupName : "");
        data.put("timestamp", FieldValue.serverTimestamp());

        String chatId = getDirectChatId(sender, receiver);
        
        // Update parent doc with participants for easy listing
        Map<String, Object> parent = new HashMap<>();
        parent.put("participants", java.util.Arrays.asList(sender, receiver));
        parent.put("lastMessage", text);
        parent.put("lastUpdate", FieldValue.serverTimestamp());
        db.collection("direct_chats").document(chatId).set(parent, com.google.firebase.firestore.SetOptions.merge());

        db.collection("direct_chats").document(chatId).collection("messages").add(data);
    }

    public ListenerRegistration listenToMyDirectChats(String username, Consumer<List<Map<String, Object>>> callback) {
        return db.collection("direct_chats")
                .whereArrayContains("participants", username)
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Map<String, Object> map = new HashMap<>(doc.getData());
                        map.put("chatId", doc.getId());
                        list.add(map);
                    }
                    callback.accept(list);
                });
    }

    public ListenerRegistration listenToDirectMessages(String u1, String u2, Consumer<List<GroupMessage>> callback) {
        String chatId = getDirectChatId(u1, u2);
        return db.collection("direct_chats").document(chatId).collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener((query, e) -> {
                    if (e != null || query == null) return;
                    List<GroupMessage> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        GroupMessage m = new GroupMessage();
                        m.authorName = doc.getString("sender");
                        m.message    = doc.getString("message");
                        m.date       = doc.getString("date");
                        Long pid     = doc.getLong("photoId");
                        Long plid    = doc.getLong("planId");
                        Long igid    = doc.getLong("invitationGroupId");
                        m.photoId    = pid != null ? pid.intValue() : 0;
                        m.planId     = plid != null ? plid : 0;
                        m.invitationGroupId   = igid != null ? igid : 0;
                        m.invitationGroupName = doc.getString("invitationGroupName");
                        list.add(m);
                    }
                    callback.accept(list);
                });
    }

    private String getDirectChatId(String u1, String u2) {
        if (u1.compareTo(u2) < 0) return u1 + "_" + u2;
        else return u2 + "_" + u1;
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
                            Long photoId   = doc.getLong("photoId");
                            Long planId    = doc.getLong("planId");
                            if (author == null || text == null) continue;
                            if (localDb.groupMessageDao().countByContent(localGroupId, author, text) > 0) continue;

                            GroupMessage msg = new GroupMessage();
                            msg.groupId    = localGroupId;
                            msg.userId     = -1;
                            msg.authorName = author;
                            msg.message    = text;
                            msg.date       = date != null ? date : "";
                            msg.photoId    = photoId != null ? photoId.intValue() : 0;
                            msg.planId     = planId != null ? planId : 0;
                            localDb.groupMessageDao().insertOrIgnore(msg);                        }
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
        data.put("planId",  notif.planId);
        data.put("groupId", notif.groupId);
        data.put("groupName", notif.groupName != null ? notif.groupName : "");
        data.put("senderUsername", notif.senderUsername != null ? notif.senderUsername : "");
        data.put("date",    notif.date    != null ? notif.date    : "");
        data.put("isRead",  false);
        data.put("timestamp", FieldValue.serverTimestamp());

        db.collection("notifications")
                .add(data)
                .addOnFailureListener(e -> android.util.Log.w("FirebaseRepository", "saveNotification failed", e));
    }

    public void clearNotificationsForUser(String username, Consumer<Boolean> callback) {
        clearNotificationsPage(username, callback);
    }

    private void clearNotificationsPage(String username, Consumer<Boolean> callback) {
        db.collection("notifications")
                .whereEqualTo("targetUsername", username)
                .limit(450)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.accept(true);
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : query) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> clearNotificationsPage(username, callback))
                            .addOnFailureListener(e -> {
                                android.util.Log.w("FirebaseRepository", "clearNotificationsForUser failed", e);
                                callback.accept(false);
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.w("FirebaseRepository", "clearNotificationsForUser failed", e);
                    callback.accept(false);
                });
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
                            Long plid = doc.getLong("planId");
                            Long gid  = doc.getLong("groupId");
                            n.photoId = pid != null ? pid.intValue() : 0;
                            n.planId  = plid != null ? plid : 0;
                            n.groupId = gid != null ? gid : 0;
                            n.groupName = doc.getString("groupName") != null ? doc.getString("groupName") : "";
                            n.senderUsername = doc.getString("senderUsername") != null ? doc.getString("senderUsername") : "";
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

    public void followUser(String follower, String followed) {
        String docId = sanitize(follower) + "_" + sanitize(followed);
        Map<String, Object> data = new HashMap<>();
        data.put("follower", follower);
        data.put("followed", followed);
        data.put("timestamp", FieldValue.serverTimestamp());
        db.collection("follows").document(docId).set(data);

        AppNotification notif = new AppNotification();
        notif.type = "FOLLOW";
        notif.senderUsername = follower;
        notif.message = follower + " a commencé à vous suivre !";
        notif.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        saveNotification(followed, notif);
    }

    public void unfollowUser(String follower, String followed) {
        String docId = sanitize(follower) + "_" + sanitize(followed);
        db.collection("follows").document(docId).delete();
    }

    public void isFollowing(String follower, String followed, Consumer<Boolean> callback) {
        String docId = sanitize(follower) + "_" + sanitize(followed);
        db.collection("follows").document(docId).get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists()));
    }

    public void getFollowers(String username, Consumer<List<String>> callback) {
        db.collection("follows").whereEqualTo("followed", username).get()
                .addOnSuccessListener(query -> {
                    List<String> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) list.add(doc.getString("follower"));
                    callback.accept(list);
                });
    }

    public void getFollowing(String username, Consumer<List<String>> callback) {
        db.collection("follows").whereEqualTo("follower", username).get()
                .addOnSuccessListener(query -> {
                    List<String> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) list.add(doc.getString("followed"));
                    callback.accept(list);
                });
    }

    public void getFriends(String username, Consumer<List<String>> callback) {
        getFollowing(username, following -> {
            getFollowers(username, followers -> {
                List<String> friends = new ArrayList<>();
                for (String f : following) {
                    if (followers.contains(f)) friends.add(f);
                }
                callback.accept(friends);
            });
        });
    }

    public void getGroupMembers(String groupName, Consumer<List<String>> callback) {
        db.collection("group_members")
                .whereEqualTo("groupName", groupName)
                .whereEqualTo("status", "MEMBER")
                .get()
                .addOnSuccessListener(query -> {
                    List<String> members = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : query) {
                        String username = doc.getString("username");
                        if (username != null) members.add(username);
                    }
                    callback.accept(members);
                });
    }

    public interface OnLikesChangedListener {
        void onChanged(int newLikes);
    }
}
