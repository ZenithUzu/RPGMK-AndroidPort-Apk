package com.zenithuzu.rpgmkport;

import android.content.Context;
import android.net.Uri;

import android.database.Cursor;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Importa un fangame (de un .zip o una carpeta) a la carpeta privada de la app y lee su
 * nombre, autor y versión. Es la misma lógica que tools/rmxp_port.py de RMXP-Android-Ports.
 */
public final class Juego
{
    public static class Info
    {
        public String titulo = "", autor = "", version = "";
        public int archivos;
        public List<String> avisos = new ArrayList<>();
    }

    /** Cosas de Windows que pueden fallar en Android (revisión rápida, no exacta). */
    static List<String> revisar(Origen o)
    {
        List<String> avisos = new ArrayList<>();
        int video = 0;
        List<String> dlls = new ArrayList<>();
        for (String r : o.archivos()) {
            String b = r.toLowerCase(Locale.ROOT);
            if (b.endsWith(".dll") && !b.startsWith("rgss") && !b.contains("/rgss")) dlls.add(r.substring(r.lastIndexOf('/') + 1));
            if (b.endsWith(".wmv") || b.endsWith(".avi") || b.endsWith(".mpg")) video++;
        }
        if (!dlls.isEmpty())
            avisos.add(dlls.size() + " .dll de Windows (" + String.join(", ", dlls.subList(0, Math.min(3, dlls.size())))
                + (dlls.size() > 3 ? "…" : "") + "): lo que hagan no funcionará en Android");
        if (video > 0) avisos.add(video + " videos .wmv/.avi/.mpg: pásalos a .ogv para que se vean");
        for (String r : o.archivos())
            if (r.matches("(?i)Data/Scripts\\.(rxdata|rvdata2?)")) {
                String t = scripts(o, r);
                if (t.contains("Win32API")) avisos.add("Usa Win32API (funciones de Windows): se reemplazan las comunes, alguna puede fallar");
                if (t.contains("Kernel32") || t.contains("kernel32")) avisos.add("Llama a kernel32: puede fallar");
            }
        return avisos;
    }

