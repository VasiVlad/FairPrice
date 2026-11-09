package ru.vlad.unitprice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class MainActivity extends Activity {

    private static final int PRIMARY = 0xFF1E7A3C;
    private static final int PRIMARY_DARK = 0xFF145C2C;
    private static final int TEXT = 0xFF17201A;
    private static final int MUTED = 0xFF6B7280;
    private static final int DIVIDER = 0xFFE6EAE6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int DANGER = 0xFFC62828;
    private static final int BG = 0xFFF5F7F4;

    private TextView status;
    private View statusDot;
    private TextView statusButton;
    private TextView updateMessage;

    private int versionTaps;
    private long lastVersionTap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(hero());
        root.addView(statusCard(), top(16));
        root.addView(section("Отображение"), sectionLp(26));
        root.addView(displayCard());
        root.addView(section("Обновления"), sectionLp(26));
        root.addView(updateCard());
        root.addView(section("Как включить"), sectionLp(26));
        root.addView(howToCard());
        root.addView(section("Магазины"), sectionLp(26));
        root.addView(storesCard());
        root.addView(footer(), top(24));

        setContentView(scroll);
    }

    private View hero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2E8B4A, PRIMARY_DARK});
        bg.setCornerRadius(dp(22));
        hero.setBackground(bg);
        hero.setElevation(dp(3));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(74), dp(74));
        logoLp.rightMargin = dp(12);
        hero.addView(logo, logoLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Цена за кг");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Честная цена в любимых магазинах");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xE6FFFFFF);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(2);
        texts.addView(subtitle, subLp);

        TextView note = new TextView(this);
        note.setText("Считает на телефоне. Без рекламы и сбора данных.");
        note.setTextSize(12);
        note.setTextColor(0xB3FFFFFF);
        LinearLayout.LayoutParams noteLp = wrap();
        noteLp.topMargin = dp(6);
        texts.addView(note, noteLp);

        hero.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return hero;
    }

    private View statusCard() {
        LinearLayout card = card();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotLp.rightMargin = dp(10);
        row.addView(statusDot, dotLp);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(TEXT);
        row.addView(status, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(row);

        statusButton = pill("Настройки доступности", true);
        statusButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {
                status.setText("Откройте настройки → Специальные возможности вручную.");
            }
        });
        card.addView(statusButton, top(14));
        return card;
    }

    private View displayCard() {
        LinearLayout card = card();

        card.addView(switchRow(
                "Показывать цену за кг",
                "Чипы на карточках товаров",
                Prefs.overlayEnabled(this),
                (v, checked) -> Prefs.setOverlayEnabled(this, checked)));

        card.addView(divider(), top(14));

        card.addView(switchRow(
                "Во всех приложениях",
                "Выключите, чтобы работать только в магазинах",
                Prefs.allApps(this),
                (v, checked) -> Prefs.setAllApps(this, checked)));

        return card;
    }

    private View updateCard() {
        LinearLayout card = card();

        TextView version = new TextView(this);
        version.setText("Версия " + versionName());
        version.setTextSize(16);
        version.setTextColor(TEXT);
        version.setTypeface(Typeface.DEFAULT_BOLD);
        version.setOnClickListener(v -> onVersionTap());
        card.addView(version);

        TextView hint = new TextView(this);
        hint.setText("Обновления приходят с GitHub. Скачивание и установка — прямо в приложении.");
        hint.setTextSize(13);
        hint.setTextColor(MUTED);
        hint.setLineSpacing(0, 1.25f);
        card.addView(hint, top(6));

        updateMessage = new TextView(this);
        updateMessage.setText("Проверьте, есть ли новая версия.");
        updateMessage.setTextSize(13);
        updateMessage.setTextColor(MUTED);
        card.addView(updateMessage, top(10));

        TextView check = pill("Проверить обновления", true);
        check.setOnClickListener(v -> checkUpdates(check));
        card.addView(check, top(14));
        return card;
    }

    private View howToCard() {
        LinearLayout card = card();

        TextView steps = new TextView(this);
        steps.setText("1. Откройте «Настройки» → «Специальные возможности».\n"
                + "2. Найдите «Цена за кг» и включите переключатель.\n"
                + "3. Откройте приложение магазина и прокрутите список товаров.\n"
                + "4. Если цены не появляются — отключите оптимизацию батареи.");
        steps.setTextSize(14);
        steps.setTextColor(0xFF374151);
        steps.setLineSpacing(0, 1.35f);
        card.addView(steps);

        TextView note = new TextView(this);
        note.setText("На Xiaomi и OnePlus также включите автозапуск и «Батарея без ограничений».");
        note.setTextSize(13);
        note.setTextColor(MUTED);
        note.setLineSpacing(0, 1.25f);
        card.addView(note, top(12));
        return card;
    }

    private View storesCard() {
        LinearLayout card = card();

        TextView list = new TextView(this);
        list.setText("Пятёрочка · Перекрёсток · Купер · Самокат\n"
                + "Ozon Fresh · ВкусВилл · Бристоль\n"
                + "Детский мир");
        list.setTextSize(14);
        list.setTextColor(TEXT);
        list.setLineSpacing(0, 1.35f);
        card.addView(list);

        TextView note = new TextView(this);
        note.setText("Магнит и Дикси — в работе. Список расширяется.");
        note.setTextSize(13);
        note.setTextColor(MUTED);
        card.addView(note, top(10));
        return card;
    }

    private View footer() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView open = new TextView(this);
        open.setText("Открытый исходный код. Без рекламы, аналитики и сбора данных.\n"
                + "Интернет — только для проверки обновлений.");
        open.setTextSize(12);
        open.setTextColor(MUTED);
        open.setGravity(Gravity.CENTER);
        open.setLineSpacing(0, 1.25f);
        footer.addView(open);

        TextView github = new TextView(this);
        github.setText("github.com/VasiVlad/FairPrice");
        github.setTextSize(12);
        github.setTextColor(PRIMARY);
        github.setTypeface(Typeface.DEFAULT_BOLD);
        github.setGravity(Gravity.CENTER);
        github.setPadding(dp(8), dp(8), dp(8), dp(8));
        github.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/VasiVlad/FairPrice")));
            } catch (Exception ignored) {
            }
        });
        footer.addView(github, top(4));
        return footer;
    }

    private LinearLayout switchRow(String title, String subtitle, boolean checked,
                                   CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextSize(13);
        s.setTextColor(MUTED);
        texts.addView(s, top(2));

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setOnCheckedChangeListener(listener);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.leftMargin = dp(12);
        row.addView(toggle, swLp);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setElevation(dp(2));
        return card;
    }

    private TextView section(String text) {
        TextView v = new TextView(this);
        v.setText(text.toUpperCase());
        v.setTextSize(12);
        v.setTextColor(MUTED);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.08f);
        return v;
    }

    private LinearLayout.LayoutParams sectionLp(int margin) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(margin);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(DIVIDER);
        return v;
    }

    private TextView pill(String text, boolean filled) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(13), dp(18), dp(13));
        b.setMinHeight(dp(48));
        b.setFocusable(true);
        b.setClickable(true);
        stylePill(b, filled);
        return b;
    }

    private void stylePill(TextView b, boolean filled) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (filled) {
            bg.setColor(PRIMARY);
        } else {
            bg.setColor(CARD);
            bg.setStroke(dp(1.5f), PRIMARY);
        }
        b.setBackground(new RippleDrawable(
                ColorStateList.valueOf(filled ? 0x33FFFFFF : 0x221E7A3C), bg, null));
        b.setTextColor(filled ? Color.WHITE : PRIMARY);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = dp(margin);
        return lp;
    }

    private void checkUpdates(TextView button) {
        button.setEnabled(false);
        updateMessage.setText("Проверяю обновления…");
        UpdateChecker.check(this, (message, url) -> runOnUiThread(() -> {
            button.setEnabled(true);
            updateMessage.setText(message);
            if (url != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Доступно обновление")
                        .setMessage("Вышла новая версия приложения. Скачать и установить?")
                        .setPositiveButton("Установить", (d, w) -> updateTo(url))
                        .setNegativeButton("Позже", null)
                        .show();
            }
        }));
    }

    private void updateTo(String url) {
        if (!canInstall()) {
            openUnknownSources();
            return;
        }
        updateMessage.setText("Скачиваю обновление…");
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("не удалось создать папку");
                }
                File apk = new File(dir, "fairprice-update.apk");

                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "FairPrice-Android");

                int code = c.getResponseCode();
                if (code != 200) {
                    throw new Exception("HTTP " + code);
                }
                try (InputStream in = c.getInputStream();
                     FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                }

                runOnUiThread(() -> {
                    updateMessage.setText("Скачано. Открываю установщик…");
                    try {
                        UpdateInstaller.install(this, apk);
                    } catch (Exception e) {
                        updateMessage.setText("Не удалось запустить установку: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> updateMessage.setText("Не удалось скачать: " + e.getMessage()));
            }
        }).start();
    }

    private boolean canInstall() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
    }

    private void openUnknownSources() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            updateMessage.setText("Разрешите установку из этого источника и попробуйте снова.");
        } catch (Exception e) {
            updateMessage.setText("Разрешите установку из неизвестных источников в настройках системы.");
        }
    }

    private void onVersionTap() {
        long now = System.currentTimeMillis();
        if (now - lastVersionTap > 1500) {
            versionTaps = 0;
        }
        lastVersionTap = now;
        versionTaps++;
        if (versionTaps >= 7) {
            versionTaps = 0;
            startActivity(new Intent(this, TestActivity.class));
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "—";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) {
            return;
        }
        boolean enabled = isServiceEnabled();

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(enabled ? PRIMARY : DANGER);
        statusDot.setBackground(dot);

        if (enabled) {
            status.setText("Служба включена — цены появятся в магазинах.");
            status.setTextColor(TEXT);
            statusButton.setText("Настройки доступности");
            stylePill(statusButton, false);
        } else {
            status.setText("Служба выключена. Включите её, чтобы видеть цены.");
            status.setTextColor(DANGER);
            statusButton.setText("Включить службу");
            stylePill(statusButton, true);
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        String me = getPackageName() + "/" + UnitPriceService.class.getName();
        String meShort = getPackageName() + "/.UnitPriceService";
        return enabled.contains(me) || enabled.contains(meShort);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
