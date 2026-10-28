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
    private TextView mTextoPaso, mAyuda;
    private LinearLayout mBarras;
    private FrameLayout mContenido;
    private MaterialButton mSiguiente;
    private final Handler mUi = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String[]> mElegirZip, mElegirIcono;
    private ActivityResultLauncher<Uri> mElegirCarpeta;

    @Override
    protected void onCreate(Bundle state)
    {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        mCabecera = findViewById(R.id.cabecera);
        mTextoPaso = findViewById(R.id.paso);
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
        if (mPaso == 2)
            guardarDatos();
        if (mPaso < PASOS)
            mostrar(mPaso + 1);
    }

    private void mostrar(int paso)
    {
        if (mPaso == 2 && paso != 2)
            guardarDatos();
        mPaso = paso;
        mContenido.removeAllViews();
        mCabecera.setVisibility(paso == 0 ? View.GONE : View.VISIBLE);
        mTextoPaso.setText(getString(R.string.paso, paso, PASOS));
        dibujarBarras(paso);
        mAyuda.setVisibility(View.GONE);
        mSiguiente.setEnabled(true);
        mSiguiente.setText(R.string.continuar);

        switch (paso) {
            case 0:
                View v = inflar(R.layout.paso_bienvenida);
                GradientDrawable circulo = new GradientDrawable();
                circulo.setShape(GradientDrawable.OVAL);
                circulo.setColor(MaterialColors.getColor(v, com.google.android.material.R.attr.colorPrimaryContainer));
                v.findViewById(R.id.circulo).setBackground(circulo);
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

    private void dibujarBarras(int paso)
    {
        mBarras.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 1; i <= PASOS; i++) {
            View b = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(4 * dp);
            d.setColor(MaterialColors.getColor(mBarras, i <= paso
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorSurfaceContainerHighest));
            b.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(4 * dp), 1);
            lp.setMarginStart(i == 1 ? 0 : Math.round(4 * dp));
            mBarras.addView(b, lp);
        }
    }

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
        ((LinearLayout) mContenido.getChildAt(0)).addView(v);
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

    private void pasoCrear()
    {
        LinearLayout box = encabezado(R.string.crear_titulo, R.string.crear_texto);
        TextView aviso = new TextView(this, null, 0, R.style.Rpg_Cuerpo);
        aviso.setText(getString(R.string.juego_listo, mInfo.titulo, mInfo.version) + "\n" + mInfo.autor);
        box.addView(aviso);
        mSiguiente.setText(R.string.crear);
        mSiguiente.setEnabled(false);
        mAyuda.setText(R.string.en_construccion);
        mAyuda.setVisibility(View.VISIBLE);
    }
}
