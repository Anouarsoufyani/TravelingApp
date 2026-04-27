package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.travelshare.data.models.GroupMessage;

import java.util.List;

@Dao
public interface GroupMessageDao {
    @Insert
    void insert(GroupMessage message);

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY id ASC")
    LiveData<List<GroupMessage>> getMessagesForGroup(long groupId);
}
