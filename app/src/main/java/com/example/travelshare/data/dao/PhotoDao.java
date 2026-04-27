package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import com.example.travelshare.data.models.Photo;

@Dao
public interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY id DESC")
    LiveData<List<Photo>> getAllPhotos();

    @Query("SELECT * FROM photos WHERE visibility = 'PUBLIC' OR visibility IS NULL ORDER BY id DESC")
    LiveData<List<Photo>> getPublicPhotos();

    @Insert
    long insertPhoto(Photo photo);

    @Query("SELECT * FROM photos WHERE (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY RANDOM() LIMIT :limit")
    LiveData<List<Photo>> getRandomPhotos(int limit);

    @Query("SELECT * FROM photos WHERE category = :category COLLATE NOCASE AND (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY id DESC")
    LiveData<List<Photo>> getPhotosByCategory(String category);

    @Query("SELECT * FROM photos WHERE tags LIKE '%' || :tag || '%' AND (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY id DESC")
    LiveData<List<Photo>> getPhotosByTag(String tag);

    @Query("SELECT * FROM photos WHERE (title LIKE '%' || :searchQuery || '%' OR location LIKE '%' || :searchQuery || '%' OR author LIKE '%' || :searchQuery || '%' OR tags LIKE '%' || :searchQuery || '%') AND (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY id DESC")
    LiveData<List<Photo>> searchPhotos(String searchQuery);

    @Query("SELECT * FROM photos WHERE ABS(latitude - :lat) <= :delta AND ABS(longitude - :lng) <= :delta AND latitude != 0 AND longitude != 0 ORDER BY id DESC")
    LiveData<List<Photo>> getPhotosByLocation(double lat, double lng, double delta);

    @Query("SELECT * FROM photos WHERE date BETWEEN :startDate AND :endDate AND (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY date DESC")
    LiveData<List<Photo>> getPhotosByDateRange(String startDate, String endDate);

    @Query("SELECT * FROM photos WHERE author = :author ORDER BY id DESC")
    LiveData<List<Photo>> getPhotosByAuthor(String author);

    @Query("SELECT * FROM photos WHERE author = :author AND (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY id DESC")
    LiveData<List<Photo>> getPublicPhotosByAuthor(String author);

    @Query("UPDATE photos SET likes = :likes WHERE id = :photoId")
    void updateLikes(int photoId, int likes);

    @Query("DELETE FROM photos WHERE id = :photoId")
    void deletePhoto(int photoId);

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    Photo getPhotoById(int photoId);

    @Query("SELECT * FROM photos WHERE (title LIKE '%' || :q || '%' OR location LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%') AND (visibility = 'PUBLIC' OR visibility IS NULL) ORDER BY id DESC LIMIT 5")
    List<Photo> searchPhotosSync(String q);

    @Query("SELECT * FROM photos WHERE groupId = :groupId ORDER BY id DESC")
    LiveData<List<Photo>> getPhotosByGroup(long groupId);
}