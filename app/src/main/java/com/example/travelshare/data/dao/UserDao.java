package com.example.travelshare.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.travelshare.data.models.User;

@Dao
public interface UserDao {
    @Insert
    long insertUser(User user);

    @Query("SELECT * FROM users WHERE login = :login")
    User getUserByLogin(String login);

    @Query("SELECT * FROM users WHERE id = :userId")
    User getUserById(long userId);

    @Query("SELECT * FROM users WHERE login =:login AND password =:password")
    User login(String login, String password);

    @Query("UPDATE users SET avatarUri = :avatarUri, bio = :bio WHERE id = :userId")
    void updateUserProfile(long userId, String avatarUri, String bio);
}
