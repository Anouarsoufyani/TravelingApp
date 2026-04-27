package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String login;
    public String password;
    public String nom;
    public String prenom;
    public String dateNaissance;
    public String telephone;
    public String email;
    public String centresInteret;
}
