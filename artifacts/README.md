# Artefactos DJIFlyPro

Los APK y AAB de esta carpeta se construyeron con la App Key de DJI presente, de
modo que el preflight de release se ejecutó de verdad y el resultado **sí puede
registrarse con el SDK**. La firma sigue siendo la keystore de desarrollo local,
así que no es publicable en Google Play.

La compilación release incluye R8 y lint vital.

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0-alpha.2.apk` | 205,002,230 bytes | `ce47e8857bac39214dbedbbd1d3feb9ec87f08399ab98a455124bc23f90f810a` |
| `DJIFlyPro-1.1.0-alpha.2.aab` | 198,917,609 bytes | `469872bc57db2514795fe742623be14199762ea3fb2fdf365d9189e0bc86f3d7` |
| `DJIFlyPro-1.1.0-alpha.2-debug.apk` | 238,562,076 bytes | `60f6fcd6f18e050f152fc741d401578ccb48af5bfa96724e2fc32a9117231643` |

Los tres binarios salen del mismo build y del mismo entorno, incluido el
`assembleDebug`, que `scripts/build-release.sh` ejecuta siempre en la misma
pasada. Antes el APK debug se copiaba solo si ya existía, lo que dejaba
posibilidad de publicar junto a un release un debug de otra build, sin la key.

`scripts/build-release.sh` nombra los binarios a partir de la `versionName` que
declara el propio build, no de un literal escrito a mano. Así el fichero
descargado se identifica con el producto y su versión en vez de llamarse
`sample-release.apk`, y el nombre no puede desincronizarse del binario.

## Dónde vive la App Key

En `.local/api-key.properties`, que está en `.gitignore` y con permisos `600`.
`scripts/build-release.sh` la lee y la exporta como `AIRCRAFT_API_KEY`, y el
build la toma del entorno antes que de `gradle.properties`, que sí está
versionado. Comprobado: la clave no aparece en ningún fichero versionado, ni en
el árbol de trabajo, ni en el staging area.

Si falta, el preflight de release aborta:

```
DJIFlyPro release preflight failed: AIRCRAFT_API_KEY is empty.
```

## Verificaciones realizadas

- `com.dji.sdk.API_KEY` presente en el manifiesto de los **tres** binarios
  (APK release, APK debug y AAB), verificado con `aapt2 dump xmltree` y
  extrayendo el manifiesto del AAB.
- `DJIFlyPro-1.1.0-alpha.2.apk`: `apksigner verify --verbose` correcto; APK
  Signature Scheme v2 y v3, un firmante, RSA 2048. La v1 (JAR) no se emite:
  AGP la omite con `minSdk 24` porque la plataforma verifica la v2 por su
  cuenta. `v2` y `v3` se piden de forma explícita en `signingConfigs` para que
  los esquemas del artefacto publicado sean una decisión y no un efecto del
  `minSdk`.
- `zipalign -c 4` y `zipalign -c -P 16` correctos, y las 63 bibliotecas
  nativas (303 MB descomprimidas) van comprimidas con `extractNativeLibs`, que
  es el ajuste que el instalador del sistema maneja bien.
- El APK contiene solo `lib/arm64-v8a`. Es lo que declara `abiFilters` y lo que
  instala en móviles y tablets ARM de 64 bits. No instala en emuladores x86_64
  ni en ARM de 32 bits; para eso hay que quitar el `abiFilters` y recompilar.
- `DJIFlyPro-1.1.0-alpha.2.aab`: `jarsigner -verify` terminó con código 0.
- Identidad: `com.djiflypro.app`, `versionName 1.1.0-alpha.2`, `versionCode 3`,
  `minSdk 24`, `targetSdk 35`, `compileSdk 35`.
- El APK y el manifiesto merged no declaran `MANAGE_EXTERNAL_STORAGE`.
- El asset `assets/validated_wpml_profiles.json` está empaquetado y contiene una
  lista de perfiles vacía, por lo que la subida y la ejecución automática de
  misiones WPML siguen bloqueadas.
- `lintDebug`: 0 errores.
- `:sample:testDebugUnitTest`: 292 pruebas, 0 fallos.
- `sha256sum -c SHA256SUMS.txt` sobre los binarios recién copiados, dentro del
  propio script, antes de que termine.

## Lo que sigue sin estar hecho

- **El registro con DJI no se ha probado en un dispositivo.** La key está en el
  binario, pero que DJI la acepte para `com.djiflypro.app` sólo se comprueba
  conectando un aircraft. Ver `docs/PRUEBAS.md`.
- La allowlist de WPML sigue vacía, así que la ejecución automática de misiones
  está bloqueada aunque la app se registre.
- La firma es la de desarrollo. Para Google Play hace falta una clave privada
  de producción fuera del repositorio.
- `GMAP_API_KEY` y `MAPLIBRE_TOKEN` siguen vacías: el mapa abre sin teselas.
  El resto de la cartografía no depende del mapa.

## Firma

El firmante de estas compilaciones es `CN=DJIFlyPro Local Test`, una keystore de
desarrollo creada en `.local/djiflypro-test.jks`, SHA-256 del certificado
`8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43`. No es una
clave de producción y no debe usarse para publicar en Google Play.

## Release en GitHub

Estos tres binarios están publicados como assets del release
`v1.1.0-alpha.2`, junto con `SHA256SUMS.txt` y las notas de
`artifacts/release-notes.md`.

El manifiesto de hashes usa nombres de fichero sin ruta, para que
`sha256sum -c SHA256SUMS.txt` funcione sobre los ficheros descargados juntos.

El release `v1.1.0-alpha.1` anterior se conserva: sus binarios se compilaron
sin App Key y no pueden registrarse con DJI.

Para publicar una versión nueva: `scripts/publish-release.sh <version>`.

## Nota sobre `.deb`

Android no usa paquetes `.deb`. Los binarios publicados son `.apk` (instalable
directamente) y `.aab` (formato de subida para Google Play). Las dependencias
van incluidas dentro de los binarios, no se instalan por separado.
