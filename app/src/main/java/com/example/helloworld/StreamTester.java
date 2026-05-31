package com.example.helloworld;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StreamTester {

    private static final int MAX_REDIRECTS = 10;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    /** Per-read cap during body download — must be long enough for streaming chunks. */
    private static final int MAX_STREAM_READ_TIMEOUT_MS = 10000;
    /** Safety cap only — curl -m limits by time, not size. */
    private static final long SAFETY_MAX_BYTES = 512L * 1024 * 1024;
    private static final long PROGRESS_INTERVAL_MS = 400;

    interface ProgressListener {
        void onLine(String line);
    }

    static final class Result {
        final boolean success;
        final String summary;

        Result(boolean success, String summary) {
            this.success = success;
            this.summary = summary;
        }
    }

    static Result test(String startUrl, ProgressListener listener) {
        return test(startUrl, "StreamTester/1.0 (curl-like)", 10000, listener);
    }

    static Result test(String startUrl, String userAgent, int maxDurationMs, ProgressListener listener) {
        long overallStart = System.currentTimeMillis();
        String currentUrl = startUrl;
        int redirectCount = 0;
        long totalBytes = 0;
        int finalStatus = -1;
        String finalMessage = "";
        String stopReason = "";

        HttpURLConnection conn = null;
        Result result = null;
        try {
            while (remainingMs(overallStart, maxDurationMs) > 0) {
                URL url = new URL(currentUrl);
                String host = url.getHost();
                int port = url.getPort();
                if (port < 0) {
                    port = url.getDefaultPort();
                }

                String ip = resolveHostIp(listener, overallStart, host);

                emit(listener, overallStart, "* Trying " + ip + ":" + port + "...");
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("User-Agent", userAgent);
                applyTimeouts(conn, overallStart, maxDurationMs);

                long connectStart = System.currentTimeMillis();
                conn.connect();
                emit(listener, overallStart,
                        "* Connected to " + host + " (" + ip + ") port " + port
                                + " (" + (System.currentTimeMillis() - connectStart) + " ms)");

                logRequestHeaders(listener, overallStart, conn, userAgent);

                int status = conn.getResponseCode();
                finalMessage = conn.getResponseMessage() != null ? conn.getResponseMessage() : "";
                emit(listener, overallStart, "< HTTP/1.1 " + status + " " + finalMessage);

                logResponseHeaders(listener, overallStart, conn);

                if (status >= 300 && status < 400) {
                    String location = conn.getHeaderField("Location");
                    abortConnection(conn);
                    conn = null;
                    if (location == null || location.isEmpty()) {
                        result = fail(listener, overallStart, "Redirect without Location header.");
                        break;
                    }
                    if (redirectCount >= MAX_REDIRECTS) {
                        result = fail(listener, overallStart, "Too many redirects (>" + MAX_REDIRECTS + ").");
                        break;
                    }
                    currentUrl = resolveUrl(currentUrl, location);
                    redirectCount++;
                    emit(listener, overallStart, "* Follow redirect (" + redirectCount + ") -> " + currentUrl);
                    continue;
                }

                finalStatus = status;
                applyStreamReadTimeout(conn, overallStart, maxDurationMs);
                InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (stream != null) {
                    emitProgressHeader(listener, overallStart);
                    byte[] buffer = new byte[16384];
                    int read;
                    long lastProgressAt = System.currentTimeMillis();
                    while (true) {
                        long remaining = remainingMs(overallStart, maxDurationMs);
                        if (remaining <= 0) {
                            stopReason = "time limit reached (" + (maxDurationMs / 1000) + "s)";
                            break;
                        }
                        applyStreamReadTimeout(conn, overallStart, maxDurationMs);
                        try {
                            read = stream.read(buffer);
                        } catch (SocketTimeoutException e) {
                            if (remainingMs(overallStart, maxDurationMs) <= 0) {
                                stopReason = "time limit reached (" + (maxDurationMs / 1000) + "s)";
                            }
                            break;
                        } catch (SocketException e) {
                            if (totalBytes > 0) {
                                break;
                            }
                            throw e;
                        } catch (IOException e) {
                            if (totalBytes > 0 && isBenignStreamEnd(e)) {
                                break;
                            }
                            throw e;
                        }
                        if (read == -1) {
                            break;
                        }
                        totalBytes += read;

                        long now = System.currentTimeMillis();
                        if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                            emitProgressLine(listener, overallStart, totalBytes, now - overallStart);
                            lastProgressAt = now;
                        }

                        if (totalBytes >= SAFETY_MAX_BYTES) {
                            stopReason = "safety byte cap reached";
                            break;
                        }
                    }
                    try {
                        stream.close();
                    } catch (Exception ignored) {
                    }

                    long elapsed = System.currentTimeMillis() - overallStart;
                    emitProgressLine(listener, overallStart, totalBytes, elapsed);

                    if (!stopReason.isEmpty()) {
                        emit(listener, overallStart,
                                "* Operation timed out after " + elapsed
                                        + " milliseconds with " + totalBytes + " out of -1 bytes received");
                    }
                } else {
                    emit(listener, overallStart, "* No response body");
                }

                emit(listener, overallStart, "* Closing connection 0");
                abortConnection(conn);
                conn = null;
                break;
            }

            if (result == null) {
                if (remainingMs(overallStart, maxDurationMs) <= 0 && finalStatus < 200) {
                    stopReason = "time limit reached before stream started";
                }

                long elapsed = System.currentTimeMillis() - overallStart;
                boolean timedOut = !stopReason.isEmpty();
                boolean ok = finalStatus >= 200 && finalStatus < 400 && (totalBytes > 0 || !timedOut);
                String summary = String.format(Locale.US,
                        "=== Result ===\nStatus: %d %s\nRedirects: %d\nBytes discarded: %s\nTotal time: %d ms\nResult: %s",
                        finalStatus,
                        finalMessage,
                        redirectCount,
                        formatBytes(totalBytes),
                        elapsed,
                        ok ? "OK" : "FAILED");
                result = new Result(ok, summary);
            }

        } catch (SocketTimeoutException e) {
            result = finishAfterTimeout(listener, overallStart, finalStatus, finalMessage, redirectCount,
                    totalBytes, stopReason, maxDurationMs);
        } catch (IOException e) {
            if (finalStatus >= 200 && finalStatus < 400 && totalBytes > 0
                    && (remainingMs(overallStart, maxDurationMs) <= 0 || isBenignStreamEnd(e))) {
                result = finishAfterTimeout(listener, overallStart, finalStatus, finalMessage, redirectCount,
                        totalBytes, stopReason, maxDurationMs);
            } else {
                result = fail(listener, overallStart, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } catch (Exception e) {
            result = fail(listener, overallStart, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            abortConnection(conn);
        }
        return result;
    }

    private static String resolveHostIp(ProgressListener listener, long traceStart, String host) {
        if (host == null || host.isEmpty()) {
            return host;
        }
        if (host.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
            emit(listener, traceStart, "* IP " + host);
            return host;
        }
        try {
            String ip = InetAddress.getByName(host).getHostAddress();
            emit(listener, traceStart, "* IP " + ip + " (" + host + ")");
            return ip;
        } catch (IOException e) {
            emit(listener, traceStart, "* Resolve " + host + " failed: " + e.getMessage());
            return host;
        }
    }

    private static void emit(ProgressListener listener, long traceStart, String line) {
        listener.onLine(formatTimestamp(traceStart) + "  " + sanitizeDebugLine(line));
    }

    /** Mask CDN vendor strings in debug output. */
    private static String sanitizeDebugLine(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String sanitized = line.replaceAll("(?i)cloudflare", "ucs");
        sanitized = sanitized.replaceAll("(?i)cf-", "ucs-");
        sanitized = sanitized.replaceAll("(?i)CF-RAY", "UCS-RAY");
        sanitized = sanitized.replaceAll("(?i)\\bcf", "ucs");
        return sanitized;
    }

    private static void emitProgressHeader(ProgressListener listener, long traceStart) {
        emit(listener, traceStart, "  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current");
        emit(listener, traceStart, "                                 Dload  Upload   Total   Spent    Left  Speed");
    }

    private static void emitProgressLine(ProgressListener listener, long traceStart, long totalBytes, long elapsedMs) {
        double speed = elapsedMs > 0 ? (totalBytes * 1000.0 / elapsedMs) : 0;
        String line = String.format(Locale.US,
                "100 %8s    0 %8s    0     0 %7s      0 --:--:-- %6s --:--:-- %7s",
                formatBytes(totalBytes),
                formatBytes(totalBytes),
                formatSpeed(speed),
                formatElapsed(elapsedMs),
                formatSpeed(speed));
        emit(listener, traceStart, line);
    }

    private static String formatTimestamp(long traceStart) {
        double seconds = (System.currentTimeMillis() - traceStart) / 1000.0;
        return String.format(Locale.US, "+%.2fs", seconds);
    }

    private static String formatElapsed(long elapsedMs) {
        int totalSec = (int) (elapsedMs / 1000);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format(Locale.US, "%d:%02d", min, sec);
    }

    private static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024) {
            return String.format(Locale.US, "%3.0f", bytesPerSec);
        }
        if (bytesPerSec < 1024 * 1024) {
            return String.format(Locale.US, "%4.0fk", bytesPerSec / 1024.0);
        }
        return String.format(Locale.US, "%4.1fM", bytesPerSec / (1024.0 * 1024.0));
    }

    private static void applyTimeouts(HttpURLConnection conn, long overallStart, int maxDurationMs) {
        long remaining = remainingMs(overallStart, maxDurationMs);
        int remainingMs = (int) Math.max(1, Math.min(remaining, Integer.MAX_VALUE));
        conn.setConnectTimeout(Math.min(CONNECT_TIMEOUT_MS, remainingMs));
        conn.setReadTimeout(Math.min(Math.max(remainingMs, 1000), CONNECT_TIMEOUT_MS));
    }

    private static void applyStreamReadTimeout(HttpURLConnection conn, long overallStart, int maxDurationMs) {
        long remaining = remainingMs(overallStart, maxDurationMs);
        int remainingMs = (int) Math.max(1, Math.min(remaining, Integer.MAX_VALUE));
        conn.setReadTimeout(Math.min(Math.max(remainingMs, 1000), MAX_STREAM_READ_TIMEOUT_MS));
    }

    private static boolean isBenignStreamEnd(IOException e) {
        if (e instanceof SocketException) {
            String message = e.getMessage();
            return message != null && message.toLowerCase(Locale.US).contains("closed");
        }
        return false;
    }

    private static Result finishAfterTimeout(
            ProgressListener listener,
            long traceStart,
            int finalStatus,
            String finalMessage,
            int redirectCount,
            long totalBytes,
            String stopReason,
            int maxDurationMs
    ) {
        long elapsed = System.currentTimeMillis() - traceStart;
        if (stopReason == null || stopReason.isEmpty()) {
            stopReason = "time limit reached (" + (maxDurationMs / 1000) + "s)";
        }
        emitProgressLine(listener, traceStart, totalBytes, elapsed);
        emit(listener, traceStart,
                "* Operation timed out after " + elapsed
                        + " milliseconds with " + totalBytes + " out of -1 bytes received");
        emit(listener, traceStart, "* Closing connection 0");

        boolean ok = finalStatus >= 200 && finalStatus < 400 && totalBytes > 0;
        String summary = String.format(Locale.US,
                "=== Result ===\nStatus: %d %s\nRedirects: %d\nBytes discarded: %s\nTotal time: %d ms\nResult: %s",
                finalStatus,
                finalMessage,
                redirectCount,
                formatBytes(totalBytes),
                elapsed,
                ok ? "OK" : "FAILED");
        return new Result(ok, summary);
    }

    private static void abortConnection(HttpURLConnection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    private static long remainingMs(long start, int maxDurationMs) {
        return maxDurationMs - (System.currentTimeMillis() - start);
    }

    private static Result fail(ProgressListener listener, long traceStart, String message) {
        emit(listener, traceStart, "* ERROR: " + message);
        String summary = "=== Result ===\nResult: FAILED\nError: " + message;
        return new Result(false, summary);
    }

    private static void logRequestHeaders(ProgressListener listener, long traceStart, HttpURLConnection conn, String userAgent) {
        emit(listener, traceStart, "> GET " + conn.getURL().getPath() +
                (conn.getURL().getQuery() != null ? "?" + conn.getURL().getQuery() : "") +
                " HTTP/1.1");
        emit(listener, traceStart, "> Host: " + conn.getURL().getHost());
        emit(listener, traceStart, "> User-Agent: " + userAgent);
        emit(listener, traceStart, "> Accept: */*");
    }

    private static void logResponseHeaders(ProgressListener listener, long traceStart, HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.regionMatches(true, 0, "X-Android-", 0, 10)) {
                continue;
            }
            for (String value : entry.getValue()) {
                emit(listener, traceStart, "< " + name + ": " + value);
            }
        }
    }

    private static String resolveUrl(String base, String location) throws Exception {
        URI baseUri = URI.create(base);
        return baseUri.resolve(location).toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1fK", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1fM", bytes / (1024.0 * 1024.0));
    }
}
