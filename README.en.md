[Español](README.md) | **English**

# Portémon · Android

Build an APK for an **RPG Maker XP, VX or VX Ace game directly on your phone**, with its own name, icon and application ID. This also targets fangames made with Pokémon Essentials.

It uses **mkxp-z** and the shared template from [Portémon PC](https://github.com/ZenithUzu/portemon-pc), the companion Windows tool. The generated APK runs the game without a separate player app or emulating Windows.

**[Download](https://github.com/ZenithUzu/portemon-android/releases/tag/ultima) · [Quick start](#quick-start) · [Report an issue](#diagnostics-and-support) · [Credits](#credits) · [License](#license)**

> **Status: experimental and in testing.** Building, signing and installing an APK does not guarantee compatibility with every game. Performance and available features depend on the game and phone.

## About the project

A personal project by **ZenithUzu**, developed **as a hobby and without a profit motive** to learn, experiment and make it easier to create Android ports of RPG Maker games. Maintenance happens in spare time; updates and issue responses depend on that availability.

It builds on community engines, libraries and contributions, credited below.

### Use of artificial intelligence

**ChatGPT (OpenAI)** and **Claude (Anthropic)** have been used to assist with programming, investigating bugs, proposing improvements, preparing tests and writing documentation.

AI can make mistakes. Its involvement does not guarantee correct, secure code or compatibility with every game. Project decisions and verification remain the maintainers' responsibility. Automated tests complement testing on real devices; they do not replace it.

These tools did not author the third-party engines or libraries. Mentioning them does not imply affiliation, sponsorship or endorsement by OpenAI or Anthropic.

## Version 0.6 · Portémon

New identity and workspace: direct ZIP/folder access, compact project navigation and Android dynamic colors. Keeps the application ID, app signing key and game signing keys, so it updates RPGMK Port in place. The former `RPGMK-Port.apk` download remains available for compatibility. Existing build-file and copy handling is unchanged.

## Version 0.5

Includes the shared template with manually opened controller settings, optional small key labels in both editor and game, and an editor toolbar draggable from ⠿. Adds `RMXP PANTALLA` brightness and transition logs to investigate dark screens; this issue remains under investigation. Rebuild an installed game and update it using the same application ID and signing key to apply these changes.

## Requirements

- **Android 8.0 or later**. Material You dynamic colors require Android 12 or later.
- A complete game as a folder containing `Game.ini` or a ZIP file.
- Free storage for the APK and working files.
- Permission to install apps from this source when installing the builder and games.

No PC, Android Studio or on-device engine compilation is required.

## Quick start

1. Download **Portemon.apk** from [Releases](https://github.com/ZenithUzu/portemon-android/releases/tag/ultima), install it and open **Portémon**.
2. Choose the game ZIP or folder. The original is not modified. Review warnings about DLLs, WMV videos and Windows calls such as `Win32API`.
3. Check the **name, author, version and icon**. For MIDI music, select an **external SoundFont (.sf2)** if needed; its header is validated.
4. Check the **signing key**. For a game already built on PC, import `juego.json` and `firma.jks` from that game's profile folder. For a new game, export and back up the generated key.
5. Tap **Crear APK** (Build APK). Once finished, choose **Instalar APK creado** (Install built APK), **Guardar el APK** (Save APK) or **Crear otra app** (Create another app). Installing the completed file does not rebuild it.

**Universal APK** includes every architecture available in the template. Without it, the APK targets your phone to reduce its size.

## Features

| Area | Capabilities |
|---|---|
| Builder | Step-by-step wizard, ZIP/folder input, compatibility checks, name, icon and version. |
| Interface | Material design, dynamic colors on supported Android versions and wizard data restoration after screen recreation. |
| Signing | Per-game key, export/import compatible with PC profiles. |
| Installation | Separate build/install actions; the previous APK is replaced once the new one finishes signing. |
| Shared runtime | Sidebar menu, control editor, on-screen keyboard and Xbox/Switch profiles with D-pad, sticks and triggers. |
| Game options | Configurable game/music pause in menus and editor, touch customization, vibration and FPS targets including 60/120. |
| Saves and audio | Save manager, control profiles and MIDI through FluidSynth and a SoundFont. |
| Diagnostics | Exportable build log; generated games can export their own runtime log. |

FPS targets do not guarantee measured performance. See the [runtime features](https://github.com/ZenithUzu/portemon-pc/blob/main/README.en.md) and [PC/Android comparison](https://github.com/ZenithUzu/portemon-pc/blob/main/docs/paridad-pc-android.md) (Spanish).

## Updating and preserving saves and keys

Updating the **builder** does not change the engine or interface in **already installed games**. To apply those changes:

1. Install the new builder.
2. Rebuild the APK from the complete game.
3. Keep the same application ID and signing key, then install it as an update.

Back up saves and export signing keys before testing. Uninstalling a game may delete its internal data.

A certificate may contain the author's name, but the tool generates the key: **it is not the creator's private key and does not authenticate an official port**. A different key prevents updating an existing APK.

## Diagnostics and support

- **Build failure:** use **Guardar registro de creación** (Save build log) in the final step.
- **In-game failure:** open the generated game and use **Back → Save log**. The builder cannot read another app's private log.
- **USB capture:** use **Log del juego** in the PC tool.
- **Silent MIDI:** check for a bundled SoundFont or select an external `.sf2` before building.

Open an [issue](https://github.com/ZenithUzu/portemon-android/issues) with builder/engine version, game/version, phone model, Android version, reproduction steps and log. For FPS issues, include the target, current screen Hz, area and turbo state.

Remove personal data from attachments. Do not publish private signing keys, passwords or complete games.

The [recommended tests](https://github.com/ZenithUzu/portemon-pc/blob/main/docs/pruebas-ports.md) (Spanish) cover installation, updating, saves, menus and controls. UI tests with a simulated engine do not measure real FPS or confirm physical vibration.

## How it works and stays aligned with PC

The app bundles an **APK template with a prebuilt engine**. It inserts game files, updates application details and signs the result with a per-game key.

The engine, menu and controls are maintained in RMXP-Android-Ports. This builder's workflow waits for the template corresponding to the engine requested by PC and runs checks before publishing `ultima`. Both tools share this base; their build/install workflows differ.

## For developers

Requires **Java 17** and the **Android SDK**:

```sh
./gradlew assembleDebug
```

| Path | Responsibility |
|---|---|
| `app/src/main/java/com/zenithuzu/rpgmkport/MainActivity.java` | Wizard and navigation. |
| `Origen.java`, `Juego.java` in the same package | Source reading, metadata and compatibility checks. |
| `Constructor.java`, `Firma.java` in the same package | APK editing/signing and PC-compatible profiles. |
| `Novedades.java` in the same package | Update checks. |
| `.github/workflows/apk.yml` | Build, checks and publication. |

The builder uses `app/firma-app.jks`, a **publicly included test key** to preserve update continuity. It must not be treated as a secret or reused for other projects. Games use their own keys, which should be backed up and kept private.

## Credits

- [ARSCLib](https://github.com/REAndroid/ARSCLib) (Apache-2.0) and [apksig](https://android.googlesource.com/platform/tools/apksig) (Apache-2.0): editing and signing the APK on the phone.

- [Portémon PC](https://github.com/ZenithUzu/portemon-pc): engine, controls,
  menu and patches shipped with each game.
- [mkxp-z](https://github.com/mkxp-z/mkxp-z) and the projects it builds on (see
  RMXP-Android-Ports' credits).
- Step-by-step UI idea: [Pokémon Reloaded on Android](https://github.com/Klisee/pokemon-reloaded-android).
- [Material Components for Android](https://github.com/material-components/material-components-android) (Apache-2.0): the Material You UI.
- Icons: [Material Icons](https://fonts.google.com/icons) (Apache-2.0).

## License

This project's code is published under [GPL-2.0-or-later](LICENSE). Third-party components retain their own licenses and notices. The project's nonprofit purpose does not add a noncommercial restriction to the GPL.

**Games and their assets are not included or covered by this license.** Use games you have the right to port and respect their authors' permissions before distributing APKs.

An independent project, without affiliation or endorsement from Nintendo, The Pokémon Company, Game Freak, the RPG Maker rights holders or fangame authors. Trademarks and assets belong to their respective owners.

The software is provided without warranty under its license. AI assistance does not alter third-party rights or credits.
