package com.localchat.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small, dependency-free native Android client for the local LocalChat server.
 * It intentionally talks only to the user configured server; it never contacts a
 * cloud endpoint and does not expose the Ollama port to the phone.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "localchat";
    private static final String KEY_SERVER = "server";
    private static final String KEY_MODEL = "model";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_CHATS = "chats";
    private static final int BG = Color.rgb(14, 18, 22);
    private static final int SURFACE = Color.rgb(28, 34, 40);
    private static final int SURFACE_2 = Color.rgb(39, 47, 55);
    private static final int TEXT = Color.rgb(241, 245, 249);
    private static final int MUTED = Color.rgb(157, 171, 184);
    private static final int BLUE = Color.rgb(48, 125, 246);
    private static final int GREEN = Color.rgb(61, 205, 136);
    private static final int RED = Color.rgb(245, 105, 105);

    private SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ArrayList<Conversation> conversations = new ArrayList<>();
    private Conversation activeConversation;
    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText composer;
    private TextView statusView;
    private TextView titleView;
    private LinearLayout modeContainer;
    private String selectedMode = "standard";
    private boolean sending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadConversations();
        if (serverUrl().isEmpty()) showWelcome(); else showChat();
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }

    private void showWelcome() {
        LinearLayout root = column(BG, 24, 30, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView mark = label("✦", 44, BLUE, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        root.addView(mark, lp(-1, dp(68)));
        TextView heading = label("LocalChat", 30, TEXT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading, lp(-1, -2));
        TextView sub = label("Deine private KI auf deinem PC", 16, MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, margins(lp(-1, -2), 0, 8, 0, 28));

        LinearLayout card = column(SURFACE, 20, 20, 20, 20);
        card.setBackground(round(SURFACE, 22));
        card.addView(label("Mit LocalChat-Server verbinden", 19, TEXT, Typeface.BOLD));
        card.addView(margins(label("Trage die lokale Adresse deines Windows-PCs ein. Die App spricht nur mit diesem Server.", 14, MUTED, Typeface.NORMAL), 0, 8, 0, 18));
        EditText address = input("z. B. 192.168.3.67:8787", false);
        address.setText(serverUrl().replace("http://", "").replace("https://", ""));
        card.addView(address, lp(-1, dp(56)));
        TextView connect = button("Verbinden", BLUE, TEXT);
        card.addView(margins(connect, 0, 16, 0, 0));
        connect.setOnClickListener(v -> {
            String normalized = normalizeUrl(address.getText().toString());
            if (normalized.isEmpty()) {
                address.setError("Bitte eine Serveradresse eingeben.");
                return;
            }
            preferences.edit().putString(KEY_SERVER, normalized).apply();
            hideKeyboard(address);
            showChat();
        });
        root.addView(card, lp(-1, -2));

        TextView privacy = label("Privat im eigenen Netzwerk\nOllama bleibt auf dem PC; die App öffnet keinen weiteren Port.", 13, MUTED, Typeface.NORMAL);
        privacy.setGravity(Gravity.CENTER);
        root.addView(margins(privacy, 0, 24, 0, 0));
        setContentView(root);
    }

    private void showChat() {
        if (conversations.isEmpty()) createConversation();
        if (activeConversation == null) activeConversation = conversations.get(0);

        LinearLayout root = column(BG, 0, 0, 0, 0);
        root.addView(buildTopBar());

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        messageContainer = column(BG, 16, 18, 16, 18);
        chatScroll.addView(messageContainer, lp(-1, -2));
        root.addView(chatScroll, lp(-1, 0, 1));
        root.addView(buildComposer());
        setContentView(root);
        renderMessages();
        checkConnection(serverUrl(), token(), false);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(12), dp(12), dp(10));
        bar.setBackgroundColor(BG);

        TextView chats = iconButton("☰", "Chats");
        chats.setOnClickListener(v -> showHistory());
        bar.addView(chats, lp(dp(44), dp(44)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        titleView = label(activeConversation.title, 17, TEXT, Typeface.BOLD);
        titleView.setSingleLine(true);
        statusView = label("● Prüfe Verbindung", 12, MUTED, Typeface.NORMAL);
        center.addView(titleView, lp(-1, -2));
        center.addView(statusView, lp(-1, -2));
        bar.addView(center, lp(0, dp(46), 1));

        TextView fresh = iconButton("＋", "Neuer Chat");
        fresh.setOnClickListener(v -> { createConversation(); renderMessages(); });
        bar.addView(fresh, lp(dp(44), dp(44)));
        TextView settings = iconButton("⚙", "Einstellungen");
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings, lp(dp(44), dp(44)));
        return bar;
    }

    private View buildComposer() {
        LinearLayout outer = column(BG, 12, 0, 12, 12);
        modeContainer = new LinearLayout(this);
        modeContainer.setGravity(Gravity.CENTER_VERTICAL);
        modeContainer.setPadding(0, 0, 0, dp(10));
        outer.addView(modeContainer, lp(-1, dp(42)));
        renderModes();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(SURFACE, 24));
        composer = input("Nachricht an deine lokale KI", true);
        composer.setBackgroundColor(Color.TRANSPARENT);
        composer.setTextColor(TEXT);
        composer.setHintTextColor(MUTED);
        composer.setMinLines(1);
        composer.setMaxLines(5);
        row.addView(composer, lp(0, -2, 1));
        TextView send = iconButton("↑", "Senden");
        send.setTextSize(25);
        send.setTextColor(Color.WHITE);
        send.setBackground(round(BLUE, 19));
        send.setOnClickListener(v -> sendMessage());
        row.addView(send, margins(lp(dp(42), dp(42)), 8, 0, 0, 0));
        outer.addView(row, lp(-1, -2));
        return outer;
    }

    private void renderModes() {
        if (modeContainer == null) return;
        modeContainer.removeAllViews();
        addMode("fast", "Schnell", "kurz & flott");
        addMode("standard", "Standard", "ausgewogen");
        addMode("think", "Denken", "gründlicher");
    }

    private void addMode(String value, String name, String hint) {
        TextView choice = label(name, 13, value.equals(selectedMode) ? TEXT : MUTED, Typeface.BOLD);
        choice.setGravity(Gravity.CENTER);
        choice.setContentDescription(name + ", " + hint);
        choice.setPadding(dp(12), 0, dp(12), 0);
        choice.setBackground(round(value.equals(selectedMode) ? SURFACE_2 : BG, 18));
        choice.setOnClickListener(v -> { selectedMode = value; renderModes(); });
        modeContainer.addView(choice, margins(lp(-2, dp(36)), 0, 0, 8, 0));
    }

    private void renderMessages() {
        if (messageContainer == null || activeConversation == null) return;
        messageContainer.removeAllViews();
        if (activeConversation.messages.isEmpty()) {
            TextView greeting = label("Willkommen bei LocalChat", 26, TEXT, Typeface.BOLD);
            greeting.setGravity(Gravity.CENTER);
            messageContainer.addView(margins(greeting, 0, dp(56), 0, 0));
            TextView explanation = label("Deine Unterhaltung bleibt auf deinem Gerät. Wähle oben einen Modus und starte mit einer Nachricht.", 15, MUTED, Typeface.NORMAL);
            explanation.setGravity(Gravity.CENTER);
            messageContainer.addView(margins(explanation, dp(20), dp(12), dp(20), 0));
        } else {
            for (ChatMessage message : activeConversation.messages) addBubble(message);
        }
        if (titleView != null) titleView.setText(activeConversation.title);
        handler.postDelayed(() -> chatScroll.fullScroll(View.FOCUS_DOWN), 80);
    }

    private void addBubble(ChatMessage message) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity("user".equals(message.role) ? Gravity.RIGHT : Gravity.LEFT);
        TextView bubble = label(message.text, 16, TEXT, Typeface.NORMAL);
        bubble.setLineSpacing(0, 1.10f);
        bubble.setPadding(dp(15), dp(12), dp(15), dp(12));
        bubble.setTextIsSelectable(true);
        bubble.setBackground(round("user".equals(message.role) ? BLUE : SURFACE, 19));
        int max = (int) (getResources().getDisplayMetrics().widthPixels * .84f);
        line.addView(bubble, new LinearLayout.LayoutParams(max, -2));
        messageContainer.addView(line, margins(lp(-1, -2), 0, 0, 0, 12));
    }

    private void sendMessage() {
        String question = composer.getText().toString().trim();
        if (question.isEmpty() || sending) return;
        if (serverUrl().isEmpty()) { showWelcome(); return; }
        sending = true;
        composer.setText("");
        ChatMessage user = new ChatMessage("user", question);
        ChatMessage waiting = new ChatMessage("assistant", selectedMode.equals("think") ? "Ich prüfe das sorgfältig …" : "Einen Moment …");
        activeConversation.messages.add(user);
        activeConversation.messages.add(waiting);
        if (activeConversation.messages.size() == 2) activeConversation.title = makeTitle(question);
        saveConversations();
        renderMessages();

        final String endpoint = serverUrl();
        final String secret = token();
        final String model = selectedModel();
        final String mode = selectedMode;
        final JSONArray history = historyForRequest();
        network.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("message", question);
                body.put("history", history);
                body.put("mode", mode);
                if (!model.isEmpty()) body.put("model", model);
                JSONObject response = postJson(endpoint + "/chat", body, secret);
                String answer = response.optString("response", "").trim();
                if (answer.isEmpty()) answer = response.optString("error", "Keine Antwort erhalten.");
                final String finalAnswer = answer;
                handler.post(() -> finishMessage(waiting, finalAnswer, false));
            } catch (Exception error) {
                final String problem = friendlyError(error);
                handler.post(() -> finishMessage(waiting, problem, true));
            }
        });
    }

    private JSONArray historyForRequest() {
        JSONArray history = new JSONArray();
        int stop = Math.max(0, activeConversation.messages.size() - 2);
        int start = Math.max(0, stop - 16);
        try {
            for (int i = start; i < stop; i++) {
                ChatMessage item = activeConversation.messages.get(i);
                if (item.failed) continue;
                JSONObject turn = new JSONObject();
                turn.put("role", item.role);
                turn.put("content", item.text);
                history.put(turn);
            }
        } catch (Exception ignored) { }
        return history;
    }

    private void finishMessage(ChatMessage waiting, String content, boolean failed) {
        waiting.text = content;
        waiting.failed = failed;
        sending = false;
        saveConversations();
        renderMessages();
        if (failed) setStatus("● Nicht verbunden", RED);
        else setStatus("● Verbunden", GREEN);
    }

    private void checkConnection(String base, String secret, boolean toastOnResult) {
        setStatus("● Prüfe Verbindung", MUTED);
        network.execute(() -> {
            boolean available = false;
            String message = "Nicht erreichbar";
            try {
                JSONObject health = getJson(base + "/health", secret);
                available = health.optBoolean("ok", true);
                message = available ? "Verbunden" : "Server meldet ein Problem";
            } catch (Exception healthError) {
                try {
                    int code = getStatus(base, secret);
                    available = code >= 200 && code < 400;
                    message = available ? "Verbunden (älterer Server)" : "Nicht erreichbar";
                } catch (Exception ignored) { }
            }
            final boolean result = available;
            final String resultMessage = message;
            handler.post(() -> {
                setStatus(result ? "● " + resultMessage : "● " + resultMessage, result ? GREEN : RED);
                if (toastOnResult) Toast.makeText(this, result ? "Server ist erreichbar." : "Server nicht erreichbar. Adresse und WLAN prüfen.", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showHistory() {
        Dialog dialog = baseDialog("Chats");
        LinearLayout content = (LinearLayout) dialog.findViewById(101);
        TextView newChat = button("＋ Neuer Chat", BLUE, TEXT);
        content.addView(newChat, margins(lp(-1, dp(48)), 0, 0, 0, 12));
        newChat.setOnClickListener(v -> { dialog.dismiss(); createConversation(); renderMessages(); });
        for (Conversation conversation : conversations) {
            TextView row = label(conversation.title, 16, conversation.id.equals(activeConversation.id) ? TEXT : MUTED, Typeface.NORMAL);
            row.setSingleLine(true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setBackground(round(conversation.id.equals(activeConversation.id) ? SURFACE_2 : SURFACE, 14));
            row.setOnClickListener(v -> { activeConversation = conversation; dialog.dismiss(); renderMessages(); saveConversations(); });
            content.addView(row, margins(lp(-1, dp(52)), 0, 0, 0, 8));
        }
        dialog.show();
    }

    private void showSettings() {
        Dialog dialog = baseDialog("Einstellungen");
        LinearLayout content = (LinearLayout) dialog.findViewById(101);
        content.addView(label("SERVER", 12, MUTED, Typeface.BOLD));
        EditText server = input("192.168.3.67:8787", false);
        server.setText(serverUrl());
        content.addView(margins(server, 0, 7, 0, 16));
        content.addView(label("ZUGRIFFSCODE (optional)", 12, MUTED, Typeface.BOLD));
        EditText access = input("Nur wenn am Server gesetzt", false);
        access.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        access.setText(token());
        content.addView(margins(access, 0, 7, 0, 16));
        content.addView(label("MODELL", 12, MUTED, Typeface.BOLD));
        EditText model = input("Automatisch – Server-Standard", false);
        model.setText(selectedModel());
        content.addView(margins(model, 0, 7, 0, 6));
        content.addView(label("Tipp: qwen3:1.7b ist die schnelle Empfehlung für einen Ryzen 5 mit 16 GB RAM. Die Modellliste zeigt nur bereits auf dem PC installierte Modelle.", 13, MUTED, Typeface.NORMAL));
        TextView models = button("Geladene Modelle anzeigen", SURFACE_2, TEXT);
        content.addView(margins(models, 0, 14, 0, 8));
        models.setOnClickListener(v -> fetchModels(normalizeUrl(server.getText().toString()), access.getText().toString(), model));
        TextView test = button("Verbindung testen", SURFACE_2, TEXT);
        content.addView(margins(test, 0, 0, 0, 8));
        test.setOnClickListener(v -> checkConnection(normalizeUrl(server.getText().toString()), access.getText().toString(), true));
        TextView save = button("Einstellungen speichern", BLUE, TEXT);
        content.addView(save, margins(lp(-1, dp(50)), 0, 8, 0, 0));
        save.setOnClickListener(v -> {
            String address = normalizeUrl(server.getText().toString());
            if (address.isEmpty()) { server.setError("Serveradresse fehlt"); return; }
            preferences.edit().putString(KEY_SERVER, address)
                    .putString(KEY_TOKEN, access.getText().toString().trim())
                    .putString(KEY_MODEL, model.getText().toString().trim()).apply();
            dialog.dismiss();
            checkConnection(address, access.getText().toString().trim(), false);
        });
        dialog.show();
    }

    private void fetchModels(String base, String secret, EditText target) {
        if (base.isEmpty()) { target.setError("Zuerst Serveradresse eingeben"); return; }
        network.execute(() -> {
            try {
                JSONObject data = getJson(base + "/models", secret);
                JSONArray values = data.optJSONArray("models");
                final ArrayList<String> names = new ArrayList<>();
                names.add("");
                if (values != null) for (int i = 0; i < values.length(); i++) names.add(values.optString(i));
                handler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Auf dem PC verfügbare Modelle")
                        .setItems(names.toArray(new String[0]), (d, which) -> target.setText(names.get(which)))
                        .show());
            } catch (Exception error) {
                handler.post(() -> Toast.makeText(this, "Modellliste nicht verfügbar. Nutze den erweiterten LocalChat-Server.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private Dialog baseDialog(String title) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(SURFACE, 20, 20, 20, 20);
        content.setId(101);
        content.addView(label(title, 22, TEXT, Typeface.BOLD), margins(lp(-1, -2), 0, 0, 0, 16));
        scroll.addView(content, lp(-1, -2));
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(SURFACE, 24));
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * .93f), -2);
        }
        return dialog;
    }

    private void createConversation() {
        Conversation conversation = new Conversation();
        conversations.add(0, conversation);
        activeConversation = conversation;
        saveConversations();
    }

    private void loadConversations() {
        String raw = preferences == null ? "" : preferences.getString(KEY_CHATS, "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray stored = new JSONArray(raw);
            for (int i = 0; i < stored.length(); i++) {
                JSONObject source = stored.getJSONObject(i);
                Conversation conversation = new Conversation();
                conversation.id = source.optString("id", UUID.randomUUID().toString());
                conversation.title = source.optString("title", "Neuer Chat");
                JSONArray messages = source.optJSONArray("messages");
                if (messages != null) for (int m = 0; m < messages.length(); m++) {
                    JSONObject entry = messages.getJSONObject(m);
                    conversation.messages.add(new ChatMessage(entry.optString("role", "assistant"), entry.optString("text", ""), entry.optBoolean("failed", false)));
                }
                conversations.add(conversation);
            }
        } catch (Exception ignored) { conversations.clear(); }
    }

    private void saveConversations() {
        try {
            JSONArray stored = new JSONArray();
            int count = 0;
            for (Conversation conversation : conversations) {
                if (count++ >= 20) break;
                JSONObject result = new JSONObject();
                result.put("id", conversation.id);
                result.put("title", conversation.title);
                JSONArray messages = new JSONArray();
                int start = Math.max(0, conversation.messages.size() - 80);
                for (int i = start; i < conversation.messages.size(); i++) {
                    ChatMessage message = conversation.messages.get(i);
                    JSONObject entry = new JSONObject();
                    entry.put("role", message.role);
                    entry.put("text", message.text);
                    entry.put("failed", message.failed);
                    messages.put(entry);
                }
                result.put("messages", messages);
                stored.put(result);
            }
            preferences.edit().putString(KEY_CHATS, stored.toString()).apply();
        } catch (Exception ignored) { }
    }

    private JSONObject postJson(String endpoint, JSONObject payload, String secret) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(300000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!secret.isEmpty()) connection.setRequestProperty("X-LocalChat-Key", secret);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) { output.write(payload.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = connection.getResponseCode();
        String body = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (code >= 400) throw new Exception(new JSONObject(body).optString("error", "Serverfehler " + code));
        return new JSONObject(body);
    }

    private JSONObject getJson(String endpoint, String secret) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        if (!secret.isEmpty()) connection.setRequestProperty("X-LocalChat-Key", secret);
        int code = connection.getResponseCode();
        String body = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if (code >= 400) throw new Exception("Serverfehler " + code);
        return new JSONObject(body);
    }

    private int getStatus(String endpoint, String secret) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        if (!secret.isEmpty()) connection.setRequestProperty("X-LocalChat-Key", secret);
        return connection.getResponseCode();
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    private String serverUrl() { return normalizeUrl(preferences.getString(KEY_SERVER, "")); }
    private String token() { return preferences.getString(KEY_TOKEN, "").trim(); }
    private String selectedModel() { return preferences.getString(KEY_MODEL, "").trim(); }
    private String normalizeUrl(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.isEmpty()) return "";
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) clean = "http://" + clean;
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }
    private String makeTitle(String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > 38 ? clean.substring(0, 37) + "…" : clean;
    }
    private String friendlyError(Exception error) {
        String detail = error.getMessage() == null ? "" : error.getMessage();
        if (detail.contains("timed out")) return "Die Anfrage hat zu lange gedauert. Der PC oder das Modell ist gerade beschäftigt.";
        if (detail.contains("Connection refused") || detail.contains("failed to connect")) return "Der LocalChat-Server ist nicht erreichbar. Prüfe, ob er auf dem PC läuft.";
        return detail.isEmpty() ? "Verbindung zum LocalChat-Server fehlgeschlagen." : "Fehler: " + detail;
    }
    private void setStatus(String text, int color) { if (statusView != null) { statusView.setText(text); statusView.setTextColor(color); } }
    private LinearLayout column(int color, int left, int top, int right, int bottom) {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(left), dp(top), dp(right), dp(bottom)); layout.setBackgroundColor(color); return layout;
    }
    private TextView label(String text, float size, int color, int style) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private TextView button(String text, int background, int foreground) {
        TextView view = label(text, 15, foreground, Typeface.BOLD); view.setGravity(Gravity.CENTER); view.setBackground(round(background, 15)); view.setClickable(true); return view;
    }
    private TextView iconButton(String text, String description) {
        TextView view = label(text, 22, TEXT, Typeface.NORMAL); view.setGravity(Gravity.CENTER); view.setContentDescription(description); view.setBackground(round(SURFACE, 14)); view.setClickable(true); return view;
    }
    private EditText input(String hint, boolean multiline) {
        EditText view = new EditText(this); view.setTextSize(16); view.setTextColor(TEXT); view.setHintTextColor(MUTED); view.setHint(hint); view.setPadding(dp(14), 0, dp(14), 0); view.setBackground(round(SURFACE_2, 15)); view.setSingleLine(!multiline); if (multiline) view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE); return view;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams lp(int width, int height, float weight) { return new LinearLayout.LayoutParams(width, height, weight); }
    private <T extends View> T margins(T view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = view.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) view.getLayoutParams() : lp(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        view.setLayoutParams(params);
        return view;
    }
    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params, int left, int top, int right, int bottom) { params.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return params; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void hideKeyboard(View view) { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0); }

    private static class Conversation {
        String id = UUID.randomUUID().toString();
        String title = "Neuer Chat";
        ArrayList<ChatMessage> messages = new ArrayList<>();
    }
    private static class ChatMessage {
        String role, text; boolean failed;
        ChatMessage(String role, String text) { this(role, text, false); }
        ChatMessage(String role, String text, boolean failed) { this.role = role; this.text = text; this.failed = failed; }
    }
}
