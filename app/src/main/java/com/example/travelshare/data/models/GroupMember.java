package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "group_members",
        indices = {@Index(value = {"groupId", "userId"}, unique = true)})
public class GroupMember {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long groupId;
    public long userId;
    public String userName;
    public String status;
}
