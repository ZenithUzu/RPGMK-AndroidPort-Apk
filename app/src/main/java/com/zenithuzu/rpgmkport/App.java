package com.zenithuzu.rpgmkport;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/** Material You: en Android 12+ toda la app toma los colores del fondo de pantalla. */
public class App extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
