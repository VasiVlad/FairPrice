package ru.vlad.unitprice;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Полигон парсера: рисует карточки во всех известных форматах.
 * Ожидаемые значения:
 *   Пятёрочка  13499₽ / 400 г      → 337 ₽/кг
 *   Перекрёсток 200 г • 399,99 ₽/шт → 2000 ₽/кг
 *   Купер      283,00 ₽ / 221 г     → 1281 ₽/кг
 *   Самокат    119 ₽ / 75 г         → 1587 ₽/кг
 *   Ozon       205 ₽ / 970 мл       → 211 ₽/л
 *   Бристоль   104 99 ₽ / 340Г      → 309 ₽/кг
 */
public final class TestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(12);
        root.setPadding(p, p, p, p);

        header(root, "Пятёрочка — 337 ₽/кг");
        row(root,
                card("Шампиньоны Global Village", "400 г", "134,99 ₽", false, false),
                card("Грудка цыпленка", "950 г", "59,99 ₽", false, false));

        header(root, "Перекрёсток — 2000 ₽/кг");
        row(root,
                card("Опята культивируемые", "200 г", "399,99", false, true),
                card("Грибы Шиитаке", "150 г", "279,99", false, true));

        header(root, "Купер — 1281 ₽/кг");
        row(root,
                card("Чикен Премьер", " 221 г", "283,00 ₽", false, false),
                card("Шримп Ролл", " 160 г", "468,00 ₽", false, false));

        header(root, "Самокат — 1587 ₽/кг");
        row(root,
                card("Вода Святой Источник", "75 г", "119 ₽", false, false),
                card("Огурцы короткоплодные", "450 г", "199 ₽", false, false));

        header(root, "Ozon — 211 ₽/л");
        row(root,
                card("Молоко высокобелковое 1,5% 970 мл", null, "price_205₽", true, false),
                card("Сливки питьевые 10,0%", null, "price_169₽", true, false));

        header(root, "Бристоль — 309 ₽/кг");
        row(root,
                card("Кукуруза сахарная СОЮЗ СПЕЛЫХ В/С ГОСТ Ж/Б 340Г", null, "104 99 ₽", true, false),
                card("Горошек зеленый СОЮЗ СПЕЛЫХ В/С ГОСТ Ж/Б 400Г", null, "104 99 ₽", true, false));

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UnitPriceService.allowSelfStatic = true;
        Prefs.setAllowSelf(this, true);
    }

    @Override
    protected void onPause() {
        UnitPriceService.allowSelfStatic = false;
        Prefs.setAllowSelf(this, false);
        super.onPause();
    }

    private void header(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(6);
        root.addView(t, lp);
    }

    private void row(LinearLayout root, LinearLayout a, LinearLayout b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a);
        row.addView(b);
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout card(String name, String weight, String price,
                              boolean priceAsDesc, boolean combined) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackgroundColor(0xFFF2F2F2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(lp);

        ImageView photo = new ImageView(this);
        photo.setBackgroundColor(0xFFBBDEFB);
        card.addView(photo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        if (combined) {
            card.addView(tv(weight + " • " + price + " ₽/шт", 14, Color.BLACK));
            card.addView(tv(name, 15, Color.BLACK));
        } else {
            if (priceAsDesc) {
                TextView p = new TextView(this);
                p.setContentDescription(price);
                p.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(34)));
                card.addView(p);
            } else {
                card.addView(tv(price, 22, Color.BLACK));
            }
            card.addView(tv(name, 15, Color.BLACK));
            if (weight != null) {
                card.addView(tv(weight, 14, Color.GRAY));
            }
        }
        return card;
    }

    private TextView tv(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
