package ru.vlad.unitprice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private TextView status;
    private TextView updateStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        root.setPadding(p, p, p, p);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Цена за кг");
        title.setTextSize(26f);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Показывает цену за килограмм поверх карточек товаров.\n"
                + "Работает без интернета и не отправляет данные наружу.");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        root.addView(subtitle, subLp);

        status = new TextView(this);
        status.setTextSize(16f);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(24);
        root.addView(status, stLp);

        Button open = new Button(this);
        open.setText("Открыть настройки доступности");
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        root.addView(open, subLp);

        Switch all = new Switch(this);
        all.setText("Работать во всех приложениях");
        all.setChecked(Prefs.allApps(this));
        all.setOnCheckedChangeListener((v, checked) -> Prefs.setAllApps(this, checked));
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.topMargin = dp(16);
        root.addView(all, swLp);

        Switch overlaySwitch = new Switch(this);
        overlaySwitch.setText("Показывать чипы");
        overlaySwitch.setChecked(Prefs.overlayEnabled(this));
        overlaySwitch.setOnCheckedChangeListener((v, checked) -> Prefs.setOverlayEnabled(this, checked));
        LinearLayout.LayoutParams swLp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp2.topMargin = dp(8);
        root.addView(overlaySwitch, swLp2);

        TextView hint = new TextView(this);
        hint.setText("Если чипы не появляются: включи службу «Цена за кг» в списке доступности, "
                + "затем открой приложение магазина и немного прокрути список товаров.\n\n"
                + "Быстро включить и выключить чипы можно плиткой «Цена за кг» в шторке "
                + "быстрых настроек (нажми карандаш в шторке и перетащи плитку).");
        hint.setTextSize(14f);
        hint.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(20);
        root.addView(hint, hLp);

        TextView apps = new TextView(this);
        apps.setText("Проверено на: Пятёрочка, Перекрёсток.\nДальше: Самокат, Ozon Fresh.");
        apps.setTextSize(13f);
        apps.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(8);
        root.addView(apps, aLp);

        updateStatus = new TextView(this);
        updateStatus.setTextSize(14f);
        updateStatus.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uLp.topMargin = dp(16);
        root.addView(updateStatus, uLp);

        Button checkUpdates = new Button(this);
        checkUpdates.setText("Проверить обновления");
        checkUpdates.setOnClickListener(v -> checkUpdates(checkUpdates));
        root.addView(checkUpdates, uLp);

        Button testParser = new Button(this);
        testParser.setText("Тест парсера");
        testParser.setOnClickListener(v -> startActivity(new Intent(this, TestActivity.class)));
        root.addView(testParser, uLp);

        setContentView(root);
    }

    private void checkUpdates(Button button) {
        button.setEnabled(false);
        updateStatus.setText("Проверяю обновления…");
        UpdateChecker.check(this, (message, url) -> runOnUiThread(() -> {
            button.setEnabled(true);
            updateStatus.setText(message);
            if (url != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Доступно обновление")
                        .setMessage(message)
                        .setPositiveButton("Скачать", (d, w) -> {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("Позже", null)
                        .show();
            }
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (status == null) {
            return;
        }
        if (isServiceEnabled()) {
            status.setText("Служба включена — чипы должны появляться в приложениях.");
            status.setTextColor(0xFF207A3A);
        } else {
            status.setText("Служба выключена. Включи «Цена за кг» в настройках доступности.");
            status.setTextColor(0xFFB00020);
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
