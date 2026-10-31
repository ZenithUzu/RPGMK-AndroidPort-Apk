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
        public int motor;               // 0=automatic, 1=XP, 2=VX, 3=VX Ace
        public boolean insurgence;       // opt-in legacy Essentials compatibility profile
        public boolean ahorrar;          // "Ahorrar espacio": Graphics/Audio se leen del APK
        public File soundfont;           // null = el SoundFont que ya venga en el juego
        public List<String> abis = new ArrayList<>(); // vacía = todas las de la plantilla
    }

    public interface Progreso { void paso(String texto); }

    private static final int LABEL = 0x01010001;   // android:label
    private static final String[] FUERA = { ".exe", ".dll", "desktop.ini", "thumbs.db", ".url", ".lnk" };
    private static final String[] SIN_COMPRIMIR = { ".png", ".jpg", ".jpeg", ".ogg", ".mp3", ".wav", ".wma", ".ogv", ".mp4" };

    private Constructor() {}

    static boolean cabeceraSoundfont(byte[] h)
    {
        return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
            && h[8] == 's' && h[9] == 'f' && h[10] == 'b' && h[11] == 'k';
    }

    /** Versión que siempre crece: minutos desde 2020 (igual que en la PC). */
    public static long versionCodeAhora() { return (System.currentTimeMillis() / 1000 - 1577836800L) / 60; }

    public static void construir(File plantilla, Origen juego, Datos d, Firma firma, File salida, Progreso p) throws Exception
    {
        Motor motor = Motor.detectar(juego,d.motor);
        if(d.insurgence && motor.rgss!=1)throw new IllegalArgumentException("El perfil Insurgence es solo para RPG Maker XP.");
        p.paso("Motor: "+motor.nombre()+(motor.web()?" · Android System WebView":" · RGSS"+motor.rgss+" · mkxp-z"));
        p.paso("Leyendo la plantilla…");
        ApkModule apk = ApkModule.loadApkFile(plantilla);
        File sinFirma = new File(salida.getPath() + ".sinfirma");
        File firmado = new File(salida.getPath() + ".nuevo");
        boolean cerrado = false;
        try {

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

        p.paso("Agregando el juego…");
        List<String> ligero = new ArrayList<>();
        agregar(apk, juego, ligero, !motor.web() && d.ahorrar);

        if(motor.web()) {
            JSONObject runtime=new JSONObject().put("engine","mv").put("root",motor.webRoot);
            apk.add(new ByteInputSource(runtime.toString().getBytes(StandardCharsets.UTF_8),"assets/game/portemon_runtime.json"));
            apk.removeDir("assets/rmxp");
            apk.removeDir("assets/game/save");
            apk.removeDir("assets/game/www/save");
        } else {
            // La plantilla tiene prioridad sobre parches antiguos que traiga el juego, igual que en PC.
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
                apk.removeInputSource("assets/game/" + resto);
                apk.add(new RenamedInputSource<>("assets/game/" + resto, s));
            }
            Collections.sort(parches);

            if (d.soundfont != null) {
                apk.removeInputSource("assets/game/soundfont.sf2");
                apk.add(new FileInputSource(d.soundfont, "assets/game/soundfont.sf2"));
            }
            JSONObject conf = new JSONObject(mkxpJson(juego, d.titulo, parches, d.soundfont != null));
            conf.put("rgssVersion",motor.rgss);
            apk.add(new ByteInputSource(escaparJson(conf.toString()).getBytes(StandardCharsets.US_ASCII), "assets/game/mkxp.json"));
            String ini = juego.buscar("Game.ini");
            apk.removeInputSource("assets/game/"+ini);
            apk.add(new ByteInputSource(motor.ini(Juego.leerTexto(juego,ini),d.titulo).getBytes(StandardCharsets.UTF_8),"assets/game/Game.ini"));
            apk.removeInputSource("assets/game/rmxp_motor.txt");
            apk.add(new ByteInputSource((motor.rgss+"\n").getBytes(StandardCharsets.US_ASCII),"assets/game/rmxp_motor.txt"));
            apk.removeInputSource("assets/game/rmxp_insurgence.txt");
            apk.add(new ByteInputSource((d.insurgence ? "1\n" : "0\n").getBytes(StandardCharsets.US_ASCII),
                "assets/game/rmxp_insurgence.txt"));
            if (d.insurgence) p.paso("Perfil Insurgence experimental: Ruby antiguo, caché, GIF y mapas planos");
            if (d.ahorrar) {
                Collections.sort(ligero);
                apk.add(new ByteInputSource((String.join("\n", ligero) + "\n").getBytes(StandardCharsets.UTF_8), "assets/game/rmxp_ligero.txt"));
            }

        }

        p.paso("Escribiendo el APK…");
        java.util.Set<String> delJuego = new java.util.HashSet<>();
        for (String r : juego.archivos())
            delJuego.add("assets/game/" + r);
        int total = delJuego.size();
        int[] n = { 0 };
        apk.writeApk(sinFirma, (nombre, metodo, largo) -> {
            if (delJuego.contains(nombre) && ++n[0] % 100 == 0)
                p.paso("Escribiendo el APK… " + n[0] + " de " + total + " archivos");
        });
        apk.close();
        cerrado = true;

        p.paso("Firmando a nombre de " + firma.autor + "…");
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("juego", firma.llave,
            Collections.singletonList(firma.certificado)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
            .setInputApk(sinFirma)
            .setOutputApk(firmado)
            .setV1SigningEnabled(false) // con v2 basta (Android 7+): se firma una sola vez
            .setMinSdkVersion(24)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign();
        java.nio.file.Files.move(firmado.toPath(), salida.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                if (!cerrado) apk.close();
            } finally {
                sinFirma.delete();
                firmado.delete();
            }
        }
    }

    private static void ponerNombre(ResXmlElement e, String nombre)
    {
        ResXmlAttribute a = e.searchAttributeByResourceId(LABEL);
        if (a != null)
            a.setValueAsString(nombre);
    }

    private static void agregar(ApkModule apk, Origen o, List<String> ligero, boolean ahorrar)
    {
        for (String r : o.archivos()) {
            String bajo = r.toLowerCase(Locale.ROOT);
            if (termina(bajo, FUERA) || bajo.equals("mkxp.json") || bajo.equals("rmxp_insurgence.txt") || bajo.equals("rmxp_motor.txt") || bajo.equals("portemon_runtime.json"))
                continue;
            InputSource s = o instanceof Origen.Carpeta
                ? new FileInputSource(((Origen.Carpeta) o).archivo(r), "assets/game/" + r)
                : new InputSource("assets/game/" + r) {
                    @Override public java.io.InputStream openStream() throws java.io.IOException { return o.abrir(r); }
                };
            if (termina(bajo, SIN_COMPRIMIR))
                s.setMethod(ZipEntry.STORED); // ya vienen comprimidos; así el motor los lee directo
            apk.add(s);
            // Ahorrar espacio: igual que write_ligero de la PC (solo nombres ASCII, sin el .sf2)
            String top = r.contains("/") ? bajo.substring(0, r.indexOf('/')) : "";
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
    static String mkxpJson(Origen juego, String titulo, List<String> parches, boolean soundfontExterno) throws Exception
    {
        JSONObject conf = new JSONObject();
        String propio = juego.buscar("mkxp.json");
        if (propio != null) {
            try (java.io.InputStream in = juego.abrir(propio)) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                Juego.copiar(in, b);
                conf = new JSONObject(sinComentarios(b.toString("UTF-8")));
            } catch (Exception ignored) {
                conf = new JSONObject(); // con errores: se usa uno nuevo
            }
        }
        if (!conf.has("windowTitle")) conf.put("windowTitle", titulo);
        if (!conf.has("subImageFix")) conf.put("subImageFix", true);
        conf.put("fullscreen", true);
        conf.put("vsync", false); // el control de FPS de Android lleva el reloj
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
        if (soundfontExterno)
            conf.put("midiSoundFont", "soundfont.sf2");
        else if (conf.optString("midiSoundFont").isEmpty()) {
            String sf2 = null;
            for (String a : juego.archivos())
                if (a.toLowerCase(Locale.ROOT).endsWith(".sf2") && (sf2 == null
                    || a.split("/").length < sf2.split("/").length
                    || (a.split("/").length == sf2.split("/").length && a.compareTo(sf2) < 0)))
                    sf2 = a;
            if (sf2 != null)
                conf.put("midiSoundFont", sf2);
        }
        return escaparJson(conf.toString(2));
    }

    private static String escaparJson(String json)
    {
        // Solo ASCII: evita que el motor intente adivinar la codificación del JSON.
        StringBuilder sb = new StringBuilder();
        for (char c : json.toCharArray())
            sb.append(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
        return sb.toString();
    }


    /** Comentarios //, de bloque y comas finales, sin alterar cadenas del juego. */
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
            } else if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '*') {
                int fin = t.indexOf("*/", i + 2);
                i = fin < 0 ? t.length() : fin + 1;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        String limpio = out.toString();
        out.setLength(0);
        comillas = false;
        for (int i = 0; i < limpio.length(); i++) {
            char c = limpio.charAt(i);
            if (comillas) {
                out.append(c);
                if (c == '\\' && i + 1 < limpio.length()) out.append(limpio.charAt(++i));
                else if (c == '"') comillas = false;
            } else if (c == '"') {
                comillas = true;
                out.append(c);
            } else if (c == ',') {
                int j = i + 1;
                while (j < limpio.length() && Character.isWhitespace(limpio.charAt(j))) j++;
                if (j >= limpio.length() || (limpio.charAt(j) != '}' && limpio.charAt(j) != ']')) out.append(c);
            } else out.append(c);
        }
        return out.toString();
    }
}
