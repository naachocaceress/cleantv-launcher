# Backlog

Lo que falta por hacer en CleanTV Launcher y en las TV box.

## Pendiente

- [ ] **Pasar las dos TV box a la base nueva**: el launcher ya está sobre cleanyourtv (v2.7), con los temas Dock, Círculos y
  Mosaicos, el panel lateral, los fondos de Wikimedia y mover apps, y probado en la 222 como copia de prueba
  (`com.gathin.tvhomedev`). Falta instalarlo como inicio en la 222 y en la 221 (estaba apagada) con
  `launcher\install.ps1 192.168.0.222 192.168.0.221` y empujar el tema `themes/manhattan`. La actualización encima conserva
  el tema guardado (Dock en la 222).
- [ ] **Disney+**: comprobar si la versión instalada en la TV box es la última disponible (APKMirror, hash verificado) y actualizarla si no.
- [ ] **Continuar viendo: canales de vista previa**: leer también los canales de vista previa de las apps (filas por app, como
  Google TV). Hace falta una app que los publique en este TV para poder probarlo; hoy no hay ninguna.
- [ ] **Historial: abrir el contenido exacto**: YouTube y otras no publican un enlace al video en la sesión, así que la tarjeta
  abre la app y no el video. Ver si hay otra fuente.
- [ ] **Historial: cartel**: YouTube no publica imagen en la sesión; hoy la tarjeta muestra el banner de la app.
- [ ] **Dock y Círculos: banners nítidos**: `ClassicAdapter` carga el banner de la app a la densidad del TV; upstream lo hace
  a xhdpi o más (`AppAdapter.loadBanner`), que se ve mejor en el B866V2 (hdpi). Copiar esa mejora.
- [ ] **Dock y Círculos: código del tema Mosaicos viejo**: `ClassicTheme.TILES` y sus ramas ya no se usan (Mosaicos es el de
  upstream); quitarlos de `ClassicHome`, `ClassicAdapter` y `ClassicShelfDecoration`.
- [ ] **Continuar viendo con el panel de carrera / cinematic_home**: la fila está pensada para la portada normal; revisar cómo
  convive con el panel de carrera de un tema y con el estante cinematográfico.
- [ ] **Traducciones**: los textos de los temas y del panel están en español e inglés; sumar los otros idiomas de upstream si hace falta.

## Hecho recientemente

- Base nueva: el launcher se puso al día con Gathin23/cleanyourtv v2.7 (merge real, con su historia).
- Volvieron los temas **Dock** y **Círculos** (Mosaicos es el de upstream), **mover apps**, los **fondos de Wikimedia** (ahora
  dentro de la colección de fondos de upstream) y el **panel de ajustes lateral**, también para los ajustes largos de upstream.
- Fila *Continuar viendo* (Watch Next más un historial propio), con interruptor en el panel, en los tres temas.
- Tema `manhattan` con las fotos de siempre y la franja horaria de upstream.
- Firma fija en el repositorio: las actualizaciones se instalan encima y conservan los ajustes. Script `launcher/install.ps1` para Windows.
- Segunda TV box (192.168.0.221) igualada a la primera: mismas apps y versiones, mismos paquetes deshabilitados.
