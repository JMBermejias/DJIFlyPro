# Artefactos DJIFlyPro

> **No distribuibles.** Los APK y AAB de esta carpeta se generaron con
> `scripts/build-release.sh --allow-missing-api-key` porque `AIRCRAFT_API_KEY`
> está vacía. Sin la App Key de DJI registrada para `com.djiflypro.app` no
> pueden registrarse con el SDK ni conectarse a ningún aircraft. Existen solo
> como evidencia de compilación, R8, lint y firma. El build release ahora
> aborta si falta la App Key, así que estos artefactos ya no son reproducibles
> sin proporcionarla.

La compilación release incluye R8 y lint vital.

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0.apk` | 205,002,138 bytes | `c93f36f8bf5e276924a829d5858d989d4616cb6c7f148a61419aedf494a35727` |
| `DJIFlyPro-1.1.0.aab` | 198,917,491 bytes | `83deb28a9b1408f5337e8a6a4a57e3bdc0e61f5a5cfb137165851cf9d7171686` |
| `DJIFlyPro-1.1.0-debug.apk` | 238,557,908 bytes | `fef20561703da7d1a46961251c29785cc162e734b44ae06e380821ddd74af24d` |

`scripts/build-release.sh` nombra los binarios a partir de la `versionName` que
declara el propio build, no de un literal escrito a mano. Así el fichero
descargado se identifica con el producto y su versión en vez de llamarse
`sample-release.apk`, y el nombre no puede desincronizarse del binario.

## Verificaciones realizadas

- `DJIFlyPro-1.1.0.apk`: `apksigner verify --verbose` correcto; APK Signature
  Scheme v2, un firmante, RSA 2048.
- `DJIFlyPro-1.1.0.aab`: `jarsigner -verify` terminó con código 0.
- Identidad del APK: `com.djiflypro.app`, versión `1.1.0`, `versionCode 2`,
  `minSdk 24`, `targetSdk 35`, `compileSdk 35`.
- El APK y el manifiesto merged no declaran `MANAGE_EXTERNAL_STORAGE`.
- El asset `assets/validated_wpml_profiles.json` está empaquetado y contiene una
  lista de perfiles vacía, por lo que la subida y la ejecución automática de
  misiones WPML están bloqueadas.
- Las cinco recetas de `assets/algorithms/` están empaquetadas, incluida
  `cartography.cross.v1.json`.
- `lintDebug`: 0 errores.
- `:sample:testDebugUnitTest`: 292 pruebas, 0 fallos.
- El preflight de release rechaza la build si falta `AIRCRAFT_API_KEY` o la firma,
  y solo se omitió aquí con `--allow-missing-api-key`.
- R8: 0 advertencias de clase ausente y ninguna regla `-dontwarn` aplicada a
  código propio. Las advertencias restantes de "implicit default constructor"
  proceden de `proguard-android-optimize.txt` de AGP y de los `proguard.txt` de
  DJI SDK, Play Services, Room, Lifecycle, Navigation, Glide, OkHttp y
  JetBrains; no se pueden resolver desde esta aplicación.

## Firma

El firmante de estas compilaciones es `CN=DJIFlyPro Local Test`, una keystore de
desarrollo creada en `.local/djiflypro-test.jks`. No es una clave de producción y
no debe usarse para publicar en Google Play.

Antes de distribución hay que: (1) registrar `com.djiflypro.app` en DJI Developer
y proporcionar `AIRCRAFT_API_KEY`; (2) generar una clave privada de producción
fuera del repositorio; (3) recompilar sin `--allow-missing-api-key`;
(4) recalcular los hashes y conservar la salida de `apksigner` y `jarsigner`
como evidencia.

## Release en GitHub

Estos binarios se publican como assets del release, junto con
`SHA256SUMS.txt` y las notas de `artifacts/release-notes.md`.

El manifiesto de hashes usa nombres de fichero sin ruta, para que
`sha256sum -c SHA256SUMS.txt` funcione sobre los ficheros descargados juntos.

Para publicar una versión nueva: `scripts/publish-release.sh <version>`.

## Nota sobre `.deb`

Android no usa paquetes `.deb`. Los binarios publicados son `.apk` (instalable
directamente) y `.aab` (formato de subida para Google Play). Las dependencias
van incluidas dentro de los binarios, no se instalan por separado.
