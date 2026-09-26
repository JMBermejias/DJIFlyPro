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

## Pedir la App Key a DJI

La App Key no se genera: se pide. El proceso, según la documentación oficial de
MSDK V5:

1. **Cuenta de desarrollador** en https://developer.dji.com. DJI pide email y,
   según la versión del registro que te toque, una tarjeta o un teléfono para
   verificar la identidad. La documentación de DJI de 2020 decía que la tarjeta
   solo se usaba para verificar y no se cobraba nada; **confírmalo en sus
   condiciones actuales** antes de introducir datos de pago.
2. **Crear la app** en el Developer Center: pestaña **Apps** → **CREATE APP**.
   Rellena App Type, App Name, Software Platform (**Android**) y **Package
   Name**.
3. **Esperar el correo de activación.** La App Key no existe hasta que
   completas esa activación. Cuenta con que tarde, no es inmediato.
4. **Copiar la App Key**: 24 dígitos hexadecimales, visibles ya en el developer
   center.

El Package Name debe ser exactamente el `applicationId` del proyecto, que en
este repo es `com.djiflypro.app` (`android-sdk-v5-sample/build.gradle`). Si no
coinciden, el registro falla con:

```
errorType='SDK', errorCode='INVALID_METADATA'
```

DJI genera además un **APP ID** con una communication key de 256 dígitos. Ese
es otro producto, para *Application Activation and Aircraft Binding* y Flight
Hub, y **no hace falta** para volar con el MSDK. Solo se necesita la App Key.

El cableado ya está hecho: `build.gradle` declara el `applicationId` y rellena
`manifestPlaceholders["API_KEY"]` desde `AIRCRAFT_API_KEY`. No hay que tocar
ningún fichero, solo definir la variable.

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

## Misiones automáticas en una Mini 3: no van a funcionar

Si el aircraft es una DJI Mini 3 o Mini 3 Pro, la **subida y ejecución de
misiones WPML no funcionarán nunca con ese modelo**, y no es culpa de esta app.
El firmware del aircraft no implementa misiones de ruta, así que el MSDK no
puede ofrecerlas. DJI lo confirma explícitamente: *la app DJI Fly tampoco tiene
función de rutas en estos modelos*.

El síntoma es un error `errorType=WAYPOINT`,
`errorCode=REQUEST_HANDLER_NOT_FOUND` al subir el KMZ. Registrar la App Key, o
añadir un perfil de allowlist, no lo cambia.

Con una Mini 3 sí funcionan: conexión, telemetría, cámara, vídeo, control manual
y RTH. El planificador y la exportación WPML/KMZ también siguen sirviendo para
generar y revisar planes en el móvil.

Ver `docs/COMPATIBILITY.md` para las citas textuales y el detalle técnico.

## Guía de vuelo manual: cómo volar un plan sin misiones WPML

Como la Mini 3 no puede ejecutar misiones de ruta, la app incluye una guía que
reemplaza esa parte. **La app no vuela el aircraft en ningún momento**: tú
pilotas con el mando y la pantalla te dice hacia dónde ir.

Se abre desde el planificador con **Guía de vuelo manual**, y funciona con
cualquier plan guardado. No necesita conexión para cargarse, ni subida, ni
perfil de allowlist.

Qué muestra:

- Punto actual sobre el total, y porcentaje completado.
- Coordenadas exactas del punto y altitud objetivo.
- **Distancia** hasta el punto y rumbo en grados.
- **Giro a ejecutar**: "GIRA IZQUIERDA 40°" o "GIRA DERECHA 12°". Es el
  rumbo menos el morro del aircraft, con el signo ya resuelto: positivo es
  derecha.
- Diferencia de altitud, en metros, con "sube" o "baja".
- Batería, nivel GPS y distancia a casa.

Cómo avanza:

- **Automáticamente** cuando estás a 8 m o menos del punto y a 3 m o menos de la
  altura planificada.
- A mano con **Marcar como alcanzado**, si el radio te parece estrecho.
- **Saltar punto** para saltarte uno, y **Punto anterior** para retroceder.

Detalles que importan:

- Si el enlace está caído, la pantalla dice **SIN ENLACE** en lugar de mostrar
  una dirección calculada con datos viejos. Guidar hacia el punto equivocado es
  peor que no guidar.
- Si el SDK no entrega rumbo, dice **GIRO DESCONOCIDO** en vez de inventar un
  lado.
- El rumbo viene de `KeyCompassHeading`, que es un rumbo de brújula, no el yaw
  relativo al cuerpo. Así el cálculo no depende del modo de attitude.
- Las coordenadas no utilizables (fuera de rango, NaN, o 0/0) se descartan al
  construir la sesión, y la app dice cuántos descartó.
- Cada llegada, salto, retroceso y reinicio queda en `audit.jsonl`.

La geometría está aislada en `NavigationGuidance` y las etiquetas de pantalla en
`GuidanceLabels`, ambas con tests. La dirección del giro tiene tests explícitos
para ambos hemisferios y para el cruce de 0/360, porque un error de signo ahí
apunta al aircraft en dirección contraria.

## Misiones automáticas en general: bloqueadas por diseño

Aun con un aircraft que sí soporte WPML, la subida y ejecución automática no se
habilitan hasta que exista un perfil revisado en `validated_wpml_profiles.json`
con coincidencia exacta de producto, control remoto y ambas versiones de
firmware, más la referencia de una prueba física registrada. La allowlist
incluida está vacía a propósito.

No es un descuido pendiente de clave: es la barrera que impide que la app envíe un
plan de vuelo a un aircraft con el que nadie la ha probado. Para añadir un
perfil, ver `docs/COMPATIBILITY.md`. El planificador incluye **Exportar informe
de validación**, que emite los valores exactos que reporta el SDK para el
hardware conectado, para no tener que deducirlos a mano.

## Control remoto

Conectar el aircraft no basta: hace falta el control remoto de DJI emparejado.
El MSDK V5 trabaja sobre el enlace del control remoto, no sobre el móvil. El
móvil hace de terminal y visualizador.

Un detalle sobre la serie Mavic 3 / Mini 3 / Air 3: la app oficial de DJI se
conecta a esos modelos por su propio enlace, y una app MSDK es un cliente
distinto de ese enlace. Que el modelo aparezca en la lista de productos
soportados de MSDK significa que el SDK lo reconoce, no que todas las funciones
funcionen: el caso de las misiones de ruta en la Mini 3 es exactamente el
contraejemplo.

## Antes de publicar de verdad

- App Key de DJI registrada.
- Clave privada de producción, fuera del repositorio.
- Firmar con `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Compilar **sin** `--allow-missing-api-key`.
- Recalcular los hashes y regenerar los artefactos.
