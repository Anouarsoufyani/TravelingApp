package com.example.travelshare.ui.theme.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.content.Context;
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
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.theme.adapters.PhotoAdapter;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.util.List;
import java.util.Locale;

public class ExplorerFragment extends Fragment {

    public static final String ARG_SEARCH_QUERY = "search_query";
    private static final int REQUEST_VOICE = 101;

    private SharedViewModel viewModel;
    private PhotoAdapter adapter;
    private LiveData<List<Photo>> currentSource;

    // Chips
    private final int[] CHIP_IDS = {R.id.chip_all, R.id.chip_nature, R.id.chip_urbain, R.id.chip_culture, R.id.chip_magasin, R.id.chip_nearby};
    private View chipRoot;

    private ActivityResultLauncher<String> locationPermLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) fetchNearbyPhotos();
                    else Toast.makeText(getContext(), "Permission GPS refusée", Toast.LENGTH_SHORT).show();
                }
        );
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

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        chipRoot = view;

        // Chargement initial : photos publiques (ou filtré si query passée via passerelle)
        String prefillQuery = getArguments() != null ? getArguments().getString(ARG_SEARCH_QUERY, "") : "";
        if (!prefillQuery.isEmpty()) {
            EditText etSearch = view.findViewById(R.id.et_search_query);
            etSearch.setText(prefillQuery);
            observeSource(viewModel.searchPhotos(prefillQuery));
        } else {
            observeSource(viewModel.getPublicPhotos());
        }
        setActiveChip(R.id.chip_all);

        // ── Recherche texte ────────────────────────────────────────────────
        EditText etSearch = view.findViewById(R.id.et_search_query);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    observeSource(viewModel.getPublicPhotos());
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
                    observeSource(viewModel.getPublicPhotos());
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
        setupChip(view, R.id.chip_all, null);
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

        // ── Flux aléatoire ─────────────────────────────────────────────────
        view.findViewById(R.id.btn_random_feed).setOnClickListener(v -> {
            setActiveChip(-1); // aucun chip sélectionné
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

    /** Branche un chip sur une requête (null = toutes les photos publiques) */
    private void setupChip(View root, int chipId, @Nullable String category) {
        TextView chip = root.findViewById(chipId);
        chip.setOnClickListener(v -> {
            setActiveChip(chipId);
            LiveData<List<Photo>> source = (category == null)
                    ? viewModel.getPublicPhotos()
                    : viewModel.getPhotosByCategory(category);
            observeSource(source);
        });
    }

    /** Met le chip sélectionné en noir (bg_pill_solid) et réinitialise les autres */
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

    /** Récupère la position GPS et filtre les photos à moins de 50 km */
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
                observeSource(viewModel.getPublicPhotos());
                setActiveChip(R.id.chip_all);
            }
        } catch (SecurityException e) {
            Toast.makeText(getContext(), "Permission GPS manquante", Toast.LENGTH_SHORT).show();
        }
    }

    /** Détache l'ancienne source et observe la nouvelle */
    private void observeSource(LiveData<List<Photo>> newSource) {
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
                etSearch.setText(query); // déclenche le TextWatcher → recherche automatique
                Toast.makeText(getContext(), "Recherche : " + query, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
