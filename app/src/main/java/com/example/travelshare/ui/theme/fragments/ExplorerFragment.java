package com.example.travelshare.ui.theme.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.travelshare.R;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.theme.adapters.PhotoAdapter;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.util.List;
import java.util.Locale;

public class ExplorerFragment extends Fragment implements SensorEventListener {

    public static final String ARG_SEARCH_QUERY = "search_query";
    private static final int REQUEST_VOICE = 101;
    private static final int PAGE_SIZE = 20;

    // ── Capteur accéléromètre (détection du shake) ─────────────────────────
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float lastX, lastY, lastZ;
    private long lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD = 12f;
    private static final long SHAKE_COOLDOWN_MS = 1500;

    private SharedViewModel viewModel;
    private PhotoAdapter adapter;
    private LiveData<List<Photo>> currentSource;
    private boolean isGridView = true;

    // ── Pagination ─────────────────────────────────────────────────────────
    private int currentOffset = 0;
    private boolean isPaginationMode = false;
    private boolean isLoadingMore = false;
    private SwipeRefreshLayout swipeRefresh;

    // Chips
    private final int[] CHIP_IDS = {R.id.chip_all, R.id.chip_nature, R.id.chip_urbain, R.id.chip_culture, R.id.chip_magasin, R.id.chip_nearby, R.id.chip_likes};
    private View chipRoot;

