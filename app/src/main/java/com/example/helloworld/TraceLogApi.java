package com.example.helloworld;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class TraceLogApi {

    /** Verified endpoint — HTTPS on this host is not reachable. */
    private static final String LOGS_URL = "http://195.62.32.129/api/stream-trace/logs";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    static final class SaveResult {
        final int id;
        final String logUuid;

        SaveResult(int id, String logUuid) {
            this.id = id;
            this.logUuid = logUuid;
        }
    }

    static SaveResult save(String streamingUrl, String traceText, int maxSeconds) throws Exception {
        JSONObject body = new JSONObject();
        body.put("streamingUrl", streamingUrl);
        body.put("traceText", traceText);
        body.put("maxSeconds", maxSeconds);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(LOGS_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty("User-Agent", "ChannelTracer/1.0");

        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload);
        }

        int status = conn.getResponseCode();
        java.io.InputStream rawStream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = readBody(rawStream);
        conn.disconnect();

        if (status < 200 || status >= 300) {
            throw new Exception("HTTP " + status + ": " + responseBody);
        }

        JSONObject root = new JSONObject(responseBody);
        if (!root.optBoolean("ok", false)) {
            throw new Exception("API rejected log: " + responseBody);
        }

        return new SaveResult(
                root.optInt("id", -1),
                root.optString("logUuid", "")
        );
    }

    private static String readBody(java.io.InputStream rawStream) throws Exception {
        if (rawStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(rawStream, StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        return body.toString();
    }
}
