# CleanTV Launcher

*[English version](README.en.md)*

Una pantalla de inicio limpia para TV boxes con Android TV, sin anuncios, cuentas ni rastreo. Es un fork de
**[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)** (el launcher base, la guía de limpieza del TV, 25 idiomas,
temas, biblioteca de apps, tarjetas de módulos y teclas del control vienen de ahí) con varios añadidos propios.

> **Basado en el trabajo de otras personas.** La línea es [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) →
> [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup) → este repositorio, y el launcher
> actual se puso al día con [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (licencia MIT, ver
> [Licencia](#licencia)). Todo lo que no figura en [Qué agrega este fork](#qué-agrega-este-fork) es de ellos y se documenta
> en su README, en [`docs/`](docs/) y en [`themes/`](themes/).

## Qué agrega este fork

### Tres temas: Dock, Círculos y Mosaicos

- **Mosaicos** es la pantalla de inicio de upstream tal cual (placa de vidrio con las apps fijas, filas de tarjetas debajo),
  y es la que se usa por defecto.
- **Dock** y **Círculos** son los dos temas propios de este fork: un dock compacto de íconos con el nombre de la app sobre
  él, y una sola fila larga de círculos con el nombre grande y "Tus apps". Siguen siendo el código de antes, aislado en
  `ClassicHome` y sus clases `Classic…` (con los recursos `c_…`), así que no se mezclan con los archivos de upstream.
- En esos dos temas se pueden **mover apps** (mantené OK en una app, o Menú): dentro del dock, de la grilla y entre ambos.
  En Mosaicos manda el menú de tarjeta y *Reordenar apps* de upstream.
- El tema se cambia en el panel lateral: *Ajustes → Tema*. Cada uno recuerda sus apps fijas y su orden.
- En Dock, *Ajustes → Tamaño de los íconos del dock* elige entre **pequeño** (el de siempre, hasta 8 apps), **mediano** (hasta 7) y
  **grande** (hasta 6): cuanto más grandes los íconos, menos caben a lo ancho. Todo el launcher usa la tipografía de Mosaicos (Outfit).

### Panel de ajustes lateral

El botón **Ajustes** abre un panel sobre el borde derecho, con el inicio atenuado detrás, en los tres temas: *Tema*,
*Fondo de pantalla*, *Tamaño de los íconos del dock* (solo en Dock), *Más ajustes del launcher* (Mosaicos) y *Ajustes de Android*, más
*Entradas* si el TV las tiene. Mantener OK en el botón sigue abriendo el menú de upstream (módulos, ajustes del TV...). Los
ajustes largos de upstream (apps por fila, filas bajo el dock, animaciones, fondo) también se ven como panel lateral y no
a pantalla completa.

### Fondos de pantalla de Wikimedia

*Ajustes → Fondo de pantalla → Nuevo fondo de la web* baja una foto de una lista de imágenes con licencia libre de
Wikimedia Commons (CC0, dominio público, CC BY, CC BY-SA), la recorta a 1920×1080 y la guarda con su autor y licencia. Se
suma a la carpeta de fondos del tema (`wallpapers/`) con el prefijo `web-`, así que **upstream la rota o la fija igual que a
las del tema**, en cualquier tema del launcher. El panel también deja pasar al fondo siguiente o anterior, quitar el que se
bajó y volver al del tema. La lista no trae fotos: se descargan solo cuando las pedís (es la única razón de que el
launcher tenga permiso de internet). Se regenera con
[`tools/make_wallpaper_catalog.py`](tools/make_wallpaper_catalog.py), o sumás la tuya con `c_wallpaper_catalog_url` en
[`classic_config.xml`](launcher/app/src/main/res/values/classic_config.xml).

### Actualizar sin perder los ajustes

Android no deja instalar una versión firmada con otra clave encima de la instalada: hay que desinstalar, y eso borra los
ajustes del launcher. Para que no pase, **todas las compilaciones se firman con la misma clave**, que está en el repositorio
(`launcher/signing/cleantv.keystore`, la clave debug de siempre con su clave `android`). No protege nada y no hace falta que
lo haga: es un launcher que se instala a mano, no una app de tienda. Con esa clave, `adb install -r` (o los scripts)
actualizan encima y se conservan ajustes, orden y tema. Un TV que ya tiene una versión firmada con otra clave
necesita una desinstalación, una sola vez.

### Instalar en Windows con `adb.exe`

[`launcher/install.ps1`](launcher/install.ps1) compila, instala en uno o varios TV, y lo deja como inicio.
No necesita Python (los scripts de upstream sí) y no toca el launcher de fábrica:

```powershell
launcher\install.ps1 192.168.0.222 192.168.0.221
```

### Tema Manhattan (fotos y dock)

[`themes/manhattan/`](themes/manhattan/) es un tema común de upstream con las fotos de Manhattan que traía este fork
(mañana, tarde, atardecer y noche, con la franja horaria que elige el propio launcher), seis columnas y el dock con Netflix,
Prime Video, YouTube, Disney+, Crunchyroll y Stremio. Sirve en los tres temas del launcher (el fondo lo ponen todos con el
mismo motor de upstream). Se empuja sin recompilar, ver [`themes/README.md`](themes/README.md). Sin Python, a mano:

```powershell
$d = "/sdcard/Android/data/com.gathin.tvhome/files"
adb shell mkdir -p $d/wallpapers
adb push themes\manhattan\config.json $d/config.json
Get-ChildItem themes\manhattan\wallpapers | ForEach-Object { adb push $_.FullName "$d/wallpapers/$($_.Name)" }
adb shell am force-stop com.gathin.tvhome
```

Las fotos son de Unsplash (no MIT): ver [`themes/manhattan/CREDITS.md`](themes/manhattan/CREDITS.md). Sin ningún tema, el
fondo es el cielo de upstream (Mosaicos) o negro (Dock y Círculos).

### Valores por defecto y traducciones

[`config.xml`](launcher/app/src/main/res/values/config.xml) trae el dock de este fork (Netflix, Prime Video, YouTube, Disney+,
Crunchyroll, Stremio) y el menú de tarjeta de upstream (`tile_menu`) activado, y oculta atajos de TV box que no se usan. Los
textos de los temas y del panel están en español e inglés (`classic_strings.xml`); en los otros 24 idiomas de upstream, esas
pocas cadenas salen en inglés.

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
Un TV que ya tenía este launcher con el tema Dock o Círculos guardado sigue abriendo en ese tema.

## Mantener el fork al día con upstream

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # una vez
git fetch upstream && git merge upstream/main
```

Los puntos donde este fork toca archivos de upstream son pocos, para que el merge duela poco:
`MainActivity.java` (la sección del panel lateral y una línea al comienzo de cada método del ciclo de
vida que cede el control a `ClassicHome` cuando el tema es Dock o Círculos), `activity_main.xml`,
`LauncherSettingsActivity.java` y su layout (el aspecto de panel), `AndroidManifest.xml` (permiso de internet y el
tema del panel), `values/styles.xml` y `config.xml` (dock, apps ocultas, menú de tarjeta), `app/build.gradle` (la firma) y los
scripts de instalación (dos líneas). Todo lo demás son archivos nuevos: `ClassicHome`, `ClassicAdapter` y compañía,
`ConfigSidebar`, `WallpaperStore`, los recursos `c_…` y
`classic_*.xml`, `install.ps1`, `themes/manhattan/`.

Upstream también trae `.github/` (su CI, con un límite de 480 KB para el APK, y un bot de mantenimiento programado),
`CLAUDE.md`, `AGENTS.md` y `GEMINI.md` (reglas de sus mantenedores para agentes). Este fork no los usa: después de cada merge,
`git rm -r .github CLAUDE.md AGENTS.md GEMINI.md`.

Qué quedó fuera por tener equivalente en upstream: el salvapantallas de fotos (upstream trae el del reloj) y los íconos de
marca dibujados en la máquina (cada app muestra su banner).

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
