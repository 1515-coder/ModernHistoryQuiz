package com.lzq.shigangquiz.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class FavoriteStore {
    private static final String FILE_NAME = "state/favorites.json";

    private final AtomicJsonStore store;
    private final Set<String> ids = new HashSet<>();

    public FavoriteStore(Context context) {
        store = new AtomicJsonStore(context);
    }

    public synchronized void load() throws IOException, JSONException {
        ids.clear();
        JSONObject root = new JSONObject(store.readFile(FILE_NAME, "{\"version\":1,\"question_ids\":[]}"));
        JSONArray array = root.optJSONArray("question_ids");
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            String id = array.optString(i, "").trim();
            if (!id.isEmpty()) ids.add(id);
        }
    }

    public synchronized boolean contains(String questionId) {
        return ids.contains(questionId);
    }

    public synchronized boolean toggle(String questionId) throws IOException, JSONException {
        boolean nowFavorite;
        if (ids.contains(questionId)) {
            ids.remove(questionId);
            nowFavorite = false;
        } else {
            ids.add(questionId);
            nowFavorite = true;
        }
        save();
        return nowFavorite;
    }

    public synchronized Set<String> snapshot() {
        return new HashSet<>(ids);
    }

    private void save() throws JSONException, IOException {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray array = new JSONArray();
        for (String id : ids) array.put(id);
        root.put("question_ids", array);
        store.writeFile(FILE_NAME, root.toString(2));
    }
}
