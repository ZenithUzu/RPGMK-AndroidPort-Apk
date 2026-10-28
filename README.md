**Español** | [English](README.en.md)

# RPGMK-AndroidPort

Convierte un fangame de **RPG Maker XP, VX o VX Ace** (por ejemplo, uno hecho con
Pokémon Essentials) en una **app de Android propia, desde el mismo teléfono**. No hace
falta PC, JoiPlay ni Kirin: cada juego queda como una app con su nombre, su icono y la
firma de su autor.

Es la versión para teléfono de [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports)
(la herramienta para Windows) y usa su mismo motor, **mkxp-z**: el juego corre de verdad,
no hay Windows emulado.

> **Estado: en desarrollo.** Ya funciona la parte de elegir el juego y revisar sus datos.
> Armar e instalar el APK llega en las siguientes partes.

## Cómo se usa

1. Descarga **RPGMK-Port.apk** de la [última versión](https://github.com/ZenithUzu/RPGMK-AndroidPort-Apk/releases/tag/ultima) e instálala.
2. Abre la app y presiona **Empezar**.
3. **Elige el juego**: el `.zip` tal como lo descargaste, o la carpeta que tiene `Game.ini`.
   Se copia dentro de la app; el original no se toca.
4. **Revisa los datos**: nombre, autor (va en la firma), versión e icono. Salen del
   `Game.ini` y del Léeme del juego.
5. **Crear e instalar** *(próximamente)*.

La app usa los colores de tu fondo de pantalla (Material You, Android 12 o más nuevo).

## Cómo funciona (plan)

No se compila nada en el teléfono. Todos los APK de juegos son iguales por dentro (el
mismo motor ya compilado); solo cambian los archivos del juego, el nombre, el icono, el id
y la firma. Por eso la app:

1. trae una **plantilla** del APK, compilada por RMXP-Android-Ports;
2. le mete el juego y le cambia nombre, icono, id y versión;
3. la **firma** a nombre del autor (con una llave propia por juego, que puedes respaldar);
4. la **instala**.

| Parte | Qué hace | Estado |
|---|---|---|
| 1 | Interfaz, importar juego (.zip o carpeta), leer datos | Hecha |
| 2 | Plantilla del APK y armar el APK con el juego | Pendiente |
| 3 | Firma por juego, instalar, actualizar sin perder partidas | Pendiente |

## Para desarrolladores

```
app/src/main/java/com/zenithuzu/rpgmkport/
  MainActivity.java   asistente por pasos
  Juego.java          importar el juego y leer nombre, autor y versión
.github/workflows/apk.yml   compila y publica la versión "ultima"
```

Compilar: `./gradlew assembleDebug` (Java 17 y el SDK de Android).
La app se firma con `app/firma-app.jks` (llave pública de prueba, guardada en el repo a
propósito para que cada versión nueva se instale encima). Los APK de los juegos se firman
con otra llave, la de cada autor.

## Créditos

- [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports): motor, controles,
  menú y parches que lleva cada juego.
- [mkxp-z](https://github.com/mkxp-z/mkxp-z) y los proyectos en los que se apoya (ver los
  créditos de RMXP-Android-Ports).
- Idea de la interfaz por pasos: [Pokémon Reloaded en Android](https://github.com/Klisee/pokemon-reloaded-android).
- Iconos: [Material Icons](https://fonts.google.com/icons) (Apache-2.0).

## Licencia

[GPL-2.0-or-later](LICENSE). Los juegos no están incluidos ni cubiertos por esta licencia:
portar un fangame necesita el permiso de su autor, y los fangames de Pokémon usan propiedad
de Nintendo, The Pokémon Company y Game Freak.
