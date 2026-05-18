package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.travelshare.data.models.AppNotification;

import java.util.List;

@Dao
public interface AppNotificationDao {
    @Insert
    void insert(AppNotification notification);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE targetUserId = :userId AND type = :type AND message = :message")
    int countByContent(long userId, String type, String message);

    @Query("SELECT * FROM app_notifications WHERE targetUserId = :userId ORDER BY date DESC, id DESC")
    LiveData<List<AppNotification>> getForUser(long userId);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    void markRead(long id);

    @Query("DELETE FROM app_notifications WHERE targetUserId = :userId")
    void deleteAllForUser(long userId);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE targetUserId = :userId AND isRead = 0")
    LiveData<Integer> getUnreadCount(long userId);

    @Query("SELECT COUNT(*)gi FROM app_notifications WHERE targetUserId = :userId AND groupId = :groupId AND type = 'GROUP_MESSAGE' AND isRead = 0")
    LiveData<Integer> getUnreadCountForGroup(long userId, long groupId);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE targetUserId = :userId AND groupId = :groupId")
    void markGroupMessagesRead(long userId, long groupId);
}
