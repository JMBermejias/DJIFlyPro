# Seguridad y límites operativos

DJIFlyPro es una herramienta de planificación y ayuda al vuelo; no sustituye el sistema de seguridad del aircraft, la autorización regulatoria ni la verificación humana previa al vuelo.

## Barreras implementadas

- No se ejecuta código importado: las recetas son JSON validado, con límite de 1 MiB, esquema y rangos restringidos, SHA-256 y auditoría.
- Las rutas se calculan en WGS84 y se rechazan coordenadas, altitudes, velocidades, separaciones y waypoints fuera de los límites del planificador.
- La ejecución y la subida WPML están bloqueadas de forma explícita hasta que un perfil revisado identifique exactamente aircraft, control remoto y ambas versiones de firmware. La allowlist incluida está vacía en esta entrega.
- Una versión de firmware no vacía o un producto de familia enterprise no habilitan por sí solos una misión; no se afirma compatibilidad de Phantom 4, Mini 3 ni de firmware no probado.
- La subida y el inicio de misión requieren registro, conexión, capacidad del producto, perfil validado, waylines válidos, selección explícita y una confirmación del operador; las comprobaciones se repiten al enviar la orden.
- Cada intento bloqueado por la barrera queda registrado en `audit.jsonl` con la etapa, el producto, el control remoto, ambos firmwares y el motivo, para que una negativa sea revisable y no silenciosa.
- La pérdida de RC se exporta como `GO_BACK`; el comportamiento final debe verificarse en el firmware concreto.
- Las acciones de takeoff, RTH, aterrizaje y sticks virtuales pasan por `FlightSafetyPolicy` y telemetría (GPS, home, batería, RC y estado de vuelo).

## Lista antes de cualquier vuelo

1. Verificar que el modelo, control remoto y firmware pertenecen a una combinación probada y registrada en la allowlist.
2. Comprobar batería, GPS, home point, enlace RC, obstáculos y personas en el área.
3. Revisar espacio aéreo, permisos, líneas eléctricas, edificios, árboles y otros obstáculos.
4. Generar y leer el `mission.json`; comprobar puntos, altitud, rumbo, sentido, fotos y distancia.
5. Abrir el KMZ en el simulador o en un entorno de pruebas y comprobar que el aircraft lo acepta.
6. Ejecutar primero una misión pequeña con observador y plan de abortos.

## No probado

La compilación y las pruebas unitarias no sustituyen una prueba física. La conexión real, telemetría, cámara, takeoff, RTH y las misiones deben probarse en una zona segura con el modelo exacto. No publicar ni operar una misión automática en un espacio aéreo no autorizado.
