package com.zenithuzu.rpgmkport;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * De dónde se leen los archivos del juego, sin copiarlo antes: una carpeta o el .zip tal cual.
 * Las rutas son relativas a la carpeta que tiene Game.ini, con "/".
 */
public abstract class Origen implements java.io.Closeable
{
    public abstract List<String> archivos();
    public abstract InputStream abrir(String rel) throws IOException;
    public abstract String nombre();
    @Override public void close() throws IOException {}

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

    /** El .zip leído directo, por su canal (sirve para archivos elegidos con el selector de Android). */
    public static class Zip extends Origen
    {
        private final org.apache.commons.compress.archivers.zip.ZipFile zip;
        private final String prefijo;   // carpeta dentro del zip que tiene Game.ini ("" si es la raíz)
        private final String nombre;
        private final List<String> lista = new ArrayList<>();

        public Zip(String ruta, String nombre) throws IOException
        {
            this(java.nio.channels.FileChannel.open(new File(ruta).toPath()), nombre);
        }

        public Zip(java.nio.channels.SeekableByteChannel canal, String nombre) throws IOException
        {
            this.nombre = nombre;
            // Nombres sin marca UTF-8 (zips de Windows con ñ o acentos): codificación vieja CP437
            zip = new org.apache.commons.compress.archivers.zip.ZipFile(canal, nombre, "Cp437", true);
            String ini = null;
            for (Enumeration<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> en = zip.getEntries(); en.hasMoreElements(); ) {
                String n = en.nextElement().getName();
                String bajo = n.toLowerCase(Locale.ROOT);
                if ((bajo.equals("game.ini") || bajo.endsWith("/game.ini") || ((bajo.equals("index.html") || bajo.endsWith("/index.html")) && zip.getEntry(n.substring(0,n.length()-10)+"js/rpg_core.js")!=null)) && n.split("/").length <= 4
                    && (ini == null || n.length() < ini.length()))
                    ini = n;
            }
            if (ini == null) {
                zip.close();
                throw new Juego.SinGameIni();
            }
            prefijo = ini.substring(0, ini.length() - (ini.toLowerCase(Locale.ROOT).endsWith("index.html")?10:8));
            for (Enumeration<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> en = zip.getEntries(); en.hasMoreElements(); ) {
                org.apache.commons.compress.archivers.zip.ZipArchiveEntry e = en.nextElement();
                if (!e.isDirectory() && e.getName().startsWith(prefijo) && !e.getName().contains("../"))
                    lista.add(e.getName().substring(prefijo.length()));
            }
        }

        @Override public String nombre() { return nombre; }
        @Override public List<String> archivos() { return lista; }
        @Override public InputStream abrir(String rel) throws IOException { return zip.getInputStream(zip.getEntry(prefijo + rel)); }
        @Override public void close() throws IOException { zip.close(); }
    }
}
