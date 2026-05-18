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

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User getUserByEmail(String email);

    @Query("SELECT * FROM users WHERE id = :userId")
    User getUserById(long userId);

    @Query("UPDATE users SET bio = :bio WHERE id = :userId")
    void updateUserBio(long userId, String bio);
}
