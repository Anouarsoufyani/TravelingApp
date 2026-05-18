package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "comments")
public class Comment {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long photoId;
    public long userId;
    public String authorName;
    public String text;
    public String date;
}
