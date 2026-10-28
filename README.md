**Español** | [English](README.en.md)

# RPGMK-AndroidPort

Convierte un fangame de **RPG Maker XP, VX o VX Ace** (por ejemplo, uno hecho con
Pokémon Essentials) en una **app de Android propia, desde el mismo teléfono**. No hace
falta PC, JoiPlay ni Kirin: cada juego queda como una app con su nombre, su icono y la
firma de su autor.

Es la versión para teléfono de [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports)
(la herramienta para Windows) y usa su mismo motor, **mkxp-z**: el juego corre de verdad,
no hay Windows emulado.

> **Estado: en pruebas.** Ya arma, firma e instala el APK. Falta probarlo en más teléfonos y juegos.

## Cómo se usa

1. Descarga **RPGMK-Port.apk** de la [última versión](https://github.com/ZenithUzu/RPGMK-AndroidPort-Apk/releases/tag/ultima) e instálala.
2. Abre la app y presiona **Empezar**.
3. **Elige el juego**: el `.zip` tal como lo descargaste, o la carpeta que tiene `Game.ini`.
   Se lee directo, sin copiarlo ni descomprimirlo (el original no se toca). Verás cuántos
   archivos tiene y una **revisión de compatibilidad**: avisa si usa `.dll`, videos `.wmv`
   o funciones de Windows (`Win32API`).
4. **Revisa los datos**: nombre, autor (va en la firma), versión e icono. Salen del
   `Game.ini` y del Léeme del juego.
5. **Firma**: si el juego ya lo hiciste en la PC, usa **Importar firma de la PC** y elige
   `juego.json` y `firma.jks` de `RMXP-Android-Ports\juegos\<juego>`. Así el APK nuevo se
   instala **encima** del de la PC sin perder partidas. Si no, se crea una firma nueva a
   nombre del autor; respáldala con **Exportar firma**.
6. **Crear e instalar**: arma el APK, lo firma y Android te pregunta si lo instalas. También
   puedes **Guardar el APK** para pasarlo a otro teléfono.

La app usa los colores de tu fondo de pantalla (Material You, Android 12 o más nuevo) y
te avisa cuando hay una versión nueva (de la app o del motor).

Cada juego trae lo mismo que los de la PC: menú de opciones, controles editables,
perfil de controles, gestor de partidas, vibración, parche `Win32API` para Essentials
viejos y música MIDI. Ver [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports#qué-trae-la-app).

## Cómo funciona

No se compila nada en el teléfono. Todos los APK de juegos son iguales por dentro (el
mismo motor ya compilado); solo cambian los archivos del juego, el nombre, el icono, el id
y la firma. Por eso la app:

1. trae una **plantilla** del APK, compilada por RMXP-Android-Ports;
2. le mete el juego y le cambia nombre, icono, id y versión;
3. la **firma** a nombre del autor (una sola vez, firma v2) (con una llave propia por juego, que puedes respaldar);
4. la **instala**.

| Parte | Qué hace | Estado |
|---|---|---|
| 1 | Interfaz, importar juego (.zip o carpeta), leer datos | Hecha |
| 2 | Plantilla del APK y armar el APK con el juego | Hecha |
| 3 | Firma por juego (compatible con la PC), instalar, actualizar | Hecha |

## Para desarrolladores

```
app/src/main/java/com/zenithuzu/rpgmkport/
  MainActivity.java   asistente por pasos
  Origen.java         leer el juego desde una carpeta o el .zip, sin copiarlo
  Juego.java          abrir el juego, leer nombre, autor y versión, y revisar compatibilidad
  Novedades.java      aviso de versión nueva (release "ultima")
  Constructor.java    armar el APK desde la plantilla (ARSCLib) y firmarlo (apksig)
  Firma.java          firma por juego, mismo formato que la PC (firma.jks + juego.json)
.github/workflows/apk.yml   compila y publica la versión "ultima"
```

Compilar: `./gradlew assembleDebug` (Java 17 y el SDK de Android).
La app se firma con `app/firma-app.jks` (llave pública de prueba, guardada en el repo a
propósito para que cada versión nueva se instale encima). Los APK de los juegos se firman
con otra llave, la de cada autor.

## Créditos

- [ARSCLib](https://github.com/REAndroid/ARSCLib) (Apache-2.0) y [apksig](https://android.googlesource.com/platform/tools/apksig) (Apache-2.0): editar y firmar el APK en el teléfono.

- [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports): motor, controles,
  menú y parches que lleva cada juego.
- [mkxp-z](https://github.com/mkxp-z/mkxp-z) y los proyectos en los que se apoya (ver los
  créditos de RMXP-Android-Ports).
- Idea de la interfaz por pasos: [Pokémon Reloaded en Android](https://github.com/Klisee/pokemon-reloaded-android).
- [Material Components for Android](https://github.com/material-components/material-components-android) (Apache-2.0): la interfaz Material You.
- Iconos: [Material Icons](https://fonts.google.com/icons) (Apache-2.0).

## Licencia

[GPL-2.0-or-later](LICENSE). Los juegos no están incluidos ni cubiertos por esta licencia:
portar un fangame necesita el permiso de su autor, y los fangames de Pokémon usan propiedad
de Nintendo, The Pokémon Company y Game Freak.
