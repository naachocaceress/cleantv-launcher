# Backlog

Lo que falta por hacer en CleanTV Launcher y en las TV box.

## Pendiente

- [ ] **Pasar las dos TV box a la base nueva**: el launcher ya está sobre cleanyourtv (v2.7) y probado en la 222 como copia
  de prueba (`com.gathin.tvhomedev`); falta instalarlo como inicio en la 222 y en la 221 (estaba apagada) con
  `launcher\install.ps1 192.168.0.222 192.168.0.221` y empujar el tema `themes/manhattan`.
- [ ] **Disney+**: comprobar si la versión instalada en la TV box es la última disponible (APKMirror, hash verificado) y actualizarla si no.
- [ ] **Continuar viendo: canales de vista previa**: leer también los canales de vista previa de las apps (filas por app, como
  Google TV). Hace falta una app que los publique en este TV para poder probarlo; hoy no hay ninguna.
- [ ] **Historial: abrir el contenido exacto**: YouTube y otras no publican un enlace al video en la sesión, así que la tarjeta
  abre la app y no el video. Ver si hay otra fuente.
- [ ] **Historial: cartel**: YouTube no publica imagen en la sesión; hoy la tarjeta muestra el banner de la app.
- [ ] **Continuar viendo con el panel de carrera / cinematic_home**: la fila está pensada para la portada normal; revisar cómo
  convive con el panel de carrera de un tema y con el estante cinematográfico.
- [ ] **Opcional, si se extraña**: fondos descargados de Wikimedia (como colección en `wallpapers/` del tema) y un tema con
  íconos redondos (Círculos). Ambos salieron al pasar a la base de upstream.

## Hecho recientemente

- Base nueva: el launcher se puso al día con Gathin23/cleanyourtv v2.7 (merge real, con su historia) y se re-aplicaron solo
  los añadidos propios; ver el README.
- Fila *Continuar viendo* (Watch Next más un historial propio) sobre la base nueva, con interruptor en los ajustes del launcher.
- Tema `manhattan` con las fotos de siempre y la franja horaria de upstream.
- Firma fija en el repositorio: las actualizaciones se instalan encima y conservan los ajustes. Script `launcher/install.ps1` para Windows.
- Segunda TV box (192.168.0.221) igualada a la primera: mismas apps y versiones, mismos paquetes deshabilitados.
