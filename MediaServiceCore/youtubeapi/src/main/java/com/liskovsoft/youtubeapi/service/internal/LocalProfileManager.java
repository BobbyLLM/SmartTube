package com.liskovsoft.youtubeapi.service.internal;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.youtubeapi.app.AppService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns local profile identity. It intentionally has no account or OAuth dependency. */
public final class LocalProfileManager {
    private static final String PREFS = "local_profiles";
    private static final String PROFILES = "profiles";
    private static final String ACTIVE_ID = "active_id";
    private static final String DEFAULT_ID = "local_default";
    private static final String DEFAULT_NAME = "Guest";
    private static final LocalProfileManager INSTANCE = new LocalProfileManager();

    public interface Listener {
        void onProfileChanged();
    }

    public static final class Profile {
        private final String id;
        private final String name;

        Profile(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    private final SharedPreferences prefs;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private LocalProfileManager() {
        Context context = AppService.instance().getContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getString(PROFILES, null) == null) {
            saveProfiles(Collections.singletonList(new Profile(DEFAULT_ID, DEFAULT_NAME)));
            prefs.edit().putString(ACTIVE_ID, DEFAULT_ID).apply();
        }
    }

    public static LocalProfileManager instance() {
        return INSTANCE;
    }

    public List<Profile> list() {
        return new ArrayList<>(loadProfiles().values());
    }

    public Profile getActive() {
        Map<String, Profile> profiles = loadProfiles();
        Profile active = profiles.get(prefs.getString(ACTIVE_ID, DEFAULT_ID));
        return active != null ? active : list().get(0);
    }

    public String getActiveId() {
        return getActive().getId();
    }

    public void select(String id) {
        if (!loadProfiles().containsKey(id) || id.equals(getActiveId())) return;
        prefs.edit().putString(ACTIVE_ID, id).apply();
        notifyListeners();
    }

    public Profile create(String name) {
        String cleanName = cleanName(name);
        Map<String, Profile> profiles = loadProfiles();
        Profile profile = new Profile("local_" + UUID.randomUUID().toString(), cleanName);
        profiles.put(profile.id, profile);
        saveProfiles(profiles.values());
        select(profile.id);
        return profile;
    }

    public void rename(String id, String name) {
        Map<String, Profile> profiles = loadProfiles();
        if (!profiles.containsKey(id)) return;
        profiles.put(id, new Profile(id, cleanName(name)));
        saveProfiles(profiles.values());
        notifyListeners();
    }

    public boolean delete(String id) {
        Map<String, Profile> profiles = loadProfiles();
        if (profiles.size() <= 1 || !profiles.containsKey(id)) return false;
        profiles.remove(id);
        saveProfiles(profiles.values());
        if (id.equals(getActiveId())) {
            prefs.edit().putString(ACTIVE_ID, profiles.values().iterator().next().id).apply();
        }
        notifyListeners();
        return true;
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    private Map<String, Profile> loadProfiles() {
        Map<String, Profile> result = new LinkedHashMap<>();
        String encoded = prefs.getString(PROFILES, "");
        for (String item : encoded.split("\\|")) {
            String[] parts = item.split("\\t", 2);
            if (parts.length == 2 && !parts[0].isEmpty()) result.put(parts[0], new Profile(parts[0], parts[1]));
        }
        if (result.isEmpty()) result.put(DEFAULT_ID, new Profile(DEFAULT_ID, DEFAULT_NAME));
        return result;
    }

    private void saveProfiles(Iterable<Profile> profiles) {
        StringBuilder encoded = new StringBuilder();
        for (Profile profile : profiles) {
            if (encoded.length() > 0) encoded.append('|');
            encoded.append(profile.id).append('\t').append(profile.name.replace("|", " ").replace("\t", " "));
        }
        prefs.edit().putString(PROFILES, encoded.toString()).apply();
    }

    private String cleanName(String name) {
        String result = name == null ? "Profile" : name.trim();
        return result.isEmpty() ? "Profile" : result;
    }

    private void notifyListeners() {
        for (Listener listener : listeners) listener.onProfileChanged();
    }
}
