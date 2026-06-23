package com.lzq.shigangquiz.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class StudyStateStore {
    private static final String FILE_NAME = "state/progress.json";
    private static final String LEGACY_FILE_NAME = "quiz_state.json";

    private final Context context;
    private final AtomicJsonStore store;
    private int attempts;
    private int correct;
    private final Set<String> answeredIds = new HashSet<>();
    private final Set<String> wrongIds = new HashSet<>();

    public StudyStateStore(Context context) {
        this.context = context.getApplicationContext();
        store = new AtomicJsonStore(context);
    }

    public synchronized void load() throws IOException, JSONException {
        answeredIds.clear();
        wrongIds.clear();
        attempts = 0;
        correct = 0;

        File currentFile = new File(context.getFilesDir(), FILE_NAME);
        if (!currentFile.exists() && migrateLegacyState()) return;

        JSONObject root = new JSONObject(store.readFile(FILE_NAME,
                "{\"version\":2,\"attempts\":0,\"correct\":0,\"answered_ids\":[],\"wrong_ids\":[]}"));
        attempts = Math.max(0, root.optInt("attempts", root.optInt("answered", 0)));
        correct = Math.max(0, root.optInt("correct", 0));
        readIds(root.optJSONArray("answered_ids"), answeredIds, false);
        readIds(root.optJSONArray("wrong_ids"), wrongIds, false);
    }

    public synchronized void recordAnswer(String questionId, boolean isCorrect)
            throws IOException, JSONException {
        attempts++;
        answeredIds.add(questionId);
        if (isCorrect) correct++;
        else wrongIds.add(questionId);
        save();
    }

    public synchronized void removeWrong(String questionId) throws IOException, JSONException {
        if (wrongIds.remove(questionId)) save();
    }

    public synchronized void reset() throws IOException, JSONException {
        attempts = 0;
        correct = 0;
        answeredIds.clear();
        wrongIds.clear();
        save();
    }

    public synchronized int getAnswered() {
        return answeredIds.size();
    }

    public synchronized double getAccuracy() {
        return attempts == 0 ? 0.0 : correct * 100.0 / attempts;
    }

    public synchronized int getWrongCount() {
        return wrongIds.size();
    }

    public synchronized Set<String> getWrongIds() {
        return new HashSet<>(wrongIds);
    }

    private boolean migrateLegacyState() throws IOException, JSONException {
        File legacy = new File(context.getFilesDir(), LEGACY_FILE_NAME);
        if (!legacy.exists()) return false;

        JSONObject root = new JSONObject(store.readFile(LEGACY_FILE_NAME, "{\"records\":[]}"));
        JSONArray records = root.optJSONArray("records");
        if (records == null) return false;

        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            String rawId = record.optString("id", "").trim();
            if (rawId.isEmpty()) continue;
            String id = rawId.startsWith("builtin:") ? rawId : "builtin:" + rawId;
            answeredIds.add(id);
            attempts += Math.max(0, record.optInt("attempts", 0));
            correct += Math.max(0, record.optInt("correct", 0));
            if (record.optBoolean("active_wrong", false)) wrongIds.add(id);
        }
        save();
        return true;
    }

    private static void readIds(JSONArray array, Set<String> destination, boolean addBuiltinPrefix) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            String id = array.optString(i, "").trim();
            if (id.isEmpty()) continue;
            if (addBuiltinPrefix && !id.contains(":")) id = "builtin:" + id;
            destination.add(id);
        }
    }

    private void save() throws JSONException, IOException {
        JSONObject root = new JSONObject();
        root.put("version", 2);
        root.put("attempts", attempts);
        root.put("correct", correct);
        JSONArray answeredArray = new JSONArray();
        for (String id : answeredIds) answeredArray.put(id);
        root.put("answered_ids", answeredArray);
        JSONArray wrongArray = new JSONArray();
        for (String id : wrongIds) wrongArray.put(id);
        root.put("wrong_ids", wrongArray);
        store.writeFile(FILE_NAME, root.toString(2));
    }
}
