package com.example.helloworld;

import android.content.Context;
import android.content.SharedPreferences;

final class SessionStore {

    private static final String PREFS_NAME = "channel_tracer_session";
    private static final String KEY_HOST = "host";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SECONDS = "seconds";
    private static final String KEY_USER_AGENT_INDEX = "user_agent_index";
    private static final String KEY_STREAM_URL = "stream_url";

    static final class Session {
        final String host;
        final String username;
        final String password;
        final String seconds;
        final int userAgentIndex;
        final String streamUrl;

        Session(String host, String username, String password, String seconds, int userAgentIndex, String streamUrl) {
            this.host = host;
            this.username = username;
            this.password = password;
            this.seconds = seconds;
            this.userAgentIndex = userAgentIndex;
            this.streamUrl = streamUrl;
        }
    }

    private SessionStore() {
    }

    static Session load(Context context) {
        SharedPreferences prefs = prefs(context);
        return new Session(
                prefs.getString(KEY_HOST, ""),
                prefs.getString(KEY_USERNAME, ""),
                prefs.getString(KEY_PASSWORD, ""),
                prefs.getString(KEY_SECONDS, "30"),
                prefs.getInt(KEY_USER_AGENT_INDEX, 0),
                prefs.getString(KEY_STREAM_URL, "")
        );
    }

    static void save(
            Context context,
            String host,
            String username,
            String password,
            String seconds,
            int userAgentIndex,
            String streamUrl
    ) {
        prefs(context).edit()
                .putString(KEY_HOST, host)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_SECONDS, seconds)
                .putInt(KEY_USER_AGENT_INDEX, userAgentIndex)
                .putString(KEY_STREAM_URL, streamUrl)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
