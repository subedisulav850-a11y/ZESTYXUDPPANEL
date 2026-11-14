package com.ff.zesty.proxy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class LoginActivity extends Activity {

    private String API_URL;
    private String GAME_NAME;
    private String TELEGRAM_URL;

    private static final String PREFS     = "zesty_proxy_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER  = "auth_user";

    private EditText etKey;
    private TextView tvStatus;
    private View dot;
    private Button btnActivate;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_login);
        try { if (getActionBar() != null) getActionBar().hide(); } catch (Exception ignored) { }

        API_URL      = NativeBridge.getApiUrl();
        GAME_NAME    = NativeBridge.getGame();
        TELEGRAM_URL = NativeBridge.getTelegram();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String rawToken = prefs.getString(KEY_TOKEN, "");
        String token = Crypto.dec(rawToken);
        if (token != null && !token.isEmpty()) {
            goMain();
            return;
        }

        etKey = (EditText) findViewById(R.id.et_license_key);
        if (etKey != null) {
            etKey.setHint("Enter your Key...");
            etKey.setSaveEnabled(false);
        }
        
        tvStatus = (TextView) findViewById(R.id.tv_login_status);
        dot = findViewById(R.id.dot_login);
        btnActivate = (Button) findViewById(R.id.btn_activate);

        int progressId =getResources().getIdentifier("progress_login", "id", getPackageName());
        if (progressId != 0) {
            progress = (ProgressBar) findViewById(progressId);
        }

        if (btnActivate != null) {
            btnActivate.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    doOnlineLogin();
                }
            });
        }

        LinearLayout llTelegram = (LinearLayout) findViewById(R.id.ll_telegram);
        if (llTelegram != null) {
            llTelegram.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openTelegram(); }
            });
        }

        LinearLayout llWhatsApp = (LinearLayout) findViewById(R.id.ll_whatsapp);
        if (llWhatsApp != null) {
            llWhatsApp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openWhatsApp(); }
            });
        }
    }

    private void doOnlineLogin() {
        final String key = etKey != null ? etKey.getText().toString().trim() : "";

        if (key.isEmpty()) {
            setStatus("Please enter your Key!", R.drawable.dot_warn);
            return;
        }

        if (btnActivate != null) btnActivate.setEnabled(false);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        setStatus("Connecting to server...", R.drawable.dot_on);

        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... voids) {
                try {
                    String hwid = Settings.Secure.getString(
                            getContentResolver(), Settings.Secure.ANDROID_ID);
                    if (hwid == null || hwid.isEmpty()) hwid = "unknown";

                    String postData =
                        "game=" + URLEncoder.encode(GAME_NAME, "UTF-8") +
                        "&user_key=" + URLEncoder.encode(key, "UTF-8") +
                        "&serial=" + URLEncoder.encode(hwid, "UTF-8");

                    URL url = new URL(API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded; charset=UTF-8");
                    conn.setRequestProperty("User-Agent", "YungPing/1.0");

                    OutputStream os = conn.getOutputStream();
                    os.write(postData.getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                            "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    if (code < 200 || code >= 300)
                        return "{\"status\":false,\"reason\":\"HTTP " + code + "\"}";
                    return sb.toString();

                } catch (Exception e) {
                    return "{\"status\":false,\"reason\":\"Connection Error: " + e.getMessage() + "\"}";
                }
            }

            @Override protected void onPostExecute(String result) {
                if (progress != null) progress.setVisibility(View.GONE);
                if (btnActivate != null) btnActivate.setEnabled(true);

                try {
                    JSONObject json = new JSONObject(result.trim());
                    boolean ok = json.optBoolean("status", false);

                    if (ok) {
                        saveAndGo(key);
                    } else {
                        String reason = json.optString("reason",
                                        json.optString("msg",
                                        json.optString("message",
                                        "Invalid Key!")));
                        setStatus(reason, R.drawable.dot_warn);
                        showTelegramDialog(reason);
                    }
                } catch (Exception e) {
                    setStatus("Invalid server response data!", R.drawable.dot_warn);
                    showTelegramDialog("Failed to connect to the server!");
                }
            }
        }.execute();
    }

    private void openTelegram() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL));
            i.setPackage("org.telegram.messenger");
            try { startActivity(i); }
            catch (Exception e1) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Telegram!", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.com/channel/0029Vb8ytI36buMENU7E453o")));
        } catch (Throwable ignored) {
            Toast.makeText(this, "Could not open WhatsApp!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTelegramDialog(String reason) {
        new AlertDialog.Builder(this)
            .setTitle("🔑 Get Key")
            .setMessage(reason + "\n\nJoin Telegram to get your key!")
            .setPositiveButton("OPEN TELEGRAM", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { openTelegram(); }
            })
            .setNegativeButton("CLOSE", null)
            .show();
    }

    private void saveAndGo(String key) {
        String tokenEnc = Crypto.enc("KEY_USER|" + key);
        String userEnc  = Crypto.enc(key);

        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putString(KEY_TOKEN, tokenEnc);
        ed.putString(KEY_USER, userEnc);
        ed.apply();

        setStatus("Login Successful!", R.drawable.dot_on);
        goMain();
    }

    private void setStatus(String value, int dotRes) {
        if (tvStatus != null) tvStatus.setText(value);
        if (dot != null) dot.setBackgroundResource(dotRes);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
