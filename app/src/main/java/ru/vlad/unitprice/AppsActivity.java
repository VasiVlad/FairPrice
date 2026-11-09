package ru.vlad.unitprice;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppsActivity extends Activity {

    private static final int PRIMARY = 0xFF1E7A3C;
    private static final int PRIMARY_DARK = 0xFF145C2C;
    private static final int PRIMARY_LIGHT = 0xFFE7F3EB;
    private static final int TEXT = 0xFF17201A;
    private static final int MUTED = 0xFF6B7280;
    private static final int DIVIDER = 0xFFE6EAE6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int BG = 0xFFF5F7F4;

    private final List<AppEntry> all = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private AppAdapter adapter;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        selected.addAll(Prefs.enabledPackages(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(header());
        root.addView(subtitle());
        root.addView(searchBox());

        loadApps();

        // В наборе оставляем только реально установленные приложения:
        // иначе счётчик показывает больше, чем видно в списке.
        Set<String> installed = new HashSet<>();
        for (AppEntry e : all) {
            installed.add(e.pkg);
        }
        selected.retainAll(installed);
        Prefs.setEnabledPackages(this, selected);

        adapter = new AppAdapter();
        adapter.items.addAll(all);

        FrameLayout listCard = new FrameLayout(this);
        GradientDrawable lbg = new GradientDrawable();
        lbg.setColor(CARD);
        lbg.setCornerRadius(dp(18));
        listCard.setBackground(lbg);
        listCard.setClipToOutline(true);
        listCard.setElevation(dp(2));

        ListView list = new ListView(this);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(DIVIDER));
        list.setDividerHeight(dp(1));
        list.setAdapter(adapter);
        listCard.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.leftMargin = dp(16);
        listLp.rightMargin = dp(16);
        listLp.bottomMargin = dp(10);
        root.addView(listCard, listLp);

        root.addView(bottomBar());
        setContentView(root);
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(10), dp(12), dp(10));
        bar.setBackgroundColor(PRIMARY_DARK);

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(10), dp(4), dp(10), dp(4));
        back.setContentDescription("Назад");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("Приложения");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(4);
        bar.addView(title, titleLp);

        TextView allBtn = headerAction("Все");
        allBtn.setOnClickListener(v -> {
            for (AppEntry e : new ArrayList<>(adapter.items)) {
                selected.add(e.pkg);
            }
            save();
        });
        bar.addView(allBtn);

        TextView noneBtn = headerAction("Снять");
        noneBtn.setOnClickListener(v -> {
            for (AppEntry e : new ArrayList<>(adapter.items)) {
                selected.remove(e.pkg);
            }
            save();
        });
        bar.addView(noneBtn);

        return bar;
    }

    private TextView headerAction(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setTextColor(0xE6FFFFFF);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(10), dp(8), dp(10), dp(8));
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private View subtitle() {
        TextView v = new TextView(this);
        v.setText("Отметьте приложения, где показывать цену за кг. Изменения сохраняются сразу.");
        v.setTextSize(13);
        v.setTextColor(MUTED);
        v.setLineSpacing(0, 1.25f);
        v.setPadding(dp(20), dp(14), dp(20), dp(6));
        return v;
    }

    private View searchBox() {
        EditText search = new EditText(this);
        search.setHint("Поиск по названию или пакету");
        search.setHintTextColor(0xFF9CA3AF);
        search.setTextColor(TEXT);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));

        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(CARD);
        sbg.setCornerRadius(dp(14));
        sbg.setStroke(dp(1), DIVIDER);
        search.setBackground(sbg);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(12);
        search.setLayoutParams(lp);
        return search;
    }

    private View bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(4), dp(20), dp(14));

        countText = new TextView(this);
        countText.setTextSize(13);
        countText.setTextColor(MUTED);
        bar.addView(countText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView done = new TextView(this);
        done.setText("Готово");
        done.setTextSize(15);
        done.setTextColor(Color.WHITE);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(22), dp(12), dp(22), dp(12));
        done.setMinHeight(dp(46));
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(PRIMARY);
        dbg.setCornerRadius(dp(14));
        done.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), dbg, null));
        done.setOnClickListener(v -> finish());
        bar.addView(done);

        updateCount();
        return bar;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);

        Map<String, AppEntry> map = new HashMap<>();
        List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || map.containsKey(pkg)) {
                continue;
            }
            CharSequence label = ri.loadLabel(pm);
            Drawable icon = ri.loadIcon(pm);
            if (icon == null) {
                icon = getDrawable(android.R.drawable.sym_def_app_icon);
            }
            map.put(pkg, new AppEntry(label.toString(), pkg, icon,
                    Prefs.SUPPORTED.contains(pkg)));
        }

        for (String pkg : Prefs.SUPPORTED) {
            if (map.containsKey(pkg)) {
                continue;
            }
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                Drawable icon = pm.getApplicationIcon(ai);
                map.put(pkg, new AppEntry(label.toString(), pkg, icon, true));
            } catch (Exception ignored) {
            }
        }

        all.addAll(map.values());
        all.sort((a, b) -> {
            if (a.supported != b.supported) {
                return a.supported ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        adapter.items.clear();
        for (AppEntry e : all) {
            if (q.isEmpty()
                    || e.label.toLowerCase().contains(q)
                    || e.pkg.toLowerCase().contains(q)) {
                adapter.items.add(e);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void save() {
        Prefs.setEnabledPackages(this, selected);
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private void updateCount() {
        if (countText == null) {
            return;
        }
        if (selected.isEmpty()) {
            countText.setText("Ничего не выбрано — цены не будут показываться");
            countText.setTextColor(0xFFC62828);
        } else {
            countText.setText("Выбрано: " + selected.size());
            countText.setTextColor(MUTED);
        }
    }

    private View createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(10), dp(10));
        row.setMinimumHeight(dp(64));
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x111E7A3C),
                new ColorDrawable(Color.WHITE), null));

        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(42), dp(42));
        ilp.rightMargin = dp(14);
        row.addView(icon, ilp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setTextSize(16);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);

        TextView pkg = new TextView(this);
        pkg.setTextSize(12);
        pkg.setTextColor(MUTED);
        pkg.setMaxLines(1);
        pkg.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(pkg, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText("магазин");
        badge.setTextSize(11);
        badge.setTextColor(PRIMARY);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(PRIMARY_LIGHT);
        bbg.setCornerRadius(dp(6));
        badge.setBackground(bbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(8);
        meta.addView(badge, blp);

        texts.addView(meta);

        CheckBox check = new CheckBox(this);
        check.setClickable(false);
        check.setFocusable(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(6);
        row.addView(check, clp);

        Row holder = new Row();
        holder.root = row;
        holder.icon = icon;
        holder.title = title;
        holder.pkg = pkg;
        holder.badge = badge;
        holder.check = check;
        row.setTag(holder);
        return row;
    }

    private final class AppAdapter extends BaseAdapter {
        final List<AppEntry> items = new ArrayList<>();

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView : createRow();
            Row holder = (Row) view.getTag();
            AppEntry e = items.get(position);

            holder.icon.setImageDrawable(e.icon);
            holder.title.setText(e.label);
            holder.pkg.setText(e.pkg);
            holder.badge.setVisibility(e.supported ? View.VISIBLE : View.GONE);
            holder.check.setChecked(selected.contains(e.pkg));

            view.setOnClickListener(v -> {
                if (selected.contains(e.pkg)) {
                    selected.remove(e.pkg);
                } else {
                    selected.add(e.pkg);
                }
                save();
            });
            return view;
        }
    }

    private static final class AppEntry {
        final String label;
        final String pkg;
        final Drawable icon;
        final boolean supported;

        AppEntry(String label, String pkg, Drawable icon, boolean supported) {
            this.label = label;
            this.pkg = pkg;
            this.icon = icon;
            this.supported = supported;
        }
    }

    private static final class Row {
        View root;
        ImageView icon;
        TextView title;
        TextView pkg;
        TextView badge;
        CheckBox check;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
