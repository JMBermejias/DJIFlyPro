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
| `sample-release.apk` | 204,907,868 bytes | `fc9d8a6d4d1ca9c34ea72400284eb6dde0f122405cc5f8a3ceeee11f8e357c78` |
| `sample-release.aab` | 198,792,847 bytes | `3b29bbc311904c8823a93fb162a3b94261974a71b908be9a23d0e44fc4e87510` |
| `sample-debug.apk` | 238,406,510 bytes | `fde8189914b6e1e6448d89b93583f653c7dc94caeae5af36238cc6c1b2c279ad` |

## Verificaciones realizadas

- `sample-release.apk`: `apksigner verify --verbose` correcto; APK Signature Scheme v2, un firmante, RSA 2048.
- `sample-release.aab`: `jarsigner -verify` terminó con código 0.
- Identidad del APK: `com.djiflypro.app`, versión `1.0.0`, `versionCode 1`, `minSdk 24`, `targetSdk 35`, `compileSdk 35`.
- El APK y el manifiesto merged no declaran `MANAGE_EXTERNAL_STORAGE`.
- El asset `assets/validated_wpml_profiles.json` está empaquetado y contiene una lista de perfiles vacía, por lo que la subida y la ejecución automática de misiones WPML están bloqueadas.
- `lintDebug`: 0 errores.
- `:sample:testDebugUnitTest`: 49 pruebas, 0 fallos, tras un `clean`.
- El preflight de release rechaza la build si falta `AIRCRAFT_API_KEY` o la firma, y solo se omitió aquí con `--allow-missing-api-key`.
- R8: 0 advertencias de clase ausente y ninguna regla `-dontwarn` aplicada a código propio. Las 29 advertencias restantes de "implicit default constructor" proceden de `proguard-android-optimize.txt` de AGP y de los `proguard.txt` de DJI SDK, Play Services, Room, Lifecycle, Navigation, Glide, OkHttp y JetBrains; no se pueden resolver desde esta aplicación.

## Firma

El firmante de estas compilaciones es `CN=DJIFlyPro Local Test`, una keystore de desarrollo creada en `.local/djiflypro-test.jks`. No es una clave de producción y no debe usarse para publicar en Google Play.

Antes de distribución hay que: (1) registrar `com.djiflypro.app` en DJI Developer y proporcionar `AIRCRAFT_API_KEY`; (2) generar una clave privada de producción fuera del repositorio; (3) recompilar sin `--allow-missing-api-key`; (4) recalcular los hashes y conservar la salida de `apksigner` y `jarsigner` como evidencia.

## Release en GitHub

Estos tres binarios están publicados como assets del release
[`v0.1.0-alpha.1`](https://github.com/JMBermejias/DJIFlyPro/releases/tag/v0.1.0-alpha.1),
junto con `SHA256SUMS.txt` y las notas de `artifacts/release-notes.md`.

El manifiesto de hashes usa nombres de fichero sin ruta, para que
`sha256sum -c SHA256SUMS.txt` funcione sobre los ficheros descargados juntos.

Comprobado tras la publicación, descargando el asset desde el release:
`apksigner verify` correcto con esquema v2, firmante `CN=DJIFlyPro Local Test`,
SHA-256 del certificado `8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43`,
y `sha256sum -c` coincidiendo con el hash publicado.

Para publicar una versión nueva: `scripts/publish-release.sh <version>`.

## Nota sobre `.deb`

Android no usa paquetes `.deb`. Los binarios publicados son `.apk` (instalable
directamente) y `.aab` (formato de subida para Google Play). Las dependencias
van incluidas dentro de los binarios, no se instalan por separado.
