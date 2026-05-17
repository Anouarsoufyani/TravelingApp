package com.example.travelshare;

import android.app.Application;
import org.osmdroid.config.Configuration;

public class TravelingApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(getCacheDir());
        Configuration.getInstance().setOsmdroidTileCache(
                new java.io.File(getCacheDir(), "osm_tiles"));

        // Initialisation Cloudinary
        com.example.travelshare.utils.CloudinaryHelper.init(this);
    }
}
