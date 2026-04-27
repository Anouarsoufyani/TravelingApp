package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_preferences")
public class NotificationPreference {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long userId;
    public String type;
    public String value;
}
