# Build y artefactos

## Publicar en GitHub

`scripts/publish-release.sh` compila, registra los hashes, hace commit y push, etiqueta y crea el release en una sola pasada:

```bash
GH_TOKEN=<token con scope repo> scripts/publish-release.sh v0.1.0-alpha.2 --prerelease
```

Requisitos y guardas:

- El token se lee de `GH_TOKEN` o `GITHUB_TOKEN`. Nunca se escribe en el repositorio ni en la URL remota.
- Aborta si hay cambios sin commitear, para que la etiqueta corresponda al código que dice publicar.
- Aborta si hay un keystore o fichero tipo secreto versionado.
- Exige `artifacts/release-notes.md`. Conviene revisarlas como parte del código: son la afirmación pública sobre qué funciona.
- Con `--allow-missing-api-key` fuerza `prerelease` y avisa de que los binarios no pueden registrarse con DJI.

Publicar un artefacto que no venga del commit actual dejaría la historia de etiquetas mintiendo, así que el script no reconstruye: usa lo que dejó `scripts/build-release.sh`.

## Keystore del sample de DJI

El zip del sample oficial de MSDK V5 incluye `msdkkeystore.jks`. DJI tampoco lo versiona en su propio repositorio, y nada en esta compilación lo referencia, así que aquí está excluido por `.gitignore`. Si lo necesitas, conséguelo del zip del sample; no hay que sustituirlo para compilar.

## Preflight de release

El build release **aborta** si falta cualquiera de estos dos requisitos:

- `AIRCRAFT_API_KEY`: sin la App Key registrada para `com.djiflypro.app` el APK no puede registrarse con DJI, por muy bien compilado y firmado que esté.
- Firma completa: `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.

Para ejercitar el pipeline local (R8, lint, firma) sin la App Key:

```bash
./scripts/build-release.sh --allow-missing-api-key
```

El resultado se marca como `local-verification-only` y no es distribuible. Ese es exactamente el estado de los artefactos actuales.

## Dependencias

- Java 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- NDK 21.4.7075529
- Android Gradle Plugin 8.7.0
- Gradle 8.12
- DJI MSDK V5.18.0 desde los repositorios del SDK

## Comandos

Desde `android-sdk-v5-as`:

```bash
./gradlew :sample:testDebugUnitTest :sample:assembleDebug
./gradlew :sample:assembleRelease
./gradlew :sample:bundleRelease
```

Verificación reproducible usada en esta entrega:

```bash
./gradlew clean :sample:testDebugUnitTest :sample:lintDebug :sample:assembleDebug --no-daemon
./scripts/build-release.sh --allow-missing-api-key --no-daemon
```

Resultados obtenidos: 49 pruebas correctas, `lintDebug` con 0 errores, y release sin advertencias de clase ausente de R8. Las advertencias de "implicit default constructor" que quedan provienen de `proguard-android-optimize.txt` de AGP y de los `proguard.txt` de DJI SDK, Play Services, Room, Lifecycle, Navigation, Glide, OkHttp y JetBrains.

`ShippedWpmlAllowlistTest` valida el asset `validated_wpml_profiles.json` real. El asset está declarado como input de la tarea de test, así que un perfil inválido o con marcadores sin rellenar rompe la compilación en lugar de llegar a un dispositivo.

También existe `scripts/build-release.sh`, que carga las propiedades de firma locales desde `.local/signing.properties`, compila ambos artefactos y los copia a `artifacts/`.

Los artefactos aparecerán en `android-sdk-v5-sample/build/outputs/`. Copia los artefactos finales a `artifacts/` solo después de verificar la firma.

## Verificación de artefactos

```bash
$SDK/build-tools/35.0.0/apksigner verify --verbose --print-certs artifacts/DJIFlyPro-1.0.0.apk
$SDK/build-tools/35.0.0/aapt dump badging artifacts/DJIFlyPro-1.0.0.apk
$SDK/build-tools/35.0.0/aapt dump permissions artifacts/DJIFlyPro-1.0.0.apk
jarsigner -verify artifacts/DJIFlyPro-1.0.0.aab
( cd artifacts && sha256sum -c SHA256SUMS.txt )
unzip -p artifacts/DJIFlyPro-1.0.0.apk assets/validated_wpml_profiles.json
```

Sustituye `1.0.0` por la `versionName` del build. `scripts/build-release.sh`
ya nombra los binarios y regenera `SHA256SUMS.txt`; el `cd` mantiene el
manifiesto verificable, porque sus rutas son relativas a `artifacts/`.

La última orden debe mostrar una lista de perfiles vacía mientras no se haya registrado una validación física.

## Depuración de SDK

Si la APK se instala pero no conecta:

1. Comprueba que `AIRCRAFT_API_KEY` corresponde a `com.djiflypro.app`.
2. Comprueba que DJI Developer ha habilitado la aplicación y el modelo.
3. Revisa la versión de MSDK y el firmware.
4. Usa un dispositivo y control remoto compatibles y conecta el aircraft antes de abrir el planificador.
5. Mira el logcat filtrando por `DJI` y `DJIFlyPro`.

## Release

No subas una AAB firmada con la clave de prueba. Genera una clave privada propia, guárdala fuera del repositorio y configúrala mediante propiedades o variables de entorno.
