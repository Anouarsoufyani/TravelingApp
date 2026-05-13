package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.travelshare.data.models.GroupMessage;

import java.util.List;

@Dao
public interface GroupMessageDao {
    @Insert
    void insert(GroupMessage message);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertOrIgnore(GroupMessage message);

    @Query("SELECT COUNT(*) FROM group_messages WHERE groupId = :groupId AND authorName = :author AND message = :text")
    int countByContent(long groupId, String author, String text);

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY id ASC")
    LiveData<List<GroupMessage>> getMessagesForGroup(long groupId);
}
