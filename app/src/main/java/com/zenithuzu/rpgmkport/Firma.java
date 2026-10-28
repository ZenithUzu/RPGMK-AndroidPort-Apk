package com.zenithuzu.rpgmkport;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * La firma de cada juego, en el mismo formato que la herramienta de PC (RMXP-Android-Ports):
 * una carpeta con firma.jks (PKCS12, alias "juego") y juego.json ({"id", "password", "author"}).
 * Así un APK hecho en la PC se puede actualizar desde el teléfono, y al revés.
 */
public final class Firma
{
    public final String id, password, autor;
    public final PrivateKey llave;
    public final X509Certificate certificado;

    private Firma(String id, String password, String autor, PrivateKey llave, X509Certificate cert)
    {
        this.id = id;
        this.password = password;
        this.autor = autor;
        this.llave = llave;
        this.certificado = cert;
    }

    /** La firma guardada en esa carpeta, o null si no hay. */
    public static Firma cargar(File dir) throws Exception
    {
        File json = new File(dir, "juego.json"), jks = new File(dir, "firma.jks");
        if (!json.isFile() || !jks.isFile())
            return null;
        JSONObject o = new JSONObject(new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8));
        String pass = o.getString("password");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(jks)) {
            ks.load(in, pass.toCharArray());
        }
        String alias = ks.containsAlias("juego") ? "juego" : ks.aliases().nextElement();
        PrivateKey key = (PrivateKey) ks.getKey(alias, pass.toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        return new Firma(o.getString("id"), pass, o.optString("author"), key, cert);
    }

    /** Crea una firma nueva a nombre del autor (como keytool en la PC) y la guarda en dir. */
    public static Firma crear(File dir, String id, String autor, String titulo) throws Exception
    {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048, new SecureRandom());
        KeyPair kp = g.generateKeyPair();
        X509Certificate cert = autofirmado(kp, Der.nombre(nombre(autor), nombre(titulo)));
        byte[] r = new byte[18];
        new SecureRandom().nextBytes(r);
        String pass = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(r);

        dir.mkdirs();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("juego", kp.getPrivate(), pass.toCharArray(), new X509Certificate[] { cert });
        try (OutputStream out = new FileOutputStream(new File(dir, "firma.jks"))) {
            ks.store(out, pass.toCharArray());
        }
        JSONObject o = new JSONObject().put("id", id).put("password", pass).put("author", autor);
        Files.write(new File(dir, "juego.json").toPath(), o.toString(2).getBytes(StandardCharsets.UTF_8));
        return new Firma(id, pass, autor, kp.getPrivate(), cert);
    }

    private static String nombre(String s) { return s == null || s.trim().isEmpty() ? "Desconocido" : s.trim(); }

    // ------------------------------------------------------------------ certificado

    /** Certificado X.509 v3 autofirmado (SHA256withRSA, 100 años), escrito a mano en DER. */
    static X509Certificate autofirmado(KeyPair kp, byte[] nombreDer) throws Exception
    {
        byte[] algoritmo = Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.NULL);
        Date desde = new Date(), hasta = new Date(desde.getTime() + 36500L * 86400000L);
        byte[] tbs = Der.seq(
            Der.tag(0xA0, Der.entero(BigInteger.valueOf(2))),                  // versión 3
            Der.entero(new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE)),
            algoritmo,
            nombreDer,                                                        // emisor
            Der.seq(Der.fecha(desde), Der.fecha(hasta)),
            nombreDer,                                                        // sujeto = emisor
            kp.getPublic().getEncoded());                                     // SubjectPublicKeyInfo
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] cert = Der.seq(tbs, algoritmo, Der.bits(s.sign()));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new java.io.ByteArrayInputStream(cert));
    }

    /** Lo mínimo de ASN.1 DER para escribir el certificado. */
    static final class Der
    {
        static final byte[] NULL = { 0x05, 0x00 };

        static byte[] tag(int tag, byte[]... partes)
        {
            ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
            for (byte[] p : partes)
                cuerpo.write(p, 0, p.length);
            int n = cuerpo.size();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(tag);
            if (n < 128) {
                out.write(n);
            } else {
                int bytes = n < 256 ? 1 : n < 65536 ? 2 : 3;
                out.write(0x80 | bytes);
                for (int i = bytes - 1; i >= 0; i--)
                    out.write((n >> (8 * i)) & 0xFF);
            }
            out.write(cuerpo.toByteArray(), 0, n);
            return out.toByteArray();
        }

        static byte[] seq(byte[]... p) { return tag(0x30, p); }
        static byte[] set(byte[]... p) { return tag(0x31, p); }
        static byte[] entero(BigInteger v) { return tag(0x02, v.toByteArray()); }
        static byte[] utf8(String s) { return tag(0x0C, s.getBytes(StandardCharsets.UTF_8)); }

        static byte[] bits(byte[] b)
        {
            byte[] c = new byte[b.length + 1];
            System.arraycopy(b, 0, c, 1, b.length);
            return tag(0x03, c);
        }

        static byte[] fecha(Date d)
        {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return tag(0x18, f.format(d).getBytes(StandardCharsets.US_ASCII)); // GeneralizedTime
        }

        static byte[] oid(String s)
        {
            String[] p = s.split("\\.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
            for (int i = 2; i < p.length; i++) {
                long v = Long.parseLong(p[i]);
                byte[] tmp = new byte[10];
                int n = 0;
                tmp[n++] = (byte) (v & 0x7F);
                while ((v >>= 7) > 0)
                    tmp[n++] = (byte) (0x80 | (v & 0x7F));
                while (n > 0)
                    out.write(tmp[--n]);
            }
            return tag(0x06, out.toByteArray());
        }

        /** CN=autor, O=juego, OU=Fangame (como la herramienta de PC). */
        static byte[] nombre(String autor, String titulo)
        {
            return seq(
                set(seq(oid("2.5.4.11"), utf8("Fangame"))),
                set(seq(oid("2.5.4.10"), utf8(titulo))),
                set(seq(oid("2.5.4.3"), utf8(autor))));
        }
    }

    /** Copia la carpeta de la firma (para importar/exportar). */
    static void copiarCarpeta(File de, File a) throws IOException
    {
        a.mkdirs();
        for (String n : new String[] { "juego.json", "firma.jks" })
            Files.copy(new File(de, n).toPath(), new File(a, n).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
