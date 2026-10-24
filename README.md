# CleanTV Launcher

*[English version](README.en.md)*

Una pantalla de inicio limpia para TV boxes con Android TV, sin anuncios, cuentas ni rastreo. Es un fork de
**[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)** (el launcher base, la guía de limpieza del TV, 25 idiomas,
temas, biblioteca de apps, tarjetas de módulos y teclas del control vienen de ahí) con unos pocos añadidos propios.

> **Basado en el trabajo de otras personas.** La línea es [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) →
> [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup) → este repositorio, y el launcher
> actual se puso al día con [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (licencia MIT, ver
> [Licencia](#licencia)). Todo lo que no figura en [Qué agrega este fork](#qué-agrega-este-fork) es de ellos y se documenta
> en su README, en [`docs/`](docs/) y en [`themes/`](themes/).

## Qué agrega este fork

### Continuar viendo

Entre el encabezado y el dock aparece una fila de tarjetas con lo último que viste, como en Google TV. Cada tarjeta lleva la
app, el título, el cartel y cuánto avanzaste. Viene de dos lugares, mezclados del más reciente al más viejo:

- **Lo que las apps publican en *Watch Next* de Android TV** (series y películas que seguís viendo, próximos episodios).
  Netflix, YouTube o Disney+ lo hacen en un TV con Google TV, pero **en un TV box sin servicios de Google casi ninguna app
  publica nada**, así que esta fuente suele estar vacía.
- **El historial propio del launcher**, que cubre ese caso: un servicio mínimo mira las sesiones de reproducción de las otras
  apps (la misma información que muestra la notificación de "reproduciendo") y guarda título, canal o serie, cartel y
  posición. Sirve con cualquier app que informe lo que reproduce (YouTube, Stremio...). Las que no informan el título
  (Netflix, Prime Video) aparecen igual como "usaste esta app", con su banner. OK abre la app, o el contenido si la app
  publicó un enlace a él. Todo se guarda en el almacenamiento privado del launcher; no sale del TV y no se lee ninguna
  notificación.

La fila se ve mientras el dock tiene el foco: ↑ desde el dock llega a ella, ↑ otra vez llega a los botones de arriba y ↓
vuelve al dock. Se apaga en *Ajustes del launcher → Continuar viendo*; sin nada para mostrar, no aparece.

El acceso se da una sola vez por ADB, y los scripts de instalación ya lo hacen. A mano:

```bash
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaHistoryService
```

### Actualizar sin perder los ajustes

Android no deja instalar una versión firmada con otra clave encima de la instalada: hay que desinstalar, y eso borra los
ajustes del launcher. Para que no pase, **todas las compilaciones se firman con la misma clave**, que está en el repositorio
(`launcher/signing/cleantv.keystore`, la clave debug de siempre con su clave `android`). No protege nada y no hace falta que
lo haga: es un launcher que se instala a mano, no una app de tienda. Con esa clave, `adb install -r` (o los scripts)
actualizan encima y se conservan ajustes, orden e historial. Un TV que ya tiene una versión firmada con otra clave necesita
una desinstalación, una sola vez.

### Instalar en Windows con `adb.exe`

[`launcher/install.ps1`](launcher/install.ps1) compila, instala en uno o varios TV, lo deja como inicio y activa el historial.
No necesita Python (los scripts de upstream sí) y no toca el launcher de fábrica:

```powershell
launcher\install.ps1 192.168.0.222 192.168.0.221
```

### Tema Manhattan

[`themes/manhattan/`](themes/manhattan/) pone las fotos de Manhattan que traía este fork (mañana, tarde, atardecer y noche, con
la franja horaria que elige el propio launcher), seis columnas y el dock con Netflix, Prime Video, YouTube, Disney+,
Crunchyroll y Stremio. Es un tema común de upstream: se empuja sin recompilar (ver [`themes/README.md`](themes/README.md)).
Sin Python, a mano:

```powershell
$d = "/sdcard/Android/data/com.gathin.tvhome/files"
adb shell mkdir -p $d/wallpapers
adb push themes\manhattan\config.json $d/config.json
Get-ChildItem themes\manhattan\wallpapers | ForEach-Object { adb push $_.FullName "$d/wallpapers/$($_.Name)" }
adb shell am force-stop com.gathin.tvhome
```

Las fotos son de Unsplash (no MIT): ver [`themes/manhattan/CREDITS.md`](themes/manhattan/CREDITS.md).

### Valores por defecto

[`config.xml`](launcher/app/src/main/res/values/config.xml) trae el dock de este fork (Netflix, Prime Video, YouTube, Disney+,
Crunchyroll, Stremio), `launcher_settings_button` y `tile_menu` activados (OK en *Ajustes* abre los ajustes del launcher y
mantener OK en una app ofrece Mover e Información) y oculta atajos de TV box que no se usan.

## Instalación

Necesitás JDK 17 o más nuevo y el Android SDK (plataforma 34), y el TV en la misma red con la depuración activada
(Ajustes → Preferencias del dispositivo → Acerca de → apretá OK siete veces en **Compilación**; después, Opciones de
desarrollador → Depuración USB).

Windows: `launcher\install.ps1 <ip del TV>` (arriba). macOS y Linux, o Windows con Python, con los scripts de upstream:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=192.168.1.50 launcher/build-and-install.sh      # la IP de tu TV
```

Compila el release, lo instala y lo deja como inicio. El launcher de fábrica se detecta y se deshabilita **solo después** de
comprobar que este arranca (ver la guía de upstream); `STOCK_LAUNCHER=none` no toca nada. Gradle funciona con el JDK de
Android Studio (`JAVA_HOME=…\Android Studio\jbr`) y un `launcher/local.properties` con `sdk.dir=` en formato `C:/Users/…/Sdk`.
Con `-PlauncherApplicationId=com.gathin.tvhomedev` se instala una copia de prueba al lado, sin tocar el inicio.

El id de la aplicación sigue siendo `com.gathin.tvhome`, como en el original: mantenerlo conserva tus ajustes al actualizar.

## Mantener el fork al día con upstream

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # una vez
git fetch upstream && git merge upstream/main
```

Los puntos donde este fork toca archivos de upstream son pocos, para que el merge duela poco:
`MainActivity.java` (sección *Continue watching*, tres enganches en `onResume`, `onTileFocused` y `onTileVerticalKey`),
`activity_main.xml` (bloque `recoBlock`), `LauncherSettingsActivity.java` y su layout (una fila), `AndroidManifest.xml`
(permisos y servicio), `config.xml` (dock, apps ocultas y dos interruptores), `app/build.gradle` (la firma) y los scripts de
instalación (dos líneas). Todo lo demás son archivos nuevos: `RecoStore`, `RecoAdapter`, `HistoryStore`,
`MediaHistoryService`, `install.ps1`, `themes/manhattan/`.

Upstream también trae `.github/` (su CI, con un límite de 480 KB para el APK, y un bot de mantenimiento programado),
`CLAUDE.md`, `AGENTS.md` y `GEMINI.md` (reglas de sus mantenedores para agentes). Este fork no los usa: después de cada merge,
`git rm -r .github CLAUDE.md AGENTS.md GEMINI.md`.

Qué se quitó respecto de la versión anterior del fork, por existir algo equivalente (y mejor mantenido) en upstream: los
temas Dock, Círculos y Mosaicos (la placa de vidrio de upstream es el Mosaicos), los fondos descargados de Wikimedia, el
salvapantallas de fotos (upstream trae el del reloj), mover apps (menú de tarjeta y *Reordenar apps*) y las traducciones propias.

## Herramientas de limpieza

Vienen de upstream: la guía para limpiar y configurar el TV por ADB ([`PROMPT.md`](PROMPT.md)), el cliente ADB en Python
([`tools/tv.py`](tools/tv.py)), las listas por modelo de TV en [`examples/`](examples/) y los scripts
[`tools/disable.sh`](tools/disable.sh) y [`tools/restore.sh`](tools/restore.sh). Los paquetes se deshabilitan, nunca se
desinstalan. Ver [CONTRIBUTING.md](CONTRIBUTING.md) para sumar tu modelo.

Probado solo en dos ZTE B866V2 (Android 9, ARM de 32 bits, 2 GB de RAM). **Fire TV no está soportado por este fork.**

## Licencia

MIT, ver [`LICENSE`](LICENSE): © 2026 Gathin (obra original) y © 2026 Nacho (cambios de este fork). Las tipografías están bajo
la SIL Open Font License, ver `launcher/FONT-LICENSE-OFL.txt`. Los créditos están en [`CREDITS.md`](CREDITS.md). Los nombres
y logos de marcas pertenecen a sus dueños.
