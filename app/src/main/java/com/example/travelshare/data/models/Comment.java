package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "comments",
        foreignKeys = {
                @ForeignKey(entity = Photo.class, parentColumns = "id", childColumns = "photoId", onDelete = ForeignKey.CASCADE)
        })
public class Comment {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long photoId;
    public long userId;
    public String authorName;
    public String text;
    public String date;
}