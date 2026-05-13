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
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        AppDatabase.databaseWriteExecutor.execute(() -> {
            planDao.updatePlan(plan);
            if (plan.liked) {
                String username = new SessionManager(getApplication()).getUsername();
                if (username != null && !username.isEmpty()) {
                    FirebaseRepository.getInstance().savePlan(username, plan);
                }
            }
        });
    }

    public void deletePlan(long planId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            stepDao.deleteStepsForPlan(planId);
            planDao.deletePlan(planId);
        });
    }

    public void deleteAllUnsaved(long userId) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                planDao.deleteAllUnsavedForUser(userId));
    }

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

            double[] cityCenter = geocode(city);
            boolean sensitiveWeather = weatherTolerances != null && !weatherTolerances.isEmpty();

            // ── 1 requête Overpass par activité (5 max), 5 résultats chacune ──
            // economique=résultat[0], equilibre=résultat[2], confort=résultat[4]
            Map<String, String[][]> overpassCache = new java.util.concurrent.ConcurrentHashMap<>();
            if (cityCenter[0] != 0 || cityCenter[1] != 0) {
                java.util.concurrent.ExecutorService overpassPool =
                        java.util.concurrent.Executors.newFixedThreadPool(Math.min(activities.size(), 5));
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                final double cLat = cityCenter[0], cLng = cityCenter[1];
                for (String activity : activities) {
                    final String act = activity;
                    futures.add(overpassPool.submit(() -> {
                        // Requête Overpass unique pour l'activité, 5 résultats nommés
                        String[][] places = fetchRealPlaces(cLat, cLng, act, sensitiveWeather, 5);
                        if (places != null && places.length > 0) {
                            // Distribuer selon le budget : 0=eco, 2=equilibre, 4=confort
                            overpassCache.put(act + "_economique",
                                    new String[][]{places[Math.min(0, places.length-1)]});
                            overpassCache.put(act + "_equilibre",
                                    new String[][]{places[Math.min(2, places.length-1)]});
                            overpassCache.put(act + "_confort",
                                    new String[][]{places[Math.min(4, places.length-1)]});
                        }
                    }));
                }
                for (java.util.concurrent.Future<?> f : futures) {
                    try { f.get(25, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception ignored) {}
                }
                overpassPool.shutdown();
            }

            String[] types = {"economique", "equilibre", "confort"};
            for (String type : types) {
                List<PlanStep> steps = buildSteps(city, activities, type, durationHours,
                        weatherTolerances, effort, budgetMax, cityCenter, overpassCache);
                steps = deduplicateSteps(steps, overpassCache, city, type);

                // Lieux obligatoires
                if (requiredPlaces != null && !requiredPlaces.trim().isEmpty()) {
                    String[] places = requiredPlaces.split(",");
                    int slotIdx = steps.size();
                    String[] slots = {"Matin", "Après-midi", "Soir"};
                    for (String place : places) {
                        String p = place.trim();
                        if (p.isEmpty()) continue;
                        // Recherche par nom exact dans Overpass → coordonnées précises
                        String[] placeInfo = searchPlaceByName(p, cityCenter[0], cityCenter[1]);
                        // Si Overpass ne trouve rien → Nominatim en fallback
                        if (placeInfo == null || "0".equals(placeInfo[0])) {
                            placeInfo = geocodeWithType(p + " " + city,
                                    cityCenter[0], cityCenter[1]);
                        }
                        String detectedType = osmClassToStepType(placeInfo[2], placeInfo[3]);

                        PlanStep custom = new PlanStep();
                        custom.stepOrder   = slotIdx;
                        custom.name        = p;
                        custom.type        = detectedType;
                        custom.timeSlot    = slots[Math.min(slotIdx, slots.length - 1)];
                        custom.durationMin = guessDurationFromType(detectedType, type);
                        custom.costEur     = guessCostFromType(detectedType, type);
                        custom.description = "Lieu demandé — prix et durée estimés.";
                        try {
                            custom.lat = Double.parseDouble(placeInfo[0]);
                            custom.lng = Double.parseDouble(placeInfo[1]);
                        } catch (Exception e) {
                            custom.lat = cityCenter[0]; custom.lng = cityCenter[1];
                        }
                        steps.add(custom);
                        slotIdx++;
                        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
                    }
                }

                int budget = estimateBudget(steps);
                if (budget > budgetMax && !type.equals("economique")) continue;

                // Géocoder uniquement les étapes sans coordonnées (fallback)
                geocodeStepsWithCenter(steps, city, cityCenter);

                TravelPlan plan = new TravelPlan();
                plan.userId            = userId;
                plan.city              = city;
                plan.type              = type;
                plan.activities        = actStr;
                plan.budgetEur         = budget;
                plan.durationHours     = durationHours;
                plan.effort            = effort;
                plan.liked             = false;
                plan.saved             = false;
                plan.date              = date;
                plan.requiredPlaces    = requiredPlaces;
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

    // ── Overpass API — vrais lieux ────────────────────────────────────────────

    /**
     * Interroge Overpass API pour trouver de vrais POIs près du centre ville.
     * Retourne un tableau de [count] résultats sous la forme {nom, lat, lon}.
     */
    /**
     * Requête Overpass simple : nœuds OSM nommés du type correspondant à l'activité.
     * Filtre ["name"] obligatoire → seulement des vrais lieux avec un nom.
     */
    private String[][] fetchRealPlaces(double centerLat, double centerLng,
                                        String activity, boolean sensitiveWeather, int count) {
        double delta = 0.15; // ~16 km rayon
        String bbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f",
                centerLat - delta, centerLng - delta,
                centerLat + delta, centerLng + delta);

        // Tags OSM selon l'activité (nœuds + chemins nommés)
        String osmFilter;
        switch (activity) {
            case "Culture":
                osmFilter = "node[\"tourism\"=\"museum\"][\"name\"](" + bbox + ");\n"
                          + "way[\"tourism\"=\"museum\"][\"name\"](" + bbox + ");";
                break;
            case "Restauration":
                osmFilter = "node[\"amenity\"=\"restaurant\"][\"name\"](" + bbox + ");\n"
                          + "node[\"amenity\"=\"cafe\"][\"name\"](" + bbox + ");";
                break;
            case "Loisirs":
                if (sensitiveWeather)
                    osmFilter = "node[\"amenity\"=\"cinema\"][\"name\"](" + bbox + ");\n"
                              + "node[\"amenity\"=\"theatre\"][\"name\"](" + bbox + ");";
                else
                    osmFilter = "way[\"leisure\"=\"park\"][\"name\"](" + bbox + ");\n"
                              + "node[\"leisure\"=\"park\"][\"name\"](" + bbox + ");";
                break;
            case "Découverte":
                osmFilter = "node[\"tourism\"=\"attraction\"][\"name\"](" + bbox + ");\n"
                          + "node[\"historic\"=\"monument\"][\"name\"](" + bbox + ");";
                break;
            case "Shopping":
                osmFilter = "node[\"shop\"=\"mall\"][\"name\"](" + bbox + ");\n"
                          + "way[\"shop\"=\"mall\"][\"name\"](" + bbox + ");\n"
                          + "node[\"amenity\"=\"marketplace\"][\"name\"](" + bbox + ");";
                break;
            default:
                osmFilter = "node[\"tourism\"=\"attraction\"][\"name\"](" + bbox + ");";
        }

        String query = "[out:json][timeout:25];\n(\n" + osmFilter + "\n);\nout center " + count + ";";
        try {
            java.net.URL url = new java.net.URL("https://overpass-api.de/api/interpreter");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setConnectTimeout(15000); con.setReadTimeout(25000);
            byte[] body = ("data=" + java.net.URLEncoder.encode(query, "UTF-8")).getBytes("UTF-8");
            con.getOutputStream().write(body);

            if (con.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String[][] result = parseOverpassResult(sb.toString(), count);
                if (result != null && result.length > 0) return result;
            }
        } catch (Exception ignored) {}

        // Fallback Nominatim si Overpass échoue
        return fetchNominatimFallback(centerLat, centerLng, activity, sensitiveWeather, count);
    }

    /** Fallback Nominatim si Overpass est indisponible. */
    private String[][] fetchNominatimFallback(double lat, double lng,
                                               String activity, boolean sensitiveWeather, int count) {
        String keyword;
        switch (activity) {
            case "Culture":      keyword = "musée"; break;
            case "Restauration": keyword = "restaurant"; break;
            case "Loisirs":      keyword = sensitiveWeather ? "cinéma" : "parc"; break;
            case "Découverte":   keyword = "monument"; break;
            case "Shopping":     keyword = "centre commercial"; break;
            default:             keyword = activity;
        }
        double d = 0.15;
        String viewbox = String.format(Locale.US,
                "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=1", lng-d, lat-d, lng+d, lat+d);
        List<String[]> results = new ArrayList<>();
        try {
            String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded
                    + "&format=json&limit=" + count + viewbox);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "TravelingApp/1.0");
            con.setConnectTimeout(6000); con.setReadTimeout(6000);
            if (con.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                // Parse tableau JSON Nominatim
                String json = sb.toString();
                int idx = 0;
                while (idx < json.length() && results.size() < count) {
                    int start = json.indexOf("{\"place_id\":", idx);
                    if (start < 0) break;
                    int end = json.indexOf("}", start) + 1;
                    String elem = json.substring(start, Math.min(end + 200, json.length()));
                    String name = extractJsonString(elem, "display_name");
                    if (name != null && name.contains(",")) name = name.substring(0, name.indexOf(",")).trim();
                    String latS = extractJsonString(elem, "lat");
                    String lonS = extractJsonString(elem, "lon");
                    if (name != null && !name.isEmpty() && latS != null && lonS != null) {
                        results.add(new String[]{name, latS, lonS});
                    }
                    idx = start + 1;
                }
            }
        } catch (Exception ignored) {}
        return results.isEmpty() ? null : results.toArray(new String[0][]);
    }

    private String[][] fetchTopPlaces(double centerLat, double centerLng,
                                       String activity, String planType, int count,
                                       boolean sensitiveWeather) {
        String osmTag, osmValue, fallbackTag = null, fallbackValue = null;

        switch (activity) {
            case "Culture":
                // économique : monuments gratuits / équilibré : musées / confort : galeries d'art
                if ("economique".equals(planType)) {
                    osmTag = "historic"; osmValue = "monument";
                    fallbackTag = "tourism"; fallbackValue = "museum";
                } else if ("confort".equals(planType)) {
                    osmTag = "tourism"; osmValue = "gallery";
                    fallbackTag = "tourism"; fallbackValue = "museum";
                } else {
                    osmTag = "tourism"; osmValue = "museum";
                    fallbackTag = "historic"; fallbackValue = "monument";
                }
                break;

            case "Restauration":
                // économique : fast food / café / équilibré : restaurant / confort : restaurant (même tag, lieu différent)
                if ("economique".equals(planType)) {
                    osmTag = "amenity"; osmValue = "cafe";
                    fallbackTag = "amenity"; fallbackValue = "fast_food";
                } else {
                    osmTag = "amenity"; osmValue = "restaurant";
                    fallbackTag = "amenity"; fallbackValue = "cafe";
                }
                break;

            case "Loisirs":
                if (sensitiveWeather) {
                    osmTag = "amenity"; osmValue = "cinema";
                    fallbackTag = "amenity"; fallbackValue = "theatre";
                } else if ("economique".equals(planType)) {
                    osmTag = "leisure"; osmValue = "park";
                    fallbackTag = "leisure"; fallbackValue = "garden";
                } else if ("confort".equals(planType)) {
                    osmTag = "leisure"; osmValue = "spa";
                    fallbackTag = "leisure"; fallbackValue = "fitness_centre";
                } else {
                    osmTag = "amenity"; osmValue = "cinema";
                    fallbackTag = "leisure"; fallbackValue = "park";
                }
                break;

            case "Découverte":
                if ("economique".equals(planType)) {
                    osmTag = "tourism"; osmValue = "viewpoint";
                    fallbackTag = "tourism"; fallbackValue = "attraction";
                } else {
                    osmTag = "tourism"; osmValue = "attraction";
                    fallbackTag = "historic"; fallbackValue = "monument";
                }
                break;

            case "Shopping":
                // économique : marché / équilibré : centre commercial / confort : boutiques haut de gamme
                if ("economique".equals(planType)) {
                    osmTag = "amenity"; osmValue = "marketplace";
                    fallbackTag = "shop"; fallbackValue = "second_hand";
                } else if ("confort".equals(planType)) {
                    osmTag = "shop"; osmValue = "boutique";
                    fallbackTag = "shop"; fallbackValue = "clothes";
                } else {
                    osmTag = "shop"; osmValue = "mall";
                    fallbackTag = "shop"; fallbackValue = "department_store";
                }
                break;

            default:
                return null;
        }

        String[][] result = fetchFromOverpass(centerLat, centerLng, osmTag, osmValue, count);
        if ((result == null || result.length == 0) && fallbackTag != null) {
            result = fetchFromOverpass(centerLat, centerLng, fallbackTag, fallbackValue, count);
        }
        return result;
    }

    private String[][] fetchFromOverpass(double centerLat, double centerLng,
                                          String osmTag, String osmValue, int count) {
        double delta = 0.12; // ~13 km rayon — plus large pour trouver plus de résultats
        String bbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f",
                centerLat - delta, centerLng - delta,
                centerLat + delta, centerLng + delta);
        String query = "[out:json][timeout:20];\n"
                + "(\n"
                + "  node[\"" + osmTag + "\"=\"" + osmValue + "\"](" + bbox + ");\n"
                + "  way[\"" + osmTag + "\"=\"" + osmValue + "\"](" + bbox + ");\n"
                + ");\n"
                + "out center " + count + ";";
        try {
            java.net.URL url = new java.net.URL("https://overpass-api.de/api/interpreter");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setConnectTimeout(15000); con.setReadTimeout(15000);
            byte[] body = ("data=" + java.net.URLEncoder.encode(query, "UTF-8")).getBytes("UTF-8");
            con.getOutputStream().write(body);

            if (con.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return parseOverpassResult(sb.toString(), count);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String[][] parseOverpassResult(String json, int maxCount) {
        List<String[]> results = new ArrayList<>();
        int elemStart = json.indexOf("\"elements\":[");
        if (elemStart < 0) return null;
        String rest = json.substring(elemStart + 12);

        // Séparer chaque élément OSM
        String[] parts = rest.split("\\{\"type\":");
        for (String part : parts) {
            if (results.size() >= maxCount) break;
            String name = extractJsonString(part, "name");
            if (name == null || name.isEmpty()) continue;

            double lat = 0, lon = 0;

            // Nœud : lat/lon directs
            String latStr = extractJsonNumber(part, "\"lat\":");
            String lonStr = extractJsonNumber(part, "\"lon\":");
            if (latStr != null) try { lat = Double.parseDouble(latStr); } catch (Exception ignored) {}
            if (lonStr != null) try { lon = Double.parseDouble(lonStr); } catch (Exception ignored) {}

            // Chemin (way) : centre
            if (lat == 0 && part.contains("\"center\":{")) {
                int ci = part.indexOf("\"center\":{");
                String cp = part.substring(ci);
                String cLat = extractJsonNumber(cp, "\"lat\":");
                String cLon = extractJsonNumber(cp, "\"lon\":");
                if (cLat != null) try { lat = Double.parseDouble(cLat); } catch (Exception ignored) {}
                if (cLon != null) try { lon = Double.parseDouble(cLon); } catch (Exception ignored) {}
            }

            if (lat != 0 && lon != 0) {
                // Extraire l'adresse depuis les tags OSM si disponible
                String num    = extractJsonString(part, "addr:housenumber");
                String street = extractJsonString(part, "addr:street");
                String post   = extractJsonString(part, "addr:postcode");
                String addrCity = extractJsonString(part, "addr:city");
                String address = buildAddress(num, street, post, addrCity);
                results.add(new String[]{name, String.valueOf(lat), String.valueOf(lon), address});
            }
        }

        if (results.isEmpty()) return null;
        return results.toArray(new String[0][]);
    }

    /**
     * Cherche un lieu par son nom exact (ou approché) dans Overpass.
     * Retourne {lat, lon, class, type} ou null si introuvable.
     * Ex : "McDonald's" → trouve le vrai restaurant avec ses coordonnées GPS.
     */
    private String[] searchPlaceByName(String name, double centerLat, double centerLng) {
        if (centerLat == 0 && centerLng == 0) return null;
        double delta = 0.15;
        String bbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f",
                centerLat - delta, centerLng - delta,
                centerLat + delta, centerLng + delta);
        // Recherche insensible à la casse, partielle (~ = regex)
        String safeName = name.replace("\"", "").replace("'", ".");
        String query = "[out:json][timeout:15];\n"
                + "(\n"
                + "  node[\"name\"~\"" + safeName + "\",i](" + bbox + ");\n"
                + "  way[\"name\"~\"" + safeName + "\",i](" + bbox + ");\n"
                + ");\n"
                + "out center 1;";
        try {
            java.net.URL url = new java.net.URL("https://overpass-api.de/api/interpreter");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setConnectTimeout(12000); con.setReadTimeout(15000);
            byte[] body = ("data=" + java.net.URLEncoder.encode(query, "UTF-8")).getBytes("UTF-8");
            con.getOutputStream().write(body);
            if (con.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String json = sb.toString();
                if (!json.contains("\"elements\":[]")) {
                    String[][] results = parseOverpassResult(json, 1);
                    if (results != null && results.length > 0 && results[0] != null) {
                        // Détecter le type depuis les tags OSM
                        String osmClass = extractJsonString(json, "amenity");
                        if (osmClass == null) osmClass = extractJsonString(json, "tourism");
                        if (osmClass == null) osmClass = extractJsonString(json, "leisure");
                        if (osmClass == null) osmClass = extractJsonString(json, "shop");
                        String[] r = results[0];
                        // {lat, lon, class, type} pour compatibilité avec geocodeWithType
                        return new String[]{r[1], r[2],
                                osmClass != null ? "amenity" : "",
                                osmClass != null ? osmClass : ""};
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String buildAddress(String num, String street, String postcode, String city) {
        StringBuilder sb = new StringBuilder();
        if (num != null && !num.isEmpty()) sb.append(num).append(" ");
        if (street != null && !street.isEmpty()) sb.append(street);
        if ((postcode != null && !postcode.isEmpty()) || (city != null && !city.isEmpty())) {
            if (sb.length() > 0) sb.append(", ");
            if (postcode != null && !postcode.isEmpty()) sb.append(postcode).append(" ");
            if (city != null && !city.isEmpty()) sb.append(city);
        }
        return sb.toString().trim();
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        int end = json.indexOf("\"", idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }

    private String extractJsonNumber(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        idx += key.length();
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        // Le signe négatif est au début, pas en milieu de nombre
        int start = idx;
        if (idx < json.length() && json.charAt(idx) == '-') idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
        if (end <= start) return null;
        return json.substring(start, end);
    }

    // ── Construction des étapes ───────────────────────────────────────────────

    private List<PlanStep> buildSteps(String city, Set<String> activities, String type,
                                       int durationHours, Set<String> weatherTolerances,
                                       String effort, int budgetMax,
                                       double[] cityCenter,
                                       Map<String, String[][]> overpassCache) {
        List<PlanStep> steps = new ArrayList<>();
        boolean sensitiveWeather = weatherTolerances != null && !weatherTolerances.isEmpty();

        // Nombre max d'étapes selon durée + effort
        int maxSteps = maxStepsFor(durationHours, effort);

        // Durée par étape selon effort
        int stepDurationMin = stepDurationFor(effort);

        // Budget par étape : économique=25%, équilibré=60%, confort=90% du budget max
        double budgetRatio = "economique".equals(type) ? 0.25
                : "equilibre".equals(type) ? 0.60 : 0.90;
        int totalBudget   = (int) (budgetMax * budgetRatio);
        int perStepBudget = maxSteps > 0 ? totalBudget / maxSteps : totalBudget;

        String[] allSlots = {"Matin", "Matin", "Après-midi", "Après-midi", "Soir", "Soir", "Soir"};
        int order = 0, slotIdx = 0;

        for (String activity : activities) {
            if (order >= maxSteps || slotIdx >= allSlots.length) break;
            PlanStep step = stepFor(city, activity, type, allSlots[slotIdx], order,
                    sensitiveWeather, overpassCache, stepDurationMin, perStepBudget);
            if (step != null) { steps.add(step); order++; slotIdx++; }
        }

        // Restauration automatique si durée > 3h et pas sélectionnée
        if (durationHours > 3 && order < maxSteps
                && !activities.contains("Restauration") && slotIdx < allSlots.length) {
            PlanStep meal = stepFor(city, "Restauration", type, allSlots[slotIdx], order,
                    sensitiveWeather, overpassCache, stepDurationMin, perStepBudget);
            if (meal != null) steps.add(meal);
        }

        return steps;
    }

    /** Nombre d'étapes selon durée et effort. */
    private int maxStepsFor(int durationHours, String effort) {
        int base = durationHours <= 2 ? 2
                : durationHours <= 4 ? 3
                : durationHours <= 6 ? 4 : 5;
        if ("Facile".equals(effort))  return Math.max(1, base - 1);
        if ("Intense".equals(effort)) return base + 1;
        return base;
    }

    /** Durée par étape selon effort. */
    private int stepDurationFor(String effort) {
        if ("Facile".equals(effort))  return 40;
        if ("Intense".equals(effort)) return 90;
        return 60;
    }

    private PlanStep stepFor(String city, String activity, String type, String slot,
                              int order, boolean sensitiveWeather,
                              Map<String, String[][]> cache,
                              int stepDurationMin, int perStepBudget) {
        PlanStep s = new PlanStep();
        s.stepOrder = order;
        s.timeSlot  = slot;
        s.type      = activity;
        s.lat       = 0;
        s.lng       = 0;

        // ── Remplir avec un vrai lieu depuis Overpass ────────────────────────
        String[][] places = cache.get(activity + "_" + type);
        if (places != null && places.length > 0) {
            String[] place = places[0];
            if (place != null) {
                s.name = place[0];
                try {
                    s.lat = Double.parseDouble(place[1]);
                    s.lng = Double.parseDouble(place[2]);
                } catch (Exception ignored) {}
                // Adresse extraite depuis les tags OSM (peut être vide)
                if (place.length > 3 && place[3] != null && !place[3].isEmpty()) {
                    s.address = place[3];
                }
            }
        }

        // ── Compléter nom (fallback), durée réelle, coût adapté au budget ──
        // La durée de base est ajustée par l'effort (stepDurationMin)
        // Le coût est proportionnel au budget utilisateur (perStepBudget)
        switch (activity) {
            case "Culture":
                if (s.name == null) s.name = "economique".equals(type)
                        ? "Monuments & patrimoine de " + city
                        : "equilibre".equals(type) ? "Musée de " + city
                        : "Visite guidée – " + city;
                s.description = "Découverte culturelle de " + city + ".";
                // Durée : monuments = courte, musées = normale, visite guidée = longue
                s.durationMin = "economique".equals(type)
                        ? (int)(stepDurationMin * 0.9)
                        : "equilibre".equals(type) ? stepDurationMin
                        : (int)(stepDurationMin * 1.4);
                // Coût : monuments souvent gratuits, musées ~30% du budget/étape, confort ~70%
                s.costEur = "economique".equals(type) ? 0
                        : "equilibre".equals(type) ? (int)(perStepBudget * 0.4)
                        : (int)(perStepBudget * 0.8);
                break;

            case "Restauration":
                if (s.name == null) s.name = "economique".equals(type)
                        ? "Café local"
                        : "equilibre".equals(type) ? "Restaurant"
                        : "Restaurant gastronomique";
                s.description = "Pause repas à " + city + ".";
                s.durationMin = "economique".equals(type)
                        ? (int)(stepDurationMin * 0.7)
                        : "equilibre".equals(type) ? (int)(stepDurationMin * 0.9)
                        : (int)(stepDurationMin * 1.3);
                // Restauration : 20% (éco), 45% (équil), 75% (confort) du budget/étape
                s.costEur = "economique".equals(type) ? Math.max(5, (int)(perStepBudget * 0.2))
                        : "equilibre".equals(type) ? (int)(perStepBudget * 0.45)
                        : (int)(perStepBudget * 0.75);
                break;

            case "Loisirs":
                if (s.name == null) {
                    s.name = sensitiveWeather ? "Cinéma"
                            : "economique".equals(type) ? "Parc"
                            : "equilibre".equals(type) ? "Loisirs"
                            : "Spa & bien-être";
                }
                s.description = sensitiveWeather ? "Activité couverte à " + city + "."
                        : "Loisirs à " + city + ".";
                s.durationMin = stepDurationMin;
                s.costEur = "economique".equals(type) ? (sensitiveWeather ? (int)(perStepBudget*0.2) : 0)
                        : "equilibre".equals(type) ? (int)(perStepBudget * 0.4)
                        : (int)(perStepBudget * 0.9);
                break;

            case "Découverte":
                if (s.name == null) s.name = "economique".equals(type)
                        ? "Quartier historique de " + city
                        : "equilibre".equals(type) ? "Site emblématique de " + city
                        : "Expérience exclusive – " + city;
                s.description = "À la découverte de " + city + ".";
                s.durationMin = (int)(stepDurationMin * 1.2);
                s.costEur = "economique".equals(type) ? 0
                        : "equilibre".equals(type) ? (int)(perStepBudget * 0.25)
                        : (int)(perStepBudget * 1.2);
                break;

            case "Shopping":
                if (s.name == null) s.name = "economique".equals(type)
                        ? "Marché"
                        : "equilibre".equals(type) ? "Centre commercial"
                        : "Boutiques";
                s.description = "Shopping à " + city + ".";
                s.durationMin = (int)(stepDurationMin * 1.1);
                // Shopping : coût = le plus variable — directement proportionnel au budget
                s.costEur = "economique".equals(type) ? (int)(perStepBudget * 0.3)
                        : "equilibre".equals(type) ? (int)(perStepBudget * 0.7)
                        : perStepBudget;
                break;

            default:
                return null;
        }

        s.openingHours = defaultOpeningHours(activity);
        return s;
    }

    private String defaultOpeningHours(String type) {
        switch (type) {
            case "Culture":      return "9h00 – 18h00";
            case "Restauration": return "12h00 – 14h00 / 19h00 – 22h00";
            case "Loisirs":      return "10h00 – 20h00";
            case "Découverte":   return "Toute la journée";
            case "Shopping":     return "10h00 – 19h30";
            default:             return "";
        }
    }

    // ── Géocodage Nominatim (fallback pour les étapes sans coordonnées) ──────

    private void geocodeStepsWithCenter(List<PlanStep> steps, String city, double[] cityCenter) {
        if (cityCenter[0] == 0 && cityCenter[1] == 0) {
            // Ville inconnue : fallback Nominatim basique
            for (PlanStep step : steps) {
                if (step.lat != 0 || step.lng != 0) continue;
                double[] coords = geocode(typeToKeyword(step.type) + " " + city);
                step.lat = coords[0]; step.lng = coords[1];
                try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
            }
            return;
        }
        for (PlanStep step : steps) {
            if (step.lat != 0 || step.lng != 0) continue; // déjà géocodé par Overpass

            double[] coords = new double[]{0, 0};
            boolean usedNominatim = false;

            // 1. Nominatim avec le nom réel + ville
            if (step.name != null && !step.name.isEmpty()) {
                coords = geocodeNear(step.name + " " + city, cityCenter[0], cityCenter[1]);
                usedNominatim = true;
            }

            // 2. Nominatim avec le type générique
            if (coords[0] == 0) {
                coords = geocodeNear(typeToKeyword(step.type) + " " + city,
                        cityCenter[0], cityCenter[1]);
                usedNominatim = true;
            }

            // Sleep CGU Nominatim seulement si on a vraiment appelé Nominatim
            if (usedNominatim) {
                try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
            }

            // 3. Dernier recours : centre-ville (pas de sleep nécessaire)
            if (coords[0] == 0 || distanceKm(coords[0], coords[1], cityCenter[0], cityCenter[1]) >= 80) {
                coords = cityCenter;
            }

            step.lat = coords[0];
            step.lng = coords[1];
        }
    }

    private void geocodeSteps(List<PlanStep> steps, String city) {
        double[] cityCenter = geocode(city);
        for (PlanStep step : steps) {
            if (step.lat != 0 || step.lng != 0) continue;
            double[] coords = cityCenter[0] != 0
                    ? geocodeNear(typeToKeyword(step.type) + " " + city, cityCenter[0], cityCenter[1])
                    : geocode(typeToKeyword(step.type) + " " + city);
            if (coords[0] != 0 && (cityCenter[0] == 0
                    || distanceKm(coords[0], coords[1], cityCenter[0], cityCenter[1]) < 80)) {
                step.lat = coords[0]; step.lng = coords[1];
            }
            try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
        }
    }

    private double[] geocodeNear(String query, double centerLat, double centerLng) {
        double d = 0.4;
        String viewbox = String.format(Locale.US,
                "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=1",
                centerLng - d, centerLat - d, centerLng + d, centerLat + d);
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded
                    + "&format=json&limit=1" + viewbox);
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
                    double lon = Double.parseDouble(json.substring(loi, json.indexOf("\"", loi)));
                    return new double[]{lat, lon};
                }
            }
        } catch (Exception ignored) {}
        return geocode(query);
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
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

    /**
     * Géocode un lieu et retourne {lat, lon, class, type} depuis Nominatim.
     * Ex : {"48.856", "2.352", "amenity", "restaurant"}
     */
    private String[] geocodeWithType(String query, double centerLat, double centerLng) {
        String[] result = {"0", "0", "", ""};
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            String viewbox = (centerLat != 0)
                    ? String.format(Locale.US,
                        "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=1",
                        centerLng - 0.4, centerLat - 0.4, centerLng + 0.4, centerLat + 0.4)
                    : "";
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded
                    + "&format=json&limit=1&addressdetails=0" + viewbox);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "TravelingApp/1.0");
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
                    result[0] = json.substring(li, json.indexOf("\"", li));
                    int loi = json.indexOf("\"lon\":\"") + 7;
                    result[1] = json.substring(loi, json.indexOf("\"", loi));
                    String cls = extractJsonString(json, "class");
                    String typ = extractJsonString(json, "type");
                    result[2] = cls != null ? cls : "";
                    result[3] = typ != null ? typ : "";
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** Convertit les tags OSM class/type en type d'étape TravelPath. */
    private String osmClassToStepType(String osmClass, String osmType) {
        if (osmClass == null || osmType == null) return "Découverte";
        switch (osmClass) {
            case "amenity":
                switch (osmType) {
                    case "restaurant": case "fast_food": case "cafe":
                    case "bar": case "pub": case "food_court":
                        return "Restauration";
                    case "cinema": case "theatre": case "arts_centre":
                    case "nightclub": case "casino":
                        return "Loisirs";
                    case "museum": case "library":
                        return "Culture";
                }
                break;
            case "tourism":
                switch (osmType) {
                    case "museum": case "gallery": case "artwork":
                        return "Culture";
                    case "attraction": case "viewpoint": case "zoo":
                    case "theme_park":
                        return "Découverte";
                    case "hotel": case "hostel":
                        return "Découverte";
                }
                break;
            case "leisure":
                switch (osmType) {
                    case "park": case "garden": case "nature_reserve":
                        return "Loisirs";
                    case "sports_centre": case "fitness_centre":
                    case "swimming_pool": case "spa":
                        return "Loisirs";
                }
                break;
            case "shop":
                return "Shopping";
            case "historic":
                return "Culture";
        }
        return "Découverte";
    }

    private int guessCostFromType(String type, String planType) {
        switch (type) {
            case "Restauration":
                return "economique".equals(planType) ? 12 : "equilibre".equals(planType) ? 25 : 60;
            case "Culture":
                return "economique".equals(planType) ? 0 : "equilibre".equals(planType) ? 12 : 40;
            case "Loisirs":
                return "economique".equals(planType) ? 0 : "equilibre".equals(planType) ? 15 : 50;
            case "Shopping":
                return "economique".equals(planType) ? 20 : "equilibre".equals(planType) ? 60 : 150;
            default:
                return "economique".equals(planType) ? 0 : "equilibre".equals(planType) ? 10 : 30;
        }
    }

    private int guessDurationFromType(String type, String planType) {
        switch (type) {
            case "Restauration": return "economique".equals(planType) ? 45 : 70;
            case "Culture":      return 90;
            case "Loisirs":      return 80;
            case "Shopping":     return 75;
            default:             return 60;
        }
    }

    /**
     * Si deux étapes ont le même nom (doublon Overpass), remplace la deuxième
     * par le résultat suivant disponible dans le cache, ou laisse le nom template.
     */
    private List<PlanStep> deduplicateSteps(List<PlanStep> steps,
                                             Map<String, String[][]> cache,
                                             String city, String type) {
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        for (PlanStep step : steps) {
            if (step.name == null) continue;
            String key = step.name.trim().toLowerCase();
            if (usedNames.contains(key)) {
                // Chercher un résultat alternatif dans le cache pour cette activité
                String cacheKey = step.type + "_" + type;
                String[][] places = cache.get(cacheKey);
                boolean replaced = false;
                if (places != null) {
                    for (String[] place : places) {
                        if (place == null) continue;
                        String altKey = place[0].trim().toLowerCase();
                        if (!usedNames.contains(altKey)) {
                            step.name = place[0];
                            try {
                                step.lat = Double.parseDouble(place[1]);
                                step.lng = Double.parseDouble(place[2]);
                            } catch (Exception ignored) {}
                            usedNames.add(altKey);
                            replaced = true;
                            break;
                        }
                    }
                }
                if (!replaced) {
                    // Pas d'alternatif → nom générique court
                    step.name = step.type + " — " + city;
                    step.lat = 0; step.lng = 0;
                    usedNames.add(step.name.trim().toLowerCase());
                }
            } else {
                usedNames.add(key);
            }
        }
        return steps;
    }

    private int estimateBudget(List<PlanStep> steps) {
        int total = 0;
        for (PlanStep s : steps) total += s.costEur;
        return total;
    }
}
