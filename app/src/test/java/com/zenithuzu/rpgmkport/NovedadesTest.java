package com.zenithuzu.rpgmkport;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NovedadesTest {
    private JSONObject release(String id, String date) throws Exception {
        return new JSONObject().put("body", "Compilada. id: " + id + ".").put("published_at", date);
    }
    @Test public void ignoreOlderReleaseDuringBuildAndSamePublishedBuild() throws Exception {
        long build = java.time.Instant.parse("2026-10-29T13:00:00Z").toEpochMilli();
        assertFalse(Novedades.masReciente(release("old-aaa", "2026-10-29T12:00:00Z"), "new-bbb", build));
        assertFalse(Novedades.masReciente(release("new-bbb", "2026-10-29T13:10:00Z"), "new-bbb", build));
        assertTrue(Novedades.masReciente(release("new-ccc", "2026-10-29T13:10:00Z"), "new-bbb", build));
        assertFalse(Novedades.masReciente(release("new-ccc", "invalid date"), "new-bbb", build));
    }
}
