package com.example.helloworld;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements
        CategoryAdapter.Listener,
        ChannelAdapter.Listener {

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (SMART-TV; LINUX; Tizen 5.5) AppleWebKit/537.36 Chrome/79.0.3945.79 Safari/537.36",
            "VLC/3.0.20 LibVLC/3.0.20",
            "ChannelTracer/1.0"
    };

    private static final String[] USER_AGENT_LABELS = {
            "Chrome 120 (Windows)",
            "Tizen 5.5 Smart TV",
            "VLC 3.0",
            "ChannelTracer"
    };

    private final ExecutorService apiExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText hostInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText categorySearch;
    private EditText channelSearch;
    private EditText secondsInput;
    private EditText streamUrlInput;
    private Button reloadButton;
    private Button startButton;
    private Spinner userAgentSpinner;
    private TextView resultText;
    private TextView logUuidText;
    private ScrollView debugScroll;
    private RecyclerView categoryList;
    private RecyclerView channelList;

    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;

    private Category selectedCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);

        hostInput = findViewById(R.id.hostInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        categorySearch = findViewById(R.id.categorySearch);
        channelSearch = findViewById(R.id.channelSearch);
        secondsInput = findViewById(R.id.secondsInput);
        streamUrlInput = findViewById(R.id.streamUrlInput);
        reloadButton = findViewById(R.id.reloadButton);
        startButton = findViewById(R.id.startButton);
        userAgentSpinner = findViewById(R.id.userAgentSpinner);
        resultText = findViewById(R.id.resultText);
        logUuidText = findViewById(R.id.logUuidText);
        debugScroll = findViewById(R.id.debugScroll);

        categoryList = findViewById(R.id.categoryList);
        channelList = findViewById(R.id.channelList);

        categoryAdapter = new CategoryAdapter(this);
        channelAdapter = new ChannelAdapter(this);

        categoryList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setLayoutManager(new LinearLayoutManager(this));
        categoryList.setAdapter(categoryAdapter);
        channelList.setAdapter(channelAdapter);

        ArrayAdapter<String> uaAdapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                USER_AGENT_LABELS
        );
        uaAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        userAgentSpinner.setAdapter(uaAdapter);

        restoreSession();

        reloadButton.setOnClickListener(v -> reloadData());
        startButton.setOnClickListener(v -> startSelectedTests());

        categorySearch.addTextChangedListener(simpleWatcher(text -> categoryAdapter.setFilter(text)));
        channelSearch.addTextChangedListener(simpleWatcher(text -> channelAdapter.setFilter(text)));
    }

    private void reloadData() {
        String host = textOf(hostInput);
        String username = textOf(usernameInput);
        String password = textOf(passwordInput);

        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            toast(R.string.credentials_required);
            return;
        }

        setLoading(true);
        publishPlainOutput(getString(R.string.loading));
        selectedCategory = null;
        safeUpdateChannels(new ArrayList<>());
        channelAdapter.setSelectedStreamId(-1);
        streamUrlInput.setText("");

        apiExecutor.execute(() -> {
            try {
                List<Category> categories = XtreamApi.fetchCategories(host, username, password);

                runOnUiSafely(() -> {
                    categoryList.post(() -> categoryAdapter.setItems(categories));
                    categoryAdapter.setSelectedId("");
                    publishPlainOutput("");
                    setLoading(false);
                });
            } catch (Throwable e) {
                runOnUiSafely(() -> {
                    publishPlainOutput(getString(R.string.load_failed, errorMessage(e)));
                    setLoading(false);
                });
            }
        });
    }

    @Override
    public void onCategoryClick(Category category) {
        selectedCategory = category;
        categoryAdapter.setSelectedId(category.id);

        String host = textOf(hostInput);
        String username = textOf(usernameInput);
        String password = textOf(passwordInput);

        setLoading(true);
        apiExecutor.execute(() -> {
            try {
                List<Channel> channels = XtreamApi.fetchChannels(host, username, password, category.id);
                runOnUiSafely(() -> {
                    categoryAdapter.updateCount(category.id, channels.size());
                    safeUpdateChannels(channels);
                    channelAdapter.setSelectedStreamId(-1);
                    streamUrlInput.setText("");
                    setLoading(false);
                });
            } catch (Throwable e) {
                runOnUiSafely(() -> {
                    publishPlainOutput(getString(R.string.load_failed, errorMessage(e)));
                    setLoading(false);
                });
            }
        });
    }

    @Override
    public void onChannelClick(Channel channel) {
        try {
            channelAdapter.setSelectedStreamId(channel.streamId);
            streamUrlInput.setText(buildStreamUrl(channel));
        } catch (Throwable e) {
            publishPlainOutput(getString(R.string.load_failed, errorMessage(e)));
        }
    }

    @Override
    public void onChannelCheckedChanged(Channel channel, boolean checked) {
        channel.selected = checked;
    }

    private void startSelectedTests() {
        List<Channel> selected = channelAdapter.getSelectedChannels();
        String manualUrl = textOf(streamUrlInput);

        if (selected.isEmpty() && manualUrl.isEmpty()) {
            toast(R.string.no_selection);
            return;
        }

        int seconds = parseSeconds();
        String userAgent = USER_AGENTS[userAgentSpinner.getSelectedItemPosition()];
        int maxReadMs = seconds * 1000;
        final int maxSeconds = seconds;
        final String noUrlMessage = getString(R.string.no_url);

        startButton.setEnabled(false);
        reloadButton.setEnabled(false);
        showLogUuidPending();
        publishPlainOutput(getString(R.string.testing));

        testExecutor.execute(() -> {
            StringBuilder output = new StringBuilder();
            long[] lastPublish = {0L};

            if (selected.isEmpty()) {
                runStreamTest(output, lastPublish, "Manual URL", manualUrl, userAgent, maxReadMs, seconds, noUrlMessage);
            } else {
                for (Channel channel : selected) {
                    runStreamTest(output, lastPublish, channel.name + " (" + channel.streamId + ")",
                            buildStreamUrl(channel), userAgent, maxReadMs, seconds, noUrlMessage);
                }
            }

            publishFormattedOutput(output.toString(), true);
            runOnUiSafely(() -> {
                startButton.setEnabled(true);
                reloadButton.setEnabled(true);
            });
        });
    }

    private void runStreamTest(
            StringBuilder output,
            long[] lastPublish,
            String label,
            String url,
            String userAgent,
            int maxReadMs,
            int maxSeconds,
            String noUrlMessage
    ) {
        if (url.isEmpty()) {
            appendLine(output, label + ": " + noUrlMessage);
            publishFormattedOutput(output.toString(), true);
            return;
        }

        int sectionStart = output.length();

        appendLine(output, "=== " + label + " ===");
        publishFormattedOutput(output.toString(), true);

        StreamTester.Result result = StreamTester.test(url, userAgent, maxReadMs, line -> {
            appendLine(output, line);
            long now = System.currentTimeMillis();
            if (now - lastPublish[0] >= 200) {
                lastPublish[0] = now;
                publishFormattedOutput(output.toString(), true);
            }
        });
        appendLine(output, result.summary);
        publishFormattedOutput(output.toString(), true);

        String traceText = output.substring(sectionStart);
        saveTraceLog(output, lastPublish, url, traceText, maxSeconds);
    }

    private void saveTraceLog(StringBuilder output, long[] lastPublish, String streamingUrl, String traceText, int maxSeconds) {
        try {
            TraceLogApi.SaveResult saved = TraceLogApi.save(streamingUrl, traceText, maxSeconds);
            appendLine(output, "* Log saved to log center — uuid: " + saved.logUuid);
            showLogUuid(saved.logUuid);
        } catch (Throwable e) {
            appendLine(output, "* Log save failed: " + errorMessage(e));
            showLogUuidFailed(errorMessage(e));
        }
        lastPublish[0] = System.currentTimeMillis();
        publishFormattedOutput(output.toString(), true);
    }

    private void showLogUuidPending() {
        runOnUiSafely(() -> {
            if (logUuidText == null) {
                return;
            }
            logUuidText.setText(R.string.log_uuid_saving);
            logUuidText.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        });
    }

    private void showLogUuid(String uuid) {
        runOnUiSafely(() -> {
            if (logUuidText == null) {
                return;
            }
            logUuidText.setText(uuid);
            logUuidText.setTextColor(ContextCompat.getColor(this, R.color.debug_result));
        });
    }

    private void showLogUuidFailed(String message) {
        runOnUiSafely(() -> {
            if (logUuidText == null) {
                return;
            }
            logUuidText.setText(getString(R.string.log_uuid_hint));
            logUuidText.setTextColor(ContextCompat.getColor(this, R.color.debug_error));
        });
    }

    private String buildStreamUrl(Channel channel) {
        return XtreamApi.buildLiveStreamUrl(
                textOf(hostInput),
                textOf(usernameInput),
                textOf(passwordInput),
                channel.streamId,
                "ts"
        );
    }

    private int parseSeconds() {
        try {
            int value = Integer.parseInt(textOf(secondsInput));
            if (value < 1) {
                return 1;
            }
            if (value > 60) {
                return 60;
            }
            return value;
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    private void safeUpdateChannels(List<Channel> channels) {
        channelList.post(() -> channelAdapter.setItems(channels));
    }

    private void publishPlainOutput(String text) {
        runOnUiSafely(() -> resultText.setText(text));
    }

    private void publishFormattedOutput(String text, boolean scrollToEnd) {
        runOnUiSafely(() -> {
            resultText.setText(DebugFormatter.format(this, text));
            if (scrollToEnd) {
                scrollDebugToBottom();
            }
        });
    }

    private void scrollDebugToBottom() {
        if (debugScroll == null) {
            return;
        }
        debugScroll.post(() -> {
            debugScroll.fullScroll(View.FOCUS_DOWN);
            resultText.post(() -> debugScroll.scrollTo(0, resultText.getBottom()));
        });
    }

    private void runOnUiSafely(Runnable action) {
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            try {
                action.run();
            } catch (Throwable e) {
                publishPlainOutput(getString(R.string.load_failed, errorMessage(e)));
                setLoading(false);
            }
        });
    }

    private static String errorMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private void setLoading(boolean loading) {
        reloadButton.setEnabled(!loading);
    }

    private String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private TextWatcher simpleWatcher(TextListener listener) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                listener.onChange(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    @Override
    protected void onPause() {
        saveSession();
        super.onPause();
    }

    private void restoreSession() {
        SessionStore.Session session = SessionStore.load(this);
        hostInput.setText(session.host);
        usernameInput.setText(session.username);
        passwordInput.setText(session.password);
        secondsInput.setText(session.seconds);
        streamUrlInput.setText(session.streamUrl);

        int index = session.userAgentIndex;
        if (index < 0 || index >= USER_AGENT_LABELS.length) {
            index = 0;
        }
        userAgentSpinner.setSelection(index);
    }

    private void saveSession() {
        SessionStore.save(
                this,
                textOf(hostInput),
                textOf(usernameInput),
                textOf(passwordInput),
                textOf(secondsInput),
                userAgentSpinner.getSelectedItemPosition(),
                textOf(streamUrlInput)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        apiExecutor.shutdownNow();
        testExecutor.shutdownNow();
    }

    private interface TextListener {
        void onChange(String text);
    }
}
