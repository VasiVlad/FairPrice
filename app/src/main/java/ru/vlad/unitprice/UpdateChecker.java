package ru.vlad.unitprice;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Проверка обновлений через GitHub Releases. Запрос только по нажатию кнопки. */
final class UpdateChecker {

    // Владелец/репозиторий на GitHub.
    static final String REPO = "VasiVlad/FairPrice";

    interface Callback {
        void onResult(String message, String downloadUrl);
    }

    private UpdateChecker() {
    }

    static void check(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + REPO + "/releases/latest");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "UnitPrice-Android");

                int code = c.getResponseCode();
                if (code != 200) {
                    callback.onResult("Не удалось проверить обновления (HTTP " + code + ")", null);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line);
                    }
                }

                JSONObject json = new JSONObject(sb.toString());
                String tag = json.optString("tag_name", "");
                String htmlUrl = json.optString("html_url", "");
                String downloadUrl = htmlUrl;

                JSONArray assets = json.optJSONArray("assets");
                if (assets != null && assets.length() > 0) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String name = a.optString("name", "");
                        if (name.endsWith(".apk")) {
                            downloadUrl = a.optString("browser_download_url", htmlUrl);
                            break;
                        }
                    }
                }

                String current = currentVersion(app);
                String latest = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;

                if (compare(latest, current) > 0) {
                    callback.onResult("Доступна версия " + latest + " (у вас " + current + ")",
                            downloadUrl);
                } else {
                    callback.onResult("У вас последняя версия (" + current + ")", null);
                }
            } catch (Exception e) {
                callback.onResult("Не удалось проверить: " + e.getMessage(), null);
            }
        }).start();
    }

    private static String currentVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName == null ? "0" : pi.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Сравнивает версии вида 0.2 / 1.0.3. Возвращает >0, если a новее b. */
    private static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseInt(pa[i]) : 0;
            int y = i < pb.length ? parseInt(pb[i]) : 0;
            if (x != y) {
                return x - y;
            }
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9].*$", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
