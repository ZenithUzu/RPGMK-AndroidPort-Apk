package com.zenithuzu.rpgmkport;

import com.android.apksig.ApkSigner;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ResFile;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.FileInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.archive.RenamedInputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;

/**
 * Arma el APK de un juego a partir de la plantilla (RMXP-Android-Ports, sin juego): le mete el
 * juego en assets/game, le cambia id, nombre, versión e icono, y lo firma. No compila nada.
 * Hace lo mismo que stage_game + gradle_build de tools/rmxp_port.py.
 */
public final class Constructor
{
    public static class Datos
    {
        public String titulo, version;
        public long versionCode;
        public byte[] iconoPng;          // null = el icono de la plantilla
        public byte[] fondoPng;          // fondo del icono (gris, negro o blanco); null = el de la plantilla
        public boolean ahorrar;          // "Ahorrar espacio": Graphics/Audio se leen del APK
        public List<String> abis = new ArrayList<>(); // vacía = todas las de la plantilla
    }

    public interface Progreso { void paso(String texto); }

    private static final int LABEL = 0x01010001;   // android:label
    private static final String[] FUERA = { ".exe", ".dll", "desktop.ini", "thumbs.db", ".url", ".lnk" };
    private static final String[] SIN_COMPRIMIR = { ".png", ".jpg", ".jpeg", ".ogg", ".mp3", ".wav", ".wma", ".ogv", ".mp4" };

    private Constructor() {}

    /** Versión que siempre crece: minutos desde 2020 (igual que en la PC). */
    public static long versionCodeAhora() { return (System.currentTimeMillis() / 1000 - 1577836800L) / 60; }

