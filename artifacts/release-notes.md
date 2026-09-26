# DJIFlyPro v0.1.0-alpha.1

Prerelease de la base nativa Android sobre DJI Mobile SDK V5.18.0
(`applicationId com.djiflypro.app`).

## Aviso: estos artefactos aún no pueden volar

Este release **no es funcional para 제어 de vuelos**. Los APK se compilaron
usando el flag `--allow-missing-api-key` porque `AIRCRAFT_API_KEY` está vacía:

> Sin la App Key de DJI registrada para `com.djiflypro.app`, la aplicación no
> puede registrarse con el SDK ni conectarse a ningún aircraft. La interfaz
> abrirá, la planificación y la exportación WPML funcionarán, pero todo lo que
> toque el aircraft real fallará.

Para que sirvan de algo hace falta, en este orden:

1. Registrar `com.djiflypro.app` en el DJI Developer Console y obtener su App Key.
2. Compilar de nuevo con `AIRCRAFT_API_KEY` definida. El build release **aborta**
   si falta, así que no es posible publicar por accidente otro APK sin clave.
3. Firmar con una clave privada de producción fuera del repositorio.
4. Probar físicamente antes de habilitar misiones automáticas.

Publicamos esta prerelease como evidencia de compilación, R8, lint y firma, y
como base sobre la que iterar.

## Qué incluye

- Panel de estado del SDK, centro de control, telemetría y cámara.
- Planificador para fachada, cubierta, planta solar, campo y malla genérica, con
  geometría WGS84 y límites preventivos de memoria y de número de waypoints.
- Exportación `mission.json` y KMZ/WPML mediante `WPMZManager`.
- Recetas algorítmicas declarativas en JSON (máximo 1 MiB, SHA-256, auditoría).
  No se ejecuta código importado: AAR, DEX, bytecode y scripts se rechazan.
- Registro local de auditoría en `audit.jsonl`, incluidos los intentos bloqueados
  por la barrera de ejecución.

## Barrera de ejecución WPML

La subida y la ejecución automática están **bloqueadas** en este build. La
allowlist `validated_wpml_profiles.json` va empaquetada vacía, y para
habilitarla hace falta un perfil revisado con coincidencia exacta de producto,
control remoto y ambas versiones de firmware, más la referencia de una prueba
física registrada.

- Un firmware conocido no es una certificación de compatibilidad ni de seguridad.
- Los perfiles declarados fallan cerrados: esquema desconocido, tamaño
  excedido, campo inválido o marcador de plantilla sin rellenar producen una
  allowlist vacía. Un perfil inválido invalida el fichero completo, no se omite.
- La selección de waylines y la confirmación del operador se repiten justo antes
  de enviar la orden. Si el aircraft no confirma todas las waylines
  seleccionadas, la misión no arranca.

## Assets

| Archivo | Descripción |
|---|---|
| `DJIFlyPro-1.0.0.apk` | APK release, R8 aplicado, firmado con clave de pruebas local |
| `DJIFlyPro-1.0.0.aab` | Android App Bundle para Play, misma base de código |
| `DJIFlyPro-1.0.0-debug.apk` | APK debug, con símbolos, para diagnóstico |
| `SHA256SUMS.txt` | Hashes SHA-256 de los tres binarios |

El nombre de los ficheros lleva la `versionName` que declara la propia
aplicación (`1.0.0`). La etiqueta del release es `v0.1.0-alpha.1`, que refleja
que esto es una prerelease sin App Key, no la versión de la app. Son dos cosas
distintas a propósito.

No se adjunta `.deb`: Android no usa ese formato. Las dependencias van
incluidas dentro de los APK y el Bundle, no se instalan aparte.

## Instalación

El APK se instala directamente:

```bash
sha256sum -c SHA256SUMS.txt
adb install -r DJIFlyPro-1.0.0.apk
```

O cópialo al dispositivo y ábrelo, habilitando "instalar apps de orígenes
desconocidos" para esa app de Archivos. Android 8.0 o superior.

Requisitos: Android 7.0 (API 24) o superior, `arm64-v8a`. No necesita
instalar dependencias por separado.

El `.aab` **no es instalable directamente**: es el formato de subida para
Google Play, o para Play Console / `bundletool`.

## Firma

El firmante de esta compilación es `CN=DJIFlyPro Local Test`, una keystore de
desarrollo. No es una clave de producción y no debe usarse para publicar en
Google Play. Al reinstalar sobre una build firmada con otra clave habrá que
desinstalar antes.

## Verificación

- 49 pruebas unitarias, 0 fallos, tras un `clean`.
- `lintDebug`: 0 errores.
- Release sin advertencias de clase ausente de R8.
- APK verificado con `apksigner` (esquema v2); Bundle verificado con `jarsigner`.
- Identidad: `com.djiflypro.app` 1.0.0, versionCode 1, minSdk 24, targetSdk 35.
- `MANAGE_EXTERNAL_STORAGE` ausente.

## No verificado

Sin hardware no se ha probado: conexión, telemetría, control, takeoff, RTH,
cámara, subida de misión ni ejecución. La variante Legacy para Phantom 4
(MSDK V4) no existe todavía; `legacy/preflight.sh` lista lo que falta.

## Código

https://github.com/JMBermejias/DJIFlyPro
