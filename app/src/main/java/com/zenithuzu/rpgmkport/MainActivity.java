package com.zenithuzu.rpgmkport;

import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

/**
 * Asistente en pasos: bienvenida → elegir juego → revisar datos → crear la app.
 * Diseño Material You: los colores siguen al fondo de pantalla del teléfono.
 */
public class MainActivity extends AppCompatActivity
{
    private static final int PASOS = 3;

    private int mPaso;
    private File mJuego;            // carpeta del juego importado (la que tiene Game.ini)
    private Juego.Info mInfo;
    private Uri mIcono;
    private boolean mAhorrar, mOcupado;

    private View mCabecera;
    private TextView mAyuda;
    private LinearLayout mBarras;
    private FrameLayout mContenido;
    private MaterialButton mSiguiente;
    private final Handler mUi = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String[]> mElegirZip, mElegirIcono, mImportarFirma;
    private ActivityResultLauncher<Uri> mElegirCarpeta, mExportarFirma;
    private ActivityResultLauncher<String> mGuardarApk;
    private File mApk;              // el último APK creado

    @Override
    protected void onCreate(Bundle state)
    {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        mCabecera = findViewById(R.id.cabecera);
        mAyuda = findViewById(R.id.ayuda);
        mBarras = findViewById(R.id.barras);
        mContenido = findViewById(R.id.contenido);
        mSiguiente = findViewById(R.id.siguiente);

        mElegirZip = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importar(uri, true);
        });
        mElegirCarpeta = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) importar(uri, false);
        });
        mElegirIcono = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                mIcono = uri;
                mostrar(mPaso);
            }
        });

        mImportarFirma = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris != null && !uris.isEmpty()) importarFirma(uris);
        });
        mExportarFirma = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) exportarFirma(uri);
        });
        mGuardarApk = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"), uri -> {
            if (uri != null) guardarApk(uri);
        });

        findViewById(R.id.atras).setOnClickListener(v -> atras());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed()
            {
                if (mPaso == 0) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    atras();
                }
            }
        });
        mSiguiente.setOnClickListener(v -> siguiente());

        if (state != null) {
            mPaso = state.getInt("paso");
            String j = state.getString("juego");
            if (j != null && new File(j).isDirectory()) {
                mJuego = new File(j);
                mInfo = Juego.leer(mJuego);
            }
        }
        mostrar(mPaso);
    }

    @Override
    protected void onSaveInstanceState(Bundle out)
    {
        super.onSaveInstanceState(out);
        out.putInt("paso", mPaso);
        if (mJuego != null)
            out.putString("juego", mJuego.getPath());
    }

    // ------------------------------------------------------------------ navegación

    private void atras()
    {
        if (!mOcupado && mPaso > 0)
            mostrar(mPaso - 1);
    }

    private void siguiente()
    {
        if (mPaso == 3) {
            if (!mOcupado)
                crear();
            return;
        }
        if (mPaso == 2)
            guardarDatos();
        if (mPaso < PASOS)
            mostrar(mPaso + 1);
    }

    private void mostrar(int paso)
    {
        mEstadoFirma = null;
        if (mPaso == 2 && paso != 2)
            guardarDatos();
        mPaso = paso;
        mContenido.removeAllViews();
        mCabecera.setVisibility(paso == 0 ? View.GONE : View.VISIBLE);
        dibujarBarras(paso);
        mAyuda.setVisibility(View.GONE);
        mSiguiente.setEnabled(true);
        mSiguiente.setText(R.string.continuar);

        switch (paso) {
            case 0:
                View v = inflar(R.layout.paso_bienvenida);
                LinearLayout como = v.findViewById(R.id.como);
                int[] pasos = { R.string.como_1, R.string.como_2, R.string.como_3 };
                for (int i = 0; i < pasos.length; i++) {
                    LinearLayout fila = new LinearLayout(this);
                    fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    fila.setPadding(0, dp(10), 0, 0);
                    fila.addView(numero(i + 1, false, false), new LinearLayout.LayoutParams(dp(28), dp(28)));
                    TextView t = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
                    t.setText(pasos[i]);
                    t.setPadding(dp(14), 0, 0, 0);
                    fila.addView(t);
                    como.addView(fila);
                }
                mSiguiente.setText(R.string.empezar);
                break;
            case 1:
                pasoJuego();
                break;
            case 2:
                pasoDatos();
                break;
            default:
                pasoCrear();
        }
    }

    /** Pasos numerados unidos por líneas: hechos con ✓, el actual relleno, los demás con borde. */
    private void dibujarBarras(int paso)
    {
        mBarras.removeAllViews();
        int[] nombres = { R.string.paso_juego, R.string.paso_datos, R.string.paso_crear };
        for (int i = 1; i <= PASOS; i++) {
            if (i > 1) {
                View linea = new View(this);
                linea.setBackgroundColor(color(i <= paso ? androidx.appcompat.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOutlineVariant));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(2), 1);
                lp.setMargins(dp(6), 0, dp(6), 0);
                mBarras.addView(linea, lp);
            }
            mBarras.addView(numero(i, i < paso, i == paso), new LinearLayout.LayoutParams(dp(26), dp(26)));
            if (i == paso) {
                TextView t = new TextView(this);
                t.setText(nombres[i - 1]);
                t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
                t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
                t.setPadding(dp(8), 0, 0, 0);
                mBarras.addView(t);
            }
        }
    }

    /** Círculo con número (o ✓ si ya se hizo). */
    private TextView numero(int n, boolean hecho, boolean actual)
    {
        TextView t = new TextView(this);
        t.setGravity(android.view.Gravity.CENTER);
        t.setText(hecho ? "✓" : String.valueOf(n));
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (hecho || actual) {
            d.setColor(color(androidx.appcompat.R.attr.colorPrimary));
            t.setTextColor(color(com.google.android.material.R.attr.colorOnPrimary));
        } else {
            d.setColor(color(com.google.android.material.R.attr.colorSecondaryContainer));
            t.setTextColor(color(com.google.android.material.R.attr.colorOnSecondaryContainer));
        }
        t.setBackground(d);
        return t;
    }

    private int color(int attr) { return MaterialColors.getColor(mContenido, attr); }

    private View inflar(int layout)
    {
        View v = LayoutInflater.from(this).inflate(layout, mContenido, false);
        mContenido.addView(v);
        return v;
    }

    /** Título y texto de cada paso, y debajo lo que se agregue. */
    private LinearLayout encabezado(int titulo, int texto)
    {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this, null, 0, R.style.Rpg_Titulo);
        t.setText(titulo);
        box.addView(t);
        TextView c = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        c.setText(texto);
        c.setPadding(0, dp(12), 0, dp(8));
        box.addView(c);
        mContenido.addView(box);
        return box;
    }

    private View opcion(ViewGroup padre, int icono, CharSequence titulo, CharSequence sub, View.OnClickListener click)
    {
        View o = LayoutInflater.from(this).inflate(R.layout.opcion, padre, false);
        ((ImageView) o.findViewById(R.id.icono)).setImageResource(icono);
        ((TextView) o.findViewById(R.id.titulo)).setText(titulo);
        ((TextView) o.findViewById(R.id.sub)).setText(sub);
        o.setOnClickListener(click);
        padre.addView(o);
        return o;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------------ paso 1: el juego

    private TextView mEstado;
    private LinearProgressIndicator mBarra;

    private void pasoJuego()
    {
        LinearLayout box = encabezado(R.string.juego_titulo, R.string.juego_texto);
        opcion(box, R.drawable.ic_zip, getString(R.string.importar_zip), getString(R.string.importar_zip_sub),
            v -> { if (!mOcupado) mElegirZip.launch(new String[] { "application/zip", "application/x-zip-compressed", "application/octet-stream" }); });
        opcion(box, R.drawable.ic_carpeta, getString(R.string.elegir_carpeta), getString(R.string.elegir_carpeta_sub),
            v -> { if (!mOcupado) mElegirCarpeta.launch(null); });

        mBarra = new LinearProgressIndicator(this);
        mBarra.setIndeterminate(true);
        mBarra.setVisibility(mOcupado ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        box.addView(mBarra, lp);
        mEstado = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        mEstado.setPadding(0, dp(12), 0, 0);
        box.addView(mEstado);
        if (mJuego != null && mInfo != null)
            mEstado.setText(getString(R.string.juego_listo, mInfo.titulo, mInfo.version));

        mSiguiente.setEnabled(mJuego != null && !mOcupado);
        if (mJuego == null) {
            mAyuda.setText(R.string.falta_juego);
            mAyuda.setVisibility(View.VISIBLE);
        }
    }

    private void importar(Uri uri, boolean zip)
    {
        mOcupado = true;
        mostrar(1);
        new Thread(() -> {
            String error = null;
            File juego = null;
            try {
                Juego.Progreso p = n -> mUi.post(() -> { if (mEstado != null) mEstado.setText(getString(R.string.copiando, n)); });
                juego = zip ? Juego.importarZip(this, uri, p) : Juego.importarCarpeta(this, uri, p);
            } catch (Juego.SinGameIni e) {
                error = getString(R.string.sin_game_ini);
            } catch (Exception e) {
                error = getString(R.string.error, e.getMessage());
            }
            File listo = juego;
            String fallo = error;
            mUi.post(() -> {
                mOcupado = false;
                if (listo != null) {
                    mJuego = listo;
                    mInfo = Juego.leer(listo);
                    mIcono = null;
                }
                mostrar(1);
                if (fallo != null)
                    mEstado.setText(fallo);
            });
        }).start();
    }

    // ------------------------------------------------------------------ paso 2: datos

    private void pasoDatos()
    {
        encabezado(R.string.datos_titulo, R.string.datos_texto);
        View v = inflar(R.layout.paso_datos);
        ((TextInputEditText) v.findViewById(R.id.campo_nombre)).setText(mInfo.titulo);
        ((TextInputEditText) v.findViewById(R.id.campo_autor)).setText(mInfo.autor);
        ((TextInputEditText) v.findViewById(R.id.campo_version)).setText(mInfo.version);
        MaterialSwitch ahorrar = v.findViewById(R.id.ahorrar);
        ahorrar.setChecked(mAhorrar);
        ahorrar.setOnCheckedChangeListener((b, on) -> mAhorrar = on);

        View icono = v.findViewById(R.id.opcion_icono);
        ImageView img = icono.findViewById(R.id.icono);
        ((TextView) icono.findViewById(R.id.titulo)).setText(R.string.icono);
        ((TextView) icono.findViewById(R.id.sub)).setText(R.string.icono_sub);
        if (mIcono != null) {
            img.setImageURI(mIcono);
            img.setImageTintList(null);
        } else {
            img.setImageResource(R.drawable.ic_imagen);
        }
        icono.setOnClickListener(x -> mElegirIcono.launch(new String[] { "image/png" }));

        // El contenido va dentro del encabezado anterior para que todo se desplace junto
        mContenido.removeView(v);
        LinearLayout box = (LinearLayout) mContenido.getChildAt(0);
        box.addView(v);

        // Firma: la del teléfono, una importada de la PC, o una nueva
        TextView t = new TextView(this, null, 0, R.style.Rpg_Titulo);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        t.setText(R.string.firma_titulo);
        t.setPadding(0, dp(28), 0, 0);
        box.addView(t);
        mEstadoFirma = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        mEstadoFirma.setPadding(0, dp(6), 0, 0);
        box.addView(mEstadoFirma);
        mostrarFirma();
        opcion(box, R.drawable.ic_carpeta, getString(R.string.importar_firma), getString(R.string.importar_firma_sub),
            x -> mImportarFirma.launch(new String[] { "*/*" }));
        opcion(box, R.drawable.ic_zip, getString(R.string.exportar_firma), getString(R.string.exportar_firma_sub),
            x -> exportar());
    }

    private TextView mEstadoFirma;

    /** Carpeta de la firma: igual que en la PC, juegos/<nombre del juego>. */
    private File carpetaFirma()
    {
        return new File(getFilesDir(), "firmas/" + Juego.slug(mInfo.titulo, "juego"));
    }

    private Firma firmaGuardada()
    {
        try {
            return Firma.cargar(carpetaFirma());
        } catch (Exception e) {
            return null;
        }
    }

    private void mostrarFirma()
    {
        if (mEstadoFirma == null)
            return;
        Firma f = firmaGuardada();
        mEstadoFirma.setText(f != null ? getString(R.string.firma_guardada, f.id)
            : getString(R.string.firma_nueva, mInfo.autor.isEmpty() ? "?" : mInfo.autor));
    }

    private void importarFirma(java.util.List<Uri> uris)
    {
        guardarDatos();
        File tmp = new File(getCacheDir(), "firma-importada");
        Juego.borrar(tmp);
        tmp.mkdirs();
        try {
            for (Uri u : uris) {
                String n = nombreDe(u);
                if (n == null || !(n.equalsIgnoreCase("juego.json") || n.toLowerCase().endsWith(".jks")))
                    continue;
                try (java.io.InputStream in = getContentResolver().openInputStream(u);
                     java.io.OutputStream out = new java.io.FileOutputStream(new File(tmp, n.toLowerCase().endsWith(".jks") ? "firma.jks" : "juego.json"))) {
                    Juego.copiar(in, out);
                }
            }
            Firma f = Firma.cargar(tmp); // comprueba que la contraseña abre la llave
            if (f == null)
                throw new IllegalArgumentException(getString(R.string.firma_mal));
            Firma.copiarCarpeta(tmp, carpetaFirma());
            if (!f.autor.isEmpty() && mInfo.autor.isEmpty())
                mInfo.autor = f.autor;
            aviso(getString(R.string.firma_lista, f.id));
            mostrar(2);
        } catch (Exception e) {
            aviso(e instanceof IllegalArgumentException ? e.getMessage() : getString(R.string.error, e.getMessage()));
        }
    }

    /** Si el juego aún no tiene firma (no se ha creado el APK), se crea ahora para poder exportarla. */
    private void exportar()
    {
        guardarDatos();
        if (firmaGuardada() == null) {
            try {
                Firma.crear(carpetaFirma(), "com." + Juego.slug(mInfo.autor, "fangame") + "."
                    + Juego.slug(mInfo.titulo, "juego"), mInfo.autor, mInfo.titulo);
            } catch (Exception e) {
                aviso(getString(R.string.error, e.getMessage()));
                return;
            }
            mostrarFirma();
        }
        mExportarFirma.launch(null);
    }

    private void exportarFirma(Uri arbol)
    {
        try {
            androidx.documentfile.provider.DocumentFile dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, arbol);
            androidx.documentfile.provider.DocumentFile sub = dir.createDirectory(Juego.slug(mInfo.titulo, "juego"));
            for (String n : new String[] { "juego.json", "firma.jks" }) {
                androidx.documentfile.provider.DocumentFile d = sub.createFile("application/octet-stream", n);
                try (java.io.InputStream in = new java.io.FileInputStream(new File(carpetaFirma(), n));
                     java.io.OutputStream out = getContentResolver().openOutputStream(d.getUri())) {
                    Juego.copiar(in, out);
                }
            }
            aviso(getString(R.string.firma_exportada));
        } catch (Exception e) {
            aviso(getString(R.string.error, e.getMessage()));
        }
    }

    private String nombreDe(Uri u)
    {
        try (android.database.Cursor c = getContentResolver().query(u, new String[] { android.provider.OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst())
                return c.getString(0);
        }
        return u.getLastPathSegment();
    }

    private void aviso(String texto)
    {
        com.google.android.material.snackbar.Snackbar.make(findViewById(R.id.raiz), texto, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
    }

    private void guardarDatos()
    {
        if (mContenido.getChildCount() == 0 || mInfo == null)
            return;
        TextInputEditText n = mContenido.findViewById(R.id.campo_nombre);
        if (n == null)
            return;
        mInfo.titulo = String.valueOf(n.getText()).trim();
        mInfo.autor = String.valueOf(((TextInputEditText) mContenido.findViewById(R.id.campo_autor)).getText()).trim();
        mInfo.version = String.valueOf(((TextInputEditText) mContenido.findViewById(R.id.campo_version)).getText()).trim();
    }

    // ------------------------------------------------------------------ paso 3: crear

    private TextView mProgreso;

    private void pasoCrear()
    {
        LinearLayout box = encabezado(R.string.crear_titulo, R.string.crear_texto);
        TextView resumen = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        resumen.setText(getString(R.string.juego_listo, mInfo.titulo, mInfo.version) + "\n" + mInfo.autor);
        box.addView(resumen);
        LinearProgressIndicator barra = new LinearProgressIndicator(this);
        barra.setIndeterminate(true);
        barra.setVisibility(mOcupado ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        box.addView(barra, lp);
        mProgreso = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        mProgreso.setPadding(0, dp(12), 0, 0);
        box.addView(mProgreso);
        if (mApk != null && mApk.isFile() && !mOcupado)
            opcion(box, R.drawable.ic_zip, getString(R.string.guardar_apk), mApk.getName(),
                x -> mGuardarApk.launch(mApk.getName()));
        mSiguiente.setText(R.string.crear);
        mSiguiente.setEnabled(!mOcupado);
    }

    /** Arma el APK con la plantilla, lo firma e inicia la instalación. */
    private void crear()
    {
        if (!getPackageManager().canRequestPackageInstalls()) {
            aviso(getString(R.string.permiso_instalar));
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())));
            return;
        }
        mOcupado = true;
        mostrar(3);
        new Thread(() -> {
            String fallo = null;
            File apk = new File(getCacheDir(), Juego.slug(mInfo.titulo, "juego") + ".apk");
            try {
                File plantilla = plantilla();
                Firma firma = firmaGuardada();
                if (firma == null)
                    firma = Firma.crear(carpetaFirma(), "com." + Juego.slug(mInfo.autor, "fangame") + "."
                        + Juego.slug(mInfo.titulo, "juego"), mInfo.autor, mInfo.titulo);
                Constructor.Datos d = new Constructor.Datos();
                d.titulo = mInfo.titulo;
                d.version = mInfo.version.isEmpty() ? "1.0" : mInfo.version;
                d.versionCode = Constructor.versionCodeAhora();
                d.ahorrar = mAhorrar;
                for (String abi : android.os.Build.SUPPORTED_ABIS)
                    if (abi.equals("arm64-v8a") || abi.equals("armeabi-v7a")) {
                        d.abis.add(abi); // solo el motor de este teléfono
                        break;
                    }
                if (mIcono != null)
                    try (java.io.InputStream in = getContentResolver().openInputStream(mIcono)) {
                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                        Juego.copiar(in, b);
                        d.iconoPng = b.toByteArray();
                    }
                Constructor.construir(plantilla, mJuego, d, firma, apk,
                    t -> mUi.post(() -> { if (mProgreso != null) mProgreso.setText(t); }));
                mApk = apk;
            } catch (Exception e) {
                fallo = getString(R.string.error, String.valueOf(e.getMessage()));
            }
            String error = fallo;
            mUi.post(() -> {
                mOcupado = false;
                mostrar(3);
                if (error != null)
                    mProgreso.setText(error);
                else
                    instalar(apk);
            });
        }).start();
    }

    /** La plantilla viene dentro de esta app; se copia afuera una vez para poder leerla. */
    private File plantilla() throws java.io.IOException
    {
        File f = new File(getFilesDir(), "plantilla.apk");
        long tam = getAssets().openFd("plantilla.apk").getLength();
        if (!f.isFile() || f.length() != tam)
            try (java.io.InputStream in = getAssets().open("plantilla.apk");
                 java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                Juego.copiar(in, out);
            }
        return f;
    }

    private void instalar(File apk)
    {
        mProgreso.setText(R.string.instalando);
        try {
            android.content.pm.PackageInstaller pi = getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams params = new android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(params);
            try (android.content.pm.PackageInstaller.Session s = pi.openSession(id)) {
                try (java.io.InputStream in = new java.io.FileInputStream(apk);
                     java.io.OutputStream out = s.openWrite("juego.apk", 0, apk.length())) {
                    Juego.copiar(in, out);
                    s.fsync(out);
                }
                android.content.Intent i = new android.content.Intent(this, MainActivity.class).setAction("rpgmk.instalado");
                android.app.PendingIntent p = android.app.PendingIntent.getActivity(this, 0, i,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_MUTABLE);
                s.commit(p.getIntentSender());
            }
        } catch (Exception e) {
            mProgreso.setText(getString(R.string.fallo_instalar, e.getMessage()));
        }
    }

    /** Respuesta del instalador de Android. */
    @Override
    protected void onNewIntent(android.content.Intent intent)
    {
        super.onNewIntent(intent);
        if (!"rpgmk.instalado".equals(intent.getAction()))
            return;
        int estado = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999);
        if (estado == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
            android.content.Intent confirmar = intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT);
            if (confirmar != null)
                startActivity(confirmar); // el aviso de Android: "¿Quieres instalar…?"
            return;
        }
        String texto;
        if (estado == android.content.pm.PackageInstaller.STATUS_SUCCESS)
            texto = getString(R.string.instalado);
        else if (estado == android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT)
            texto = getString(R.string.conflicto);
        else
            texto = getString(R.string.fallo_instalar, String.valueOf(intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)));
        if (mPaso != 3)
            mostrar(3);
        mProgreso.setText(texto);
    }

    private void guardarApk(Uri uri)
    {
        try (java.io.InputStream in = new java.io.FileInputStream(mApk);
             java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            Juego.copiar(in, out);
            aviso(getString(R.string.apk_guardado));
        } catch (Exception e) {
            aviso(getString(R.string.error, e.getMessage()));
        }
    }
}
