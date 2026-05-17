package com.example.travelshare.utils;

import android.content.Context;
import android.net.Uri;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for Cloudinary integration.
 * Manage initialization and provides a simplified upload method.
 */
public class CloudinaryHelper {

    // IMPORTANT: Remplacer par vos vraies clés Cloudinary
    private static final String CLOUD_NAME = "db68oxg2m";
    private static final String UPLOAD_PRESET = "ml_default";

    public static void init(Context context) {
        try {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", CLOUD_NAME);
            MediaManager.init(context, config);
        } catch (IllegalStateException e) {
            // Déjà initialisé
        }
    }

    public interface UploadListener {
        void onSuccess(String publicUrl);
        void onError(String message);
    }

    public static void uploadImage(Uri imageUri, UploadListener listener) {
        if (imageUri == null) {
            listener.onError("URI de l'image est nulle");
            return;
        }

        MediaManager.get().upload(imageUri)
                .unsigned(UPLOAD_PRESET)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        listener.onSuccess(secureUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        listener.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }
}
