package com.lzq.shigangquiz.data;

import org.json.JSONException;
import org.json.JSONObject;

public final class SessionState {
    public String bank = QuestionRepository.BANK_ALL;
    public String mode = "sequence";
    public String category = "全部专题";
    public String type = "all";
    public String questionId = "";
    public int index = 0;
    public long updatedAt = 0L;

    public static SessionState fromJson(JSONObject object) {
        SessionState state = new SessionState();
        state.bank = object.optString("bank", QuestionRepository.BANK_ALL);
        state.mode = object.optString("mode", "sequence");
        state.category = object.optString("category", "全部专题");
        state.type = object.optString("type", "all");
        state.questionId = object.optString("question_id", "");
        state.index = Math.max(0, object.optInt("index", 0));
        state.updatedAt = object.optLong("updated_at", 0L);
        return state;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("version", 1);
        object.put("bank", bank);
        object.put("mode", mode);
        object.put("category", category);
        object.put("type", type);
        object.put("question_id", questionId);
        object.put("index", index);
        object.put("updated_at", updatedAt);
        return object;
    }
}
