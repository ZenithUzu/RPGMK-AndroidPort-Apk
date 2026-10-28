[Español](README.md) | **English**

# RPGMK-AndroidPort

Turns an **RPG Maker XP, VX or VX Ace** fangame (for example, one made with Pokémon
Essentials) into **its own Android app, right on your phone**. No PC, JoiPlay or Kirin
needed: each game becomes an app with its own name, icon and its author's signature.

It's the phone version of [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports)
(the Windows tool) and uses the same engine, **mkxp-z**: the game really runs, there is no
emulated Windows.

> **Status: testing.** It already builds, signs and installs the APK. It still needs testing on more phones and games.

## How to use it

1. Download **RPGMK-Port.apk** from the [latest release](https://github.com/ZenithUzu/RPGMK-AndroidPort-Apk/releases/tag/ultima) and install it.
2. Open the app and tap **Get started**.
3. **Choose the game**: the `.zip` as you downloaded it, or the folder with `Game.ini`.
   It's read directly, without copying or unzipping it (the original isn't touched). You'll
   see how many files it has and a **compatibility check**: it warns if the game uses
   `.dll` files, `.wmv` videos or Windows functions (`Win32API`).
4. **Check the details**: name, author (goes into the signature), version and icon, taken
   from `Game.ini` and the game's Readme.
5. **Signature**: if you already made the game on the PC, use **Import signature from PC**
   and pick `juego.json` and `firma.jks` from `RMXP-Android-Ports\juegos\<game>`. Then the
   new APK installs **over** the PC one without losing saves. Otherwise a new signature is
   created in the author's name; back it up with **Export signature**.
6. **Create and install**: builds the APK, signs it and Android asks you to install it. You
   can also **Save the APK** to move it to another phone.

The app uses your wallpaper colors (Material You, Android 12 or newer) and tells you
when a new version (of the app or the engine) is out.

Each game includes the same as the PC ones: options menu, editable controls, controls
profile, save manager, vibration, `Win32API` patch for older Essentials and MIDI music.
See [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports/blob/main/README.en.md).

## How it works

Nothing is compiled on the phone. Every game APK is the same inside (the same prebuilt
engine); only the game files, name, icon, id and signature change. So the app:

1. ships an APK **template** built by RMXP-Android-Ports;
2. puts the game into it and changes its name, icon, id and version;
3. **signs** it in the author's name (once, v2 signature) (with a key per game that you can back up);
4. **installs** it.

| Part | What it does | Status |
|---|---|---|
| 1 | UI, importing the game (.zip or folder), reading its details | Done |
| 2 | APK template and building the APK with the game | Done |
| 3 | Per-game signature (PC-compatible), install, update | Done |

## For developers

```
app/src/main/java/com/zenithuzu/rpgmkport/
  MainActivity.java   step-by-step wizard
  Origen.java         reads the game from a folder or the .zip, without copying it
  Juego.java          opens the game, reads name, author and version, checks compatibility
  Novedades.java      new-version notice ("ultima" release)
  Constructor.java    builds the APK from the template (ARSCLib) and signs it (apksig)
  Firma.java          per-game signature, same format as the PC (firma.jks + juego.json)
.github/workflows/apk.yml   builds and publishes the "ultima" release
```

Build: `./gradlew assembleDebug` (Java 17 and the Android SDK).
The app is signed with `app/firma-app.jks` (a public test key kept in the repo on purpose
so each new version installs over the previous one). Game APKs are signed with a
different key, each author's.

## Credits

- [ARSCLib](https://github.com/REAndroid/ARSCLib) (Apache-2.0) and [apksig](https://android.googlesource.com/platform/tools/apksig) (Apache-2.0): editing and signing the APK on the phone.

- [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports): engine, controls,
  menu and patches shipped with each game.
- [mkxp-z](https://github.com/mkxp-z/mkxp-z) and the projects it builds on (see
  RMXP-Android-Ports' credits).
- Step-by-step UI idea: [Pokémon Reloaded on Android](https://github.com/Klisee/pokemon-reloaded-android).
- [Material Components for Android](https://github.com/material-components/material-components-android) (Apache-2.0): the Material You UI.
- Icons: [Material Icons](https://fonts.google.com/icons) (Apache-2.0).

## License

[GPL-2.0-or-later](LICENSE). Games are not included in or covered by this license:
porting a fangame needs its author's permission, and Pokémon fangames use property of
Nintendo, The Pokémon Company and Game Freak.
