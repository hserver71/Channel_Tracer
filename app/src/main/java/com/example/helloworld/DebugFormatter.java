package com.example.helloworld;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DebugFormatter {

    private static final Pattern TIMESTAMP = Pattern.compile("^\\+[\\d.]+s");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern URL_HOST = Pattern.compile("(?<=://)([^/:\\s?#]+)");
    private static final Pattern HOST_HEADER = Pattern.compile("(?<=Host: )([^\\s/]+)");
    private static final Pattern RESOLVE_HOST = Pattern.compile("(?<=\\()([^)]+)(?=\\))");
    private static final Pattern IP_LINE = Pattern.compile("(?<=\\* IP )(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern CONNECT_MS = Pattern.compile("\\(\\d+ ms\\)");

    private DebugFormatter() {
    }

    static CharSequence format(android.content.Context context, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int tsBg = color(context, R.color.debug_timestamp_bg);
        int tsFg = color(context, R.color.debug_timestamp_text);
        int domainBg = color(context, R.color.debug_url_bg);
        int domainFg = color(context, R.color.debug_url_text);
        int ipBg = color(context, R.color.debug_request);
        int ipFg = color(context, R.color.debug_timestamp_text);
        int error = color(context, R.color.debug_error);
        int result = color(context, R.color.debug_result);

        int offset = 0;
        while (offset <= text.length()) {
            int lineEnd = text.indexOf('\n', offset);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }

            String line = text.substring(offset, lineEnd);
            styleLine(builder, offset, lineEnd, line, tsBg, tsFg, domainBg, domainFg, ipBg, ipFg, error, result);

            offset = lineEnd + 1;
            if (lineEnd == text.length()) {
                break;
            }
        }

        return builder;
    }

    private static void styleLine(
            SpannableStringBuilder builder,
            int lineStart,
            int lineEnd,
            String line,
            int tsBg,
            int tsFg,
            int domainBg,
            int domainFg,
            int ipBg,
            int ipFg,
            int error,
            int result
    ) {
        Matcher ts = TIMESTAMP.matcher(line);
        if (ts.find()) {
            int badgeStart = lineStart + ts.start();
            int badgeEnd = lineStart + ts.end();
            applyBadge(builder, badgeStart, badgeEnd, tsBg, tsFg);
        }

        highlightPattern(builder, line, lineStart, HOST_HEADER, domainBg, domainFg);
        highlightPattern(builder, line, lineStart, URL_HOST, domainBg, domainFg);
        highlightPattern(builder, line, lineStart, RESOLVE_HOST, domainBg, domainFg);
        highlightPattern(builder, line, lineStart, IP_LINE, ipBg, ipFg);
        highlightIpv4(builder, line, lineStart, ipBg, ipFg);

        Matcher connectMs = CONNECT_MS.matcher(line);
        while (connectMs.find()) {
            int start = lineStart + connectMs.start();
            int end = lineStart + connectMs.end();
            applyBadge(builder, start, end, tsBg, tsFg);
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("===")) {
            setFg(builder, lineStart, lineEnd, result);
            builder.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (trimmed.contains("FAILED") || trimmed.contains("ERROR") || trimmed.contains("timed out")) {
            setFg(builder, lineStart, lineEnd, error);
        } else if (trimmed.startsWith("*") && trimmed.contains("Log saved to log center")) {
            setFg(builder, lineStart, lineEnd, result);
            highlightUuid(builder, line, lineStart, tsBg, tsFg);
        } else if (trimmed.startsWith("*") && trimmed.contains("Log save failed")) {
            setFg(builder, lineStart, lineEnd, error);
        }
    }

    private static void highlightUuid(
            SpannableStringBuilder builder,
            String line,
            int lineStart,
            int bg,
            int fg
    ) {
        Matcher uuidMatcher = UUID.matcher(line);
        while (uuidMatcher.find()) {
            applyBadge(builder, lineStart + uuidMatcher.start(), lineStart + uuidMatcher.end(), bg, fg);
        }
    }

    private static void highlightIpv4(
            SpannableStringBuilder builder,
            String line,
            int lineStart,
            int bg,
            int fg
    ) {
        Matcher matcher = IPV4.matcher(line);
        while (matcher.find()) {
            applyBadge(builder, lineStart + matcher.start(), lineStart + matcher.end(), bg, fg);
        }
    }

    private static void highlightPattern(
            SpannableStringBuilder builder,
            String line,
            int lineStart,
            Pattern pattern,
            int bg,
            int fg
    ) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            int start = lineStart + matcher.start(1);
            int end = lineStart + matcher.end(1);
            applyBadge(builder, start, end, bg, fg);
        }
    }

    private static void applyBadge(SpannableStringBuilder builder, int start, int end, int bg, int fg) {
        builder.setSpan(new BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(fg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void setFg(SpannableStringBuilder builder, int start, int end, int color) {
        if (start >= end) {
            return;
        }
        builder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int color(android.content.Context context, int resId) {
        return ContextCompat.getColor(context, resId);
    }
}
