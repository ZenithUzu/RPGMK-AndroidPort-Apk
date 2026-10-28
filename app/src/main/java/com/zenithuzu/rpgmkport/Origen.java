package com.zenithuzu.rpgmkport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * De dónde se leen los archivos del juego, sin copiarlo antes: una carpeta o el .zip tal cual.
 * Las rutas son relativas a la carpeta que tiene Game.ini, con "/".
 */
public abstract class Origen
{
    public abstract List<String> archivos();
    public abstract InputStream abrir(String rel) throws IOException;
    public abstract String nombre();

    /** Un archivo dentro del juego sin importar mayúsculas (null si no está). */
    public String buscar(String rel)
    {
        for (String a : archivos())
            if (a.equalsIgnoreCase(rel))
                return a;
        return null;
    }

    // ------------------------------------------------------------------ carpeta

    public static class Carpeta extends Origen
    {
        final File raiz;
        private List<String> lista;

        public Carpeta(File raiz) { this.raiz = raiz; }

        @Override public String nombre() { return raiz.getName(); }

        @Override public synchronized List<String> archivos()
        {
            if (lista == null) {
                lista = new ArrayList<>();
                listar(raiz, "");
            }
            return lista;
        }

        private void listar(File d, String rel)
        {
            File[] hijos = d.listFiles();
            if (hijos != null)
                for (File f : hijos)
                    if (f.isDirectory()) listar(f, rel + f.getName() + "/");
                    else lista.add(rel + f.getName());
        }

        @Override public InputStream abrir(String rel) throws IOException { return new FileInputStream(new File(raiz, rel)); }

        public File archivo(String rel) { return new File(raiz, rel); }
    }

    // ------------------------------------------------------------------ .zip

    /** El .zip leído directo (se abre por su ruta, también /proc/self/fd/N de un archivo elegido). */
    public static class Zip extends Origen
    {
        private final ZipFile zip;
        private final String prefijo;   // carpeta dentro del zip que tiene Game.ini ("" si es la raíz)
        private final String nombre;
        private final List<String> lista = new ArrayList<>();

        public Zip(String ruta, String nombre) throws IOException
        {
            this.nombre = nombre;
            ZipFile z;
            try {
                z = new ZipFile(ruta);
                z.size();
                leerNombres(z); // nombres raros fallan aquí
            } catch (IllegalArgumentException | java.util.zip.ZipException e) {
                // Zips de Windows con ñ o acentos: codificación vieja (CP437)
                z = new ZipFile(new File(ruta), Charset.forName("Cp437"));
            }
            zip = z;
            String ini = null;
            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                String n = en.nextElement().getName();
                String bajo = n.toLowerCase(Locale.ROOT);
                if ((bajo.equals("game.ini") || bajo.endsWith("/game.ini")) && n.split("/").length <= 4
                    && (ini == null || n.length() < ini.length()))
                    ini = n;
            }
            if (ini == null)
                throw new Juego.SinGameIni();
            prefijo = ini.substring(0, ini.length() - "game.ini".length());
            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && e.getName().startsWith(prefijo) && !e.getName().contains("../"))
                    lista.add(e.getName().substring(prefijo.length()));
            }
        }

        private static void leerNombres(ZipFile z)
        {
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); )
                en.nextElement().getName();
        }

        @Override public String nombre() { return nombre; }
        @Override public List<String> archivos() { return lista; }
        @Override public InputStream abrir(String rel) throws IOException { return zip.getInputStream(zip.getEntry(prefijo + rel)); }
    }
}
