package com.example.travelshare.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.travelshare.data.dao.AppNotificationDao;
import com.example.travelshare.data.dao.CommentDao;
import com.example.travelshare.data.dao.GroupDao;
import com.example.travelshare.data.dao.GroupMemberDao;
import com.example.travelshare.data.dao.GroupMessageDao;
import com.example.travelshare.data.dao.NotificationPreferenceDao;
import com.example.travelshare.data.dao.PhotoDao;
import com.example.travelshare.data.dao.PlanningDao;
import com.example.travelshare.data.dao.PlanStepDao;
import com.example.travelshare.data.dao.ReportDao;
import com.example.travelshare.data.dao.TravelPlanDao;
import com.example.travelshare.data.dao.UserDao;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Comment;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.models.GroupMessage;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.models.Planning;
import com.example.travelshare.data.models.PlanStep;
import com.example.travelshare.data.models.Report;
import com.example.travelshare.data.models.TravelPlan;
import com.example.travelshare.data.models.User;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
    entities = {User.class, Planning.class, Photo.class, Comment.class, Group.class, GroupMember.class, GroupMessage.class, Report.class, NotificationPreference.class, AppNotification.class, TravelPlan.class, PlanStep.class},
    version = 31,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract PlanningDao planningDao();
    public abstract PhotoDao photoDao();
    public abstract CommentDao commentDao();
    public abstract GroupDao groupDao();
    public abstract GroupMemberDao groupMemberDao();
    public abstract GroupMessageDao groupMessageDao();
    public abstract ReportDao reportDao();
    public abstract NotificationPreferenceDao notificationPreferenceDao();
    public abstract AppNotificationDao appNotificationDao();
    public abstract TravelPlanDao travelPlanDao();
    public abstract PlanStepDao planStepDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "tp3_database"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
