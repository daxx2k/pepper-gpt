package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

final class AppThemeViews {
    private static ThemePalette current(Context context) {
        return ThemePalette.named(context.getSharedPreferences("PepperGPT_Prefs", 0)
                .getString("ui_theme", "glass"));
    }

    static void form(Activity activity) {
        ThemePalette theme = current(activity);
        Window window = activity.getWindow();
        systemBars(window, theme);
        View root = activity.findViewById(android.R.id.content);
        root.setBackgroundColor(theme.background);
        style(root, theme);
    }

    static void immersive(Activity activity, String name) {
        ThemePalette theme = ThemePalette.named(name);
        View root = view(activity, "immersive_root");
        if (!(root instanceof ViewGroup)) return;
        View speechBar = view(activity, "speech_bar");
        if (speechBar != null) speechBar.setAlpha(0f);
        root.setBackgroundColor(theme.background);
        ViewGroup layout = (ViewGroup)root;
        if (layout.getChildCount() > 0) layout.getChildAt(0).setBackgroundColor(theme.background);
        View scroll = view(activity, "story_scroll_view");
        if (scroll != null) scroll.setBackground(card(activity, theme));
        View illustration = view(activity, "story_illustration");
        if (illustration != null && illustration.getParent() instanceof View)
            ((View)illustration.getParent()).setBackground(card(activity, theme));
        label(activity, "story_text", theme.ink);
        label(activity, "story_placeholder_label", theme.muted);
        View progress = view(activity, "story_loading");
        if (progress instanceof ProgressBar) ((ProgressBar)progress).setIndeterminateTintList(color(theme.accent));
        icon(activity, "btn_close_immersive", theme);
    }

    static void radio(Activity activity) {
        ThemePalette theme = current(activity);
        View root = view(activity, "radioScrollRoot");
        if (root == null) return;
        root.setBackgroundColor(theme.background);
        style(root, theme);
        View toggle = view(activity, "btnDanceToggle");
        if (toggle instanceof Button) dance((Button)toggle, toggle.isActivated());
        label(activity, "statusText", theme.muted);
    }

    static void dance(Button button, boolean enabled) {
        ThemePalette theme = current(button.getContext());
        boolean it = "it".equals(WeatherPanel.language(button.getContext()));
        button.setText(it ? (enabled ? "Ballo attivo" : "Ballo spento") : (enabled ? "Dance on" : "Dance off"));
        button.setActivated(enabled);
        button.setTextColor(enabled ? theme.accent : theme.ink);
        button.setBackgroundTintList(color(enabled ? theme.tint : theme.surface));
    }

    static AlertDialog show(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.show();
        styleDialog(dialog);
        return dialog;
    }

    static void showDialog(AlertDialog dialog) {
        dialog.show();
        styleDialog(dialog);
    }

    private static void styleDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        ThemePalette theme = current(dialog.getContext());
        View decor = window.getDecorView();
        decor.setBackgroundTintList(color(theme.surface));
        style(decor, theme);
        ListView items = dialog.getListView();
        if (items != null) {
            items.setDivider(null);
            items.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(View parent, View child) { style(child, theme); }
                @Override public void onChildViewRemoved(View parent, View child) {}
            });
        }
        decor.post(() -> style(decor, theme));
    }

    static void photo(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        ThemePalette theme = current(dialog.getContext());
        systemBars(window, theme);
        View content = window.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(theme.background);
            if (content instanceof ViewGroup && ((ViewGroup)content).getChildCount() > 0)
                ((ViewGroup)content).getChildAt(0).setBackgroundColor(theme.background);
        }
        int id = dialog.getContext().getResources().getIdentifier("btn_close_fullscreen", "id",
                dialog.getContext().getPackageName());
        View close = window.findViewById(id);
        if (close instanceof ImageView) {
            ((ImageView)close).setImageTintList(color(theme.ink));
            close.setBackground(card(close.getContext(), theme));
        }
    }

    private static void systemBars(Window window, ThemePalette theme) {
        window.setStatusBarColor(theme.background);
        window.setNavigationBarColor(theme.darkSurface ? theme.background : theme.ink);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(theme.darkSurface ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                : flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private static void style(View view, ThemePalette theme) {
        if (view.getTag() instanceof RadioStations.Station) return;
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            text.setTextColor("radio-accent".equals(view.getTag()) ? theme.accent : theme.ink);
            text.setHintTextColor(theme.muted);
            if (text instanceof EditText) {
                text.setBackgroundTintList(color(theme.accent));
                text.setHighlightColor(theme.tint);
            } else if (text instanceof CompoundButton) {
                ((CompoundButton)text).setButtonTintList(color(theme.accent));
            } else if (text instanceof Button) {
                String owner = view.getContext().getPackageName();
                int id = view.getId();
                boolean danger = id == view.getResources().getIdentifier("stopButton", "id", owner)
                        || id == view.getResources().getIdentifier("btnClearKeys", "id", owner);
                text.setBackgroundTintList(color(danger ? (theme.darkSurface ? 0xff75383d : 0xffa73b39) : theme.tint));
                if (danger) text.setTextColor(0xffffffff);
            }
        }
        if (view instanceof Spinner) {
            Spinner spinner = (Spinner)view;
            spinner.setBackgroundTintList(color(theme.accent));
            spinner.setPopupBackgroundDrawable(card(view.getContext(), theme));
            SpinnerAdapter adapter = spinner.getAdapter();
            if (adapter != null && !(adapter instanceof PaletteSpinner)) {
                int selected = spinner.getSelectedItemPosition();
                spinner.setAdapter(new PaletteSpinner(adapter, theme));
                if (selected >= 0) spinner.setSelection(selected, false);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) style(group.getChildAt(i), theme);
        }
    }

    private static final class PaletteSpinner extends BaseAdapter {
        private final SpinnerAdapter source;
        private final ThemePalette theme;

        PaletteSpinner(SpinnerAdapter source, ThemePalette theme) {
            this.source = source;
            this.theme = theme;
        }
        @Override public int getCount() { return source.getCount(); }
        @Override public Object getItem(int position) { return source.getItem(position); }
        @Override public long getItemId(int position) { return source.getItemId(position); }
        @Override public boolean hasStableIds() { return source.hasStableIds(); }
        @Override public void registerDataSetObserver(DataSetObserver observer) { source.registerDataSetObserver(observer); }
        @Override public void unregisterDataSetObserver(DataSetObserver observer) { source.unregisterDataSetObserver(observer); }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            View view = source.getView(position, recycled, parent);
            style(view, theme);
            return view;
        }
        @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
            View view = source.getDropDownView(position, recycled, parent);
            style(view, theme);
            return view;
        }
    }

    private static ColorStateList color(int color) { return ColorStateList.valueOf(color); }

    private static View view(Activity activity, String name) {
        return activity.findViewById(activity.getResources().getIdentifier(name, "id", activity.getPackageName()));
    }

    private static void label(Activity activity, String name, int color) {
        View target = view(activity, name);
        if (target instanceof TextView) ((TextView)target).setTextColor(color);
    }

    private static void icon(Activity activity, String name, ThemePalette theme) {
        View target = view(activity, name);
        if (target instanceof ImageView) {
            ((ImageView)target).setImageTintList(color(theme.ink));
            target.setBackground(card(activity, theme));
        }
    }

    private static GradientDrawable card(Context context, ThemePalette theme) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(theme.surface);
        shape.setStroke(Math.round(density), theme.line);
        shape.setCornerRadius(14 * density);
        return shape;
    }
}
