# Backlog

Lo que falta por hacer en CleanTV Launcher y en las TV box.

## Pendiente

- [ ] **Disney+**: comprobar si la versión instalada en la TV box es la última disponible (APKMirror, hash verificado) y actualizarla si no.
- [ ] **Recomendaciones: canales de vista previa**: leer también los canales de vista previa de las apps (filas por app, como Google TV). Se necesita una app que los publique en este TV para poder probarlo; hoy no hay ninguna.
- [ ] **Historial: abrir el contenido exacto**: YouTube y otras no publican un enlace al video en la sesión, así que la tarjeta abre la app y no el video. Ver si hay otra fuente (por ejemplo la actividad reciente o la API de cada app).
- [ ] **Historial: cartel**: YouTube no publica imagen en la sesión; hoy la tarjeta muestra el banner de la app.
- [ ] **Sistema de diseño**: definir uno para toda la app (colores, tipografías, espacios, radios, estados de foco y animaciones) y que los tres temas lo compartan en lugar de repetir valores sueltos.
- [ ] **Header: hora**: revisar la hora del encabezado (formato, idioma, posición) y que quede igual en los tres temas.

## Hecho recientemente

- Fila *Continuar viendo*: Watch Next más un historial propio (servicio que lee las sesiones de reproducción), con app, título, cartel y progreso.
- Firma fija en el repositorio: las actualizaciones se instalan encima y conservan los ajustes. Script `launcher/install.ps1` para Windows.
- Banners de las apps en xhdpi o mejor (de Gathin23/cleanyourtv): más nítidos en TVs de baja densidad como el B866V2.
- Segunda TV box (192.168.0.221) igualada a la primera: mismas apps y versiones, mismos paquetes deshabilitados, mismo launcher.
- Optimización del launcher: las apps ya cargadas no se vuelven a decodificar al recargar, fondos descargados en 16 bits, el hilo de descargas se cierra solo y se quitó una librería sin uso.
