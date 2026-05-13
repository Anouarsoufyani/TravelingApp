package com.example.travelshare.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.travelshare.data.models.Comment;
import java.util.List;

@Dao
public interface CommentDao {
    @Insert
    long insertComment(Comment comment);

    @Query("SELECT * FROM comments WHERE photoId = :photoId ORDER BY date DESC")
    LiveData<List<Comment>> getCommentsForPhoto(long photoId);

    @Query("DELETE FROM comments WHERE id = :id")
    void deleteComment(long id);

    @Query("SELECT * FROM comments WHERE id = :id LIMIT 1")
    Comment getCommentById(long id);

    @Query("SELECT COUNT(*) FROM comments WHERE photoId = :photoId AND authorName = :authorName AND text = :text")
    int countByContent(long photoId, String authorName, String text);
}