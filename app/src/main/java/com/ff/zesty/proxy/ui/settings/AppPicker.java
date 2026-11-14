package com.ff.zesty.proxy.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ff.zesty.proxy.config.GameTargets;
import com.ff.zesty.proxy.config.Prefs;
import com.ff.zesty.proxy.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-app target selection. Shows all supported Free Fire variants with
 * their game logos, lets the user check one or more, and persists the selection.
 */
public final class AppPicker {

    /** Notified with the number of selected apps after the user confirms. */
    public interface Listener {
        void onSelectionChanged(int selectedCount);
    }

    private final Activity activity;
    private final Listener listener;
    private final List<String> packages   = new ArrayList<>();
    private final List<String> names      = new ArrayList<>();
    private final List<Integer> logoResIds = new ArrayList<>();
    private boolean loaded = false;
    private volatile boolean cancelled = false;

    public AppPicker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public boolean isLoaded() { return loaded; }

    public void cancel() { cancelled = true; }

    /** Loads supported game apps on a background thread. */
    public void loadApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PackageManager pm = activity.getPackageManager();
                    final List<String> resPkgs  = new ArrayList<>();
                    final List<String> resNames  = new ArrayList<>();
                    final List<Integer> resLogos = new ArrayList<>();

                    for (String pkg : GameTargets.KNOWN) {
                        if (!GameTargets.isInstalled(activity, pkg)) continue;
                        if (pm.getLaunchIntentForPackage(pkg) == null) continue;
                        if (pkg.equals(activity.getPackageName())) continue;

                        String label = GameTargets.displayName(pkg);
                        int logoRes = logoResForPackage(pkg);

                        resPkgs.add(pkg);
                        resNames.add(label);
                        resLogos.add(logoRes);
                    }

                    if (cancelled || activity.isFinishing()) return;
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            packages.clear();  packages.addAll(resPkgs);
                            names.clear();     names.addAll(resNames);
                            logoResIds.clear(); logoResIds.addAll(resLogos);
                            loaded = true;
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "LoadApps").start();
    }

    /** Returns the drawable resource id for a game's logo. */
    private static int logoResForPackage(String pkg) {
        if (GameTargets.FREE_FIRE_MAX.equals(pkg))    return R.drawable.ic_game_ff_max;
        if (GameTargets.FREE_FIRE_INDIA.equals(pkg))  return R.drawable.ic_game_ff_india;
        // global + regional variants
        return R.drawable.ic_game_ff_global;
    }

    /** Shows the multi-select dialog with game logos. Must be called on the UI thread. */
    public void show() {
        if (packages.isEmpty()) {
            Toast.makeText(activity, "No supported game installed", Toast.LENGTH_SHORT).show();
            return;
        }

        final int n = packages.size();
        final boolean[] checked = new boolean[n];
        Set<String> selected = Prefs.getTargetPackages(activity);
        for (int i = 0; i < n; i++) {
            checked[i] = selected.contains(packages.get(i));
        }

        // Custom list adapter with game logos
        final GamePickerAdapter adapter = new GamePickerAdapter(
                activity, packages, names, logoResIds, checked);

        final ListView listView = new ListView(activity);
        listView.setAdapter(adapter);
        listView.setDivider(null);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            checked[position] = !checked[position];
            adapter.setChecked(position, checked[position]);
        });

        new AlertDialog.Builder(activity, R.style.Theme_Nepdon_Dialog)
            .setTitle(activity.getString(R.string.app_picker_title))
            .setView(listView)
            .setPositiveButton(R.string.nv_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Set<String> chosen = new LinkedHashSet<>();
                    for (int i = 0; i < n; i++) {
                        if (checked[i]) chosen.add(packages.get(i));
                    }
                    Prefs.setTargetPackages(activity, chosen);
                    if (listener != null) listener.onSelectionChanged(chosen.size());
                }
            })
            .setNegativeButton(R.string.nv_cancel, null)
            .show();
    }

    /** Adapter that renders each game row with its logo, name, pkg, and checkbox. */
    private static final class GamePickerAdapter extends ArrayAdapter<String> {
        private final List<String> pkgList;
        private final List<String> nameList;
        private final List<Integer> logoList;
        private final boolean[] checked;

        GamePickerAdapter(Context ctx, List<String> pkgs, List<String> names,
                          List<Integer> logos, boolean[] checked) {
            super(ctx, R.layout.item_game_picker, names);
            this.pkgList  = pkgs;
            this.nameList = names;
            this.logoList = logos;
            this.checked  = checked;
        }

        void setChecked(int pos, boolean val) {
            checked[pos] = val;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_game_picker, parent, false);
            }
            ImageView logo = convertView.findViewById(R.id.iv_game_logo);
            TextView  tvName = convertView.findViewById(R.id.tv_game_name);
            TextView  tvPkg  = convertView.findViewById(R.id.tv_game_pkg);
            CheckBox  cb     = convertView.findViewById(R.id.cb_game);

            logo.setImageResource(logoList.get(position));
            tvName.setText(nameList.get(position));
            tvPkg.setText(pkgList.get(position));
            cb.setChecked(checked[position]);

            return convertView;
        }
    }
}
