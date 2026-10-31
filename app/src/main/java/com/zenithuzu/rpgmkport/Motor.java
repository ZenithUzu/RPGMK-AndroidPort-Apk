package com.zenithuzu.rpgmkport;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Runtime selection shared by import reporting and APK construction. RGSS and experimental MV have separate runtime hosts. */
public final class Motor {
    public static final String[] NOMBRES = {"", "RPG Maker XP", "RPG Maker VX", "RPG Maker VX Ace", "RPG Maker MV (experimental)"};
    private static final String[] EXT = {"", "rxdata", "rvdata", "rvdata2"};
    private static final String[] ARCHIVO = {"", "Game.rgssad", "Game.rgss2a", "Game.rgss3a"};
    private static final String[] LIBRARY = {"", "RGSS102E.dll", "RGSS202E.dll", "RGSS301.dll"};
    public final int rgss;
    public final String scripts;
    public String webRoot="";
    public boolean web() { return rgss==4; }

    private Motor(int rgss, String scripts) { this.rgss=rgss; this.scripts=scripts; }
    public String nombre() { return NOMBRES[rgss]; }

    private static Map<String,String> datos(String ini) {
        Map<String,String> values=new LinkedHashMap<>();
        boolean game=false;
        for(String raw:ini.replace("\uFEFF", "").split("\\r?\\n")) {
            String line=raw.trim();
            if(line.startsWith("[") && line.endsWith("]")) {
                game=line.equalsIgnoreCase("[Game]"); continue;
            }
            if(!game || line.startsWith(";") || line.startsWith("#"))continue;
            int equal=line.indexOf('=');
            if(equal>=0)values.put(line.substring(0,equal).trim().toLowerCase(Locale.ROOT),line.substring(equal+1).trim());
        }
        return values;
    }
    private static String unquote(String text) {
        return text.length()>=2 && text.startsWith("\"") && text.endsWith("\"")?text.substring(1,text.length()-1):text;
    }
    private static int scriptKind(String path) {
        for(int i=1;i<=3;i++)if(path.toLowerCase(Locale.ROOT).endsWith("."+EXT[i]))return i;
        return 0;
    }
    public static Motor detectar(Origen source, int selected) {
        if(selected<0 || selected>4)throw new IllegalArgumentException("Motor desconocido");
        for(String prefix:new String[]{"","www/"}) {
            if(source.buscar(prefix+"index.html")!=null && source.buscar(prefix+"js/rpg_core.js")!=null
                    && source.buscar(prefix+"js/rpg_managers.js")!=null && source.buscar(prefix+"data/System.json")!=null
                    && (selected==0 || selected==4)) {
                Motor mv=new Motor(4,"");mv.webRoot=prefix;return mv;
            }
        }
        if(selected==4)throw new IllegalArgumentException("MV necesita index.html, js/rpg_core.js, js/rpg_managers.js y data/System.json en la raíz o www/.");
        String ini=source.buscar("Game.ini");
        if(ini==null)throw new IllegalArgumentException("No encontré datos de XP, VX, VX Ace o MV.");
        Map<String,String> values=datos(Juego.leerTexto(source,ini));
        String declared=unquote(values.getOrDefault("scripts","")).replace('\\','/');
        while(declared.startsWith("./"))declared=declared.substring(2);
        if(declared.startsWith("/") || declared.contains(":") || java.util.Arrays.asList(declared.split("/")).contains(".."))
            throw new IllegalArgumentException("Game.ini contiene una ruta Scripts fuera de la carpeta del juego.");
        int script=scriptKind(declared);
        String library=unquote(values.getOrDefault("library","")).toLowerCase(Locale.ROOT).replace('\\','/');
        library=unquote(library.substring(library.lastIndexOf('/')+1));
        int lib=library.startsWith("rgss3")?3:library.startsWith("rgss2")?2:library.startsWith("rgss1")?1:0;
        Set<Integer> present=new LinkedHashSet<>();
        for(int i=1;i<=3;i++)if(source.buscar("Data/Scripts."+EXT[i])!=null || source.buscar(ARCHIVO[i])!=null)present.add(i);
        if(!declared.isEmpty() && source.buscar(declared)!=null) {
            if(script!=0)present.add(script); else if(lib!=0)present.add(lib);
        }
        if(selected==0) {
            if(present.contains(script))selected=script;
            else if(present.size()==1)selected=present.iterator().next();
            else if(present.contains(lib))selected=lib;
            else if(!present.isEmpty())throw new IllegalArgumentException("Hay datos de varios motores. Elige XP, VX o VX Ace en Motor del juego.");
            else throw new IllegalArgumentException("No se encontraron scripts ni archivos RGSS de XP, VX o VX Ace.");
        }
        String scripts=!declared.isEmpty() && (script==0 || script==selected)?declared:"Data/Scripts."+EXT[selected];
        String loose=source.buscar(scripts);
        if(loose==null)loose=source.buscar("Data/Scripts."+EXT[selected]);
        if(loose==null && source.buscar(ARCHIVO[selected])==null)
            throw new IllegalArgumentException(NOMBRES[selected]+" necesita "+scripts+" o "+ARCHIVO[selected]+"; no se encontraron.");
        return new Motor(selected,loose==null?scripts:loose);
    }

    /** Canonical Game section in the generated APK; unknown settings/sections are kept. */
    public String ini(String original, String title) {
        // The game's original title can determine its save directory; keep it unchanged.
        title=datos(original).getOrDefault("title",title);
        String fields="Title="+title.replace('\n',' ').replace('\r',' ')+"\nLibrary="+LIBRARY[rgss]+"\nScripts="+scripts+"\n";
        StringBuilder out=new StringBuilder();
        boolean inGame=false, found=false;
        for(String line:original.replace("\uFEFF", "").split("\\r?\\n")) {
            String trimmed=line.trim();
            if(trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if(inGame)out.append(fields);
                inGame=trimmed.equalsIgnoreCase("[Game]");
                if(inGame && found)throw new IllegalArgumentException("Game.ini contiene secciones Game duplicadas");
                if(inGame)found=true;
                out.append(inGame?"[Game]":line).append('\n');
            } else if(!inGame || !trimmed.matches("(?i)(Title|Library|Scripts)\\s*=.*"))out.append(line).append('\n');
        }
        if(inGame)out.append(fields);
        else if(!found)out.append("\n[Game]\n").append(fields);
        return out.toString();
    }
}
