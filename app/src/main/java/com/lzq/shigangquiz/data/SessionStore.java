package com.lzq.shigangquiz.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public final class SessionStore {
    private static final String FILE_NAME = "state/session_state.json";
    private final AtomicJsonStore store;

    public SessionStore(Context context) {
        store = new AtomicJsonStore(context);
    }

    public SessionState load() throws IOException, JSONException {
        String fallback = new SessionState().toJson().toString();
        return SessionState.fromJson(new JSONObject(store.readFile(FILE_NAME, fallback)));
    }

    public void save(SessionState state) throws IOException, JSONException {
        state.updatedAt = System.currentTimeMillis();
        store.writeFile(FILE_NAME, state.toJson().toString(2));
    }
}
