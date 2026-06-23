package com.lzq.shigangquiz.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class Option {
    public final String label;
    public final String text;

    public Option(String label, String text) {
        this.label = label == null ? "" : label.trim().toUpperCase();
        this.text = text == null ? "" : text.trim();
    }

    public static Option fromJson(JSONObject object) throws JSONException {
        return new Option(object.getString("label"), object.getString("text"));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("label", label);
        object.put("text", text);
        return object;
    }
}
