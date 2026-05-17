package com.example.travelshare.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "groups")
public class Group {
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;
    public String description;
    public long creatorId;
    public String creatorUsername;
    public String visibility = VISIBILITY_PRIVATE;

    public boolean isPublic() {
        return VISIBILITY_PUBLIC.equals(visibility);
    }
}
