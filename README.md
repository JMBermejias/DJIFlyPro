# DJIFlyPro

DJIFlyPro es una base Android nativa para control y planificación de vuelos con DJI Mobile SDK V5.18.0.

## Qué incluye esta entrega

- Panel de estado del SDK y telemetría.
- Centro de control reutilizando el flujo de control de vuelo del SDK oficial.
- Planificador para fachada, cubierta, planta solar, campo y malla genérica.
- Cálculo de rutas en coordenadas WGS84, con solape, separación de líneas, fotos, altitud, velocidad y acciones de cámara.
- Validación de límites de seguridad y de capacidad del producto.
- Exportación de `mission.json` y de KMZ/WPML generado con `WPMZManager`.
- Subida y ejecución de misiones WPML condicionadas a conexión, registro y a un perfil de validación física con coincidencia exacta de producto, control remoto y ambos firmwares. La allowlist incluida está vacía, de modo que la ejecución automática está bloqueada en esta entrega.
- Selección de waylines y confirmación del operador repetidas justo antes del inicio.
- Recetas algorítmicas declarativas importables desde `Algoritmos y recetas`.
- Registro local de auditoría en `audit.jsonl`, incluidos los intentos bloqueados por la barrera de ejecución.

## Estructura

- `android-sdk-v5-as/`: raíz Gradle y wrapper.
- `android-sdk-v5-sample/`: aplicación Android y código DJIFlyPro.
- `android-sdk-v5-uxsdk/`: UX SDK de DJI.
- `algorithms/`: recetas JSON de ejemplo.
- `legacy/`: variante MSDK V4 para Phantom 4, con `preflight.sh` y registro de hardware.
- `docs/`: compatibilidad, seguridad y configuración.
- `artifacts/`: APK/AAB generados, cuando se hayan construido.
- `config/`: configuración local de firma (no publicar claves privadas).

## Compilar

```bash
cd /home/jmbernabeu/Privada/Software/DJIFlyPro/android-sdk-v5-as
./gradlew :sample:assembleDebug
./gradlew :sample:bundleRelease
```

El `local.properties` apunta al SDK Android instalado en `tools/android-sdk`.

## DJI App Key

1. Registra en DJI Developer una aplicación Android.
2. Usa exactamente el `applicationId` `com.djiflypro.app`.
3. Pega la clave en `AIRCRAFT_API_KEY` en `android-sdk-v5-as/gradle.properties` o como variable de entorno `AIRCRAFT_API_KEY`.
4. Usa el firmware y el control remoto soportados por DJI para el modelo concreto.

Sin la App Key, la interfaz y la generación/exportación de planes pueden funcionar, pero el registro del SDK y la conexión real no.

## Habilitar misiones WPML

La subida y la ejecución automática están bloqueadas hasta que exista un perfil de validación física con coincidencia exacta de producto, control remoto, firmware del aircraft y firmware del control remoto.

1. Conecta el hardware y usa **Exportar informe de validación** en el planificador para obtener las cadenas exactas que reporta MSDK.
2. Registra la prueba física en un entorno seguro y anota su referencia.
3. Sustituye `REPLACE_WITH_YOUR_TEST_RECORD_REFERENCE` en el informe por esa referencia.
4. Añade el perfil a `android-sdk-v5-sample/src/main/assets/validated_wpml_profiles.json` y revisa el cambio como parte del código fuente.
5. Compila: `ShippedWpmlAllowlistTest` valida el asset y falla si queda algún marcador sin rellenar.

Un perfil no válido, un asset ilegible o un esquema desconocido producen una allowlist vacía. Un firmware conocido no es una certificación de seguridad. Ver `docs/COMPATIBILITY.md` y `docs/SAFETY.md`.

## Variante Legacy (Phantom 4)

`legacy/` está reservada para una variante independiente basada en MSDK V4. Ejecuta `legacy/preflight.sh` para ver qué falta. No se puede construir hasta disponer del SDK V4 y de una App Key propia de `com.djiflypro.legacy`.

## Firmas

El repositorio no debe contener una clave privada de producción. Para una compilación de distribución, configura `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD` mediante una keystore propia. Las firmas de prueba generadas para este entorno no sirven para Google Play.

## Importante

Un planificador no puede detectar por sí solo todos los obstáculos, líneas eléctricas, permisos, zonas reguladas o restricciones de espacio aéreo. La persona operador debe verificar la misión y el entorno antes de cualquier vuelo. No se debe utilizar esta aplicación como único sistema de seguridad.
