package com.example.travelshare.ui.theme.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
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
import android.widget.Switch;
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

import java.io.File;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import com.example.travelshare.R;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.utils.NotificationUtil;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.example.travelshare.data.repository.FirebaseRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PublishFragment extends Fragment {

    private SharedViewModel viewModel;
    private SessionManager sessionManager;
    private List<Group> groupList = new ArrayList<>();

    private Uri selectedPhotoUri = null;
    private Uri cameraUri        = null;
    private ImageView ivPreview;
    private List<String> mlKitLabels = new ArrayList<>();

    // ── Enregistrement audio ───────────────────────────────────────────────
    private enum VoiceState { IDLE, RECORDING, RECORDED }
    private VoiceState voiceState = VoiceState.IDLE;
    private MediaRecorder mediaRecorder;
    private MediaPlayer   mediaPlayer;
    private String        voiceNotePath; // chemin du fichier enregistré

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri>    cameraLauncher;
    private ActivityResultLauncher<String> audioPermLauncher;
    private Button btnVoiceCached; // référence pour le callback de permission

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedPhotoUri = uri;
                        if (ivPreview != null) {
                            ivPreview.setImageURI(uri);
                            ivPreview.setVisibility(View.VISIBLE);
                            requireView().findViewById(R.id.layout_photo_placeholder).setVisibility(View.GONE);
                        }
                        runMlKit(uri);
                    }
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraUri != null) {
                        selectedPhotoUri = cameraUri;
                        if (ivPreview != null) {
                            ivPreview.setImageURI(cameraUri);
                            ivPreview.setVisibility(View.VISIBLE);
                            requireView().findViewById(R.id.layout_photo_placeholder).setVisibility(View.GONE);
                        }
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
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCat.setAdapter(catAdapter);

        Switch switchVisibility   = view.findViewById(R.id.switch_visibility);
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

        // ── Sélecteur de photo ─────────────────────────────────────────────
        ivPreview = view.findViewById(R.id.iv_photo_preview);
        view.findViewById(R.id.layout_photo_picker).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Ajouter une photo")
                        .setItems(new String[]{"Galerie", "Appareil photo"}, (dialog, which) -> {
                            if (which == 0) {
                                galleryLauncher.launch("image/*");
                            } else {
                                cameraUri = createImageUri();
                                cameraLauncher.launch(cameraUri);
                            }
                        }).show());

        // ── Spinner groupes ────────────────────────────────────────────────
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new ArrayList<>());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGroups.setAdapter(spinnerAdapter);

        viewModel.getGroupsForUser(sessionManager.getUserId()).observe(getViewLifecycleOwner(), groups -> {
            groupList = groups;
            spinnerAdapter.clear();
            for (Group g : groups) spinnerAdapter.add(g.name);
            spinnerAdapter.notifyDataSetChanged();
        });

        switchVisibility.setOnCheckedChangeListener((btn, checked) ->
                layoutGroupSelector.setVisibility(checked ? View.VISIBLE : View.GONE));

        btnNewGroup.setOnClickListener(v -> {
            EditText etName = new EditText(getContext());
            etName.setHint("Nom du groupe");
            android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(48, 16, 48, 0);
            layout.addView(etName);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Nouveau groupe")
                    .setView(layout)
                    .setPositiveButton("Créer", (dialog, which) -> {
                        String name = etName.getText().toString().trim();
                        if (!name.isEmpty()) {
                            Group g = new Group();
                            g.name = name;
                            g.creatorId = sessionManager.getUserId();
                            viewModel.insertGroup(g);
                            Toast.makeText(getContext(), "Groupe créé !", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Annuler", null).show();
        });

        // ── Message vocal réel (MediaRecorder) ────────────────────────────
        btnVoice.setOnClickListener(v -> {
            switch (voiceState) {
                case IDLE:
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                        startVoiceRecording(btnVoice);
                    } else {
                        btnVoiceCached = btnVoice;
                        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    }
                    break;
                case RECORDING:
                    stopVoiceRecording(btnVoice);
                    break;
                case RECORDED:
                    playVoiceNote(btnVoice);
                    break;
            }
        });

        // ── Tags automatiques (mots-clés + ML Kit IA) ────────────────────
        btnTags.setOnClickListener(v -> {
            String title    = etTitle.getText().toString().trim();
            String location = etLocation.getText().toString().trim();
            String category = (String) spinnerCat.getSelectedItem();
            StringBuilder tags = buildBaseTags(title, location, category);
            for (String label : mlKitLabels) {
                String tag = "#" + label.replaceAll("\\s+", "");
                if (!tags.toString().contains(tag)) tags.append(" ").append(tag);
            }
            etTags.setText(tags.toString().trim());
            if (!mlKitLabels.isEmpty()) {
                Toast.makeText(getContext(), "✨ " + mlKitLabels.size() + " tags IA ajoutés", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Publication ────────────────────────────────────────────────────
        btnPublish.setOnClickListener(v -> {
            if (!sessionManager.isLoggedIn()) {
                Toast.makeText(getContext(), "Action refusée : Mode Anonyme", Toast.LENGTH_LONG).show();
                return;
            }
            String title    = etTitle.getText().toString().trim();
            String location = etLocation.getText().toString().trim();
            String category = (String) spinnerCat.getSelectedItem();
            String tags     = etTags.getText().toString().trim();

            if (title.isEmpty() || location.isEmpty()) {
                Toast.makeText(getContext(), "Le titre et le lieu sont obligatoires", Toast.LENGTH_SHORT).show();
                return;
            }

            String date       = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            String author     = sessionManager.getUsername();
            boolean isPrivate = switchVisibility.isChecked();
            String visibility = isPrivate ? "GROUP" : "PUBLIC";
            long selectedGroupId = -1;
            if (isPrivate && !groupList.isEmpty()) {
                int pos = spinnerGroups.getSelectedItemPosition();
                if (pos >= 0 && pos < groupList.size()) selectedGroupId = groupList.get(pos).id;
            }
            final long finalGroupId = selectedGroupId;
            final String savedVoicePath = voiceNotePath;

            btnPublish.setEnabled(false);
            Toast.makeText(getContext(), "Géolocalisation du lieu...", Toast.LENGTH_SHORT).show();

            final String finalTitle = title, finalLocation = location,
                    finalCategory = category, finalTags = tags,
                    finalAuthor = author, finalVisibility = visibility, finalDate = date;

            boolean approxChecked = checkApprox.isChecked();
            com.example.travelshare.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                double[] coords = geocodeLocation(finalLocation);
                // Si lieu approximatif : arrondi à 1 décimale (~11 km)
                if (approxChecked) {
                    coords[0] = Math.round(coords[0] * 10.0) / 10.0;
                    coords[1] = Math.round(coords[1] * 10.0) / 10.0;
                }
                requireActivity().runOnUiThread(() -> {
                    Photo photo = new Photo(finalTitle, finalLocation, finalAuthor, 0,
                            coords[0], coords[1], finalDate, finalCategory, finalTags, finalVisibility);
                    if (selectedPhotoUri != null) photo.setImageUri(selectedPhotoUri.toString());
                    if (savedVoicePath != null)   photo.setVoiceUri(savedVoicePath);
                    if (finalGroupId >= 0)        photo.setGroupId(finalGroupId);
                    viewModel.insertAndGetId(photo, roomId -> {
                        savePhotoToFirestore(photo, roomId);
                        btnPublish.setEnabled(true);
                        Toast.makeText(getContext(), "Photo publiée !", Toast.LENGTH_SHORT).show();
                        triggerNotificationsForPublish(finalAuthor, finalLocation, finalCategory, finalTags);
                        etTitle.setText(""); etLocation.setText(""); etTags.setText("");
                        spinnerCat.setSelection(0);
                        switchVisibility.setChecked(false);
                        checkApprox.setChecked(false);
                        selectedPhotoUri = null;
                        ivPreview.setVisibility(View.GONE);
                        view.findViewById(R.id.layout_photo_placeholder).setVisibility(View.VISIBLE);
                        resetVoiceState(btnVoice);
                    });
                });
            });
        });

        return view;
    }

    // ── Firestore ──────────────────────────────────────────────────────────

    private void savePhotoToFirestore(Photo photo, long roomId) {
        FirebaseRepository.getInstance().savePhoto(photo, roomId);
    }

    // ── Enregistrement vocal ───────────────────────────────────────────────

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
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setOutputFile(voiceNotePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            voiceState = VoiceState.RECORDING;
            btn.setText("🛑 Arrêter");
            Toast.makeText(getContext(), "Enregistrement en cours…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Erreur d'enregistrement : " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseRecorder();
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
            Toast.makeText(getContext(), "Note vocale enregistrée ✓", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            releaseRecorder();
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
        if (btn != null) btn.setText("🎤 Message Vocal");
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releaseRecorder();
        stopMediaPlayer();
    }

    // ── ML Kit — analyse IA de l'image ────────────────────────────────────

    private void runMlKit(Uri imageUri) {
        mlKitLabels.clear();
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
                        if (!mlKitLabels.isEmpty() && isAdded()) {
                            Toast.makeText(getContext(),
                                    "🤖 IA prête : " + mlKitLabels.size() + " objets détectés",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> mlKitLabels.clear());
        } catch (Exception e) {
            mlKitLabels.clear();
        }
    }

    // ── Tags ───────────────────────────────────────────────────────────────

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

    // ── Géocodage ──────────────────────────────────────────────────────────

    private Uri createImageUri() {
        File dir = new File(requireContext().getExternalCacheDir(), "images");
        dir.mkdirs();
        File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(requireContext(), "com.example.travelshare.provider", file);
    }

    private double[] geocodeLocation(String locationName) {
        try {
            String encoded = java.net.URLEncoder.encode(locationName, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", requireContext().getPackageName());
            con.setConnectTimeout(6000); con.setReadTimeout(6000);
            if (con.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String json = sb.toString();
                if (!json.equals("[]") && json.contains("\"lat\"")) {
                    int li = json.indexOf("\"lat\":\"") + 7;
                    double lat = Double.parseDouble(json.substring(li, json.indexOf("\"", li)));
                    int loi = json.indexOf("\"lon\":\"") + 7;
                    double lon = Double.parseDouble(json.substring(loi, json.indexOf("\"", loi)));
                    return new double[]{lat, lon};
                }
            }
        } catch (Exception ignored) {}
        return new double[]{0, 0};
    }

    // ── Notifications ──────────────────────────────────────────────────────

    private void triggerNotificationsForPublish(String author, String location, String category, String tags) {
        com.example.travelshare.data.AppDatabase db =
                com.example.travelshare.data.AppDatabase.getInstance(requireContext());
        com.example.travelshare.data.AppDatabase.databaseWriteExecutor.execute(() -> {
            List<NotificationPreference> prefs = db.notificationPreferenceDao()
                    .getPreferencesForUserSync(sessionManager.getUserId());
            for (NotificationPreference p : prefs) {
                boolean match = false;
                String msg = "";
                switch (p.type) {
                    case "AUTEUR":
                        match = author.equalsIgnoreCase(p.value);
                        msg = author + " a publié une nouvelle photo."; break;
                    case "LIEU":
                        match = location.toLowerCase().contains(p.value.toLowerCase());
                        msg = "Nouvelle photo à " + location; break;
                    case "TAG":
                        match = tags.toLowerCase().contains(p.value.toLowerCase());
                        msg = "Nouvelle photo avec le tag " + p.value; break;
                    case "GROUPE":
                        match = category.equalsIgnoreCase(p.value);
                        msg = "Nouvelle photo dans la catégorie " + category; break;
                }
                if (match) {
                    final String finalMsg = msg;
                    requireActivity().runOnUiThread(() ->
                            NotificationUtil.showNotification(requireContext(), "TravelShare", finalMsg));
                }
            }
        });
    }
}
