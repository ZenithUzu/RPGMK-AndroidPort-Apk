package com.zenithuzu.rpgmkport;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Avisa si en GitHub hay una versión de la app (o de la plantilla del motor) más nueva que esta. */
final class Novedades
{
    private static final String API = "https://api.github.com/repos/ZenithUzu/RPGMK-AndroidPort-Apk/releases/tags/ultima";
    private static final String BAJAR = "https://github.com/ZenithUzu/RPGMK-AndroidPort-Apk/releases/download/ultima/Portemon.apk";

    static void revisar(Activity a)
    {
        if ("local".equals(BuildConfig.ID))
            return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                String notas;
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    Juego.copiar(in, b);
                    notas = new org.json.JSONObject(b.toString("UTF-8")).optString("body");
                }
                if (!notas.contains("id: ") || notas.contains(BuildConfig.ID))
                    return;
                a.runOnUiThread(() -> Snackbar.make(a.findViewById(android.R.id.content), R.string.nueva_version, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.descargar, v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BAJAR))))
                    .show());
            } catch (Exception ignored) {
                // sin internet: nada
            }
        }).start();
    }
}