    private ActivityResultLauncher<String> locationPermLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        locationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) fetchNearbyPhotos();
                    else Toast.makeText(getContext(), "Permission GPS refusée", Toast.LENGTH_SHORT).show();
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float deltaX = Math.abs(x - lastX);
        float deltaY = Math.abs(y - lastY);
        float deltaZ = Math.abs(z - lastZ);

        lastX = x; lastY = y; lastZ = z;

        if ((deltaX > SHAKE_THRESHOLD && deltaY > SHAKE_THRESHOLD)
                || (deltaX > SHAKE_THRESHOLD && deltaZ > SHAKE_THRESHOLD)
                || (deltaY > SHAKE_THRESHOLD && deltaZ > SHAKE_THRESHOLD)) {

            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                onShakeDetected();
            }
        }
    }

    private void onShakeDetected() {
        if (viewModel == null || !isAdded()) return;
        setActiveChip(-1);
        observeSource(viewModel.getRandomPhotos(10));
        Toast.makeText(getContext(), "🔀 Photos aléatoires !", Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explorer, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_photos);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new PhotoAdapter();
        recyclerView.setAdapter(adapter);

        // ── Pull-to-refresh ────────────────────────────────────────────────
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isPaginationMode) {
                currentOffset = 0;
                isLoadingMore = false;
                loadPage(0, true);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });

        // ── Infinite scroll ────────────────────────────────────────────────
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!isPaginationMode || dy <= 0 || isLoadingMore) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                int lastVisible;
                if (lm instanceof GridLayoutManager) {
                    lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
                } else {
                    lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                }
                if (lastVisible >= adapter.getItemCount() - 4) {
                    loadPage(currentOffset, false);
                }
            }
        });

        // ── Toggle liste / grille ──────────────────────────────────────────
        TextView btnToggle = view.findViewById(R.id.btn_toggle_view);
        btnToggle.setOnClickListener(v -> {
            isGridView = !isGridView;
            if (isGridView) {
                recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
                btnToggle.setText("☰ Liste");
            } else {
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                btnToggle.setText("⊞ Grille");
            }
        });

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        chipRoot = view;

        // Chargement initial
        String prefillQuery = getArguments() != null ? getArguments().getString(ARG_SEARCH_QUERY, "") : "";
        if (!prefillQuery.isEmpty()) {
            EditText etSearch = view.findViewById(R.id.et_search_query);
            etSearch.setText(prefillQuery);
            observeSource(viewModel.searchPhotos(prefillQuery));
        } else {
            enterPaginationMode();
        }
        setActiveChip(R.id.chip_all);

        // ── Recherche texte ────────────────────────────────────────────────
        EditText etSearch = view.findViewById(R.id.et_search_query);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    setActiveChip(R.id.chip_all);
                    enterPaginationMode();
                } else {
                    observeSource(viewModel.searchPhotos(q));
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ── Recherche vocale ───────────────────────────────────────────────
        view.findViewById(R.id.btn_voice_search).setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites un lieu, auteur ou mot-clé...");
            try {
                startActivityForResult(intent, REQUEST_VOICE);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Filtre auteur ──────────────────────────────────────────────────
        EditText etAuthor = view.findViewById(R.id.et_filter_author);
        etAuthor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String author = s.toString().trim();
                if (!author.isEmpty()) {
                    observeSource(viewModel.getPublicPhotosByAuthor(author));
                } else {
                    setActiveChip(R.id.chip_all);
                    enterPaginationMode();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ── Filtre par période ─────────────────────────────────────────────
        EditText etDateStart = view.findViewById(R.id.et_filter_date_start);
        EditText etDateEnd   = view.findViewById(R.id.et_filter_date_end);
        View.OnFocusChangeListener dateListener = (v, hasFocus) -> {
            if (!hasFocus) {
                String start = etDateStart.getText().toString().trim();
                String end   = etDateEnd.getText().toString().trim();
                if (!start.isEmpty() && !end.isEmpty()) {
                    observeSource(viewModel.getPhotosByDateRange(start, end));
                }
            }
        };
        etDateStart.setOnFocusChangeListener(dateListener);
        etDateEnd.setOnFocusChangeListener(dateListener);

        // ── Chips de catégorie ─────────────────────────────────────────────
        view.findViewById(R.id.chip_all).setOnClickListener(v -> {
            setActiveChip(R.id.chip_all);
            enterPaginationMode();
        });
        setupChip(view, R.id.chip_nature,  "Nature");
        setupChip(view, R.id.chip_urbain,  "Urbain");
        setupChip(view, R.id.chip_culture, "Culture");
        setupChip(view, R.id.chip_magasin, "Magasin");

        // ── Chip Autour de moi (GPS) ───────────────────────────────────────
        view.findViewById(R.id.chip_nearby).setOnClickListener(v -> {
            setActiveChip(R.id.chip_nearby);
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fetchNearbyPhotos();
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        // ── Mes Likes ──────────────────────────────────────────────────────
        view.findViewById(R.id.chip_likes).setOnClickListener(v -> {
            setActiveChip(R.id.chip_likes);
            List<Integer> likedIds = getLikedPhotoIds();
            if (likedIds.isEmpty()) {
                adapter.setPhotos(new java.util.ArrayList<>());
                Toast.makeText(getContext(), "Vous n'avez pas encore liké de photos", Toast.LENGTH_SHORT).show();
            } else {
                observeSource(viewModel.getPhotosByIds(likedIds));
            }
        });

        // ── Flux aléatoire ─────────────────────────────────────────────────
        view.findViewById(R.id.btn_random_feed).setOnClickListener(v -> {
            setActiveChip(-1);
            observeSource(viewModel.getRandomPhotos(10));
        });

        // ── TravelPath ─────────────────────────────────────────────────────
        view.findViewById(R.id.btn_explorer_travelpath).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new TravelPathFragment())
                    .addToBackStack(null)
                    .commit()
        );

        return view;
    }

    // ── Pagination ─────────────────────────────────────────────────────────

    private void enterPaginationMode() {
        isPaginationMode = true;
        currentOffset = 0;
        isLoadingMore = false;
        if (currentSource != null) {
            currentSource.removeObservers(getViewLifecycleOwner());
            currentSource = null;
        }
        loadPage(0, true);
    }

    private void loadPage(int offset, boolean replace) {
        if (isLoadingMore && !replace) return;
        isLoadingMore = true;
        viewModel.loadMorePublicPhotos(offset, PAGE_SIZE, photos -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (replace) adapter.setPhotos(photos);
                else adapter.appendPhotos(photos);
                currentOffset = offset + (photos != null ? photos.size() : 0);
                isLoadingMore = false;
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            });
        });
    }

    // ── Sources LiveData (filtres) ──────────────────────────────────────────

    private void setupChip(View root, int chipId, @Nullable String category) {
        root.findViewById(chipId).setOnClickListener(v -> {
            setActiveChip(chipId);
            observeSource(viewModel.getPhotosByCategory(category));
        });
    }

    private void setActiveChip(int activeId) {
        if (chipRoot == null) return;
        for (int id : CHIP_IDS) {
            TextView chip = chipRoot.findViewById(id);
            if (chip == null) continue;
            if (id == activeId) {
                chip.setBackgroundResource(R.drawable.bg_pill_solid);
                chip.setTextColor(requireContext().getResources().getColor(R.color.default_white, null));
            } else {
                chip.setBackgroundResource(R.drawable.bg_pill_outline);
                chip.setTextColor(requireContext().getResources().getColor(R.color.ink_muted, null));
            }
        }
    }

    private void fetchNearbyPhotos() {
        try {
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                observeSource(viewModel.getPhotosByLocation(loc.getLatitude(), loc.getLongitude(), 50));
                Toast.makeText(getContext(), "Photos à moins de 50 km", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Position introuvable, activez le GPS", Toast.LENGTH_SHORT).show();
                setActiveChip(R.id.chip_all);
                enterPaginationMode();
            }
        } catch (SecurityException e) {
            Toast.makeText(getContext(), "Permission GPS manquante", Toast.LENGTH_SHORT).show();
        }
    }

    public static List<Integer> getLikedPhotoIdsStatic(android.content.Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences("likes", android.content.Context.MODE_PRIVATE);
        java.util.Set<String> set = prefs.getStringSet("liked_ids", new java.util.HashSet<>());
        List<Integer> ids = new java.util.ArrayList<>();
        for (String s : set) { try { ids.add(Integer.parseInt(s)); } catch (Exception ignored) {} }
        return ids;
    }

    private List<Integer> getLikedPhotoIds() {
        return getLikedPhotoIdsStatic(requireContext());
    }

    private void observeSource(LiveData<List<Photo>> newSource) {
        isPaginationMode = false;
        if (currentSource != null) currentSource.removeObservers(getViewLifecycleOwner());
        currentSource = newSource;
        currentSource.observe(getViewLifecycleOwner(), adapter::setPhotos);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE && resultCode == Activity.RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String query = results.get(0);
                EditText etSearch = requireView().findViewById(R.id.et_search_query);
                etSearch.setText(query);
                Toast.makeText(getContext(), "Recherche : " + query, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
