package com.zenithuzu.rpgmkport;

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
    private Origen mJuego;          // el juego (carpeta o .zip), leído sin copiarlo
    private Uri mJuegoUri;
    private boolean mJuegoZip;
    private Juego.Info mInfo;
    private Uri mIcono, mSoundfont;
    private int mOrientacion;
    private boolean mAhorrar, mUniversal, mOcupado, mInsurgence;
    private int mFondo;             // fondo del icono: 0 gris, 1 negro, 2 blanco
    private static final int[] FONDOS = { 0xFF5F6368, 0xFF000000, 0xFFFFFFFF };

    private View mCabecera;
    private TextView mAyuda;
    private LinearLayout mBarras;
    private FrameLayout mContenido;
    private MaterialButton mSiguiente;
    private final Handler mUi = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String[]> mElegirZip, mElegirIcono, mElegirSoundfont, mImportarFirma;
    private ActivityResultLauncher<Uri> mElegirCarpeta, mExportarFirma;
    private ActivityResultLauncher<String> mGuardarApk, mGuardarLog;
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
                guardarDatos();
                conservarPermiso(uri);
                mIcono = uri;
                mostrar(mPaso);
            }
        });
        mElegirSoundfont = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                guardarDatos();
                conservarPermiso(uri);
                mSoundfont = uri;
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
            if (uri != null) guardarArchivo(mApk, uri, R.string.apk_guardado);
        });
        mGuardarLog = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
            if (uri != null) guardarArchivo(new File(getFilesDir(), "constructor.log"), uri, R.string.log_guardado);
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
            String j = state.getString("juego");
            if (j != null) {
                if (state.getString("icono") != null) mIcono = Uri.parse(state.getString("icono"));
                if (state.getString("soundfont") != null) mSoundfont = Uri.parse(state.getString("soundfont"));
                mAhorrar = state.getBoolean("ahorrar");
                mInsurgence = state.getBoolean("insurgence");
                mUniversal = state.getBoolean("universal");
                mFondo = Math.max(0, Math.min(FONDOS.length - 1, state.getInt("fondo")));
                if (state.getString("apk") != null) mApk = new File(state.getString("apk"));
                importar(Uri.parse(j), state.getBoolean("zip"), state);
                return;
            }
        }
        mostrar(mPaso);
        Novedades.revisar(this);
        new Thread(() -> Juego.borrar(new File(getFilesDir(), "juegos"))).start(); // copias de versiones viejas
    }

    @Override
    protected void onSaveInstanceState(Bundle out)
    {
        super.onSaveInstanceState(out);
        guardarDatos();
        out.putInt("paso", mPaso);
        out.putBoolean("ahorrar", mAhorrar);
        out.putBoolean("insurgence", mInsurgence);
        out.putBoolean("universal", mUniversal);
        out.putInt("fondo", mFondo);
        if (mInfo != null) {
            out.putString("titulo", mInfo.titulo);
            out.putString("autor", mInfo.autor);
            out.putString("version", mInfo.version);
        }
        if (mIcono != null) out.putString("icono", mIcono.toString());
        if (mSoundfont != null) out.putString("soundfont", mSoundfont.toString());
        if (mApk != null) out.putString("apk", mApk.getPath());
        if (mJuegoUri != null) {
            out.putString("juego", mJuegoUri.toString());
            out.putBoolean("zip", mJuegoZip);
        }
    }

    // ------------------------------------------------------------------ navegación

    private void atras()
    {
        if (!mOcupado && mPaso > 0)
            mostrar(mPaso - 1);
    }

    private void siguiente()
    {
        if (mOcupado) return;
        if (mPaso == 3) {
            if (mApk != null && mApk.isFile()) instalar(mApk);
            else crear();
            return;
        }
        if (mPaso == 2)
            guardarDatos();
        if (mPaso < PASOS)
            mostrar(mPaso + 1);
    }

    private void mostrar(int paso)
    {
        if (paso >= 2 && (mInfo == null || mJuego == null)) paso = 1;
        mEstadoFirma = null;
        if (mPaso == 2 && paso != 2)
            guardarDatos();
        mPaso = paso;
        mContenido.removeAllViews();
        mCabecera.setVisibility(paso == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.acciones).setVisibility(paso == 0 ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.workspace_title)).setText(paso == 0 ? R.string.workspace_home : R.string.workspace_project);
        dibujarBarras(paso);
        mAyuda.setVisibility(View.GONE);
        mSiguiente.setEnabled(true);
        mSiguiente.setText(R.string.continuar);

        switch (paso) {
            case 0:
                View v = inflar(R.layout.paso_bienvenida);
                sourceActions(v);
                v.findViewById(R.id.home_start).setOnClickListener(x -> mostrar(1));
                break;
            case 1:
                pasoJuego();
                break;
            case 2:
                mApk = null;
                pasoDatos();
                break;
            default:
                pasoCrear();
        }
    }

    /** Navegación compacta del proyecto; los pasos futuros se habilitan al avanzar. */
    private void dibujarBarras(int paso)
    {
        mBarras.removeAllViews();
        int[] names = { R.string.paso_juego, R.string.paso_datos, R.string.paso_crear };
        for (int i = 1; i <= PASOS; i++) {
            final int target = i;
            MaterialButton tab = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            tab.setText(i + " · " + getString(names[i - 1]));
            tab.setTextSize(12);
            tab.setMinWidth(0);
            tab.setMinimumWidth(0);
            tab.setPadding(dp(6), 0, dp(6), 0);
            tab.setCornerRadius(dp(12));
            tab.setEnabled(!mOcupado && i <= paso);
            if (i == paso) {
                tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(com.google.android.material.R.attr.colorPrimaryContainer)));
                tab.setTextColor(color(com.google.android.material.R.attr.colorOnPrimaryContainer));
            }
            tab.setOnClickListener(v -> { if (!mOcupado) mostrar(target); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            mBarras.addView(tab, lp);
        }
    }

    private void sourceActions(View view)
    {
        view.findViewById(R.id.source_zip).setOnClickListener(v -> {
            if (!mOcupado) mElegirZip.launch(new String[] { "application/zip", "application/x-zip-compressed", "application/octet-stream" });
        });
        view.findViewById(R.id.source_folder).setOnClickListener(v -> {
            if (!mOcupado) mElegirCarpeta.launch(null);
        });
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
        View sources = LayoutInflater.from(this).inflate(R.layout.opciones_origen, box, false);
        box.addView(sources);
        sourceActions(sources);

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
            mEstado.setText(getString(R.string.juego_listo, mInfo.titulo, mInfo.version)
                + "\n" + getString(R.string.archivos, mInfo.archivos)
                + (mInfo.avisos.isEmpty() ? "\n✓ " + getString(R.string.compatible) : "\n⚠ " + String.join("\n⚠ ", mInfo.avisos)));

        mSiguiente.setEnabled(mJuego != null && !mOcupado);
        if (mJuego == null) {
            mAyuda.setText(R.string.falta_juego);
            mAyuda.setVisibility(View.VISIBLE);
        }
    }

    private void importar(Uri uri, boolean zip)
    {
        importar(uri, zip, null);
    }

    private void importar(Uri uri, boolean zip, Bundle restaurar)
    {
        if (mOcupado) return;
        cerrarJuego();
        mInfo = null;
        mJuegoUri = uri;
        mJuegoZip = zip;
        conservarPermiso(uri);
        if (restaurar == null) {
            mIcono = null;
            mSoundfont = null;
            mApk = null;
        }
        mOcupado = true;
        mOrientacion = getRequestedOrientation();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        mostrar(1);
        new Thread(() -> {
            String error = null;
            Origen juego = null;
            Juego.Info info = null;
            try {
                Juego.Progreso p = n -> mUi.post(() -> { if (mEstado != null) mEstado.setText(getString(R.string.copiando, n)); });
                juego = zip ? Juego.abrirZip(this, uri) : Juego.abrirCarpeta(this, uri, p);
                mUi.post(() -> { if (mEstado != null) mEstado.setText(R.string.revisando); });
                info = Juego.leer(juego);
            } catch (Juego.SinGameIni e) {
                error = getString(R.string.sin_game_ini);
            } catch (Exception e) {
                error = getString(R.string.error, e.getMessage());
            }
            Origen listo = juego;
            Juego.Info leido = info;
            String fallo = error;
            mUi.post(() -> {
                mOcupado = false;
                if (isDestroyed()) {
                    if (listo != null) try { listo.close(); } catch (java.io.IOException ignored) {}
                    return;
                }
                if (listo != null && leido != null) {
                    mJuego = listo;
                    mJuegoUri = uri;
                    mJuegoZip = zip;
                    mInfo = leido;
                    if (restaurar != null) {
                        mInfo.titulo = restaurar.getString("titulo", mInfo.titulo);
                        mInfo.autor = restaurar.getString("autor", mInfo.autor);
                        mInfo.version = restaurar.getString("version", mInfo.version);
                    }
                } else if (listo != null) {
                    try { listo.close(); } catch (java.io.IOException ignored) {}
                }
                mostrar(mInfo != null && restaurar != null ? Math.max(1, Math.min(PASOS, restaurar.getInt("paso", 1))) : 1);
                if (fallo != null)
                    mEstado.setText(fallo);
                setRequestedOrientation(mOrientacion);
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
        MaterialSwitch insurgence = v.findViewById(R.id.insurgence);
        insurgence.setChecked(mInsurgence);
        insurgence.setOnCheckedChangeListener((b, on) -> mInsurgence = on);
        MaterialSwitch ahorrar = v.findViewById(R.id.ahorrar);
        ahorrar.setChecked(mAhorrar);
        ahorrar.setOnCheckedChangeListener((b, on) -> mAhorrar = on);
        MaterialSwitch universal = v.findViewById(R.id.universal);
        universal.setChecked(mUniversal);
        universal.setOnCheckedChangeListener((b, on) -> mUniversal = on);

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
        opcion((ViewGroup) v, R.drawable.ic_mando, getString(R.string.soundfont),
            mSoundfont == null ? getString(R.string.soundfont_sub) : nombreDe(mSoundfont),
            x -> mElegirSoundfont.launch(new String[] { "*/*" }));
        if (mSoundfont != null) {
            MaterialButton quitar = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            quitar.setText(R.string.soundfont_quitar);
            quitar.setOnClickListener(x -> { guardarDatos(); mSoundfont = null; mostrar(2); });
            ((ViewGroup) v).addView(quitar);
        }

        // Fondo del icono (igual que en la herramienta de PC)
        com.google.android.material.button.MaterialButtonToggleGroup fondos = new com.google.android.material.button.MaterialButtonToggleGroup(this);
        fondos.setSingleSelection(true);
        fondos.setSelectionRequired(true);
        int[] nombres = { R.string.fondo_gris, R.string.fondo_negro, R.string.fondo_blanco };
        for (int i = 0; i < nombres.length; i++) {
            MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setId(View.generateViewId());
            b.setText(nombres[i]);
            b.setTag(i);
            fondos.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (i == mFondo)
                fondos.check(b.getId());
        }
        fondos.addOnButtonCheckedListener((g, id, on) -> { if (on) mFondo = (int) g.findViewById(id).getTag(); });
        TextView tf = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        tf.setText(R.string.fondo_icono);
        tf.setPadding(0, dp(16), 0, dp(4));
        LinearLayout lv = (LinearLayout) v;
        lv.addView(tf, lv.indexOfChild(icono) + 1);
        lv.addView(fondos, lv.indexOfChild(tf) + 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        if (!mOcupado && new File(getFilesDir(), "constructor.log").isFile())
            opcion(box, R.drawable.ic_zip, getString(R.string.guardar_log), getString(R.string.guardar_log_sub),
                x -> mGuardarLog.launch("rpgmk-constructor-log.txt"));
        boolean listo = mApk != null && mApk.isFile();
        if (listo && !mOcupado) {
            opcion(box, R.drawable.ic_zip, getString(R.string.crear_otra), getString(R.string.crear_otra_sub), x -> {
                cerrarJuego(); mInfo = null; mApk = null; mJuegoUri = null;
                mIcono = null; mSoundfont = null; mostrar(1);
            });
            opcion(box, R.drawable.ic_zip, getString(R.string.editar_datos), getString(R.string.editar_datos_sub), x -> {
                mApk = null; mostrar(2);
            });
        }
        mSiguiente.setText(listo ? R.string.instalar_apk : R.string.crear);
        mSiguiente.setEnabled(!mOcupado);
    }

    /** Arma y firma el APK; instalarlo es una acción independiente. */
    private void crear()
    {
        if (mJuego == null || mInfo == null || mOcupado) return;
        mOcupado = true;
        mApk = null;
        mOrientacion = getRequestedOrientation();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        mostrar(3);
        new Thread(() -> {
            String fallo = null;
            StringBuilder registro = new StringBuilder("Portémon " + BuildConfig.ID + "\nJuego: "
                + mInfo.titulo + " " + mInfo.version + "\nAPK universal: " + mUniversal + "\n");
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
                d.insurgence = mInsurgence;
                registro.append("Perfil Insurgence experimental: ").append(mInsurgence).append("\n");
                if (mSoundfont != null) {
                    File sf2 = new File(getCacheDir(), "elegida.sf2");
                    try (java.io.InputStream in = getContentResolver().openInputStream(mSoundfont);
                         java.io.OutputStream out = new java.io.FileOutputStream(sf2)) {
                        if (in == null) throw new java.io.IOException(getString(R.string.soundfont_invalido));
                        byte[] header = new byte[12];
                        try { new java.io.DataInputStream(in).readFully(header); }
                        catch (java.io.EOFException e) { throw new java.io.IOException(getString(R.string.soundfont_invalido), e); }
                        if (!Constructor.cabeceraSoundfont(header))
                            throw new java.io.IOException(getString(R.string.soundfont_invalido));
                        out.write(header);
                        Juego.copiar(in, out);
                    }
                    d.soundfont = sf2;
                }
                for (String abi : mUniversal ? new String[0] : android.os.Build.SUPPORTED_ABIS)
                    if (abi.equals("arm64-v8a") || abi.equals("armeabi-v7a")) {
                        d.abis.add(abi); // solo el motor de este teléfono
                        break;
                    }
                // Fondo del icono: un PNG de un color, como el de la PC
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(108, 108, android.graphics.Bitmap.Config.ARGB_8888);
                bmp.eraseColor(FONDOS[mFondo]);
                java.io.ByteArrayOutputStream fb = new java.io.ByteArrayOutputStream();
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fb);
                d.fondoPng = fb.toByteArray();
                if (mIcono != null)
                    try (java.io.InputStream in = getContentResolver().openInputStream(mIcono)) {
                        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                        Juego.copiar(in, b);
                        d.iconoPng = b.toByteArray();
                    }
                Constructor.construir(plantilla, mJuego, d, firma, apk,
                    t -> {
                        registro.append(t).append('\n');
                        mUi.post(() -> { if (mProgreso != null) mProgreso.setText(t); });
                    });
                mApk = apk;
            } catch (Exception e) {
                fallo = getString(R.string.error, String.valueOf(e.getMessage()));
                java.io.StringWriter stack = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(stack));
                registro.append(stack);
            } finally {
                new File(getCacheDir(), "elegida.sf2").delete();
                try {
                    java.nio.file.Files.write(new File(getFilesDir(), "constructor.log").toPath(),
                        registro.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (java.io.IOException ignored) {}
            }
            String error = fallo;
            mUi.post(() -> {
                mOcupado = false;
                if (isDestroyed()) { cerrarJuego(); return; }
                mostrar(3);
                if (error != null)
                    mProgreso.setText(error);
                else
                    mProgreso.setText(R.string.apk_creado);
                setRequestedOrientation(mOrientacion);
            });
        }).start();
    }

    /** Siempre se usa la plantilla de esta version, aunque la anterior mida lo mismo. */
    private File plantilla() throws java.io.IOException
    {
        File f = new File(getFilesDir(), "plantilla.apk");
        File temporal = new File(getFilesDir(), "plantilla.apk.tmp");
        try {
            try (java.io.InputStream in = getAssets().open("plantilla.apk");
                 java.io.OutputStream out = new java.io.FileOutputStream(temporal)) {
                Juego.copiar(in, out);
            }
            java.nio.file.Files.move(temporal.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            temporal.delete();
        }
        return f;
    }

    private void conservarPermiso(Uri uri)
    {
        try {
            getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { /* algunos proveedores solo dan permiso temporal */ }
    }

    private void cerrarJuego()
    {
        if (mJuego != null) {
            try { mJuego.close(); } catch (java.io.IOException ignored) {}
            mJuego = null;
        }
    }

    @Override protected void onDestroy()
    {
        if (!mOcupado) cerrarJuego();
        super.onDestroy();
    }

    private void instalar(File apk)
    {
        if (!getPackageManager().canRequestPackageInstalls()) {
            aviso(getString(R.string.permiso_instalar));
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())));
            return;
        }
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
        if (mInfo == null || mJuego == null) { aviso(texto); return; }
        if (mPaso != 3)
            mostrar(3);
        mProgreso.setText(texto);
    }

    private void guardarArchivo(File archivo, Uri uri, int mensaje)
    {
        try (java.io.InputStream in = new java.io.FileInputStream(archivo);
             java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("No se pudo abrir el destino");
            Juego.copiar(in, out);
            aviso(getString(mensaje));
        } catch (Exception e) {
            aviso(getString(R.string.error, e.getMessage()));
        }
    }
}
