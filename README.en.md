[Español](README.md) | **English**

# RPGMK-AndroidPort

Turns an **RPG Maker XP, VX or VX Ace** fangame (for example, one made with Pokémon
Essentials) into **its own Android app, right on your phone**. No PC, JoiPlay or Kirin
needed: each game becomes an app with its own name, icon and its author's signature.

It's the phone version of [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports)
(the Windows tool) and uses the same engine, **mkxp-z**: the game really runs, there is no
emulated Windows.

> **Status: in development.** Choosing the game and checking its details already works.
> Building and installing the APK come in the next parts.

## How to use it

1. Download **RPGMK-Port.apk** from the [latest release](https://github.com/ZenithUzu/RPGMK-AndroidPort-Apk/releases/tag/ultima) and install it.
2. Open the app and tap **Get started**.
3. **Choose the game**: the `.zip` as you downloaded it, or the folder with `Game.ini`.
   It's copied into the app; the original isn't touched.
4. **Check the details**: name, author (goes into the signature), version and icon, taken
   from `Game.ini` and the game's Readme.
5. **Create and install** *(coming soon)*.

The app uses your wallpaper colors (Material You, Android 12 or newer).

## How it works (plan)

Nothing is compiled on the phone. Every game APK is the same inside (the same prebuilt
engine); only the game files, name, icon, id and signature change. So the app:

1. ships an APK **template** built by RMXP-Android-Ports;
2. puts the game into it and changes its name, icon, id and version;
3. **signs** it in the author's name (with a key per game that you can back up);
4. **installs** it.

| Part | What it does | Status |
|---|---|---|
| 1 | UI, importing the game (.zip or folder), reading its details | Done |
| 2 | APK template and building the APK with the game | Pending |
| 3 | Per-game signature, install, update without losing saves | Pending |

## For developers

```
app/src/main/java/com/zenithuzu/rpgmkport/
  MainActivity.java   step-by-step wizard
  Juego.java          imports the game and reads name, author and version
.github/workflows/apk.yml   builds and publishes the "ultima" release
```

Build: `./gradlew assembleDebug` (Java 17 and the Android SDK).
The app is signed with `app/firma-app.jks` (a public test key kept in the repo on purpose
so each new version installs over the previous one). Game APKs are signed with a
different key, each author's.

## Credits

- [RMXP-Android-Ports](https://github.com/ZenithUzu/RMXP-Android-Ports): engine, controls,
  menu and patches shipped with each game.
- [mkxp-z](https://github.com/mkxp-z/mkxp-z) and the projects it builds on (see
  RMXP-Android-Ports' credits).
- Step-by-step UI idea: [Pokémon Reloaded on Android](https://github.com/Klisee/pokemon-reloaded-android).
- Icons: [Material Icons](https://fonts.google.com/icons) (Apache-2.0).

## License

[GPL-2.0-or-later](LICENSE). Games are not included in or covered by this license:
porting a fangame needs its author's permission, and Pokémon fangames use property of
Nintendo, The Pokémon Company and Game Freak.
