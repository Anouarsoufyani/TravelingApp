package com.example.travelshare.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.dao.AppNotificationDao;
import com.example.travelshare.data.repository.FirebaseRepository;
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
    private final FirebaseRepository firebaseRepository;

    public SharedViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        firebaseRepository = FirebaseRepository.getInstance();
        photoDao = db.photoDao();
        commentDao = db.commentDao();
        groupDao = db.groupDao();
        groupMemberDao = db.groupMemberDao();
        groupMessageDao = db.groupMessageDao();
        reportDao = db.reportDao();
        prefDao = db.notificationPreferenceDao();
        appNotificationDao = db.appNotificationDao();
        userDao = db.userDao();
        allPhotos = firebaseRepository.getAllPhotosLive();
        publicPhotos = firebaseRepository.getPublicPhotosLive();
        allGroups = groupDao.getAllGroups();
        AppDatabase.databaseWriteExecutor.execute(() -> photoDao.clearPhotos());
    }

    public LiveData<List<Photo>> getAllPhotos() { return allPhotos; }
    public LiveData<List<Photo>> getPublicPhotos() { return publicPhotos; }

    public void insert(Photo photo) {
        insertAndGetId(photo, id -> {});
    }

    public void insertAndGetId(Photo photo, java.util.function.Consumer<Long> callback) {
        long now = System.currentTimeMillis();
        long id = now % Integer.MAX_VALUE;
        if (id <= 0) id = Math.abs(System.nanoTime() % Integer.MAX_VALUE);
        photo.setId((int) id);
        photo.setCreatedAtMillis(now);
        firebaseRepository.savePhoto(photo, id);
        callback.accept(id);
    }

    public LiveData<List<Photo>> searchPhotos(String query) {
        return firebaseRepository.searchPhotosLive(query);
    }

    public LiveData<List<Photo>> getRandomPhotos(int limit) {
        return firebaseRepository.getRandomPublicPhotosLive(limit);
    }

    public LiveData<List<Photo>> getPhotosByCategory(String category) {
        return firebaseRepository.getPhotosByCategoryLive(category);
    }

    public LiveData<List<Photo>> getPhotosByAuthor(String author) {
        return firebaseRepository.getPhotosByAuthorLive(author, false);
    }

    public LiveData<List<Photo>> getPublicPhotosByAuthor(String author) {
        return firebaseRepository.getPhotosByAuthorLive(author, true);
    }

    public LiveData<List<Photo>> getPhotosByIds(List<Integer> ids) {
        return firebaseRepository.getPhotosByIdsLive(ids);
    }

    public LiveData<List<Photo>> getPhotosByDateRange(String start, String end) {
        return firebaseRepository.getPhotosByDateRangeLive(start, end);
    }

    public LiveData<List<Photo>> getPhotosByGroup(long groupId) {
        return firebaseRepository.getPhotosByGroupLive(groupId);
    }

    public LiveData<List<Photo>> getPhotosByTag(String tag) {
        return firebaseRepository.getPhotosByTagLive(tag);
    }

    public LiveData<List<Photo>> getPhotosByLocation(double lat, double lng, double radiusKm) {
        double delta = radiusKm / 111.0;
        return firebaseRepository.getPhotosByLocationLive(lat, lng, delta);
    }

    public void updateLikes(int photoId, int likes) {
        firebaseRepository.updateLikes(photoId, likes);
    }

    public void deletePhoto(int photoId) {
        firebaseRepository.deletePhoto(photoId);
    }

    public void updatePhotoTitle(int photoId, String title) {
        firebaseRepository.updatePhotoTitle(photoId, title);
    }

    public void loadMorePublicPhotos(int offset, int limit, java.util.function.Consumer<List<Photo>> callback) {
        firebaseRepository.loadPublicPhotosPage(offset, limit, callback);
    }

    public void syncPhotosFromFirestore() {
        AppDatabase.databaseWriteExecutor.execute(() -> photoDao.clearPhotos());
    }

    public void syncGroupsFromFirestore() {
        FirebaseRepository.getInstance().syncGroupsToRoom(
                AppDatabase.getInstance(getApplication()), null);
    }

    public void syncMyMemberStatuses(String username) {
        FirebaseRepository.getInstance().syncMyMemberStatuses(
                username, AppDatabase.getInstance(getApplication()), null);
    }

    public void getPhotoById(int photoId, java.util.function.Consumer<Photo> callback) {
        firebaseRepository.getPhotoById(photoId, callback);
    }

    public void getUserByLogin(String login, java.util.function.Consumer<User> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> callback.accept(userDao.getUserByLogin(login)));
    }

    public void updateUserProfile(long userId, String avatarUri, String bio) {
        AppDatabase.databaseWriteExecutor.execute(() -> userDao.updateUserProfile(userId, avatarUri, bio));
    }

    public LiveData<List<Comment>> getCommentsForPhoto(long photoId) {
        return commentDao.getCommentsForPhoto(photoId);
    }

    public void insertComment(Comment comment) {
        AppDatabase.databaseWriteExecutor.execute(() -> commentDao.insertComment(comment));
    }

    public void deleteComment(long id) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            com.example.travelshare.data.models.Comment c = commentDao.getCommentById(id);
            commentDao.deleteComment(id);
            if (c != null) {
                FirebaseRepository.getInstance().deleteComment(c.photoId, c.authorName, c.text);
            }
        });
    }

    public LiveData<List<Group>> getAllGroups() { return allGroups; }

    public LiveData<List<Group>> getGroupsForUser(long userId) {
        return groupDao.getGroupsForUser(userId);
    }

    public void insertGroup(Group group) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupDao.insertGroup(group));
    }

    public void joinGroup(long groupId, long userId, String userName) {
        GroupMember m = new GroupMember();
        m.groupId  = groupId;
        m.userId   = userId;
        m.userName = userName;
        m.status   = "MEMBER";
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.joinGroup(m));
    }

    public void requestJoinGroup(long groupId, long userId, String userName) {
        GroupMember m = new GroupMember();
        m.groupId  = groupId;
        m.userId   = userId;
        m.userName = userName;
        m.status   = "PENDING";
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.joinGroup(m));
    }

    public void acceptJoinRequest(long groupId, long userId) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.acceptRequest(groupId, userId));
    }

    public void rejectOrLeaveGroup(long groupId, long userId) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupMemberDao.leaveGroup(groupId, userId));
    }

    public void updateLocalGroupName(long groupId, String newName) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupDao.updateGroupName(groupId, newName));
    }

    public void deleteLocalGroup(long groupId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            groupMessageDao.deleteMessagesForGroup(groupId);
            groupMemberDao.deleteMembersForGroup(groupId);
            groupDao.deleteGroupById(groupId);
        });
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

    public void insertReport(Report report) {
        AppDatabase.databaseWriteExecutor.execute(() -> reportDao.insertReport(report));
    }

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

    public LiveData<List<GroupMessage>> getGroupMessages(long groupId) {
        return groupMessageDao.getMessagesForGroup(groupId);
    }

    public void sendGroupMessage(GroupMessage msg) {
        AppDatabase.databaseWriteExecutor.execute(() -> groupMessageDao.insert(msg));
    }
}
