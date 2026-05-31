package com.example.helloworld;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class XtreamApi {

    static String normalizeHost(String host) {
        host = host.trim();
        if (host.isEmpty()) {
            return host;
        }
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    static String buildLiveStreamUrl(String host, String username, String password, int streamId, String ext) {
        return normalizeHost(host) + "/live/"
                + urlEncode(username) + "/"
                + urlEncode(password) + "/"
                + streamId + "." + ext;
    }

    static String fetchPreferredExtension(String host, String username, String password) throws Exception {
        String url = buildPlayerApiUrl(host, username, password, null, null);
        String body = httpGet(url);
        JSONObject root = new JSONObject(body);
        if (!root.has("user_info")) {
            return "ts";
        }
        JSONObject userInfo = root.getJSONObject("user_info");
        return readFormatsExtension(userInfo);
    }

    private static String readFormatsExtension(JSONObject userInfo) throws Exception {
        if (!userInfo.has("allowed_output_formats")) {
            return "ts";
        }
        Object raw = userInfo.get("allowed_output_formats");
        if (raw instanceof JSONArray) {
            JSONArray formats = (JSONArray) raw;
            for (int i = 0; i < formats.length(); i++) {
                String format = formats.optString(i, "").toLowerCase(Locale.US);
                if ("m3u8".equals(format)) {
                    return "m3u8";
                }
            }
            for (int i = 0; i < formats.length(); i++) {
                String format = formats.optString(i, "").toLowerCase(Locale.US);
                if ("ts".equals(format)) {
                    return "ts";
                }
            }
            if (formats.length() > 0) {
                return formats.optString(0, "ts");
            }
        }
        return "ts";
    }

    static List<Category> fetchCategories(String host, String username, String password) throws Exception {
        String url = buildPlayerApiUrl(host, username, password, "get_live_categories", null);
        String body = httpGet(url);
        JSONArray array = new JSONArray(body);
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            categories.add(new Category(
                    String.valueOf(item.opt("category_id")),
                    item.optString("category_name", "Unknown"),
                    item.optInt("stream_count", -1)
            ));
        }
        return categories;
    }

    static List<Channel> fetchChannels(String host, String username, String password, String categoryId) throws Exception {
        String url = buildPlayerApiUrl(host, username, password, "get_live_streams", categoryId);
        String body = httpGet(url);
        JSONArray array = new JSONArray(body);
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            channels.add(new Channel(
                    item.optInt("stream_id", 0),
                    item.optString("name", "Unknown"),
                    item.optString("category_id", categoryId)
            ));
        }
        return channels;
    }

    private static String buildPlayerApiUrl(String host, String username, String password, String action, String categoryId) {
        StringBuilder sb = new StringBuilder(normalizeHost(host));
        sb.append("/player_api.php?username=").append(urlEncode(username));
        sb.append("&password=").append(urlEncode(password));
        if (action != null && !action.isEmpty()) {
            sb.append("&action=").append(urlEncode(action));
        }
        if (categoryId != null && !categoryId.isEmpty()) {
            sb.append("&category_id=").append(urlEncode(categoryId));
        }
        return sb.toString();
    }

    private static String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "ChannelTracer/1.0");
        conn.setRequestProperty("Connection", "close");
        conn.setUseCaches(false);

        int status = conn.getResponseCode();
        java.io.InputStream rawStream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (rawStream == null) {
            conn.disconnect();
            throw new Exception("HTTP " + status + ": empty response");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(rawStream, StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        conn.disconnect();

        if (status >= 400) {
            throw new Exception("HTTP " + status + ": " + body);
        }
        return body.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }
}
