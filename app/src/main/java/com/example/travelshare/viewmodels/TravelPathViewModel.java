package com.example.travelshare.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.dao.PlanStepDao;
import com.example.travelshare.data.dao.TravelPlanDao;
import com.example.travelshare.data.models.PlanStep;
import com.example.travelshare.data.models.TravelPlan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TravelPathViewModel extends AndroidViewModel {

    private final TravelPlanDao planDao;
    private final PlanStepDao stepDao;

    public TravelPathViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        planDao = db.travelPlanDao();
        stepDao = db.planStepDao();
    }

    public LiveData<List<TravelPlan>> getPlansForUser(long userId) {
        return planDao.getPlansForUser(userId);
    }

    public LiveData<List<TravelPlan>> getSavedPlansForUser(long userId) {
        return planDao.getSavedPlansForUser(userId);
    }

    public LiveData<List<PlanStep>> getStepsForPlan(long planId) {
        return stepDao.getStepsForPlan(planId);
    }

    public void toggleLike(TravelPlan plan) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            plan.liked = !plan.liked;
            plan.saved = plan.liked;
            planDao.updatePlan(plan);
        });
    }

    public void saveLikeAndSave(TravelPlan plan) {
        AppDatabase.databaseWriteExecutor.execute(() -> planDao.updatePlan(plan));
    }

    public void deletePlan(long planId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            stepDao.deleteStepsForPlan(planId);
            planDao.deletePlan(planId);
        });
    }

    /**
     * Génère 3 parcours (économique, équilibré, confort) et les insère en base.
     * Callback appelé sur le thread appelant avec les IDs insérés.
     */
    public void generatePlans(long userId, String city, Set<String> activities,
                               int budgetMax, int durationHours, String effort,
                               String requiredPlaces, Set<String> weatherTolerances,
                               Runnable onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            StringBuilder actSb = new StringBuilder();
            for (String a : activities) { if (actSb.length() > 0) actSb.append(","); actSb.append(a); }
            String actStr = actSb.toString();

            StringBuilder wtSb = new StringBuilder();
            for (String w : weatherTolerances) { if (wtSb.length() > 0) wtSb.append(","); wtSb.append(w); }
            String wtStr = wtSb.toString();

            String[] types = {"economique", "equilibre", "confort"};
            for (String type : types) {
                List<PlanStep> steps = buildSteps(city, activities, type, durationHours, weatherTolerances);

                // Ajouter les lieux obligatoires
                if (requiredPlaces != null && !requiredPlaces.trim().isEmpty()) {
                    String[] places = requiredPlaces.split(",");
                    int slotIdx = steps.size();
                    String[] slots = {"Matin", "Après-midi", "Soir"};
                    for (String place : places) {
                        String p = place.trim();
                        if (p.isEmpty()) continue;
                        PlanStep custom = new PlanStep();
                        custom.stepOrder   = slotIdx;
                        custom.name        = p;
                        custom.type        = "Découverte";
                        custom.timeSlot    = slots[Math.min(slotIdx, slots.length - 1)];
                        custom.durationMin = 60;
                        custom.costEur     = 0;
                        custom.description = "Lieu obligatoire demandé.";
                        double[] coords = geocode(p + " " + city);
                        custom.lat = coords[0]; custom.lng = coords[1];
                        steps.add(custom);
                        slotIdx++;
                    }
                }

                int budget = estimateBudget(steps);
                if (budget > budgetMax && !type.equals("economique")) continue;

                // Géocoder chaque étape pour la carte
                geocodeSteps(steps, city);

                TravelPlan plan = new TravelPlan();
                plan.userId           = userId;
                plan.city             = city;
                plan.type             = type;
                plan.activities       = actStr;
                plan.budgetEur        = budget;
                plan.durationHours    = durationHours;
                plan.effort           = effort;
                plan.liked            = false;
                plan.saved            = false;
                plan.date             = date;
                plan.requiredPlaces   = requiredPlaces;
                plan.weatherTolerances = wtStr;

                long planId = planDao.insertPlan(plan);
                for (PlanStep step : steps) {
                    step.planId = planId;
                    stepDao.insertStep(step);
                }
            }
            if (onDone != null) onDone.run();
        });
    }

    /** Géocode chaque étape via Nominatim (1 req/s pour respecter les limites) */
    private void geocodeSteps(List<PlanStep> steps, String city) {
        for (PlanStep step : steps) {
            if (step.lat != 0 || step.lng != 0) continue;
            String keyword = typeToKeyword(step.type);
            double[] coords = geocode(keyword + " " + city);
            step.lat = coords[0];
            step.lng = coords[1];
            try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
        }
    }

    private String typeToKeyword(String type) {
        if (type == null) return "";
        switch (type) {
            case "Culture":      return "musée";
            case "Restauration": return "restaurant";
            case "Loisirs":      return "parc";
            case "Découverte":   return "monument";
            case "Shopping":     return "centre commercial";
            default:             return type;
        }
    }

    private double[] geocode(String query) {
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "TravelingApp/1.0");
            con.setConnectTimeout(5000); con.setReadTimeout(5000);
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
                    double lng = Double.parseDouble(json.substring(loi, json.indexOf("\"", loi)));
                    return new double[]{lat, lng};
                }
            }
        } catch (Exception ignored) {}
        return new double[]{0, 0};
    }

    // ── Génération des étapes ─────────────────────────────────────────────

    private List<PlanStep> buildSteps(String city, Set<String> activities,
                                      String type, int durationHours,
                                      Set<String> weatherTolerances) {
        List<PlanStep> steps = new ArrayList<>();
        int order = 0;

        // Slots horaires disponibles selon durée
        String[] slots = durationHours <= 3
                ? new String[]{"Matin"}
                : durationHours <= 6
                ? new String[]{"Matin", "Après-midi"}
                : new String[]{"Matin", "Après-midi", "Soir"};

        int slotIdx = 0;
        boolean sensitiveWeather = weatherTolerances != null && !weatherTolerances.isEmpty();

        for (String activity : activities) {
            if (slotIdx >= slots.length) break;
            String slot = slots[slotIdx];

            PlanStep step = stepFor(city, activity, type, slot, order, sensitiveWeather);
            if (step != null) {
                steps.add(step);
                order++;
                slotIdx++;
            }
        }

        // Toujours ajouter restauration si durée > 3h et pas déjà présente
        if (durationHours > 3 && !activities.contains("Restauration") && slotIdx < slots.length) {
            PlanStep meal = stepFor(city, "Restauration", type, slots[slotIdx], order, sensitiveWeather);
            if (meal != null) steps.add(meal);
        }

        return steps;
    }

    private PlanStep stepFor(String city, String activity, String type, String slot, int order, boolean sensitiveWeather) {
        PlanStep s = new PlanStep();
        s.stepOrder = order;
        s.timeSlot  = slot;
        s.type      = activity;
        s.lat       = 0;
        s.lng       = 0;

        switch (activity) {
            case "Culture":
                if ("economique".equals(type)) {
                    s.name = "Monuments & patrimoine de " + city;
                    s.description = "Explorez les monuments historiques accessibles gratuitement.";
                    s.durationMin = 75; s.costEur = 0;
                } else if ("equilibre".equals(type)) {
                    s.name = "Musée principal de " + city;
                    s.description = "Visite du musée incontournable de la ville.";
                    s.durationMin = 90; s.costEur = 12;
                } else {
                    s.name = "Visite guidée privée – " + city;
                    s.description = "Un guide expert vous dévoile les secrets de la ville.";
                    s.durationMin = 120; s.costEur = 45;
                }
                break;

            case "Restauration":
                if ("economique".equals(type)) {
                    s.name = "Déjeuner au marché local";
                    s.description = "Saveurs locales à prix doux, ambiance authentique.";
                    s.durationMin = 45; s.costEur = 8;
                } else if ("equilibre".equals(type)) {
                    s.name = "Brasserie du centre";
                    s.description = "Cuisine régionale dans un cadre convivial.";
                    s.durationMin = 60; s.costEur = 22;
                } else {
                    s.name = "Restaurant gastronomique";
                    s.description = "Une expérience culinaire d'exception.";
                    s.durationMin = 100; s.costEur = 65;
                }
                break;

            case "Loisirs":
                if (sensitiveWeather) {
                    // Si sensibilité météo : activités en intérieur
                    s.name = "Activité en intérieur — " + city;
                    s.description = "Cinéma, bowling, escape game ou musée interactif.";
                    s.durationMin = 90;
                    s.costEur = "economique".equals(type) ? 5 : "equilibre".equals(type) ? 20 : 45;
                } else if ("economique".equals(type)) {
                    s.name = "Parc & jardins publics";
                    s.description = "Détente et promenade dans les espaces verts de " + city + ".";
                    s.durationMin = 60; s.costEur = 0;
                } else if ("equilibre".equals(type)) {
                    s.name = "Activité sportive ou culturelle";
                    s.description = "Vélo, kayak ou atelier créatif selon la saison.";
                    s.durationMin = 90; s.costEur = 20;
                } else {
                    s.name = "Spa & bien-être";
                    s.description = "Moment de détente premium au cœur de " + city + ".";
                    s.durationMin = 120; s.costEur = 70;
                }
                break;

            case "Découverte":
                if ("economique".equals(type)) {
                    s.name = "Balade quartier historique";
                    s.description = "Flânez dans les ruelles emblématiques de " + city + ".";
                    s.durationMin = 90; s.costEur = 0;
                } else if ("equilibre".equals(type)) {
                    s.name = "Tour panoramique de " + city;
                    s.description = "Vue imprenable sur la ville depuis les points hauts.";
                    s.durationMin = 60; s.costEur = 15;
                } else {
                    s.name = "Expérience exclusive – " + city;
                    s.description = "Croisière, survol ou activité privatisée unique.";
                    s.durationMin = 90; s.costEur = 120;
                }
                break;

            case "Shopping":
                if ("economique".equals(type)) {
                    s.name = "Marché aux puces & brocante";
                    s.description = "Dénicher des trésors à petits prix.";
                    s.durationMin = 60; s.costEur = 10;
                } else if ("equilibre".equals(type)) {
                    s.name = "Shopping centre-ville";
                    s.description = "Les meilleures enseignes et boutiques locales.";
                    s.durationMin = 75; s.costEur = 50;
                } else {
                    s.name = "Boutiques de luxe";
                    s.description = "Prêt-à-porter, maroquinerie et créateurs de renom.";
                    s.durationMin = 90; s.costEur = 200;
                }
                break;

            default:
                return null;
        }
        return s;
    }

    private int estimateBudget(List<PlanStep> steps) {
        int total = 0;
        for (PlanStep s : steps) total += s.costEur;
        return total;
    }
}
