package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "photos")
public class Photo {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String title;
    private String location;
    private String author;
    private int likes;
    
    // Nouveaux champs
    private double latitude;
    private double longitude;
    private String date;
    private String category;
    private String tags;
    private String visibility;
    private long groupId;
    private String imageUri;


    public Photo(String title, String location, String author, int likes,
                 double latitude, double longitude, String date, String category,
                 String tags, String visibility) {
        this.title = title;
        this.location = location;
        this.author = author;
        this.likes = likes;
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = date;
        this.category = category;
        this.tags = tags;
        this.visibility = visibility;
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getAuthor() { return author; }
    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }
    
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getDate() { return date; }
    public String getCategory() { return category; }
    public String getTags() { return tags; }
    public String getVisibility() { return visibility; }
    public long getGroupId() { return groupId; }
    public void setGroupId(long groupId) { this.groupId = groupId; }
    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
}