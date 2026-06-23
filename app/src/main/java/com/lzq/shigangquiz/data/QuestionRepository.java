package com.lzq.shigangquiz.data;

import android.content.Context;

import com.lzq.shigangquiz.model.Option;
import com.lzq.shigangquiz.model.Question;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class QuestionRepository {
    public static final String BANK_ALL = "all";
    public static final String BANK_BUILTIN = "builtin";
    public static final String BANK_USER = "user";
    public static final String BANK_FAVORITES = "favorites";

    private static final String BUILTIN_ASSET = "builtin_questions.json";
    private static final String USER_FILE = "question_banks/user_questions.json";

    private final AtomicJsonStore store;
    private final List<Question> builtInQuestions = new ArrayList<>();
    private final List<Question> userQuestions = new ArrayList<>();

    public QuestionRepository(Context context) {
        store = new AtomicJsonStore(context);
    }

    public synchronized void load() throws IOException, JSONException {
        builtInQuestions.clear();
        userQuestions.clear();
        parseBank(store.readAsset(BUILTIN_ASSET), BANK_BUILTIN, builtInQuestions);
        parseBank(store.readFile(USER_FILE, emptyBank().toString()), BANK_USER, userQuestions);
        normalizeIdsAndNumbers();
    }

    public synchronized List<Question> getBuiltInQuestions() {
        return new ArrayList<>(builtInQuestions);
    }

    public synchronized List<Question> getUserQuestions() {
        return new ArrayList<>(userQuestions);
    }

    public synchronized List<Question> getAllQuestions() {
        List<Question> result = new ArrayList<>(builtInQuestions.size() + userQuestions.size());
        result.addAll(builtInQuestions);
        result.addAll(userQuestions);
        return result;
    }

    public synchronized ImportResult addQuestions(List<Question> incoming) throws IOException, JSONException {
        Set<String> existing = new HashSet<>();
        for (Question question : getAllQuestions()) existing.add(normalizeQuestion(question.question));

        int added = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (Question question : incoming) {
            String validation = validate(question);
            if (validation != null) {
                skipped++;
                errors.add(validation);
                continue;
            }
            String key = normalizeQuestion(question.question);
            if (existing.contains(key)) {
                skipped++;
                continue;
            }
            question.id = "user:" + UUID.randomUUID();
            question.bank = BANK_USER;
            question.number = userQuestions.size() + 1;
            if (question.category == null || question.category.trim().isEmpty()) {
                question.category = "用户题库";
            }
            userQuestions.add(question);
            existing.add(key);
            added++;
        }
        if (added > 0) saveUserBank();
        return new ImportResult(added, skipped, errors);
    }

    public synchronized ImportResult importJson(String text) throws JSONException, IOException {
        List<Question> parsed = new ArrayList<>();
        Object root = new JSONTokener(text).nextValue();
        JSONArray array;
        if (root instanceof JSONArray) {
            array = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            JSONObject object = (JSONObject) root;
            if (object.has("questions")) array = object.getJSONArray("questions");
            else {
                array = new JSONArray();
                array.put(object);
            }
        } else {
            throw new JSONException("JSON 顶层必须是对象或数组");
        }

        for (int i = 0; i < array.length(); i++) {
            parsed.add(Question.fromJson(array.getJSONObject(i), BANK_USER, i + 1));
        }
        return addQuestions(parsed);
    }

    public synchronized String exportUserBank() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("title", "Practice User Bank");
        root.put("count", userQuestions.size());
        JSONArray array = new JSONArray();
        for (Question question : userQuestions) array.put(question.toJson());
        root.put("questions", array);
        return root.toString(2);
    }

    public synchronized void clearUserBank() throws IOException, JSONException {
        userQuestions.clear();
        saveUserBank();
    }

    private void parseBank(String text, String bank, List<Question> destination) throws JSONException {
        Object root = new JSONTokener(text).nextValue();
        JSONArray array;
        if (root instanceof JSONArray) array = (JSONArray) root;
        else array = ((JSONObject) root).optJSONArray("questions");
        if (array == null) return;

        for (int i = 0; i < array.length(); i++) {
            Question question = Question.fromJson(array.getJSONObject(i), bank, i + 1);
            question.bank = bank;
            destination.add(question);
        }
    }

    private void normalizeIdsAndNumbers() {
        for (int i = 0; i < builtInQuestions.size(); i++) {
            Question question = builtInQuestions.get(i);
            String rawId = question.id == null || question.id.isEmpty()
                    ? String.format(Locale.US, "q%03d", i + 1)
                    : question.id;
            if (!rawId.startsWith("builtin:")) rawId = "builtin:" + rawId;
            question.id = rawId;
            question.bank = BANK_BUILTIN;
            question.number = i + 1;
        }
        for (int i = 0; i < userQuestions.size(); i++) {
            Question question = userQuestions.get(i);
            String rawId = question.id == null ? "" : question.id.trim();
            if (!rawId.startsWith("user:")) rawId = "user:" + UUID.randomUUID();
            question.id = rawId;
            question.bank = BANK_USER;
            question.number = i + 1;
        }
    }

    private void saveUserBank() throws JSONException, IOException {
        store.writeFile(USER_FILE, exportUserBank());
    }

    private static JSONObject emptyBank() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("questions", new JSONArray());
        return root;
    }

    private static String validate(Question question) {
        if (question.question == null || question.question.trim().isEmpty()) return "发现空题干";
        if (question.options.size() < 2) return "题目“" + abbreviate(question.question) + "”少于两个选项";
        if (question.answers.isEmpty()) return "题目“" + abbreviate(question.question) + "”没有答案";
        Set<String> labels = new HashSet<>();
        for (Option option : question.options) labels.add(option.label);
        if (!labels.containsAll(question.answers)) return "题目“" + abbreviate(question.question) + "”答案不在选项中";
        return null;
    }

    private static String abbreviate(String value) {
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() > 18 ? clean.substring(0, 18) + "…" : clean;
    }

    private static String normalizeQuestion(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "")
                .trim();
    }

    public static final class ImportResult {
        public final int added;
        public final int skipped;
        public final List<String> errors;

        public ImportResult(int added, int skipped, List<String> errors) {
            this.added = added;
            this.skipped = skipped;
            this.errors = errors;
        }

        public String summary() {
            String text = "已加入 " + added + " 题，跳过 " + skipped + " 题";
            if (!errors.isEmpty()) text += "\n" + errors.get(0);
            return text;
        }
    }
}
