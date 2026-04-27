package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "group_messages")
public class GroupMessage {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long   groupId;
    public long   userId;
    public String authorName;
    public String message;
    public String date;
}
