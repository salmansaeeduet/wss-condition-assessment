package com.example.cref_wss_01;

import android.app.Application;
import android.preference.PreferenceManager;

import org.osmdroid.config.Configuration;

import java.io.File;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Configuration.getInstance().load(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        // On Android 10+ WRITE_EXTERNAL_STORAGE is unavailable; use app-private cache instead.
        Configuration.getInstance().setOsmdroidTileCache(
                new File(getCacheDir(), "osmdroid"));
    }
}