    public static void construir(File plantilla, File juego, Datos d, Firma firma, File salida, Progreso p) throws Exception
    {
        p.paso("Leyendo la plantilla…");
        ApkModule apk = ApkModule.loadApkFile(plantilla);

        // Id, versión y nombre (el de la app y el de la pantalla del juego)
        AndroidManifestBlock m = apk.getAndroidManifest();
        m.setPackageName(firma.id);
        m.setVersionCode((int) d.versionCode);
        m.setVersionName(d.version);
        ponerNombre(m.getApplicationElement(), d.titulo);
        for (ResXmlElement a : m.listActivities())
            ponerNombre(a, d.titulo);

        // Icono y su fondo: los mismos archivos que usa un juego con icono propio en la PC
        for (ResFile rf : apk.listResFiles()) {
            Entry e = rf.pickOne();
            if (!rf.getFilePath().endsWith(".png"))
                continue;
            boolean icono = ("mipmap".equals(e.getTypeName()) && "ic_launcher".equals(e.getName()))
                || ("drawable".equals(e.getTypeName()) && "rmxp_game_icon".equals(e.getName()));
            boolean fondo = "drawable".equals(e.getTypeName()) && "rmxp_icon_fondo".equals(e.getName());
            byte[] nuevo = icono ? d.iconoPng : fondo ? d.fondoPng : null;
            if (nuevo != null) {
                apk.removeInputSource(rf.getFilePath());
                apk.add(new ByteInputSource(nuevo, rf.getFilePath()));
            }
        }

        // Solo el motor que usa este teléfono (el APK pesa la mitad)
        if (!d.abis.isEmpty())
            for (String abi : new String[] { "arm64-v8a", "armeabi-v7a", "x86", "x86_64" })
                if (!d.abis.contains(abi))
                    apk.removeDir("lib/" + abi);

        // Firma vieja de la plantilla y datos de Gradle: fuera (se firma de nuevo)
        for (InputSource s : apk.getInputSources())
            if (s.getAlias().startsWith("META-INF/"))
                apk.removeInputSource(s.getAlias());

        // Parches y biblioteca de Ruby de la plantilla → dentro del juego
        List<String> parches = new ArrayList<>();
        for (InputSource s : apk.getInputSources()) {
            String n = s.getAlias();
            if (!n.startsWith("assets/rmxp/"))
                continue;
            String resto = n.substring("assets/rmxp/".length());
            if (resto.startsWith("patches/")) {
                resto = resto.substring("patches/".length());
                parches.add(resto);
            }
            apk.removeInputSource(n);
            apk.add(new RenamedInputSource<>("assets/game/" + resto, s));
        }
        Collections.sort(parches);

        // El juego
        p.paso("Agregando el juego…");
        List<String> ligero = new ArrayList<>();
        agregar(apk, juego, "", ligero, d.ahorrar);
        apk.add(new ByteInputSource(mkxpJson(juego, d.titulo, parches).getBytes(StandardCharsets.US_ASCII), "assets/game/mkxp.json"));
        if (d.ahorrar) {
            Collections.sort(ligero);
            apk.add(new ByteInputSource((String.join("\n", ligero) + "\n").getBytes(StandardCharsets.UTF_8), "assets/game/rmxp_ligero.txt"));
        }

        p.paso("Escribiendo el APK…");
        File sinFirma = new File(salida.getPath() + ".sinfirma");
        apk.writeApk(sinFirma);
        apk.close();

        p.paso("Firmando a nombre de " + firma.autor + "…");
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("juego", firma.llave,
            Collections.singletonList(firma.certificado)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
            .setInputApk(sinFirma)
            .setOutputApk(salida)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign();
        //noinspection ResultOfMethodCallIgnored
        sinFirma.delete();
    }

    private static void ponerNombre(ResXmlElement e, String nombre)
    {
        ResXmlAttribute a = e.searchAttributeByResourceId(LABEL);
        if (a != null)
            a.setValueAsString(nombre);
    }

    private static void agregar(ApkModule apk, File dir, String rel, List<String> ligero, boolean ahorrar)
    {
        File[] hijos = dir.listFiles();
        if (hijos == null)
            return;
        for (File f : hijos) {
            String r = rel + f.getName();
            if (f.isDirectory()) {
                agregar(apk, f, r + "/", ligero, ahorrar);
                continue;
            }
            String bajo = f.getName().toLowerCase(Locale.ROOT);
            if (termina(bajo, FUERA) || (rel.isEmpty() && bajo.equals("mkxp.json")))
                continue;
            FileInputSource s = new FileInputSource(f, "assets/game/" + r);
            if (termina(bajo, SIN_COMPRIMIR))
                s.setMethod(ZipEntry.STORED); // ya vienen comprimidos; así el motor los lee directo
            apk.add(s);
            // Ahorrar espacio: igual que write_ligero de la PC (solo nombres ASCII, sin el .sf2)
            String top = r.contains("/") ? r.substring(0, r.indexOf('/')).toLowerCase(Locale.ROOT) : "";
            if (ahorrar && (top.equals("graphics") || top.equals("audio"))
                && r.chars().allMatch(c -> c < 128) && !bajo.endsWith(".sf2"))
                ligero.add(r);
        }
    }

    private static boolean termina(String s, String[] fines)
    {
        for (String f : fines)
            if (s.endsWith(f))
                return true;
        return false;
    }

    /** mkxp.json del juego con lo que necesita Android (como stage_game de la PC). Solo ASCII. */
    static String mkxpJson(File juego, String titulo, List<String> parches) throws Exception
    {
        JSONObject conf = new JSONObject();
        File propio = new File(juego, "mkxp.json");
        if (propio.isFile()) {
            try {
                conf = new JSONObject(sinComentarios(new String(Files.readAllBytes(propio.toPath()), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                conf = new JSONObject(); // con errores: se usa uno nuevo
            }
        }
        if (!conf.has("windowTitle")) conf.put("windowTitle", titulo);
        if (!conf.has("subImageFix")) conf.put("subImageFix", true);
        conf.put("fullscreen", true);
        conf.put("syncToRefreshrate", false); // en RPG Maker, más fps = juego más rápido
        JSONArray pre = new JSONArray();
        for (String s : parches)
            pre.put(s);
        JSONArray viejo = conf.optJSONArray("preloadScript");
        for (int i = 0; viejo != null && i < viejo.length(); i++)
            if (!parches.contains(viejo.optString(i)))
                pre.put(viejo.optString(i));
        conf.put("preloadScript", pre);
        JSONArray load = new JSONArray().put("rmxp_ruby");
        JSONArray lv = conf.optJSONArray("rubyLoadpath");
        for (int i = 0; lv != null && i < lv.length(); i++)
            if (!"rmxp_ruby".equals(lv.optString(i)))
                load.put(lv.optString(i));
        if (load.length() == 1)
            load.put(".");
        conf.put("rubyLoadpath", load);
        if (conf.optString("midiSoundFont").isEmpty()) {
            String sf2 = buscarSf2(juego, "", 0);
            if (sf2 != null)
                conf.put("midiSoundFont", sf2);
        }
        // Solo ASCII: así el motor no intenta adivinar la codificación (se caía con algunas)
        StringBuilder sb = new StringBuilder();
        for (char c : conf.toString(2).toCharArray())
            sb.append(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        return sb.toString();
    }

    private static String buscarSf2(File dir, String rel, int nivel)
    {
        File[] hijos = dir.listFiles();
        if (hijos == null || nivel > 3)
            return null;
        for (File f : hijos)
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".sf2"))
                return rel + f.getName();
        for (File f : hijos)
            if (f.isDirectory()) {
                String s = buscarSf2(f, rel + f.getName() + "/", nivel + 1);
                if (s != null)
                    return s;
            }
        return null;
    }

    /** mkxp.json admite comentarios //; se quitan sin tocar lo que va entre comillas. */
    static String sinComentarios(String t)
    {
        StringBuilder out = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (comillas) {
                out.append(c);
                if (c == '\\' && i + 1 < t.length())
                    out.append(t.charAt(++i));
                else if (c == '"')
                    comillas = false;
            } else if (c == '"') {
                comillas = true;
                out.append(c);
            } else if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '/') {
                while (i < t.length() && t.charAt(i) != '\n')
                    i++;
                out.append('\n');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
