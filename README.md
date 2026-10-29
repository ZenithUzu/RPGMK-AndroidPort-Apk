**Español** | [English](README.en.md)

# Portémon · Android

Crea un APK de un juego de **RPG Maker XP, VX o VX Ace desde el teléfono**, con su propio nombre, icono e identificador. Está orientado también a fangames hechos con Pokémon Essentials.

Usa el motor **mkxp-z** y la plantilla compartida de [Portémon PC](https://github.com/ZenithUzu/portemon-pc), la herramienta compañera para Windows. El APK generado ejecuta el juego sin necesitar una app reproductora aparte ni emular Windows.

**[Descargar la app](https://github.com/ZenithUzu/portemon-android/releases/tag/ultima) · [Inicio rápido](#inicio-rápido) · [Reportar un problema](#diagnóstico-y-soporte) · [Créditos](#créditos) · [Licencia](#licencia)**

> **Estado: experimental y en pruebas.** Crear, firmar e instalar un APK no garantiza compatibilidad con todos los juegos. El rendimiento y las funciones disponibles dependen del juego y del teléfono.

## Sobre el proyecto

Proyecto personal de **ZenithUzu**, desarrollado **por pasatiempo y sin fines de lucro** para aprender, experimentar y facilitar la creación de ports de RPG Maker para Android. Se mantiene en el tiempo libre; las actualizaciones y la atención de errores dependen de esa disponibilidad.

Se apoya en motores, bibliotecas y aportaciones de la comunidad. Los créditos de esos proyectos se conservan más abajo.

### Uso de inteligencia artificial

En el desarrollo se han utilizado **ChatGPT (OpenAI)** y **Claude (Anthropic)** como herramientas de apoyo para programar, investigar errores, proponer mejoras, preparar pruebas y redactar documentación.

La IA puede equivocarse: su participación no garantiza que el código sea correcto, seguro ni compatible con todos los juegos. Las decisiones del proyecto y la comprobación de los cambios siguen siendo responsabilidad de quienes lo mantienen. Las pruebas automáticas complementan las pruebas en dispositivos reales; no las sustituyen.

Estas herramientas no son autoras de los motores ni de las bibliotecas de terceros. Su mención tampoco implica afiliación, patrocinio o respaldo de OpenAI o Anthropic.

## Versión 0.6 · Portémon

Nueva identidad e interfaz: inicio con acceso directo a ZIP o carpeta, navegación compacta del proyecto y colores dinámicos de Android. Mantiene el identificador de la app, su firma y las firmas de los juegos, por lo que se instala como actualización de RPGMK Port. La publicación distribuye un único APK: `Portemon.apk`. Los archivos y copias del flujo de creación mantienen su funcionamiento actual.

## Versión 0.5

Incluye la plantilla compartida con apertura manual de «Mando y teclado», letras pequeñas configurables en editor y juego, y barra del editor movible desde ⠿. Añade registros `RMXP PANTALLA` de brillo y transiciones para investigar el oscurecimiento de algunos juegos; ese fallo sigue en diagnóstico. El observador evita recursión con los alias de `Graphics` usados por Essentials y Relict. Para aplicar los cambios a un juego instalado, vuelve a crear su APK y actualízalo conservando su identificador y firma.

## Requisitos

- **Android 8.0 o posterior**. Los colores dinámicos de Material You requieren Android 12 o posterior.
- Un juego completo en una carpeta con `Game.ini` o en un archivo ZIP.
- Espacio libre para el APK y sus archivos de trabajo.
- Permiso para instalar aplicaciones desde esta fuente al instalar el constructor y los juegos.

No necesitas PC, Android Studio ni compilar el motor en el teléfono.

## Inicio rápido

1. Descarga **Portemon.apk** desde [Releases](https://github.com/ZenithUzu/portemon-android/releases/tag/ultima), instálalo y abre **Portémon**.
2. Elige el ZIP o la carpeta del juego. El original no se modifica. Revisa los avisos sobre DLL, videos WMV y llamadas de Windows como `Win32API`.
3. Comprueba **nombre, autor, versión e icono**. Si necesitas música MIDI, selecciona un **SoundFont externo (.sf2)**; el selector comprueba su cabecera.
4. Revisa la **firma**. Si ya creaste ese juego en PC, importa `juego.json` y `firma.jks` desde la carpeta del perfil del juego. Si es nuevo, exporta y respalda la firma generada.
5. Pulsa **Crear APK**. Al terminar, puedes **Instalar APK creado**, **Guardar el APK** o **Crear otra app**. Instalar el archivo terminado no repite su creación.

**APK universal** incluye todas las arquitecturas disponibles en la plantilla. Sin esa opción, se genera un APK adaptado al teléfono para reducir su tamaño.

## Funciones

| Apartado | Qué ofrece |
|---|---|
| Constructor | Asistente por pasos, ZIP o carpeta, revisión de compatibilidad, nombre, icono y versión. |
| Interfaz | Diseño Material con colores dinámicos en Android compatible y recuperación de los datos del asistente al recrear la pantalla. |
| Firma | Llave por juego, exportación e importación compatible con los perfiles de PC. |
| Instalación | Creación e instalación separadas; el APK anterior se reemplaza al terminar de firmar el nuevo. |
| Motor compartido | Menú lateral, editor de controles, teclado en pantalla y perfiles Xbox/Switch con cruceta, sticks y gatillos. |
| Opciones del juego | Pausa configurable de juego y música en menú/editor, personalización táctil, vibración y objetivos de FPS, incluidos 60/120. |
| Partidas y audio | Gestor de partidas, perfiles de controles y MIDI mediante FluidSynth y un SoundFont. |
| Diagnóstico | Registro de creación exportable; el juego generado permite exportar su propio log. |

Los objetivos de FPS no garantizan el rendimiento real. Consulta las [funciones del motor](https://github.com/ZenithUzu/portemon-pc#qué-trae-la-app) y la [comparación entre PC y Android](https://github.com/ZenithUzu/portemon-pc/blob/main/docs/paridad-pc-android.md).

## Actualizar sin perder firmas y partidas

Actualizar el **constructor** no cambia el motor ni la interfaz de los **juegos ya instalados**. Para aplicar esos cambios:

1. Instala la versión nueva del constructor.
2. Vuelve a crear el APK desde el juego completo.
3. Conserva el mismo identificador y la misma llave de firma e instálalo como actualización.

Respalda tus partidas y exporta las firmas antes de probar. Desinstalar un juego puede borrar sus datos internos.

El certificado puede contener el nombre del autor, pero la llave la genera la herramienta: **no es la llave privada del creador ni acredita un port oficial**. Una firma diferente impide actualizar el APK existente.

## Diagnóstico y soporte

- **Fallo al crear el APK:** usa **Guardar registro de creación** en el último paso.
- **Fallo dentro del juego:** abre el juego generado y usa **Atrás → Guardar registro (log)**. El constructor no puede leer el registro privado de otra app.
- **Captura por USB:** utiliza **Log del juego** en la herramienta de PC.
- **MIDI sin sonido:** comprueba que el juego incluya un SoundFont o selecciona un archivo `.sf2` antes de crear el APK.

Abre un [issue](https://github.com/ZenithUzu/portemon-android/issues) con la versión del constructor y del motor, juego y versión, teléfono, Android, pasos para reproducir el fallo y registro. Para problemas de FPS, indica objetivo, Hz actuales, zona y estado del turbo.

Revisa los adjuntos para retirar datos personales. No publiques firmas privadas, contraseñas ni juegos completos.

Las [pruebas recomendadas](https://github.com/ZenithUzu/portemon-pc/blob/main/docs/pruebas-ports.md) cubren instalación, actualización, partidas, menú y controles. Las pruebas de interfaz con motor simulado no miden FPS reales ni confirman la vibración física.

## Cómo funciona y cómo se sincroniza con PC

La app incluye una **plantilla de APK con el motor ya compilado**. Inserta los archivos del juego, modifica sus datos y firma el resultado con una llave por juego.

El motor, el menú y los controles se mantienen en Portémon PC. La compilación de este constructor espera la plantilla correspondiente al motor solicitado por PC y ejecuta sus comprobaciones antes de publicar `ultima`. Las dos herramientas comparten esa base; sus flujos de construcción e instalación son distintos.

## Para desarrolladores

Requiere **Java 17** y el **SDK de Android**:

```sh
./gradlew assembleDebug
```

| Ruta | Responsabilidad |
|---|---|
| `app/src/main/java/com/zenithuzu/rpgmkport/MainActivity.java` | Asistente y navegación. |
| `Origen.java`, `Juego.java` en el mismo paquete | Lectura del origen, datos y revisión de compatibilidad. |
| `Constructor.java`, `Firma.java` en el mismo paquete | Edición y firma de APK; perfiles compatibles con PC. |
| `Novedades.java` en el mismo paquete | Comprobación de novedades. |
| `.github/workflows/apk.yml` | Compilación, comprobaciones y publicación. |

El constructor utiliza `app/firma-app.jks`, una **llave de prueba incluida públicamente** para mantener la continuidad de sus actualizaciones. No debe tratarse como un secreto ni reutilizarse para otros proyectos. Los juegos usan sus propias llaves, que debes respaldar y mantener privadas.

## Créditos

- [ARSCLib](https://github.com/REAndroid/ARSCLib) (Apache-2.0) y [apksig](https://android.googlesource.com/platform/tools/apksig) (Apache-2.0): editar y firmar el APK en el teléfono.

- [Portémon PC](https://github.com/ZenithUzu/portemon-pc): motor, controles,
  menú y parches que lleva cada juego.
- [mkxp-z](https://github.com/mkxp-z/mkxp-z) y los proyectos en los que se apoya (ver los
  créditos de RMXP-Android-Ports).
- Idea de la interfaz por pasos: [Pokémon Reloaded en Android](https://github.com/Klisee/pokemon-reloaded-android).
- [Material Components for Android](https://github.com/material-components/material-components-android) (Apache-2.0): la interfaz Material You.
- Iconos: [Material Icons](https://fonts.google.com/icons) (Apache-2.0).

## Licencia

El código de este proyecto se publica bajo [GPL-2.0-or-later](LICENSE). Los componentes de terceros conservan sus licencias y avisos. El carácter no lucrativo del proyecto no añade una prohibición de uso comercial a la GPL.

**Los juegos y sus recursos no están incluidos ni quedan cubiertos por esta licencia.** Usa juegos que tengas derecho a portar y respeta los permisos de sus autores antes de distribuir un APK.

Proyecto independiente, sin afiliación ni respaldo de Nintendo, The Pokémon Company, Game Freak, los responsables de RPG Maker ni los autores de los fangames. Las marcas y recursos pertenecen a sus respectivos titulares.

El software se ofrece sin garantía, según su licencia. La asistencia con IA no modifica los derechos ni los créditos de terceros.
