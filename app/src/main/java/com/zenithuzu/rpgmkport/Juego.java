package com.zenithuzu.rpgmkport;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

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
import java.util.Arrays;
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
    }

    public interface Progreso { void archivos(int n); }

    private Juego() {}

    static File carpetaJuegos(Context c) { return new File(c.getFilesDir(), "juegos"); }

    // ------------------------------------------------------------------ importar

    /** Descomprime el .zip y devuelve la carpeta del juego (la que tiene Game.ini). */
    public static File importarZip(Context c, Uri zip, Progreso p) throws IOException
    {
        File tmp = nuevaTemporal(c);
        try {
            descomprimir(c, zip, tmp, StandardCharsets.UTF_8, p);
        } catch (IllegalArgumentException | java.util.zip.ZipException e) {
            // Zips hechos en Windows guardan los nombres con la codificación vieja (CP437)
            borrar(tmp);
            tmp.mkdirs();
            descomprimir(c, zip, tmp, Charset.forName("Cp437"), p);
        }
        return finalizar(c, tmp);
    }

    /** Copia una carpeta elegida con el selector de Android. */
    public static File importarCarpeta(Context c, Uri arbol, Progreso p) throws IOException
    {
        File tmp = nuevaTemporal(c);
        DocumentFile raiz = DocumentFile.fromTreeUri(c, arbol);
        if (raiz == null)
            throw new IOException("carpeta no válida");
        int[] n = { 0 };
        copiarArbol(c, raiz, new File(tmp, nombreSeguro(raiz.getName(), "juego")), p, n);
        return finalizar(c, tmp);
    }

    private static File nuevaTemporal(Context c)
    {
        File tmp = new File(carpetaJuegos(c), ".nuevo");
        borrar(tmp);
        tmp.mkdirs();
        return tmp;
    }

    private static void descomprimir(Context c, Uri zip, File destino, Charset cs, Progreso p) throws IOException
    {
        String base = destino.getCanonicalPath() + File.separator;
        int n = 0;
        try (InputStream in = c.getContentResolver().openInputStream(zip);
             ZipInputStream z = new ZipInputStream(in, cs)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                File f = new File(destino, e.getName());
                if (!f.getCanonicalPath().startsWith(base)) // nada fuera de la carpeta
                    continue;
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    copiar(z, out);
                }
                if (++n % 25 == 0)
                    p.archivos(n);
            }
        }
        p.archivos(n);
    }

    private static void copiarArbol(Context c, DocumentFile src, File dst, Progreso p, int[] n) throws IOException
    {
        dst.mkdirs();
        for (DocumentFile d : src.listFiles()) {
            File f = new File(dst, nombreSeguro(d.getName(), "x"));
            if (d.isDirectory()) {
                copiarArbol(c, d, f, p, n);
            } else {
                try (InputStream in = c.getContentResolver().openInputStream(d.getUri());
                     OutputStream out = new FileOutputStream(f)) {
                    copiar(in, out);
                }
                if (++n[0] % 25 == 0)
                    p.archivos(n[0]);
            }
        }
        p.archivos(n[0]);
    }

    /** Busca Game.ini, lo mueve a juegos/<nombre> y borra lo temporal. */
    private static File finalizar(Context c, File tmp) throws IOException
    {
        File raiz = buscarRaiz(tmp);
        if (raiz == null) {
            borrar(tmp);
            throw new SinGameIni();
        }
        File destino = new File(carpetaJuegos(c), slug(leer(raiz).titulo, "juego"));
        borrar(destino);
        if (!raiz.renameTo(destino))
            throw new IOException("no se pudo mover el juego");
        borrar(tmp);
        return destino;
    }

    public static class SinGameIni extends IOException {}

    /** La carpeta (hasta 3 niveles adentro) que tiene Game.ini. */
    static File buscarRaiz(File dir)
    {
        Deque<File[]> cola = new ArrayDeque<>();
        cola.add(new File[] { dir, new File("0") });
        while (!cola.isEmpty()) {
            File[] item = cola.poll();
            int nivel = Integer.parseInt(item[1].getName());
            File[] hijos = item[0].listFiles();
            if (hijos == null)
                continue;
            for (File h : hijos)
                if (h.isFile() && h.getName().equalsIgnoreCase("Game.ini"))
                    return item[0];
            if (nivel < 3)
                for (File h : hijos)
                    if (h.isDirectory())
                        cola.add(new File[] { h, new File(String.valueOf(nivel + 1)) });
        }
        return null;
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

    public static Info leer(File raiz)
    {
        Info info = new Info();
        info.titulo = raiz.getName();
        File[] hijos = raiz.listFiles();
        if (hijos == null)
            hijos = new File[0];
        Arrays.sort(hijos);
        for (File f : hijos)
            if (f.getName().equalsIgnoreCase("Game.ini")) {
                Matcher m = Pattern.compile("^\\s*Title\\s*=\\s*(.+?)\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(leerTexto(f));
                if (m.find())
                    info.titulo = m.group(1);
            }
        for (File f : textos(hijos)) {
            for (String linea : leerTexto(f).split("\\r?\\n")) {
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
    private static java.util.List<File> textos(File[] hijos)
    {
        java.util.List<File> l = new java.util.ArrayList<>();
        for (File f : hijos)
            if (f.isFile() && LEEME.matcher(f.getName()).find())
                l.add(f);
        for (File d : hijos) {
            if (!d.isDirectory() || CARPETAS_JUEGO.contains(d.getName().toLowerCase(Locale.ROOT)))
                continue;
            File[] dentro = d.listFiles();
            if (dentro == null)
                continue;
            Arrays.sort(dentro);
            for (File f : dentro)
                if (f.isFile() && LEEME.matcher(f.getName()).find())
                    l.add(f);
        }
        return l;
    }

    /** Lee un texto en UTF-8 o, si no lo es, en la codificación de Windows. Quita formato RTF. */
    static String leerTexto(File f)
    {
        byte[] raw;
        try (InputStream in = new FileInputStream(f)) {
            raw = new byte[(int) Math.min(f.length(), 200000)];
            int n = 0, r;
            while (n < raw.length && (r = in.read(raw, n, raw.length - n)) > 0)
                n += r;
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
