package com.lzq.shigangquiz.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Question {
    public String id;
    public int number;
    public String bank;
    public String type;
    public String category;
    public String question;
    public String explanation;
    public final List<Option> options = new ArrayList<>();
    public final Set<String> answers = new LinkedHashSet<>();

    public static Question fromJson(JSONObject object, String defaultBank, int fallbackNumber)
            throws JSONException {
        Question item = new Question();
        item.id = object.optString("id", "").trim();
        item.number = object.optInt("number", fallbackNumber);
        item.bank = object.optString("bank", defaultBank).trim();
        item.type = object.optString("type", "single").trim().toLowerCase();
        item.category = object.optString("category", "未分类").trim();
        item.question = object.getString("question").trim();
        item.explanation = object.optString("explanation", "").trim();

        JSONArray optionArray = object.getJSONArray("options");
        for (int i = 0; i < optionArray.length(); i++) {
            Object raw = optionArray.get(i);
            if (raw instanceof JSONObject) {
                item.options.add(Option.fromJson((JSONObject) raw));
            } else {
                item.options.add(new Option(String.valueOf((char) ('A' + i)), String.valueOf(raw)));
            }
        }

        JSONArray answerArray = object.optJSONArray("answer");
        if (answerArray != null) {
            for (int i = 0; i < answerArray.length(); i++) {
                String answer = answerArray.optString(i, "").trim().toUpperCase();
                if (!answer.isEmpty()) item.answers.add(answer);
            }
        } else {
            String answerText = object.optString("answer_text", object.optString("answer", ""));
            for (int i = 0; i < answerText.length(); i++) {
                char c = Character.toUpperCase(answerText.charAt(i));
                if (c >= 'A' && c <= 'H') item.answers.add(String.valueOf(c));
            }
        }

        if (item.answers.size() > 1) item.type = "multiple";
        if (!"multiple".equals(item.type)) item.type = "single";
        return item;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("number", number);
        object.put("bank", bank);
        object.put("type", type);
        object.put("category", category);
        object.put("question", question);

        JSONArray optionArray = new JSONArray();
        for (Option option : options) optionArray.put(option.toJson());
        object.put("options", optionArray);

        JSONArray answerArray = new JSONArray();
        for (String answer : answers) answerArray.put(answer);
        object.put("answer", answerArray);
        object.put("answer_text", answerText());
        object.put("explanation", explanation == null ? "" : explanation);
        return object;
    }

    public List<String> answerList() {
        List<String> result = new ArrayList<>(answers);
        Collections.sort(result);
        return result;
    }

    public String answerText() {
        StringBuilder builder = new StringBuilder();
        for (String answer : answerList()) builder.append(answer);
        return builder.toString();
    }
}
