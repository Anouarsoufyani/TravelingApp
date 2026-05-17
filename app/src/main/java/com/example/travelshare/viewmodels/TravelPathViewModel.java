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

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TravelPathViewModel extends AndroidViewModel {

    public interface GenerationCallback {
        void onDone(int createdCount);
    }

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
                               GenerationCallback onDone) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Set<String> safeActivities = activities != null ? activities : Collections.emptySet();
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            StringBuilder actSb = new StringBuilder();
            if (!safeActivities.isEmpty()) {
                for (String a : safeActivities) { if (actSb.length() > 0) actSb.append(","); actSb.append(a); }
            }
            String actStr = actSb.toString();

            StringBuilder wtSb = new StringBuilder();
            if (weatherTolerances != null) {
                for (String w : weatherTolerances) { if (wtSb.length() > 0) wtSb.append(","); wtSb.append(w); }
            }
            String wtStr = wtSb.toString();

            double[] cityCenter = geocode(city);
            boolean sensitiveWeather = weatherTolerances != null && !weatherTolerances.isEmpty();

            Map<String, String[][]> overpassCache = new java.util.concurrent.ConcurrentHashMap<>();
            if (!safeActivities.isEmpty()) {
                final double cLat = cityCenter[0], cLng = cityCenter[1];
                for (String activity : safeActivities) {
                    String[][] places = (cLat != 0 || cLng != 0)
                            ? fetchRealPlaces(city, cLat, cLng, activity, sensitiveWeather, 8)
                            : fetchNominatimFallback(city, cLat, cLng, activity, sensitiveWeather, 8);
                    if (places != null && places.length > 0) {
                        overpassCache.put(activity + "_economique",
                                new String[][]{places[Math.min(0, places.length - 1)]});
                        overpassCache.put(activity + "_equilibre",
                                new String[][]{places[Math.min(2, places.length - 1)]});
                        overpassCache.put(activity + "_confort",
                                new String[][]{places[Math.min(4, places.length - 1)]});
                    }
                    sleepQuietly(1100);
                }
            }

            int createdCount = 0;
            String[] types = {"economique", "equilibre", "confort"};
            for (String type : types) {
                List<PlanStep> finalSteps = new ArrayList<>();
                
                // 1. Force required places
                if (requiredPlaces != null && !requiredPlaces.trim().isEmpty()) {
                    String[] places = requiredPlaces.split(",");
                    for (String pName : places) {
                        String p = pName.trim();
                        if (p.isEmpty()) continue;
                        
                        String[] placeInfo = searchPlaceByName(p, city, cityCenter[0], cityCenter[1]);
                        if (placeInfo == null || "0".equals(placeInfo[0])) {
                            placeInfo = geocodeWithType(p + " " + city, cityCenter[0], cityCenter[1]);
                        }
                        if (placeInfo == null || "0".equals(placeInfo[0])) continue;
                        
                        PlanStep step = new PlanStep();
                        step.name = p;
                        step.type = osmClassToStepType(placeInfo[2], placeInfo[3]);
                        step.durationMin = guessDurationFromType(step.type, type);
                        step.costEur = guessCostFromType(step.type, type);
                        step.description = "Lieu demandé.";
                        try {
                            step.lat = Double.parseDouble(placeInfo[0]);
                            step.lng = Double.parseDouble(placeInfo[1]);
                        } catch (Exception e) {
                            step.lat = cityCenter[0]; step.lng = cityCenter[1];
                        }
                        finalSteps.add(step);
                        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
                    }
                }

                // 2. Add activity suggestions until we reach a reasonable limit
                int maxTotal = Math.max(maxStepsFor(durationHours, effort), finalSteps.size() + 1);
                int stepDurationMin = stepDurationFor(effort);
                int perStepBudget = Math.max(1, budgetMax / Math.max(1, maxTotal));
                for (String activity : safeActivities) {
                    if (finalSteps.size() >= maxTotal) break;
                    String[][] places = overpassCache.get(activity + "_" + type);
                    PlanStep s = null;
                    if (places != null && places.length > 0) {
                        s = new PlanStep();
                        s.type = activity;
                        s.name = places[0][0];
                        try { s.lat = Double.parseDouble(places[0][1]); s.lng = Double.parseDouble(places[0][2]); } catch (Exception ignored) {}
                        if (places[0].length > 3) s.address = places[0][3];
                        s.durationMin = stepDurationFor(effort);
                        s.costEur = guessCostFromType(activity, type);
                        s.description = "Suggestion Traveling.";
                        s.openingHours = defaultOpeningHours(activity);
                    }

                    // Simple duplicate check
                    boolean exists = false;
                    for (PlanStep fs : finalSteps) {
                        if (fs.name != null && s != null && s.name != null
                                && fs.name.equalsIgnoreCase(s.name)) {
                            exists = true;
                            break;
                        }
                    }
                    if (s != null && !exists) finalSteps.add(s);
                }

                if (finalSteps.isEmpty()) continue;

                // 3. Assign simple ordering and slots
                String[] allSlots = {"Matin", "Après-midi", "Soir", "Nuit", "Nuit tard"};
                for (int i = 0; i < finalSteps.size(); i++) {
                    PlanStep s = finalSteps.get(i);
                    s.stepOrder = i;
                    s.timeSlot = allSlots[Math.min(i, allSlots.length - 1)];
                    
                    // Robust Final Pass: ensure every step has coordinates
                    if (s.lat == 0 || s.lng == 0) {
                        double[] coords = geocode(s.name + " " + city);
                        if (coords[0] != 0) {
                            s.lat = coords[0]; s.lng = coords[1];
                        }
                    }
                }

                int budget = estimateBudget(finalSteps);
                if (budget > budgetMax && !type.equals("economique")) continue;

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
                for (PlanStep step : finalSteps) {
                    step.planId = planId;
                    stepDao.insertStep(step);
                }
                createdCount++;
            }
            if (onDone != null) onDone.onDone(createdCount);
        });
    }

    private String[][] fetchRealPlaces(String city, double centerLat, double centerLng,
                                        String activity, boolean sensitiveWeather, int count) {
        double delta = 0.12;
        String bbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f",
                centerLat - delta, centerLng - delta,
                centerLat + delta, centerLng + delta);

        String osmFilter;
        switch (activity) {
            case "Culture":
                osmFilter = "node[\"tourism\"=\"museum\"][\"name\"](" + bbox + ");\n"
                          + "way[\"tourism\"=\"museum\"][\"name\"](" + bbox + ");\n"
                          + "relation[\"tourism\"=\"museum\"][\"name\"](" + bbox + ");";
                break;
            case "Restauration":
                osmFilter = "node[\"amenity\"=\"restaurant\"][\"name\"](" + bbox + ");\n"
                          + "way[\"amenity\"=\"restaurant\"][\"name\"](" + bbox + ");\n"
                          + "node[\"amenity\"=\"cafe\"][\"name\"](" + bbox + ");\n"
                          + "way[\"amenity\"=\"cafe\"][\"name\"](" + bbox + ");";
                break;
            case "Loisirs":
                if (sensitiveWeather)
                    osmFilter = "node[\"amenity\"=\"cinema\"][\"name\"](" + bbox + ");\n"
                              + "node[\"amenity\"=\"theatre\"][\"name\"](" + bbox + ");";
                else
                    osmFilter = "way[\"leisure\"=\"park\"][\"name\"](" + bbox + ");\n"
                              + "node[\"leisure\"=\"park\"][\"name\"](" + bbox + ");\n"
                              + "relation[\"leisure\"=\"park\"][\"name\"](" + bbox + ");";
                break;
            case "Découverte":
                osmFilter = "node[\"tourism\"=\"attraction\"][\"name\"](" + bbox + ");\n"
                          + "way[\"tourism\"=\"attraction\"][\"name\"](" + bbox + ");\n"
                          + "relation[\"tourism\"=\"attraction\"][\"name\"](" + bbox + ");\n"
                          + "node[\"historic\"=\"monument\"][\"name\"](" + bbox + ");\n"
                          + "way[\"historic\"=\"monument\"][\"name\"](" + bbox + ");\n"
                          + "relation[\"historic\"=\"monument\"][\"name\"](" + bbox + ");";
                break;
            case "Shopping":
                osmFilter = "node[\"shop\"=\"mall\"][\"name\"](" + bbox + ");\n"
                          + "way[\"shop\"=\"mall\"][\"name\"](" + bbox + ");\n"
                          + "node[\"amenity\"=\"marketplace\"][\"name\"](" + bbox + ");\n"
                          + "way[\"amenity\"=\"marketplace\"][\"name\"](" + bbox + ");";
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

        return fetchNominatimFallback(city, centerLat, centerLng, activity, sensitiveWeather, count);
    }

    private String[][] fetchNominatimFallback(String city, double lat, double lng,
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
        List<String[]> results = new ArrayList<>();
        if (lat != 0 || lng != 0) {
            double d = 0.20;
            String viewbox = String.format(Locale.US,
                    "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=1", lng-d, lat-d, lng+d, lat+d);
            results = fetchNominatimPlaces(keyword + " " + city, count, viewbox);
        }
        if (results.isEmpty()) {
            sleepQuietly(1100);
            results = fetchNominatimPlaces(keyword + " " + city, count, "");
        }
        return results.isEmpty() ? null : results.toArray(new String[0][]);
    }

    private List<String[]> fetchNominatimPlaces(String query, int count, String extraParams) {
        List<String[]> results = new ArrayList<>();
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded
                    + "&format=json&limit=" + count + extraParams);
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

                JSONArray array = new JSONArray(sb.toString());
                for (int i = 0; i < array.length() && results.size() < count; i++) {
                    JSONObject item = array.getJSONObject(i);
                    String name = item.optString("name", "");
                    if (name.isEmpty()) {
                        name = item.optString("display_name", "");
                        int comma = name.indexOf(",");
                        if (comma > 0) name = name.substring(0, comma).trim();
                    }
                    String latS = item.optString("lat", "");
                    String lonS = item.optString("lon", "");
                    if (!name.isEmpty() && !latS.isEmpty() && !lonS.isEmpty()) {
                        results.add(new String[]{name, latS, lonS, ""});
                    }
                }
            }
        } catch (Exception ignored) {}
        return results;
    }

    private void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) {}
    }

    private String[][] fetchTopPlaces(double centerLat, double centerLng,
                                       String activity, String planType, int count,
                                       boolean sensitiveWeather) {
        String osmTag, osmValue, fallbackTag = null, fallbackValue = null;

        switch (activity) {
            case "Culture":

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
        double delta = 0.12;
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
        try {
            JSONArray elements = new JSONObject(json).optJSONArray("elements");
            if (elements == null) return null;
            for (int i = 0; i < elements.length() && results.size() < maxCount; i++) {
                JSONObject element = elements.getJSONObject(i);
                JSONObject tags = element.optJSONObject("tags");
                if (tags == null) continue;
                String name = tags.optString("name", "");
                if (name.isEmpty()) continue;

                double lat = element.optDouble("lat", 0);
                double lon = element.optDouble("lon", 0);
                JSONObject center = element.optJSONObject("center");
                if ((lat == 0 || lon == 0) && center != null) {
                    lat = center.optDouble("lat", 0);
                    lon = center.optDouble("lon", 0);
                }
                if (lat == 0 || lon == 0) continue;

                String address = buildAddress(
                        tags.optString("addr:housenumber", ""),
                        tags.optString("addr:street", ""),
                        tags.optString("addr:postcode", ""),
                        tags.optString("addr:city", ""));
                results.add(new String[]{name, String.valueOf(lat), String.valueOf(lon), address});
            }
        } catch (Exception ignored) {}

        if (results.isEmpty()) return null;
        return results.toArray(new String[0][]);
    }

    private String[] searchPlaceByName(String placeName, String city, double centerLat, double centerLng) {
        String[] result = geocodeWithType(placeName + " " + city, centerLat, centerLng);
        if (result != null && !"0".equals(result[0])) return result;

        result = searchPlaceInternal(placeName, centerLat, centerLng, 0.25);
        if (result != null && !"0".equals(result[0])) return result;

        return searchPlaceInternal(placeName, 0, 0, 0);
    }

    private String[] searchPlaceInternal(String name, double lat, double lng, double delta) {
        String bbox = (lat != 0) ? String.format(Locale.US, "(%.4f,%.4f,%.4f,%.4f)",
                lat - delta, lng - delta, lat + delta, lng + delta) : "";

        String safeName = name.replace("\"", "").replace("'", ".");
        String query = "[out:json][timeout:15];\n"
                + "(\n"
                + "  node[\"name\"~\"" + safeName + "\",i]" + bbox + ";\n"
                + "  way[\"name\"~\"" + safeName + "\",i]" + bbox + ";\n"
                + ");\n"
                + "out center 1;";
        try {
            java.net.URL url = new java.net.URL("https://overpass-api.de/api/interpreter");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setConnectTimeout(10000); con.setReadTimeout(12000);
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
                        String[] r = results[0];
                        return new String[]{r[1], r[2], "landmark", "monument"};
                    }
                }
            }
        } catch (Exception ignored) {}
        return new String[]{"0", "0", "", ""};
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

        int start = idx;
        if (idx < json.length() && json.charAt(idx) == '-') idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
        if (end <= start) return null;
        return json.substring(start, end);
    }

    private List<PlanStep> buildSteps(String city, Set<String> activities, String type,
                                       int durationHours, Set<String> weatherTolerances,
                                       String effort, int budgetMax,
                                       double[] cityCenter,
                                       Map<String, String[][]> overpassCache) {
        List<PlanStep> candidates = new ArrayList<>();
        boolean sensitiveWeather = weatherTolerances != null && !weatherTolerances.isEmpty();

        int maxSteps = maxStepsFor(durationHours, effort);
        int stepDurationMin = stepDurationFor(effort);

        double budgetRatio = "economique".equals(type) ? 0.25
                : "equilibre".equals(type) ? 0.60 : 0.90;
        int totalBudget   = (int) (budgetMax * budgetRatio);
        int perStepBudget = maxSteps > 0 ? totalBudget / maxSteps : totalBudget;

        String[] allSlots = {"Matin", "Après-midi", "Soir", "Nuit"};
        int order = 0;

        // Collect all potential steps based on activities
        for (String activity : activities) {
            PlanStep step = stepFor(city, activity, type, "TBD", order,
                    sensitiveWeather, overpassCache, stepDurationMin, perStepBudget);
            if (step != null) candidates.add(step);
        }

        // Add a meal if duration is long and not already selected
        if (durationHours > 3 && candidates.size() < maxSteps && !activities.contains("Restauration")) {
            PlanStep meal = stepFor(city, "Restauration", type, "TBD", order,
                    sensitiveWeather, overpassCache, stepDurationMin, perStepBudget);
            if (meal != null) candidates.add(meal);
        }

        // Sort candidates by geographic proximity (Nearest Neighbor)
        List<PlanStep> sorted = new ArrayList<>();
        double lastLat = cityCenter[0], lastLng = cityCenter[1];

        while (!candidates.isEmpty() && sorted.size() < maxSteps) {
            int nearestIdx = -1;
            double minDist = Double.MAX_VALUE;
            
            for (int i = 0; i < candidates.size(); i++) {
                PlanStep c = candidates.get(i);
                // If coordinates are missing, treat as "at center" for proximity logic
                double cLat = c.lat != 0 ? c.lat : cityCenter[0];
                double cLng = c.lng != 0 ? c.lng : cityCenter[1];
                
                double d = distanceKm(lastLat, lastLng, cLat, cLng);
                if (d < minDist) {
                    minDist = d;
                    nearestIdx = i;
                }
            }
            
            if (nearestIdx >= 0) {
                PlanStep chosen = candidates.remove(nearestIdx);
                chosen.stepOrder = sorted.size();
                chosen.timeSlot = allSlots[Math.min(chosen.stepOrder, allSlots.length - 1)];
                sorted.add(chosen);
                if (chosen.lat != 0) {
                    lastLat = chosen.lat;
                    lastLng = chosen.lng;
                }
            } else break;
        }

        return sorted;
    }

    private int maxStepsFor(int durationHours, String effort) {
        int base = durationHours <= 2 ? 2
                : durationHours <= 4 ? 3
                : durationHours <= 6 ? 4 : 5;
        if ("Facile".equals(effort))  return Math.max(1, base - 1);
        if ("Intense".equals(effort)) return base + 1;
        return base;
    }

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

        String[][] places = cache.get(activity + "_" + type);
        if (places != null && places.length > 0) {
            String[] place = places[0];
            if (place != null) {
                s.name = place[0];
                try {
                    s.lat = Double.parseDouble(place[1]);
                    s.lng = Double.parseDouble(place[2]);
                } catch (Exception ignored) {}

                if (place.length > 3 && place[3] != null && !place[3].isEmpty()) {
                    s.address = place[3];
                }
            }
        }

        switch (activity) {
            case "Culture":
                if (s.name == null) s.name = "economique".equals(type)
                        ? "Monuments & patrimoine de " + city
                        : "equilibre".equals(type) ? "Musée de " + city
                        : "Visite guidée – " + city;
                s.description = "Découverte culturelle de " + city + ".";

                s.durationMin = "economique".equals(type)
                        ? (int)(stepDurationMin * 0.9)
                        : "equilibre".equals(type) ? stepDurationMin
                        : (int)(stepDurationMin * 1.4);

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

    private void geocodeStepsWithCenter(List<PlanStep> steps, String city, double[] cityCenter) {
        if (cityCenter[0] == 0 && cityCenter[1] == 0) {

            for (PlanStep step : steps) {
                if (step.lat != 0 || step.lng != 0) continue;
                double[] coords = geocode(typeToKeyword(step.type) + " " + city);
                step.lat = coords[0]; step.lng = coords[1];
                try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
            }
            return;
        }
        for (PlanStep step : steps) {
            if (step.lat != 0 || step.lng != 0) continue;

            double[] coords = new double[]{0, 0};
            boolean usedNominatim = false;

            if (step.name != null && !step.name.isEmpty()) {
                coords = geocodeNear(step.name + " " + city, cityCenter[0], cityCenter[1]);
                usedNominatim = true;
            }

            if (coords[0] == 0) {
                coords = geocodeNear(typeToKeyword(step.type) + " " + city,
                        cityCenter[0], cityCenter[1]);
                usedNominatim = true;
            }

            if (usedNominatim) {
                try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
            }

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
            // Standard Nominatim Search
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
                    double resLat = Double.parseDouble(json.substring(li, json.indexOf("\"", li)));
                    int loi = json.indexOf("\"lon\":\"") + 7;
                    double resLng = Double.parseDouble(json.substring(loi, json.indexOf("\"", loi)));
                    return new double[]{resLat, resLng};
                }
            }
        } catch (Exception ignored) {}
        return new double[]{0, 0};
    }

    private String[] geocodeWithType(String query, double centerLat, double centerLng) {
        String[] result = {"0", "0", "", ""};
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            java.net.URL url = new java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=" + encoded
                    + "&format=json&limit=1&addressdetails=1");
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
                JSONArray array = new JSONArray(sb.toString());
                if (array.length() > 0) {
                    JSONObject item = array.getJSONObject(0);
                    result[0] = item.optString("lat", "0");
                    result[1] = item.optString("lon", "0");
                    result[2] = item.optString("class", "");
                    result[3] = item.optString("type", "");
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

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

    private List<PlanStep> deduplicateSteps(List<PlanStep> steps,
                                             Map<String, String[][]> cache,
                                             String city, String type) {
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        for (PlanStep step : steps) {
            if (step.name == null) continue;
            String key = step.name.trim().toLowerCase();
            if (usedNames.contains(key)) {

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
