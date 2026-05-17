package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_notifications")
public class AppNotification {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long   targetUserId;
    public String type;
    public String message;
    public int    photoId;
    public long   planId;
    public long   groupId;
    public String groupName;
    public String date;
    public boolean isRead;
    public String  senderUsername;
}
