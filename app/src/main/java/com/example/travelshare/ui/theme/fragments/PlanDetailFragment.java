package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.PlanStep;
import com.example.travelshare.data.models.TravelPlan;
import com.example.travelshare.viewmodels.TravelPathViewModel;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PlanDetailFragment extends Fragment {

    private static final String ARG_PLAN_ID = "plan_id";

    public static PlanDetailFragment newInstance(long planId) {
        PlanDetailFragment f = new PlanDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAN_ID, planId);
        f.setArguments(args);
        return f;
    }

    private TravelPathViewModel viewModel;
    private MapView mapView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_plan_detail, container, false);

        long planId = getArguments() != null ? getArguments().getLong(ARG_PLAN_ID, -1) : -1;
        viewModel = new ViewModelProvider(this).get(TravelPathViewModel.class);

        // ── Carte ──────────────────────────────────────────────────────────
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        mapView = view.findViewById(R.id.map_plan);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(13.0);

        view.findViewById(R.id.btn_pd_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        TextView tvTitle    = view.findViewById(R.id.tv_pd_title);
        TextView tvBudget   = view.findViewById(R.id.tv_pd_budget);
        TextView tvDuration = view.findViewById(R.id.tv_pd_duration);
        TextView tvEffort   = view.findViewById(R.id.tv_pd_effort);
        android.widget.Button btnLike = view.findViewById(R.id.btn_pd_like);

        // ── Charger le plan ────────────────────────────────────────────────
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            TravelPlan plan = db.travelPlanDao().getPlanById(planId);
            if (plan == null || !isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                String typeLabel = "equilibre".equals(plan.type) ? "Équilibré"
                        : "economique".equals(plan.type) ? "Économique" : "Confort";
                tvTitle.setText(typeLabel + " — " + plan.city);
                tvBudget.setText(plan.budgetEur + " €");
                tvDuration.setText(plan.durationHours + " h");
                tvEffort.setText(plan.effort);
                btnLike.setText(plan.liked ? "♥ Sauvegardé" : "♡ J'aime et sauvegarder");

                btnLike.setOnClickListener(v -> {
                    plan.liked = !plan.liked;
                    plan.saved = plan.liked;
                    viewModel.saveLikeAndSave(plan);
                    btnLike.setText(plan.liked ? "♥ Sauvegardé" : "♡ J'aime et sauvegarder");
                    Toast.makeText(getContext(),
                            plan.liked ? "Parcours sauvegardé !" : "Retrait du like",
                            Toast.LENGTH_SHORT).show();
                });

                // Lancer la météo pour la ville du plan
                fetchWeather(view, plan.city);
            });
        });

        // ── Étapes ─────────────────────────────────────────────────────────
        RecyclerView rv = view.findViewById(R.id.rv_plan_steps);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        StepAdapter stepAdapter = new StepAdapter(this);
        rv.setAdapter(stepAdapter);

        viewModel.getStepsForPlan(planId).observe(getViewLifecycleOwner(), steps -> {
            stepAdapter.setSteps(steps);
            addMapMarkers(steps);
            // Charger les photos TravelShare pour chaque étape
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                java.util.Map<Long, List<com.example.travelshare.data.models.Photo>> map = new java.util.HashMap<>();
                for (PlanStep s : steps) {
                    String keyword = s.type != null ? s.type : s.name;
                    List<com.example.travelshare.data.models.Photo> photos = db.photoDao().searchPhotosSync(keyword);
                    if (!photos.isEmpty()) map.put(s.id, photos);
                }
                if (isAdded()) requireActivity().runOnUiThread(() -> stepAdapter.setPhotosMap(map));
            });
        });

        // ── Export PDF ─────────────────────────────────────────────────────
        view.findViewById(R.id.btn_pd_export_pdf).setOnClickListener(v ->
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                TravelPlan plan = db.travelPlanDao().getPlanById(planId);
                List<PlanStep> steps = db.planStepDao().getStepsForPlanSync(planId);
                if (plan == null || !isAdded()) return;
                exportToPdf(plan, steps);
            })
        );

        // ── Partager ───────────────────────────────────────────────────────
        view.findViewById(R.id.btn_pd_share).setOnClickListener(v ->
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                TravelPlan plan = db.travelPlanDao().getPlanById(planId);
                List<PlanStep> steps = db.planStepDao().getStepsForPlanSync(planId);
                if (plan == null || !isAdded()) return;
                StringBuilder sb = new StringBuilder();
                sb.append("Mon parcours TravelPath à ").append(plan.city).append("\n\n");
                for (PlanStep s : steps) {
                    sb.append("[").append(s.timeSlot).append("] ")
                      .append(s.name).append(" — ")
                      .append(s.costEur == 0 ? "Gratuit" : s.costEur + "€").append("\n");
                }
                sb.append("\nBudget total : ").append(plan.budgetEur).append(" €");
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                requireActivity().runOnUiThread(() ->
                        startActivity(Intent.createChooser(shareIntent, "Partager le parcours")));
            })
        );

        // ── Regénérer ──────────────────────────────────────────────────────
        view.findViewById(R.id.btn_pd_regenerate).setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
            Toast.makeText(getContext(), "Modifiez vos préférences et regénérez", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    // ── Météo : géocode la ville via Nominatim puis appelle OpenMeteo ──────

    private void fetchWeather(View view, String city) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 1. Géocode la ville
                String encoded = java.net.URLEncoder.encode(city, "UTF-8");
                java.net.URL geoUrl = new java.net.URL(
                        "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1");
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) geoUrl.openConnection();
                con.setRequestProperty("User-Agent", requireContext().getPackageName());
                con.setConnectTimeout(6000); con.setReadTimeout(6000);
                String geoJson = readResponse(con);
                if (geoJson == null || geoJson.equals("[]") || !geoJson.contains("\"lat\"")) return;

                double lat = parseJsonDouble(geoJson, "\"lat\":\"");
                double lng = parseJsonDouble(geoJson, "\"lon\":\"");

                // 2. Météo via OpenMeteo (pas de clé API)
                String weatherUrl = String.format(java.util.Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&current_weather=true&hourly=precipitation_probability&timezone=auto",
                        lat, lng);
                java.net.HttpURLConnection wCon = (java.net.HttpURLConnection)
                        new java.net.URL(weatherUrl).openConnection();
                wCon.setConnectTimeout(6000); wCon.setReadTimeout(6000);
                String wJson = readResponse(wCon);
                if (wJson == null || !wJson.contains("current_weather")) return;

                double temp      = parseJsonDouble(wJson, "\"temperature\":");
                double windspeed = parseJsonDouble(wJson, "\"windspeed\":");
                int weatherCode  = (int) parseJsonDouble(wJson, "\"weathercode\":");

                String icon, desc, advice;
                if (weatherCode == 0) {
                    icon = "☀️"; desc = "Ciel dégagé"; advice = "Parfait pour visiter !";
                } else if (weatherCode <= 3) {
                    icon = "⛅"; desc = "Partiellement nuageux"; advice = "Bonne journée de visite.";
                } else if (weatherCode <= 49) {
                    icon = "🌫️"; desc = "Brouillard / brume"; advice = "Visibilité réduite.";
                } else if (weatherCode <= 67) {
                    icon = "🌧️"; desc = "Pluie"; advice = "Prévoyez un imperméable.";
                } else if (weatherCode <= 77) {
                    icon = "❄️"; desc = "Neige"; advice = "Habillez-vous chaudement !";
                } else if (weatherCode <= 82) {
                    icon = "🌦️"; desc = "Averses"; advice = "Prévoyez un parapluie.";
                } else {
                    icon = "⛈️"; desc = "Orage"; advice = "Privilégiez les activités intérieures.";
                }

                final String fIcon = icon, fDesc = desc, fAdvice = advice;
                final String fTemp = String.format(java.util.Locale.getDefault(), "%.0f°C", temp);

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    view.findViewById(R.id.layout_weather).setVisibility(View.VISIBLE);
                    ((TextView) view.findViewById(R.id.tv_weather_icon)).setText(fIcon);
                    ((TextView) view.findViewById(R.id.tv_weather_desc)).setText(fDesc);
                    ((TextView) view.findViewById(R.id.tv_weather_advice)).setText(fAdvice);
                    ((TextView) view.findViewById(R.id.tv_weather_temp)).setText(fTemp);
                });
            } catch (Exception ignored) {}
        });
    }

    // ── Export PDF avec android.graphics.pdf.PdfDocument ──────────────────

    private void exportToPdf(TravelPlan plan, List<PlanStep> steps) {
        if (!isAdded()) return;
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.parseColor("#1B3A5C"));
        titlePaint.setTextSize(22f);
        titlePaint.setFakeBoldText(true);

        Paint subPaint = new Paint();
        subPaint.setColor(Color.parseColor("#555555"));
        subPaint.setTextSize(13f);

        Paint stepPaint = new Paint();
        stepPaint.setColor(Color.BLACK);
        stepPaint.setTextSize(12f);

        Paint accentPaint = new Paint();
        accentPaint.setColor(Color.parseColor("#1B8C82"));
        accentPaint.setTextSize(11f);

        String typeLabel = "equilibre".equals(plan.type) ? "Équilibré"
                : "economique".equals(plan.type) ? "Économique" : "Confort";

        int y = 60;
        canvas.drawText("TravelPath — Parcours " + typeLabel, 40, y, titlePaint);
        y += 30;
        canvas.drawText("Destination : " + plan.city, 40, y, subPaint);
        y += 20;
        canvas.drawText("Budget : " + plan.budgetEur + " €   |   Durée : " + plan.durationHours
                + " h   |   Effort : " + plan.effort, 40, y, subPaint);
        y += 30;

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        canvas.drawLine(40, y, 555, y, linePaint);
        y += 20;

        for (PlanStep s : steps) {
            if (y > 780) break; // page overflow
            canvas.drawText("[" + s.timeSlot + "]  " + s.name, 40, y, stepPaint);
            y += 18;
            canvas.drawText(s.description, 55, y, accentPaint);
            y += 16;
            String cost = s.costEur == 0 ? "Gratuit" : s.costEur + " €";
            canvas.drawText(s.durationMin + " min  ·  " + cost, 55, y, accentPaint);
            y += 24;
        }

        y += 10;
        canvas.drawLine(40, y, 555, y, linePaint);
        y += 20;
        subPaint.setTextSize(11f);
        canvas.drawText("Généré par TravelPath — Traveling", 40, y, subPaint);

        document.finishPage(page);

        try {
            File dir = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "TravelPath");
            dir.mkdirs();
            File file = new File(dir, "parcours_" + plan.city.replaceAll("\\s+", "_") + ".pdf");
            document.writeTo(new FileOutputStream(file));
            document.close();

            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            requireActivity().runOnUiThread(() -> {
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "PDF sauvegardé dans Documents/TravelPath/", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Erreur export PDF", Toast.LENGTH_SHORT).show());
        }
    }

    // ── Helpers HTTP / JSON ────────────────────────────────────────────────

    private String readResponse(java.net.HttpURLConnection con) {
        try {
            if (con.getResponseCode() != 200) return null;
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private double parseJsonDouble(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        int start = idx + key.length();
        // skip opening quote if any
        if (start < json.length() && json.charAt(start) == '"') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        try { return Double.parseDouble(json.substring(start, end)); } catch (Exception e) { return 0; }
    }

    // ── Carte ──────────────────────────────────────────────────────────────

    private void addMapMarkers(List<PlanStep> steps) {
        if (mapView == null) return;
        mapView.getOverlays().clear();
        GeoPoint firstPoint = null;
        for (PlanStep step : steps) {
            if (step.lat == 0 && step.lng == 0) continue;
            GeoPoint point = new GeoPoint(step.lat, step.lng);
            if (firstPoint == null) firstPoint = point;
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(step.name);
            marker.setSnippet(step.timeSlot + " · " + step.durationMin + " min");
            mapView.getOverlays().add(marker);
        }
        if (firstPoint != null) mapView.getController().setCenter(firstPoint);
        mapView.invalidate();
    }

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }

    // ── Adapter étapes ────────────────────────────────────────────────────

    static class StepAdapter extends RecyclerView.Adapter<StepAdapter.SVH> {
        private List<PlanStep> steps = new ArrayList<>();
        private java.util.Map<Long, List<com.example.travelshare.data.models.Photo>> photosMap = new java.util.HashMap<>();
        private Fragment fragment;

        StepAdapter(Fragment f) { this.fragment = f; }

        static class SVH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvSlot, tvDuration, tvName, tvDesc, tvCost, tvSeePhotos;
            RecyclerView rvPhotos;
            SVH(View v) {
                super(v);
                tvIcon      = v.findViewById(R.id.tv_step_icon);
                tvSlot      = v.findViewById(R.id.tv_step_slot);
                tvDuration  = v.findViewById(R.id.tv_step_duration);
                tvName      = v.findViewById(R.id.tv_step_name);
                tvDesc      = v.findViewById(R.id.tv_step_description);
                tvCost      = v.findViewById(R.id.tv_step_cost);
                tvSeePhotos = v.findViewById(R.id.tv_step_see_photos);
                rvPhotos    = v.findViewById(R.id.rv_step_photos);
            }
        }

        void setPhotosMap(java.util.Map<Long, List<com.example.travelshare.data.models.Photo>> map) {
            this.photosMap = map;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public SVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SVH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_plan_step, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SVH h, int position) {
            PlanStep s = steps.get(position);
            String icon;
            switch (s.type != null ? s.type : "") {
                case "Culture":      icon = "🏛"; break;
                case "Restauration": icon = "🍽"; break;
                case "Loisirs":      icon = "🎡"; break;
                case "Découverte":   icon = "🗺"; break;
                case "Shopping":     icon = "🛍"; break;
                default:             icon = "📍";
            }
            h.tvIcon.setText(icon);
            h.tvSlot.setText(s.timeSlot);
            h.tvDuration.setText(s.durationMin + " min");
            h.tvName.setText(s.name);
            h.tvDesc.setText(s.description);
            h.tvCost.setText(s.costEur == 0 ? "Gratuit" : s.costEur + " €");

            // Mini-galerie photos liées à cette étape
            List<com.example.travelshare.data.models.Photo> stepPhotos = photosMap.get(s.id);
            if (stepPhotos != null && !stepPhotos.isEmpty()) {
                h.rvPhotos.setVisibility(View.VISIBLE);
                h.rvPhotos.setLayoutManager(new LinearLayoutManager(
                        h.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
                h.rvPhotos.setAdapter(new PhotoMiniAdapter(stepPhotos));
                h.tvSeePhotos.setVisibility(View.GONE);
            } else {
                h.rvPhotos.setVisibility(View.GONE);
                h.tvSeePhotos.setVisibility(View.VISIBLE);
            }

            // Passerelle → TravelShare : ouvrir l'Explorer filtré sur le lieu de l'étape
            h.tvSeePhotos.setOnClickListener(v -> {
                ExplorerFragment explorer = new ExplorerFragment();
                Bundle args = new Bundle();
                args.putString(ExplorerFragment.ARG_SEARCH_QUERY, s.name);
                explorer.setArguments(args);
                fragment.requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, explorer)
                        .addToBackStack(null)
                        .commit();
            });
        }

        @Override public int getItemCount() { return steps.size(); }

        void setSteps(List<PlanStep> list) {
            this.steps = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }

    // ── Adapter mini-galerie photos ────────────────────────────────────────

    static class PhotoMiniAdapter extends RecyclerView.Adapter<PhotoMiniAdapter.PMVH> {
        private final List<com.example.travelshare.data.models.Photo> photos;

        PhotoMiniAdapter(List<com.example.travelshare.data.models.Photo> photos) {
            this.photos = photos;
        }

        static class PMVH extends RecyclerView.ViewHolder {
            ImageView imageView;
            PMVH(ImageView iv) {
                super(iv);
                this.imageView = iv;
            }
        }

        @NonNull @Override
        public PMVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            int size = (int) (80 * parent.getContext().getResources().getDisplayMetrics().density);
            int margin = (int) (4 * parent.getContext().getResources().getDisplayMetrics().density);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
            lp.setMargins(0, 0, margin, 0);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new PMVH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull PMVH h, int position) {
            com.example.travelshare.data.models.Photo photo = photos.get(position);
            String uri = photo.getImageUri();
            if (uri != null && !uri.isEmpty()) {
                Glide.with(h.imageView.getContext())
                        .load(Uri.parse(uri))
                        .centerCrop()
                        .into(h.imageView);
            } else {
                h.imageView.setImageDrawable(null);
                h.imageView.setBackgroundColor(Color.LTGRAY);
            }
        }

        @Override public int getItemCount() { return photos.size(); }
    }
}
