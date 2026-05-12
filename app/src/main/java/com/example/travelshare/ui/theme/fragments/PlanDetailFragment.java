package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private TravelPlan currentPlan;
    private Polyline routeOverlay;
    private View blockRouteTime;
    private TextView tvRouteTime;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_plan_detail, container, false);

        long planId = getArguments() != null ? getArguments().getLong(ARG_PLAN_ID, -1) : -1;
        viewModel = new ViewModelProvider(this).get(TravelPathViewModel.class);

        // ── Carte ──────────────────────────────────────────────────────────
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        mapView       = view.findViewById(R.id.map_plan);
        blockRouteTime = view.findViewById(R.id.block_route_time);
        tvRouteTime    = view.findViewById(R.id.tv_pd_route_time);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(13.0);

        // Permettre le déplacement/zoom sur la carte sans que le ScrollView intercepte
        mapView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                case android.view.MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });

        view.findViewById(R.id.btn_pd_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        TextView tvTitle    = view.findViewById(R.id.tv_pd_title);
        TextView tvBudget   = view.findViewById(R.id.tv_pd_budget);
        TextView tvDuration = view.findViewById(R.id.tv_pd_duration);
        TextView tvEffort   = view.findViewById(R.id.tv_pd_effort);
        android.widget.Button btnLike = view.findViewById(R.id.btn_pd_like);
        android.widget.Button btnRegen = view.findViewById(R.id.btn_pd_regenerate);

        // ── Charger le plan ────────────────────────────────────────────────
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            TravelPlan plan = db.travelPlanDao().getPlanById(planId);
            if (plan == null || !isAdded()) return;
            currentPlan = plan;

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

                // Régénérer : retourne au formulaire pré-rempli avec les mêmes paramètres
                btnRegen.setOnClickListener(v -> {
                    TravelPathFragment tpFragment = TravelPathFragment.newInstanceForRegen(plan);
                    requireActivity().getSupportFragmentManager().popBackStack();
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, tpFragment)
                            .addToBackStack(null)
                            .commit();
                    Toast.makeText(getContext(),
                            "Modifiez vos préférences et régénérez", Toast.LENGTH_SHORT).show();
                });

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
            addMarkersAndRoute(steps);
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
                      .append(s.costEur == 0 ? "Gratuit" : s.costEur + "€");
                    sb.append("\n");
                }
                sb.append("\nBudget total : ").append(plan.budgetEur).append(" €");
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                requireActivity().runOnUiThread(() ->
                        startActivity(Intent.createChooser(shareIntent, "Partager le parcours")));
            })
        );

        return view;
    }

    // ── Marqueurs + route GPS ──────────────────────────────────────────────

    private void addMarkersAndRoute(List<PlanStep> steps) {
        if (mapView == null) return;
        mapView.getOverlays().clear();

        // Remettre la route si déjà calculée
        if (routeOverlay != null) {
            mapView.getOverlays().add(0, routeOverlay);
        }

        List<PlanStep> validSteps = new ArrayList<>();
        List<GeoPoint> allPoints  = new ArrayList<>();

        for (PlanStep step : steps) {
            if (step.lat == 0 && step.lng == 0) continue;
            validSteps.add(step);
            GeoPoint pt = new GeoPoint(step.lat, step.lng);
            allPoints.add(pt);

            Marker marker = new Marker(mapView);
            marker.setPosition(pt);
            marker.setTitle(step.name);
            marker.setSnippet(step.timeSlot + " · " + step.durationMin + " min");
            mapView.getOverlays().add(marker);
        }

        // Auto-zoom pour montrer tous les marqueurs
        if (allPoints.size() > 1) {
            double north = -90, south = 90, east = -180, west = 180;
            for (GeoPoint p : allPoints) {
                if (p.getLatitude()  > north) north = p.getLatitude();
                if (p.getLatitude()  < south) south = p.getLatitude();
                if (p.getLongitude() > east)  east  = p.getLongitude();
                if (p.getLongitude() < west)  west  = p.getLongitude();
            }
            final double fN = north, fS = south, fE = east, fW = west;
            mapView.post(() -> {
                try {
                    mapView.zoomToBoundingBox(
                            new org.osmdroid.util.BoundingBox(fN, fE, fS, fW), true);
                } catch (Exception ignored) {}
            });
        } else if (!allPoints.isEmpty()) {
            mapView.getController().setCenter(allPoints.get(0));
        } else if (currentPlan != null) {
            // Aucun pin valide → centrer sur la ville du plan
            final String city = currentPlan.city;
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    String enc = java.net.URLEncoder.encode(city, "UTF-8");
                    java.net.URL url = new java.net.URL(
                            "https://nominatim.openstreetmap.org/search?q=" + enc + "&format=json&limit=1");
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
                    con.setRequestProperty("User-Agent", requireContext().getPackageName());
                    con.setConnectTimeout(5000); con.setReadTimeout(5000);
                    if (con.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(con.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        String json = sb.toString();
                        if (json.contains("\"lat\"")) {
                            int li = json.indexOf("\"lat\":\"") + 7;
                            double lat = Double.parseDouble(json.substring(li, json.indexOf("\"", li)));
                            int loi = json.indexOf("\"lon\":\"") + 7;
                            double lon = Double.parseDouble(json.substring(loi, json.indexOf("\"", loi)));
                            final GeoPoint cityPt = new GeoPoint(lat, lon);
                            if (isAdded()) requireActivity().runOnUiThread(() -> {
                                mapView.getController().setZoom(14.0);
                                mapView.getController().setCenter(cityPt);
                                mapView.invalidate();
                            });
                        }
                    }
                } catch (Exception ignored) {}
            });
        }

        mapView.invalidate();

        // Lancer la route OSRM si pas encore calculée
        if (routeOverlay == null && validSteps.size() >= 2) {
            fetchOsrmRoute(validSteps);
        }
    }

    /** Appelle OSRM (gratuit, open-source) pour obtenir l'itinéraire routier entre les étapes. */
    private void fetchOsrmRoute(List<PlanStep> steps) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                StringBuilder coords = new StringBuilder();
                for (int i = 0; i < steps.size(); i++) {
                    if (i > 0) coords.append(";");
                    coords.append(String.format(Locale.US, "%.6f,%.6f",
                            steps.get(i).lng, steps.get(i).lat));
                }
                String osrmUrl = "https://router.project-osrm.org/route/v1/driving/"
                        + coords + "?overview=full&geometries=geojson";

                HttpURLConnection conn = (HttpURLConnection) new URL(osrmUrl).openConnection();
                conn.setRequestProperty("User-Agent", "TravelingApp/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                List<GeoPoint> routePoints = null;
                double routeDurationSeconds = -1;

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                    reader.close();

                    JSONObject root = new JSONObject(json.toString());
                    if ("Ok".equals(root.optString("code"))) {
                        JSONArray routes = root.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);
                            routeDurationSeconds = route.optDouble("duration", -1);
                            JSONArray geoCoords = route.getJSONObject("geometry")
                                    .getJSONArray("coordinates");
                            routePoints = new ArrayList<>();
                            for (int i = 0; i < geoCoords.length(); i++) {
                                JSONArray pt = geoCoords.getJSONArray(i);
                                routePoints.add(new GeoPoint(pt.getDouble(1), pt.getDouble(0)));
                            }
                        }
                    }
                }

                if (routePoints == null || routePoints.isEmpty()) {
                    routePoints = new ArrayList<>();
                    for (PlanStep s : steps) routePoints.add(new GeoPoint(s.lat, s.lng));
                }

                final List<GeoPoint> finalPoints = routePoints;
                final String routeTimeLabel = formatRouteDuration(routeDurationSeconds);

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        drawRoutePolyline(finalPoints);
                        if (routeTimeLabel != null && blockRouteTime != null) {
                            blockRouteTime.setVisibility(View.VISIBLE);
                            tvRouteTime.setText(routeTimeLabel);
                        }
                    });
                }

            } catch (Exception e) {
                List<GeoPoint> fallback = new ArrayList<>();
                for (PlanStep s : steps) fallback.add(new GeoPoint(s.lat, s.lng));
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> drawRoutePolyline(fallback));
                }
            }
        });
    }

    /** Reverse geocoding Nominatim — retourne "15 Rue de Rivoli, 75001 Paris" ou null. */
    private static String reverseGeocode(double lat, double lng) {
        try {
            String url = String.format(java.util.Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json&zoom=18",
                    lat, lng);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            con.setRequestProperty("User-Agent", "TravelingApp/1.0");
            con.setConnectTimeout(5000); con.setReadTimeout(5000);
            if (con.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String json = sb.toString();
                // Extraire numéro + rue + code postal + ville
                String num  = extractTag(json, "house_number");
                String road = extractTag(json, "road");
                String post = extractTag(json, "postcode");
                String city = extractTag(json, "city");
                if (city == null) city = extractTag(json, "town");
                if (city == null) city = extractTag(json, "village");
                StringBuilder addr = new StringBuilder();
                if (num != null)  addr.append(num).append(" ");
                if (road != null) addr.append(road);
                if (post != null || city != null) {
                    if (addr.length() > 0) addr.append(", ");
                    if (post != null) addr.append(post).append(" ");
                    if (city != null) addr.append(city);
                }
                String result = addr.toString().trim();
                return result.isEmpty() ? null : result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractTag(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        int end = json.indexOf("\"", idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }

    private String formatRouteDuration(double seconds) {
        if (seconds <= 0) return null;
        int totalMin = (int) Math.round(seconds / 60.0);
        if (totalMin < 60) return totalMin + " min";
        int h = totalMin / 60;
        int m = totalMin % 60;
        return m > 0 ? h + "h" + String.format(Locale.US, "%02d", m) : h + "h";
    }

    /** Dessine la polyline de l'itinéraire sous les marqueurs. */
    private void drawRoutePolyline(List<GeoPoint> points) {
        if (mapView == null || points.size() < 2) return;

        routeOverlay = new Polyline(mapView);
        routeOverlay.setPoints(points);
        routeOverlay.getOutlinePaint().setColor(Color.parseColor("#1B3A5C"));
        routeOverlay.getOutlinePaint().setStrokeWidth(9f);
        routeOverlay.getOutlinePaint().setAlpha(210);
        routeOverlay.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        routeOverlay.getOutlinePaint().setStrokeJoin(Paint.Join.ROUND);

        // Insérer en index 0 → dessiné sous les marqueurs
        mapView.getOverlays().add(0, routeOverlay);
        mapView.invalidate();
    }

    // ── Météo ──────────────────────────────────────────────────────────────

    private void fetchWeather(View view, String city) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
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

                String weatherUrl = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current_weather=true&hourly=precipitation_probability&timezone=auto",
                        lat, lng);
                java.net.HttpURLConnection wCon = (java.net.HttpURLConnection)
                        new java.net.URL(weatherUrl).openConnection();
                wCon.setConnectTimeout(6000); wCon.setReadTimeout(6000);
                String wJson = readResponse(wCon);
                if (wJson == null || !wJson.contains("current_weather")) return;

                double temp      = parseJsonDouble(wJson, "\"temperature\":");
                int weatherCode  = (int) parseJsonDouble(wJson, "\"weathercode\":");

                String icon, desc, advice;
                if (weatherCode == 0)          { icon = "☀️"; desc = "Ciel dégagé";         advice = "Parfait pour visiter !"; }
                else if (weatherCode <= 3)     { icon = "⛅"; desc = "Partiellement nuageux"; advice = "Bonne journée de visite."; }
                else if (weatherCode <= 49)    { icon = "🌫️"; desc = "Brouillard";            advice = "Visibilité réduite."; }
                else if (weatherCode <= 67)    { icon = "🌧️"; desc = "Pluie";                 advice = "Prévoyez un imperméable."; }
                else if (weatherCode <= 77)    { icon = "❄️"; desc = "Neige";                 advice = "Habillez-vous chaudement !"; }
                else if (weatherCode <= 82)    { icon = "🌦️"; desc = "Averses";               advice = "Prévoyez un parapluie."; }
                else                           { icon = "⛈️"; desc = "Orage";                 advice = "Privilégiez les activités intérieures."; }

                final String fIcon = icon, fDesc = desc, fAdvice = advice;
                final String fTemp = String.format(Locale.getDefault(), "%.0f°C", temp);

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

    // ── Export PDF ─────────────────────────────────────────────────────────

    private void exportToPdf(TravelPlan plan, List<PlanStep> steps) {
        if (!isAdded()) return;
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.parseColor("#1B3A5C"));
        titlePaint.setTextSize(22f); titlePaint.setFakeBoldText(true);
        Paint subPaint = new Paint();
        subPaint.setColor(Color.parseColor("#555555")); subPaint.setTextSize(13f);
        Paint stepPaint = new Paint();
        stepPaint.setColor(Color.BLACK); stepPaint.setTextSize(12f);
        Paint accentPaint = new Paint();
        accentPaint.setColor(Color.parseColor("#1B8C82")); accentPaint.setTextSize(11f);
        Paint linePaint = new Paint(); linePaint.setColor(Color.LTGRAY);

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
        canvas.drawLine(40, y, 555, y, linePaint); y += 20;

        for (PlanStep s : steps) {
            if (y > 760) break;
            canvas.drawText("[" + s.timeSlot + "]  " + s.name, 40, y, stepPaint); y += 18;
            canvas.drawText(s.description, 55, y, accentPaint); y += 16;
            String cost = s.costEur == 0 ? "Gratuit" : s.costEur + " €";
            canvas.drawText(s.durationMin + " min  ·  " + cost, 55, y, accentPaint); y += 14;
            y += 8;
        }

        y += 10; canvas.drawLine(40, y, 555, y, linePaint); y += 20;
        subPaint.setTextSize(11f);
        canvas.drawText("Généré par TravelPath — Traveling", 40, y, subPaint);
        document.finishPage(page);

        try {
            File dir = new File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "TravelPath");
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
                try { startActivity(intent); }
                catch (Exception e) {
                    Toast.makeText(getContext(), "PDF sauvegardé dans Documents/TravelPath/", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Erreur export PDF", Toast.LENGTH_SHORT).show());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String readResponse(java.net.HttpURLConnection con) {
        try {
            if (con.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close(); return sb.toString();
        } catch (Exception e) { return null; }
    }

    private double parseJsonDouble(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        int start = idx + key.length();
        if (start < json.length() && json.charAt(start) == '"') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end))
                || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        try { return Double.parseDouble(json.substring(start, end)); } catch (Exception e) { return 0; }
    }

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }

    // ── Adapter étapes ─────────────────────────────────────────────────────

    static class StepAdapter extends RecyclerView.Adapter<StepAdapter.SVH> {
        private List<PlanStep> steps = new ArrayList<>();
        private java.util.Map<Long, List<com.example.travelshare.data.models.Photo>> photosMap = new java.util.HashMap<>();
        private final Fragment fragment;

        StepAdapter(Fragment f) { this.fragment = f; }

        static class SVH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvSlot, tvDuration, tvName, tvAddress, tvDesc, tvCost, tvSeePhotos, tvHours, tvVideo;
            RecyclerView rvPhotos;
            SVH(View v) {
                super(v);
                tvIcon      = v.findViewById(R.id.tv_step_icon);
                tvSlot      = v.findViewById(R.id.tv_step_slot);
                tvDuration  = v.findViewById(R.id.tv_step_duration);
                tvName      = v.findViewById(R.id.tv_step_name);
                tvAddress   = v.findViewById(R.id.tv_step_address);
                tvDesc      = v.findViewById(R.id.tv_step_description);
                tvCost      = v.findViewById(R.id.tv_step_cost);
                tvSeePhotos = v.findViewById(R.id.tv_step_see_photos);
                tvHours     = v.findViewById(R.id.tv_step_opening_hours);
                tvVideo     = v.findViewById(R.id.tv_step_video);
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

            // ── Adresse ───────────────────────────────────────────────────
            if (s.address != null && !s.address.isEmpty()) {
                h.tvAddress.setText("📍 " + s.address);
                h.tvAddress.setVisibility(View.VISIBLE);
            } else if (s.lat != 0 && s.lng != 0) {
                // Lazy reverse geocoding si pas d'adresse mais coordonnées valides
                h.tvAddress.setText("📍 Chargement...");
                h.tvAddress.setVisibility(View.VISIBLE);
                final long stepId = s.id;
                final TextView tvAddr = h.tvAddress;
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    String addr = reverseGeocode(s.lat, s.lng);
                    AppDatabase db = AppDatabase.getInstance(h.itemView.getContext());
                    if (addr != null && !addr.isEmpty()) {
                        db.planStepDao().updateAddress(stepId, addr);
                    }
                    final String display = (addr != null && !addr.isEmpty()) ? addr : "";
                    android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
                    main.post(() -> {
                        if (display.isEmpty()) {
                            tvAddr.setVisibility(View.GONE);
                        } else {
                            tvAddr.setText("📍 " + display);
                        }
                    });
                });
            } else {
                h.tvAddress.setVisibility(View.GONE);
            }

            h.tvDesc.setText(s.description);
            h.tvCost.setText(s.costEur == 0 ? "Gratuit" : s.costEur + " €");
            h.tvHours.setText(openingHoursFor(s.type));

            h.rvPhotos.setVisibility(View.GONE);
            h.tvSeePhotos.setVisibility(View.VISIBLE);

            // Bouton vidéo : ouvre YouTube avec le nom de l'étape
            if (h.tvVideo != null) {
                h.tvVideo.setOnClickListener(v -> {
                    String query = Uri.encode(s.name + " " + (s.type != null ? s.type : ""));
                    Intent yt = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/results?search_query=" + query));
                    h.itemView.getContext().startActivity(yt);
                });
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

        static String openingHoursFor(String type) {
            if (type == null) return "🕐 Horaires variables";
            switch (type) {
                case "Culture":      return "🕐 Mar–Dim 10h–18h (fermé Lun)";
                case "Restauration": return "🕐 Lun–Dim 12h–14h30 · 19h–22h";
                case "Loisirs":      return "🕐 Lun–Dim 9h–21h";
                case "Découverte":   return "🕐 Accès libre 24h/24";
                case "Shopping":     return "🕐 Lun–Sam 10h–19h";
                default:             return "🕐 Horaires variables";
            }
        }

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
            PMVH(ImageView iv) { super(iv); this.imageView = iv; }
        }

        @NonNull @Override
        public PMVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            int size   = (int) (80 * parent.getContext().getResources().getDisplayMetrics().density);
            int margin = (int) (4  * parent.getContext().getResources().getDisplayMetrics().density);
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
                Glide.with(h.imageView.getContext()).load(Uri.parse(uri)).centerCrop().into(h.imageView);
            } else {
                h.imageView.setImageDrawable(null);
                h.imageView.setBackgroundColor(Color.LTGRAY);
            }
        }

        @Override public int getItemCount() { return photos.size(); }
    }
}
