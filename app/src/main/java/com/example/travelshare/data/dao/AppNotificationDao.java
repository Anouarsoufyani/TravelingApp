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

    @Query("SELECT * FROM app_notifications WHERE targetUserId = :userId ORDER BY id DESC")
    LiveData<List<AppNotification>> getForUser(long userId);

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    void markRead(long id);

    @Query("DELETE FROM app_notifications WHERE targetUserId = :userId")
    void deleteAllForUser(long userId);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE targetUserId = :userId AND isRead = 0")
    LiveData<Integer> getUnreadCount(long userId);
}
