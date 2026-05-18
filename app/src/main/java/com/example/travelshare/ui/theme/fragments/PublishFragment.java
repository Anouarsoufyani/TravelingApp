package com.example.travelshare.ui.theme.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.File;

import com.bumptech.glide.Glide;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import com.example.travelshare.R;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.example.travelshare.data.repository.FirebaseRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PublishFragment extends Fragment {

    private SharedViewModel viewModel;
    private SessionManager sessionManager;
    private List<Group> groupList = new ArrayList<>();

    private List<Uri> selectedUris = new ArrayList<>();
    private Uri cameraUri        = null;
    private ImageView ivPreview;
    private RecyclerView rvThumbs;
    private ThumbAdapter thumbAdapter;
    private List<String> mlKitLabels = new ArrayList<>();

    private enum VoiceState { IDLE, RECORDING, RECORDED }
    private VoiceState voiceState = VoiceState.IDLE;
    private MediaRecorder mediaRecorder;
    private MediaPlayer   mediaPlayer;
    private String        voiceNotePath;

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri>    cameraLauncher;
    private ActivityResultLauncher<String> audioPermLauncher;
    private Button btnVoiceCached;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedUris.addAll(uris);
                        updatePhotoPreview();
                        runMlKit(uris.get(uris.size() - 1));
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraUri != null) {
                        selectedUris.add(cameraUri);
                        updatePhotoPreview();
                        runMlKit(cameraUri);
                    }
                });

        audioPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startVoiceRecording(btnVoiceCached);
                    } else {
                        Toast.makeText(getContext(), "Permission micro refusée", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_publish, container, false);
        viewModel       = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sessionManager  = new SessionManager(requireContext());

        EditText etTitle    = view.findViewById(R.id.et_pub_title);
        EditText etLocation = view.findViewById(R.id.et_pub_location);
        Spinner  spinnerCat = view.findViewById(R.id.spinner_pub_category);
        EditText etTags     = view.findViewById(R.id.et_pub_tags);

        String[] categories = {"Nature", "Urbain", "Culture", "Magasin"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_spinner_item, categories) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) super.getView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(14f);
                tv.setPadding(16, 0, 16, 0);
                return tv;
            }
            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setBackgroundColor(android.graphics.Color.parseColor("#1A2040"));
                tv.setPadding(32, 24, 32, 24);
                tv.setTextSize(15f);
                return tv;
            }
        };
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCat.setAdapter(catAdapter);

        com.google.android.material.switchmaterial.SwitchMaterial switchVisibility = view.findViewById(R.id.switch_visibility);
        View layoutGroupSelector  = view.findViewById(R.id.layout_group_selector);
        Spinner spinnerGroups     = view.findViewById(R.id.spinner_groups);
        Button btnNewGroup        = view.findViewById(R.id.btn_new_group);
        CheckBox checkApprox      = view.findViewById(R.id.check_approx_location);
        Button btnVoice           = view.findViewById(R.id.btn_record_voice);
        Button btnTags            = view.findViewById(R.id.btn_auto_tags);
        Button btnPublish         = view.findViewById(R.id.btn_publish_photo);
        btnVoiceCached = btnVoice;

        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(getContext(), "Vous devez être connecté pour publier.", Toast.LENGTH_SHORT).show();
        }

        ivPreview = view.findViewById(R.id.iv_photo_preview);
        rvThumbs  = view.findViewById(R.id.rv_publish_thumbs);
        rvThumbs.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        thumbAdapter = new ThumbAdapter(selectedUris, uri -> {
            selectedUris.remove(uri);
            updatePhotoPreview();
        });
        rvThumbs.setAdapter(thumbAdapter);
        
        view.findViewById(R.id.btn_pick_gallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        view.findViewById(R.id.btn_pick_camera).setOnClickListener(v -> takeCameraPhoto());

        switchVisibility.setOnCheckedChangeListener((btn, checked) ->
                layoutGroupSelector.setVisibility(checked ? View.VISIBLE : View.GONE));

        viewModel.getGroupsForUser(sessionManager.getUserId()).observe(getViewLifecycleOwner(), groups -> {
            groupList = groups;
            List<String> names = new ArrayList<>();
            for (Group g : groups) names.add(g.name);
            ArrayAdapter<String> gAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, names);
            gAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerGroups.setAdapter(gAdapter);
        });

        btnNewGroup.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new GroupsFragment())
                        .addToBackStack(null).commit());

        btnVoice.setOnClickListener(v -> {
            if (voiceState == VoiceState.IDLE) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    startVoiceRecording(btnVoice);
                } else {
                    audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            } else if (voiceState == VoiceState.RECORDING) {
                stopVoiceRecording(btnVoice);
            } else {
                playVoiceNote(btnVoice);
            }
        });

        btnTags.setOnClickListener(v -> {
            if (mlKitLabels.isEmpty()) {
                Toast.makeText(getContext(), "Analysez une photo d'abord", Toast.LENGTH_SHORT).show();
                return;
            }
            String title = etTitle.getText().toString().trim();
            String loc   = etLocation.getText().toString().trim();
            String cat   = (String) spinnerCat.getSelectedItem();

            StringBuilder sb = buildBaseTags(title, loc, cat);
            for (String label : mlKitLabels) {
                sb.append("#").append(label.replaceAll("\\s+", "")).append(" ");
            }
            etTags.setText(sb.toString().trim());
        });

        btnPublish.setOnClickListener(v -> {
            if (sessionManager.getUserId() == -1) {
                Toast.makeText(getContext(), "Action refusée : Mode Anonyme", Toast.LENGTH_LONG).show();
                return;
            }
            
            final String title    = etTitle.getText().toString().trim();
            final String location = etLocation.getText().toString().trim();
            final String category = (String) spinnerCat.getSelectedItem();
            final String tags     = etTags.getText().toString().trim();

            if (title.isEmpty() || location.isEmpty()) {
                Toast.makeText(getContext(), "Le titre et le lieu sont obligatoires", Toast.LENGTH_SHORT).show();
                return;
            }

            final String date       = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            final String author     = sessionManager.getUsername();
            final boolean isPrivate = switchVisibility.isChecked();
            final String visibility = isPrivate ? "GROUP" : "PUBLIC";
            
            long selectedGroupId = -1;
            if (isPrivate && !groupList.isEmpty()) {
                int pos = spinnerGroups.getSelectedItemPosition();
                if (pos >= 0 && pos < groupList.size()) selectedGroupId = groupList.get(pos).id;
            }
            final long finalGroupId = selectedGroupId;
            final String savedVoicePath = voiceNotePath;
            final boolean approxChecked = checkApprox.isChecked();

            btnPublish.setEnabled(false);
            
            if (!selectedUris.isEmpty()) {
                Toast.makeText(getContext(), "Envoi des images (" + selectedUris.size() + ") sur Cloudinary...", Toast.LENGTH_SHORT).show();
                uploadMultipleImages(new ArrayList<>(selectedUris), new ArrayList<>(), new com.example.travelshare.utils.CloudinaryHelper.UploadListener() {
                    @Override
                    public void onSuccess(String publicUrls) {
                        if (isAdded()) {
                            proceedToPublish(publicUrls, title, location, category, tags, 
                                           author, visibility, date, approxChecked, 
                                           savedVoicePath, finalGroupId, btnPublish, btnVoice, view);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                btnPublish.setEnabled(true);
                                Toast.makeText(getContext(), "Erreur upload : " + message, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            } else {
                proceedToPublish(null, title, location, category, tags, 
                               author, visibility, date, approxChecked, 
                               savedVoicePath, finalGroupId, btnPublish, btnVoice, view);
            }
        });

        return view;
    }

    private void uploadMultipleImages(List<Uri> remaining, List<String> uploadedUrls, com.example.travelshare.utils.CloudinaryHelper.UploadListener finalListener) {
        if (remaining.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < uploadedUrls.size(); i++) {
                sb.append(uploadedUrls.get(i));
                if (i < uploadedUrls.size() - 1) sb.append("|");
            }
            finalListener.onSuccess(sb.toString());
            return;
        }

        Uri next = remaining.remove(0);
        com.example.travelshare.utils.CloudinaryHelper.uploadImage(next, new com.example.travelshare.utils.CloudinaryHelper.UploadListener() {
            @Override
            public void onSuccess(String publicUrl) {
                uploadedUrls.add(publicUrl);
                uploadMultipleImages(remaining, uploadedUrls, finalListener);
            }

            @Override
            public void onError(String message) {
                finalListener.onError(message);
            }
        });
    }

    private void updatePhotoPreview() {
        if (thumbAdapter != null) thumbAdapter.notifyDataSetChanged();
        
        if (selectedUris.isEmpty()) {
            ivPreview.setVisibility(View.GONE);
            rvThumbs.setVisibility(View.GONE);
            View placeholder = getView() != null ? getView().findViewById(R.id.layout_photo_placeholder) : null;
            if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
        } else {
            ivPreview.setVisibility(View.VISIBLE);
            rvThumbs.setVisibility(View.VISIBLE);
            Uri last = selectedUris.get(selectedUris.size() - 1);
            Glide.with(this).load(last).centerCrop().into(ivPreview);
            View placeholder = getView() != null ? getView().findViewById(R.id.layout_photo_placeholder) : null;
            if (placeholder != null) placeholder.setVisibility(View.GONE);
            
            Toast.makeText(getContext(), selectedUris.size() + " photo(s) sélectionnée(s)", Toast.LENGTH_SHORT).show();
        }
    }

    // --- THUMB ADAPTER ---
    static class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.TVH> {
        private final List<Uri> uris;
        private final java.util.function.Consumer<Uri> onRemove;
        ThumbAdapter(List<Uri> uris, java.util.function.Consumer<Uri> onRemove) {
            this.uris = uris;
            this.onRemove = onRemove;
        }
        @NonNull @Override public TVH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new TVH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_publish_thumb, p, false));
        }
        @Override public void onBindViewHolder(@NonNull TVH h, int p) {
            Uri u = uris.get(p);
            Glide.with(h.itemView.getContext()).load(u).centerCrop().into(h.iv);
            h.btnRemove.setOnClickListener(v -> onRemove.accept(u));
        }
        @Override public int getItemCount() { return uris.size(); }
        static class TVH extends RecyclerView.ViewHolder {
            ImageView iv; View btnRemove;
            TVH(View v) { super(v); iv = v.findViewById(R.id.iv_thumb); btnRemove = v.findViewById(R.id.btn_remove_thumb); }
        }
    }

    private void takeCameraPhoto() {
        try {
            File imageDir = new File(requireContext().getExternalCacheDir(), "images");
            if (!imageDir.exists()) imageDir.mkdirs();
            File photoFile = new File(imageDir, "camera_photo_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", photoFile);
            cameraLauncher.launch(cameraUri);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Erreur caméra", Toast.LENGTH_SHORT).show();
        }
    }

    private void proceedToPublish(String cloudImageUrl, String title, String location, String category, String tags,
                                  String author, String visibility, String date, boolean approxChecked,
                                  String savedVoicePath, long finalGroupId, Button btnPublish, Button btnVoice, View view) {
        
        Toast.makeText(getContext(), "Géolocalisation du lieu...", Toast.LENGTH_SHORT).show();
        Context appContext = requireContext().getApplicationContext();

        com.example.travelshare.data.AppDatabase.databaseWriteExecutor.execute(() -> {
            double[] coords = geocodeLocation(appContext, location);
            if (coords == null) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        btnPublish.setEnabled(true);
                        Toast.makeText(getContext(),
                                "Lieu introuvable. Précisez la ville ou l'adresse.",
                                Toast.LENGTH_LONG).show();
                    });
                }
                return;
            }

            if (approxChecked) {
                coords[0] = Math.round(coords[0] * 10.0) / 10.0;
                coords[1] = Math.round(coords[1] * 10.0) / 10.0;
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    Photo photo = new Photo(title, location, author, 0,
                            coords[0], coords[1], date, category, tags, visibility);
                    
                    if (cloudImageUrl != null) photo.setImageUri(cloudImageUrl);
                    
                    if (savedVoicePath != null)   photo.setVoiceUri(savedVoicePath);
                    if (finalGroupId >= 0)        photo.setGroupId(finalGroupId);
                    
                    viewModel.insertAndGetId(photo, roomId -> {
                        btnPublish.setEnabled(true);
                        Toast.makeText(getContext(), "Post publié !", Toast.LENGTH_SHORT).show();
                        triggerNotificationsForPublish(author, title, location, roomId);
                        
                        // Reset UI
                        selectedUris.clear();
                        updatePhotoPreview();
                        resetVoiceState(btnVoice);
                        
                        // Clear fields
                        EditText etT = view.findViewById(R.id.et_pub_title);
                        EditText etL = view.findViewById(R.id.et_pub_location);
                        EditText etTg = view.findViewById(R.id.et_pub_tags);
                        if (etT != null) etT.setText("");
                        if (etL != null) etL.setText("");
                        if (etTg != null) etTg.setText("");
                    });
                });
            }
        });
    }

    private void startVoiceRecording(Button btn) {
        try {
            stopMediaPlayer();
            File voiceDir = new File(requireContext().getExternalCacheDir(), "voice");
            voiceDir.mkdirs();
            voiceNotePath = new File(voiceDir, "voice_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(voiceNotePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            voiceState = VoiceState.RECORDING;
            btn.setText("🛑 Arrêter (enreg...)");
        } catch (Exception e) {
            Toast.makeText(getContext(), "Erreur micro", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVoiceRecording(Button btn) {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            voiceState = VoiceState.RECORDED;
            btn.setText("▶️ Réécouter");
        } catch (Exception e) {
            resetVoiceState(btn);
            Toast.makeText(getContext(), "Erreur à l'arrêt de l'enregistrement", Toast.LENGTH_SHORT).show();
        }
    }

    private void playVoiceNote(Button btn) {
        if (voiceNotePath == null) return;
        try {
            stopMediaPlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(voiceNotePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            btn.setText("⏸ Lecture...");
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
                if (isAdded()) btn.post(() -> {
                    voiceState = VoiceState.RECORDED;
                    btn.setText("▶️ Réécouter");
                });
            });
        } catch (Exception e) {
            Toast.makeText(getContext(), "Erreur de lecture", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetVoiceState(Button btn) {
        releaseRecorder();
        stopMediaPlayer();
        voiceState    = VoiceState.IDLE;
        voiceNotePath = null;
        if (btn != null) btn.setText("Note Vocale");
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void stopMediaPlayer() {
        if (mediaPlayer != null) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void runMlKit(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), imageUri);
            ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.70f)
                    .build();
            ImageLabeler labeler = ImageLabeling.getClient(options);
            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        mlKitLabels.clear();
                        for (ImageLabel label : labels) {
                            mlKitLabels.add(label.getText());
                        }
                        // Toast IA supprimé
                    })
                    .addOnFailureListener(e -> mlKitLabels.clear());
        } catch (Exception e) {
            mlKitLabels.clear();
        }
    }

    private StringBuilder buildBaseTags(String title, String location, String category) {
        StringBuilder sb = new StringBuilder();
        if (!location.isEmpty()) sb.append("#").append(location.replaceAll("\\s+", "")).append(" ");
        if (category != null && !category.isEmpty()) sb.append("#").append(category).append(" ");
        if (!title.isEmpty()) {
            for (String word : title.split("\\s+")) {
                String clean = word.replaceAll("[^a-zA-ZÀ-ÿ]", "");
                if (clean.length() > 3)
                    sb.append("#").append(clean.substring(0, 1).toUpperCase())
                      .append(clean.substring(1).toLowerCase()).append(" ");
            }
        }
        if ("Nature".equals(category))        sb.append("#Paysage #Pleinair ");
        else if ("Urbain".equals(category))   sb.append("#Ville #Architecture ");
        else if ("Culture".equals(category))  sb.append("#Patrimoine #Art ");
        else if ("Magasin".equals(category))  sb.append("#Shopping #Boutique ");
        sb.append("#Voyage #TravelShare");
        return sb;
    }

    private double[] geocodeLocation(Context context, String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) return null;
        if (!Geocoder.isPresent()) return null;

        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(locationName.trim(), 1);
            if (addresses == null || addresses.isEmpty()) return null;

            Address address = addresses.get(0);
            return new double[]{address.getLatitude(), address.getLongitude()};
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private void triggerNotificationsForPublish(String author, String title, String location, long photoId) {
        FirebaseRepository.getInstance().getFollowers(author, followers -> {
            if (followers == null || followers.isEmpty()) return;

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            for (String follower : followers) {
                if (follower == null || follower.equalsIgnoreCase(author)) continue;

                AppNotification notif = new AppNotification();
                notif.type = "FOLLOW_POST";
                notif.senderUsername = author;
                notif.message = author + " a publié \"" + title + "\" à " + location;
                notif.photoId = (int) photoId;
                notif.date = date;
                FirebaseRepository.getInstance().saveNotification(follower, notif);
            }
        });
    }
}
