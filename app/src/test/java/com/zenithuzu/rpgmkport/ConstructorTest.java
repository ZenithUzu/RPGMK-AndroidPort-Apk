package com.zenithuzu.rpgmkport;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConstructorTest
{
    @Test public void rechazaSoundfontTruncadoODeOtroTipo()
    {
        assertFalse(Constructor.cabeceraSoundfont(new byte[4]));
        assertFalse(Constructor.cabeceraSoundfont("RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(Constructor.cabeceraSoundfont("RIFF0000sfbk".getBytes(StandardCharsets.US_ASCII)));
    }
    private Origen juego(String config, String... extras)
    {
        List<String> nombres = new java.util.ArrayList<>(Arrays.asList("mkxp.json", "Audio/fuente.sf2"));
        Collections.addAll(nombres, extras);
        return new Origen() {
            @Override public List<String> archivos() { return nombres; }
            @Override public InputStream abrir(String rel) {
                return new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
            }
            @Override public String nombre() { return "Prueba"; }
        };
    }

    @Test public void comentariosYComasConservanTextos() throws Exception
    {
        String config = "{\n// comentario\n\"url\":\"http://a//b\", /* nota */"
            + "\"texto\":\",} /* literal */\", \"lista\":[1,2,],}";
        JSONObject o = new JSONObject(Constructor.sinComentarios(config));
        assertEquals("http://a//b", o.getString("url"));
        assertEquals(",} /* literal */", o.getString("texto"));
        assertEquals(2, o.getJSONArray("lista").length());
    }

    @Test public void conservaConfiguracionYAgregaParches() throws Exception
    {
        String config = "{\"windowTitle\":\"Pokémon Ñandú\",\"preloadScript\":[\"suyo.rb\"],"
            + "\"rubyLoadpath\":[\"extra\"],\"syncToRefreshrate\":true}";
        String raw = Constructor.mkxpJson(juego(config), "Prueba", Collections.singletonList("rmxp-win32.rb"), false);
        for (char c : raw.toCharArray()) assertTrue(c < 128);
        JSONObject o = new JSONObject(raw);
        assertEquals("Pokémon Ñandú", o.getString("windowTitle"));
        assertEquals("rmxp-win32.rb", o.getJSONArray("preloadScript").getString(0));
        assertEquals("suyo.rb", o.getJSONArray("preloadScript").getString(1));
        assertEquals("rmxp_ruby", o.getJSONArray("rubyLoadpath").getString(0));
        assertFalse(o.getBoolean("syncToRefreshrate"));
        assertEquals("Audio/fuente.sf2", o.getString("midiSoundFont"));
    }

    @Test public void soundfontExternoTienePrioridad() throws Exception
    {
        JSONObject o = new JSONObject(Constructor.mkxpJson(juego("{\"midiSoundFont\":\"viejo.sf2\"}"),
            "Prueba", Collections.emptyList(), true));
        assertEquals("soundfont.sf2", o.getString("midiSoundFont"));
    }

    @Test public void soundfontDeLaRaizTienePrioridadComoEnPc() throws Exception
    {
        JSONObject o = new JSONObject(Constructor.mkxpJson(juego("{}", "fuente-nombre-muy-largo.sf2", "a/f.sf2"),
            "Prueba", Collections.emptyList(), false));
        assertEquals("fuente-nombre-muy-largo.sf2", o.getString("midiSoundFont"));
    }
}
