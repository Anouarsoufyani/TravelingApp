package com.example.travelshare.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.dao.AppNotificationDao;
import com.example.travelshare.data.dao.CommentDao;
import com.example.travelshare.data.dao.GroupDao;
import com.example.travelshare.data.dao.GroupMemberDao;
import com.example.travelshare.data.dao.GroupMessageDao;
import com.example.travelshare.data.dao.NotificationPreferenceDao;
import com.example.travelshare.data.dao.PhotoDao;
import com.example.travelshare.data.dao.ReportDao;
import com.example.travelshare.data.dao.UserDao;
import com.example.travelshare.data.models.User;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.models.GroupMessage;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.models.Report;
import java.util.List;

public class SharedViewModel extends AndroidViewModel {

    private final PhotoDao photoDao;
    private final CommentDao commentDao;
    private final GroupDao groupDao;
    private final GroupMemberDao groupMemberDao;
    private final GroupMessageDao groupMessageDao;
    private final ReportDao reportDao;
    private final NotificationPreferenceDao prefDao;
    private final AppNotificationDao appNotificationDao;
    private final UserDao userDao;

    private final LiveData<List<Photo>> allPhotos;
    private final LiveData<List<Photo>> publicPhotos;
    private final LiveData<List<Group>> allGroups;

    public SharedViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        photoDao = db.photoDao();
        commentDao = db.commentDao();
        groupDao = db.groupDao();
        groupMemberDao = db.groupMemberDao();
        groupMessageDao = db.groupMessageDao();
        reportDao = db.reportDao();
        prefDao = db.notificationPreferenceDao();
        appNotificationDao = db.appNotificationDao();
        userDao = db.userDao();
        allPhotos = photoDao.getAllPhotos();
        publicPhotos = photoDao.getPublicPhotos();
        allGroups = groupDao.getAllGroups();
    }

    // ── PHOTOS ──────────────────────────────────────────────────────────────

    public LiveData<List<Photo>> getAllPhotos() { return allPhotos; }
    public LiveData<List<Photo>> getPublicPhotos() { return publicPhotos; }

    public void insert(Photo photo) {
        AppDatabase.databaseWriteExecutor.execute(() -> photoDao.insertPhoto(photo));
    }

    public void insertAndGetId(Photo photo, java.util.function.Consumer<Long> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long id = photoDao.insertPhoto(photo);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.accept(id));
        });
    }

    public LiveData<List<Photo>> searchPhotos(String query) {
        return photoDao.searchPhotos(query);
    }

    public LiveData<List<Photo>> getRandomPhotos(int limit) {
        return photoDao.getRandomPhotos(limit);
    }

    public LiveData<List<Photo>> getPhotosByCategory(String category) {
        return photoDao.getPhotosByCategory(category);
    }

    public LiveData<List<Photo>> getPhotosByAuthor(String author) {
        return photoDao.getPhotosByAuthor(author);
    }

    public LiveData<List<Photo>> getPublicPhotosByAuthor(String author) {
        return photoDao.getPublicPhotosByAuthor(author);
    }

    public LiveData<List<Photo>> getPhotosByIds(List<Integer> ids) {
        return photoDao.getPhotosByIds(ids);
    }

    public LiveData<List<Photo>> getPhotosByDateRange(String start, String end) {
        return photoDao.getPhotosByDateRange(start, end);
    }

    public LiveData<List<Photo>> getPhotosByGroup(long groupId) {
        return photoDao.getPhotosByGroup(groupId);
    }

    public LiveData<List<Photo>> getPhotosByTag(String tag) {
        return photoDao.getPhotosByTag(tag);
    }

    public LiveData<List<Photo>> getPhotosByLocation(double lat, double lng, double radiusKm) {
        double delta = radiusKm / 111.0;
        return photoDao.getPhotosByLocation(lat, lng, delta);
    }

    public void updateLikes(int photoId, int likes) {
        AppDatabase.databaseWriteExecutor.execute(() -> photoDao.updateLikes(photoId, likes));
    }

    public void deletePhoto(int photoId) {
        AppDatabase.databaseWriteExecutor.execute(() -> photoDao.deletePhoto(photoId));
    }

    public void updatePhotoTitle(int photoId, String title) {
        AppDatabase.databaseWriteExecutor.execute(() -> photoDao.updatePhotoTitle(photoId, title));
    }

    public void loadMorePublicPhotos(int offset, int limit, java.util.function.Consumer<List<Photo>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> callback.accept(photoDao.getPublicPhotosPage(offset, limit)));
    }

    public void getPhotoById(int photoId, java.util.function.Consumer<Photo> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> callback.accept(photoDao.getPhotoById(photoId)));
    }

    public void getUserByLogin(String login, java.util.function.Consumer<User> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> callback.accept(userDao.getUserByLogin(login)));
    }

    public void updateUserProfile(long userId, String avatarUri, String bio) {
        AppDatabase.databaseWriteExecutor.execute(() -> userDao.updateUserProfile(userId, avatarUri, bio));
    }

    // ── COMMENTAIRES ────────────────────────────────────────────────────────

    public LiveData<List<Comment>> getCommentsForPhoto(long photoId) {
        return commentDao.getCommentsForPhoto(photoId);
    }

    public void insertComment(Comment comment) {
        AppDatabase.databaseWriteExecutor.execute(() -> commentDao.insertComment(comment));
    }

    public void deleteComment(long id) {
        AppDatabase.databaseWriteExecutor.execute(() -> commentDao.deleteComment(id));
    }

    // ── GROUPES ─────────────────────────────────────────────────────────────

    public LiveData<List<Group>> getAllGroups() { return allGroups; }

    public LiveData<List<Group>> getGroupsForUser(long userId) {
        return groupDao.getGroupsForUser(userId);
    }

    public void insertGroup(Group group) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupDao.insertGroup(group));
    }

    /** Rejoint directement avec status=MEMBER (créateur, ou auto-join) */
    public void joinGroup(long groupId, long userId, String userName) {
        GroupMember m = new GroupMember();
        m.groupId  = groupId;
        m.userId   = userId;
        m.userName = userName;
        m.status   = "MEMBER";
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.joinGroup(m));
    }

    /** Envoie une demande d'adhésion (status=PENDING) */
    public void requestJoinGroup(long groupId, long userId, String userName) {
        GroupMember m = new GroupMember();
        m.groupId  = groupId;
        m.userId   = userId;
        m.userName = userName;
        m.status   = "PENDING";
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.joinGroup(m));
    }

    /** Accepte une demande → status MEMBER */
    public void acceptJoinRequest(long groupId, long userId) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.acceptRequest(groupId, userId));
    }

    /** Refuse / expulse un membre */
    public void rejectOrLeaveGroup(long groupId, long userId) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.leaveGroup(groupId, userId));
    }

    public LiveData<List<Long>> getJoinedGroupIds(long userId) {
        return groupMemberDao.getJoinedGroupIds(userId);
    }

    public LiveData<List<GroupMember>> getPendingForGroup(long groupId) {
        return groupMemberDao.getPendingForGroup(groupId);
    }

    public LiveData<Integer> getPendingCountForGroup(long groupId) {
        return groupMemberDao.getPendingCountForGroup(groupId);
    }

    public void getMembership(long userId, long groupId, java.util.function.Consumer<GroupMember> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> callback.accept(groupMemberDao.getMembership(userId, groupId)));
    }

    public LiveData<List<Group>> searchGroups(String query) {
        return groupDao.searchGroups(query);
    }

    public void getGroupById(long groupId, java.util.function.Consumer<Group> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> callback.accept(groupDao.getGroupById(groupId)));
    }

    // ── SIGNALEMENTS ────────────────────────────────────────────────────────

    public void insertReport(Report report) {
        AppDatabase.databaseWriteExecutor.execute(() -> reportDao.insertReport(report));
    }

    // ── PRÉFÉRENCES DE NOTIFICATIONS ────────────────────────────────────────

    public LiveData<List<NotificationPreference>> getPreferencesForUser(long userId) {
        return prefDao.getPreferencesForUser(userId);
    }

    public void insertPreference(NotificationPreference pref) {
        AppDatabase.databaseWriteExecutor.execute(() -> prefDao.insertPreference(pref));
    }

    public void deletePreference(NotificationPreference pref) {
        AppDatabase.databaseWriteExecutor.execute(() -> prefDao.deletePreference(pref));
    }

    public List<NotificationPreference> getPreferencesSync(long userId) {
        return prefDao.getPreferencesForUserSync(userId);
    }

    // ── NOTIFICATIONS IN-APP ────────────────────────────────────────────────

    public LiveData<List<AppNotification>> getAppNotificationsForUser(long userId) {
        return appNotificationDao.getForUser(userId);
    }

    public void insertAppNotification(AppNotification n) {
        AppDatabase.databaseWriteExecutor.execute(() -> appNotificationDao.insert(n));
    }

    public void markNotificationRead(long id) {
        AppDatabase.databaseWriteExecutor.execute(() -> appNotificationDao.markRead(id));
    }

    public void clearNotificationsForUser(long userId) {
        AppDatabase.databaseWriteExecutor.execute(() -> appNotificationDao.deleteAllForUser(userId));
    }

    public LiveData<Integer> getUnreadNotificationCount(long userId) {
        return appNotificationDao.getUnreadCount(userId);
    }

    public LiveData<Integer> getUnreadCountForGroup(long userId, long groupId) {
        return appNotificationDao.getUnreadCountForGroup(userId, groupId);
    }

    public void markGroupMessagesRead(long userId, long groupId) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                appNotificationDao.markGroupMessagesRead(userId, groupId));
    }

    // ── MESSAGES DE GROUPE ──────────────────────────────────────────────────

    public LiveData<List<GroupMessage>> getGroupMessages(long groupId) {
        return groupMessageDao.getMessagesForGroup(groupId);
    }

    public void sendGroupMessage(GroupMessage msg) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupMessageDao.insert(msg));
    }
}
