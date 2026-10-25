# Backlog

Lo que falta por hacer en CleanTV Launcher y en las TV box.

## Pendiente

- [ ] **Disney+**: la versión instalada (26.18.0 rc5, del 5 de octubre) es más nueva que cualquier cosa que aparezca en búsqueda
  (26.12 de julio); no se pudo comprobar si hay una más nueva. Mirar la página de Disney+ Android TV en APKMirror.
- [ ] **Dock de la 220**: en la 222 Max está fijado en el dock (de cuando se usaba el launcher viejo) y en la 220 no; fijarlo ahí si se quiere igual.
- [ ] **Traducciones**: los textos de los temas y del panel están en español e inglés; sumar los otros idiomas de upstream si hace falta.
- [ ] **Recomendaciones / seguir viendo**: se probó (Watch Next más un historial propio sacado de las sesiones de reproducción)
  y no quedó bien, así que se quitó. Se probó también mandar `INITIALIZE_PROGRAMS` a Netflix, Prime, Crunchyroll, YouTube y Max
  (como hace el launcher de Google): no publicaron ningún canal ni programa en estas cajas, así que esa vía no sirve. Los
  launchers abiertos que lo intentan están en desarrollo temprano. Se retomaría solo con otro diseño (por ejemplo, un historial
  propio mejor hecho) o si alguna app de la caja empieza a publicar.

## Hecho recientemente

- **Las dos TV box (222 y 220, antes 221) pasadas a la base nueva**: launcher instalado como inicio, tema Dock, tema `manhattan`
  empujado, salvapantallas `PhotoDream` activo, launcher de la operadora (`com.dla.android`) deshabilitado y mismas apps y versiones.
- Base nueva: el launcher se puso al día con Gathin23/cleanyourtv v3.0 (merge real, con su historia).
- Volvieron los temas **Dock** y **Círculos** (Mosaicos es el de upstream), **mover apps**, los **fondos de Wikimedia** (ahora
  dentro de la colección de fondos de upstream) y el **panel de ajustes lateral**, también para los ajustes largos de upstream.
- **Tamaño de los íconos del dock**: pequeño (el de siempre), mediano y grande.
- **Reordenar apps** en el panel (Dock, Círculos y Mosaicos), y el código viejo del tema Mosaicos fuera de `ClassicHome`.
- **Salvapantallas con tus fotos** (`PhotoDream`) usando la colección de fondos, y el banner a densidad xhdpi en Dock y Círculos.
- **Medición** (ZTE B866V2): arranque en frío 3,1 s con el launcher viejo contra 2,4 s en Mosaicos y 2,2 s en Dock y Círculos; memoria 55 MB contra 28 a 38 MB.
- `tools/sync-upstream.ps1` (merge de upstream más limpieza más tests y lint) y un CI propio en GitHub.
- Una sola tipografía (Outfit, la de Mosaicos): se quitó Open Sans y el APK bajó de 913 a 554 KB.
- Se quitó la fila de recomendaciones y el historial (ver arriba).
- Tema `manhattan` con las fotos de siempre y la franja horaria de upstream.
- Firma fija en el repositorio: las actualizaciones se instalan encima y conservan los ajustes. Script `launcher/install.ps1` para Windows.
- Segunda TV box (192.168.0.221) igualada a la primera: mismas apps y versiones, mismos paquetes deshabilitados.
