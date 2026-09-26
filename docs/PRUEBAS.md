# Pruebas en un móvil

Resumen corto: **el APK se puede instalar, pero con esta compilación no se
conecta a ningún aircraft.** Falta la App Key de DJI. Todo lo que no necesita
drone funciona.

## Instalar

El APK release es válido y está firmado:

```bash
sha256sum -c SHA256SUMS.txt
adb install -r DJIFlyPro-1.0.0.apk
```

O cópialo al móvil y ábrelo desde Archivos, habilitando "instalar apps de
orígenes desconocidos" para esa app.

Requisitos: Android 7.0 (API 24) o superior, CPU `arm64-v8a`. La firma es una
keystore de desarrollo local (`CN=DJIFlyPro Local Test`); al reinstalar sobre
una build firmada con otra clave hay que desinstalar antes.

## Qué funciona sin drone

Todo lo que es cálculo, almacenamiento y exportación:

- Planificador para fachada, cubierta, planta solar, campo y malla genérica.
- Geometría WGS84, separación de líneas, solape, altitud, velocidad, acciones de cámara.
- Exportación de `mission.json` y de KMZ/WPML con `WPMZManager`.
- Biblioteca algorítmica: recetas JSON declarativas, con validación y SHA-256.
- Almacenamiento local de misiones y registro de auditoría en `audit.jsonl`.

Ninguna de estas funciones necesita hardware.

## Qué NO funciona, y por qué

La app **no se registra con el SDK de DJI**. El motivo es una única cadena vacía
en el manifiesto:

```
com.dji.sdk.API_KEY = ""
```

El valor lo inyecta el build desde la propiedad `AIRCRAFT_API_KEY`, que está
vacía en `android-sdk-v5-as/gradle.properties`. El build release ahora **aborta**
en vez de producir un APK así, pero los binarios publicados se generaron con
`--allow-missing-api-key` antes de añadir ese preflight.

Consecuencia práctica: sin registro no hay conexión, ni telemetría, ni control,
ni cámara. El panel principal lo dice explícitamente en vez de mostrar un error
genérico:

> Falta la API Key de DJI: registra com.djiflypro.app en DJI Developer

## Cómo habilitar las pruebas con drone

1. **Identifica el aircraft y el control remoto.** Modelo exacto, versión de
   firmware de ambos. Anótalos; los necesitarás para el allowlist de misiones.
2. **Registra la aplicación** en https://developer.dji.com con el
   `applicationId` exacto `com.djiflypro.app`. DJI lo exige para la
   distribución de apps y para SDK distintos del de desarrollo.
3. **Pon la App Key en el entorno**, no en el repositorio:

   ```bash
   export AIRCRAFT_API_KEY='<la clave que te da DJI>'
   ./scripts/build-release.sh
   ```

4. **Instala el APK resultante** y abre la app. El panel debe pasar de
   "falta la API Key" a "DJI Mobile SDK registrado correctamente".
5. **Comprueba la conexión** antes de volar nada. Si el panel sigue en
   "Producto: no conectado", el problema es otro: permisos de ubicación,
   Bluetooth, o un controlador que no se reconoce.
6. **Prueba en una zona segura**, con observador, y empieza por lo que no
   vuela: conexión, telemetría, cámara, antes de tocar el centro de control.

## Misiones automáticas: siguen bloqueadas a propósito

Aunque la App Key esté puesta y el aircraft conectado, la **subida y ejecución
automática de misiones WPML no se habilitan** hasta que exista un perfil
revisado en `validated_wpml_profiles.json` con coincidencia exacta de producto,
control remoto y ambas versiones de firmware, más la referencia de una prueba
física registrada. La allowlist incluida está vacía a propósito.

No es un descuido pendiente de clave: es la barrera que impide que la app envíe un
plan de vuelo a un aircraft con el que nadie la ha probado. Para añadir un
perfil, ver `docs/COMPATIBILITY.md`. El planificador incluye **Exportar informe
de validación**, que emite los valores exactos que reporta el SDK para el
hardware conectado, para no tener que deducirlos a mano.

## Control remoto

Conectar el aircraft no basta: hace falta el control remoto de DJI emparejado.
El MSDK V5 trabaja sobre el enlace del control remoto, no sobre el móvil. El
móvil hace de terminal y visualizador.

Si el aircraft es de la serie Mavic 3 / Mini 3 / Air 3, la app oficial de DJI se
conecta directamente por Wi-Fi a esos modelos; una app MSDK se comporta distinto
y puede no tener soporte nativo de waypoint. Eso hay que comprobarlo con el
modelo concreto, no suponerlo.

## Antes de publicar de verdad

- App Key de DJI registrada.
- Clave privada de producción, fuera del repositorio.
- Firmar con `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Compilar **sin** `--allow-missing-api-key`.
- Recalcular los hashes y regenerar los artefactos.
