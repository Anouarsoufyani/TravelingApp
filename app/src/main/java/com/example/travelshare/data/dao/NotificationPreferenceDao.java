package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.travelshare.data.models.NotificationPreference;
import java.util.List;

@Dao
public interface NotificationPreferenceDao {

    @Insert
    long insertPreference(NotificationPreference pref);

    @Delete
    void deletePreference(NotificationPreference pref);

    @Query("SELECT * FROM notification_preferences WHERE userId = :userId ORDER BY id DESC")
    LiveData<List<NotificationPreference>> getPreferencesForUser(long userId);

    @Query("SELECT * FROM notification_preferences WHERE userId = :userId")
    List<NotificationPreference> getPreferencesForUserSync(long userId);

    @Query("SELECT COUNT(*) FROM notification_preferences WHERE userId = :userId AND type = :type AND value = :value")
    int countByTypeValue(long userId, String type, String value);
}
