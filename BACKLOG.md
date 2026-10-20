# Backlog

Lo que falta por hacer en CleanTV Launcher y en las TV box.

## Pendiente

- [ ] **Disney+**: comprobar si la versión instalada en la TV box es la última disponible (APKMirror, hash verificado) y actualizarla si no.
- [ ] **Recomendaciones en el launcher**: la fila *Continuar viendo* (Watch Next de Android TV) está hecha y se activa en Ajustes, pero falta probarla en la TV box (permiso, posición de la fila en los tres temas) y ver si Stremio publica algo; si no, evaluar canales de vista previa o su API.
- [ ] **Sistema de diseño**: definir uno para toda la app (colores, tipografías, espacios, radios, estados de foco y animaciones) y que los tres temas lo compartan en lugar de repetir valores sueltos.
- [ ] **Header: hora**: revisar la hora del encabezado (formato, idioma, posición) y que quede igual en los tres temas.
- [ ] **Segunda TV box**: instalar CleanTV Launcher en la otra TV box y revisar que funcione igual (arquitectura, versión de Android, resolución).

## Hecho recientemente

- Optimización del launcher: las apps ya cargadas no se vuelven a decodificar al recargar, fondos descargados en 16 bits, el hilo de descargas se cierra solo y se quitó una librería sin uso.