    /** Texto de los scripts: cada script viene comprimido con zlib dentro del archivo. */
    private static String scripts(Origen o, String rel)
    {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = o.abrir(rel)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            copiar(in, b);
            byte[] d = b.toByteArray();
            byte[] buf = new byte[65536];
            for (int i = 0; i + 1 < d.length; i++) {
                if ((d[i] & 0xFF) != 0x78 || (d[i + 1] & 0xFF) != 0x9C)
                    continue;
                java.util.zip.Inflater inf = new java.util.zip.Inflater();
                inf.setInput(d, i, d.length - i);
                try {
                    int n;
                    while ((n = inf.inflate(buf)) > 0)
                        sb.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
                    i += (int) inf.getBytesRead() - 1;
                } catch (java.util.zip.DataFormatException ignored) {
                } finally {
                    inf.end();
                }
            }
        } catch (IOException ignored) {
        }
        return sb.toString();
    }

    public interface Progreso { void archivos(int n); }

    private Juego() {}

    public static class SinGameIni extends IOException {}

    // ------------------------------------------------------------------ abrir (sin copiar)

    /** El .zip tal cual: se lee directo, sin descomprimirlo (mucho más rápido y sin ocupar espacio). */
    public static Origen abrirZip(Context c, Uri zip) throws IOException
    {
        android.os.ParcelFileDescriptor pfd = c.getContentResolver().openFileDescriptor(zip, "r");
        if (pfd == null)
            throw new IOException("no se pudo abrir el .zip");
        ABIERTOS.add(pfd); // debe seguir abierto mientras se usa
        // Se lee por el canal ya abierto: reabrir /proc/self/fd/N lo niegan algunos teléfonos
        return new Origen.Zip(new FileInputStream(pfd.getFileDescriptor()).getChannel(), nombreDe(c, zip));
    }

    private static final List<Object> ABIERTOS = new ArrayList<>();

    /** Una carpeta elegida con el selector: se lista de una vez por carpeta (rápido). */
    public static Origen abrirCarpeta(Context c, Uri arbol, Progreso p) throws IOException
    {
        Arbol a = new Arbol(c, arbol, p);
        if (a.buscar("Game.ini") == null)
            throw new SinGameIni();
        return a;
    }

    /** Carpeta vía el selector de Android: nombre e id de todos los archivos en una consulta por carpeta. */
    static class Arbol extends Origen
    {
        private final Context c;
        private final Uri arbol;
        private final Map<String, String> ids = new LinkedHashMap<>(); // ruta relativa -> id del documento
        private final String nombre;

        Arbol(Context c, Uri arbol, Progreso p) throws IOException
        {
            this.c = c;
            this.arbol = arbol;
            String raiz = DocumentsContract.getTreeDocumentId(arbol);
            // La carpeta con Game.ini puede estar hasta 3 niveles abajo de la elegida
            String[] base = buscarRaiz(raiz, 0);
            if (base == null)
                throw new SinGameIni();
            nombre = base[1];
            listar(base[0], "", p);
            p.archivos(ids.size());
        }

        private Cursor hijos(String id)
        {
            return c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(arbol, id),
                new String[] { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE }, null, null, null);
        }

        private String[] buscarRaiz(String id, int nivel)
        {
            List<String[]> dirs = new ArrayList<>();
            try (Cursor cur = hijos(id)) {
                while (cur != null && cur.moveToNext()) {
                    if (cur.getString(1).equalsIgnoreCase("Game.ini"))
                        return new String[] { id, nombreDoc(id) };
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cur.getString(2)))
                        dirs.add(new String[] { cur.getString(0) });
                }
            }
            if (nivel < 3)
                for (String[] d : dirs) {
                    String[] r = buscarRaiz(d[0], nivel + 1);
                    if (r != null)
                        return r;
                }
            return null;
        }

        private String nombreDoc(String id)
        {
            try (Cursor cur = c.getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(arbol, id),
                    new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
                return cur != null && cur.moveToFirst() ? cur.getString(0) : "juego";
            }
        }

        private void listar(String id, String rel, Progreso p)
        {
            try (Cursor cur = hijos(id)) {
                while (cur != null && cur.moveToNext()) {
                    String n = cur.getString(1);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cur.getString(2)))
                        listar(cur.getString(0), rel + n + "/", p);
                    else if (ids.put(rel + n, cur.getString(0)) == null && ids.size() % 200 == 0)
                        p.archivos(ids.size());
                }
            }
        }

        @Override public String nombre() { return nombre; }
        @Override public List<String> archivos() { return new ArrayList<>(ids.keySet()); }
        @Override public InputStream abrir(String rel) throws IOException
        {
            InputStream in = c.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(arbol, ids.get(rel)));
            if (in == null)
                throw new IOException(rel);
            return in;
        }
    }

    static String nombreDe(Context c, Uri u)
    {
        try (Cursor cur = c.getContentResolver().query(u, new String[] { android.provider.OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cur != null && cur.moveToFirst())
                return cur.getString(0).replaceAll("(?i)\\.zip$", "");
        }
        return "juego";
    }

    // ------------------------------------------------------------------ leer datos

    private static final Pattern LEEME = Pattern.compile("^(read ?me|l[eé]e ?me|l[eé]ame|cr[eé]ditos|credits|info|instrucciones|leia-me"
        + "|change ?log|cambios|novedades|versi[oó]n|notas)", Pattern.CASE_INSENSITIVE);
    // Líneas que hablan del motor y no del juego ("Hecho con Essentials v20.1"): no dan autor ni versión
    private static final Pattern MOTOR = Pattern.compile("essentials|rpg ?maker|mkxp|joiplay|kirin|\\brgss|\\bruby\\b", Pattern.CASE_INSENSITIVE);
    // Carpetas del juego donde no hay textos (no se revisan)
    private static final java.util.Set<String> CARPETAS_JUEGO = new java.util.HashSet<>(Arrays.asList(
        "graphics", "audio", "data", "fonts", "plugins", "save", "saves"));
    private static final Pattern[] AUTOR = {
        Pattern.compile("^\\W*(?:(?:an?|un)\\s+(?:game|juego|fangame)\\s+)?(?:hecho|creado|desarrollado|programado|made|created|developed|programmed)\\s+(?:por|by)\\s*[:\\-]?\\s*(.+)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\W*(?:autor(?:es|a)?|author(?:s)?|creador(?:es|a)?|creator(?:s)?|desarrollador(?:es|a)?|developer(?:s)?|dev)\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:©|\\(c\\)|copyright)\\s*(?:\\d{4}(?:\\s*-\\s*\\d{4})?\\s*)?(?:by\\s+)?([^\\d].+)$", Pattern.CASE_INSENSITIVE),
    };
    private static final Pattern VERSION = Pattern.compile("\\b(?:v|ver\\.?|versi[oó]n|version)\\s*([0-9]+(?:\\.[0-9]+){0,3})", Pattern.CASE_INSENSITIVE);

    public static Info leer(Origen o)
    {
        Info info = new Info();
        info.titulo = o.nombre();
        info.archivos = o.archivos().size();
        info.avisos = revisar(o);
        String ini = o.buscar("Game.ini");
        if (ini != null) {
            Matcher m = Pattern.compile("^\\s*Title\\s*=\\s*(.+?)\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(leerTexto(o, ini));
            if (m.find())
                info.titulo = m.group(1);
        }
        for (String f : textos(o)) {
            for (String linea : leerTexto(o, f).split("\\r?\\n")) {
                linea = linea.trim();
                if (MOTOR.matcher(linea).find())
                    continue;
                if (info.autor.isEmpty())
                    for (Pattern p : AUTOR) {
                        Matcher m = p.matcher(linea);
                        if (m.find()) {
                            String a = m.group(1).trim().split("\\s{2,}|\\s[-|]\\s|[.;]$")[0].replaceAll("^[ .,:;-]+|[ .,:;-]+$", "");
                            if (a.length() > 1 && a.length() <= 60) {
                                info.autor = a;
                                break;
                            }
                        }
                    }
                if (info.version.isEmpty()) {
                    Matcher m = VERSION.matcher(linea);
                    if (m.find())
                        info.version = m.group(1);
                }
            }
        }
        if (info.version.isEmpty()) {
            Matcher m = VERSION.matcher(info.titulo);
            info.version = m.find() ? m.group(1) : "1.0";
        }
        return info;
    }

    /** Léeme, créditos, cambios... en la carpeta del juego y un nivel más adentro (ej. Docs/). */
    static List<String> textos(Origen o)
    {
        List<String> arriba = new ArrayList<>(), dentro = new ArrayList<>();
        for (String r : o.archivos()) {
            String[] p = r.split("/");
            String nombre = p[p.length - 1];
            if (!LEEME.matcher(nombre).find())
                continue;
            if (p.length == 1)
                arriba.add(r);
            else if (p.length == 2 && !CARPETAS_JUEGO.contains(p[0].toLowerCase(Locale.ROOT)))
                dentro.add(r);
        }
        Collections.sort(arriba);
        Collections.sort(dentro);
        arriba.addAll(dentro);
        return arriba;
    }

    /** Lee un texto en UTF-8 o, si no lo es, en la codificación de Windows. Quita formato RTF. */
    static String leerTexto(Origen o, String rel)
    {
        byte[] raw;
        try (InputStream in = o.abrir(rel)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while (b.size() < 200000 && (r = in.read(buf)) > 0)
                b.write(buf, 0, r);
            raw = b.toByteArray();
        } catch (IOException e) {
            return "";
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException e) {
            text = new String(raw, Charset.forName("windows-1252"));
        }
        if (text.startsWith("﻿"))
            text = text.substring(1);
        if (text.trim().startsWith("{\\rtf"))
            text = text.replaceAll("\\\\pard?", "\n").replaceAll("\\\\[a-z]+-?\\d* ?|[{}]", "");
        return text;
    }

    /** "Pokémon Ejemplo" -> "pokemonejemplo" (para nombres de carpeta e id de app). */
    public static String slug(String text, String def)
    {
        String s = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (s.isEmpty())
            return def;
        return Character.isLetter(s.charAt(0)) ? s : "j" + s;
    }

    // ------------------------------------------------------------------ utilidades

    private static String nombreSeguro(String n, String def)
    {
        return n == null || n.isEmpty() || n.contains("/") || n.equals("..") ? def : n;
    }

    static void copiar(InputStream in, OutputStream out) throws IOException
    {
        byte[] buf = new byte[256 * 1024];
        int r;
        while ((r = in.read(buf)) > 0)
            out.write(buf, 0, r);
    }

    static void borrar(File f)
    {
        File[] hijos = f.listFiles();
        if (hijos != null)
            for (File h : hijos)
                borrar(h);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
