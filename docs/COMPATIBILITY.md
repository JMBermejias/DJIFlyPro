# Compatibilidad de DJIFlyPro

## SDK

La aplicación `DJIFlyPro` V5 usa `com.dji:dji-sdk-v5-aircraft:5.18.0` y `com.dji:dji-sdk-v5-aircraft-provided:5.18.0`.

## Puerta de ejecución WPML

La generación, la validación, el guardado y la exportación JSON/KMZ están disponibles sin aircraft conectado. La subida y la ejecución WPML están bloqueadas de forma explícita hasta que exista un perfil de validación física.

El perfil se carga desde el asset incluido en la APK:

`android-sdk-v5-sample/src/main/assets/validated_wpml_profiles.json`

En esta entrega el asset contiene una lista vacía. Por tanto, ninguna combinación de modelo, control remoto y firmware habilita automáticamente una misión. Un firmware no vacío solo demuestra que MSDK devolvió una etiqueta; no es una certificación de compatibilidad ni de seguridad.

Cada entrada requiere coincidencia exacta de:

- `productName` (tipo de producto MSDK);
- `remoteControllerName` (tipo de control remoto MSDK);
- `aircraftFirmwareVersion` (versión exacta del aircraft);
- `remoteControllerFirmwareVersion` (versión exacta del control remoto);
- `validationReference` (referencia del registro de prueba física).

La plantilla `config/validated-wpml-profiles.example.json` muestra el formato. No se debe copiar con marcadores de posición ni con valores inventados: el perfil se añade solo después de identificar el hardware y registrar la prueba física, y debe revisarse como parte del código fuente. Si el asset no se puede leer, tiene un esquema desconocido o contiene un perfil inválido, la aplicación falla cerrada y mantiene el bloqueo.

Un perfil con marcadores sin rellenar (`REPLACE_`, `TODO`, `TBD`, `REQUIRED`, `EXAMPLE`, `<...>`, `???`) se rechaza, incluido el caso de un producto ya rellenado con la referencia de validación todavía como placeholder. Un perfil inválido invalida el fichero completo en lugar de omitirse, para que una entrada rota nunca pueda confundirse con una revisada.

## Cómo obtener la identidad exacta

El planificador incluye **Exportar informe de validación**, que escribe un fichero de texto con el producto, el control remoto y ambas versiones de firmware tal como los reporta MSDK para el hardware conectado, más un bloque JSON listo para pegar.

Uso recomendado:

1. Conectar aircraft y control remoto, y abrir el planificador.
2. Exportar el informe y comprobar que los cuatro valores están presentes y no son `UNKNOWN_*`.
3. Realizar la prueba física en una zona segura con observador.
4. Sustituir `REPLACE_WITH_YOUR_TEST_RECORD_REFERENCE` por la referencia real del registro.
5. Pegar el perfil en el asset y compilar; `ShippedWpmlAllowlistTest` valida el resultado.

El informe emitido nunca es una aprobación por sí mismo: su referencia de validación es un marcador que el cargador rechaza, de modo que no puede pegarse de vuelta en la allowlist sin editarla.

`MSDKManagerVM` consulta la versión del aircraft y la identidad/versión del control remoto, y descarta respuestas asíncronas antiguas al desconectar o cambiar de producto. La puerta vuelve a evaluarse inmediatamente antes de subir y de enviar el inicio.

## Modelos

| Modelo/familia | Estado en esta entrega | Observación |
|---|---|---|
| Matrice 350 RTK | WPML bloqueado por defecto | Solo se habilita con un perfil exacto tras validar payload, RTK, firmware y control remoto. |
| Matrice 300 RTK | WPML bloqueado por defecto | Validar payload, control remoto y comportamiento de breakpoint según la versión. |
| Matrice 30/4/400 | WPML bloqueado por defecto | La lista exacta depende de MSDK 5.18.0 y de la combinación probada. |
| Mavic 3 Enterprise | WPML bloqueado por defecto | Comprobar modelo, control remoto, firmware y comportamiento de misión. |
| Mini 3 / Mini 3 Pro | Sin misiones WPML, por firmware | Ver abajo. Telemetría, cámara, vídeo y control manual sí son objetivo de MSDK. |
| Phantom 4 | No compatible con V5 | Necesita una variante Legacy basada en MSDK V4; no se mezclan SDK en esta APK. |

## Mini 3 y Mini 3 Pro no pueden ejecutar misiones WPML

Esto no es una restricción de esta app: es una limitación del firmware del
aircraft, confirmada por el soporte de DJI.

DJI ha respondido de forma explícita en tickets de soporte y en el repositorio
oficial de MSDK V5:

> Mini 3/Mini 3 Pro's firmware does not support the native waypointMission
> function so SDK cannot support this feature as well.

> The Mini 3 Pro aircraft firmware still does not support waypoint missions.
> The DJI Fly app still lacks a route/waypoint feature, so the MSDK's waypoint
> mission functionality is not supported either.

El síntoma en la app es un error `errorType=WAYPOINT`,
`errorCode=REQUEST_HANDLER_NOT_FOUND` al subir el KMZ. No se arregla registrando
`com.djiflypro.app`, ni añadiendo un perfil de allowlist, ni cambiando de
firmware dentro de la misma familia.

Por qué el SDK no lo filtra antes: en MSDK 5.18.0,
`WaypointMissionOperatorFactory.build(ProductType)` solo trata de forma
especial `M300_RTK`, `M350_RTK`, `M30_SERIES` y `DJI_MATRICE_400`. Todo lo demás,
incluida la Mini 3, cae en la rama por defecto y recibe
`WaypointMissionV3Impl`. El rechazo llega del aircraft, no del SDK.

### Qué sí funciona con una Mini 3

- Conexión y enlace con el control remoto.
- Telemetría de vuelo.
- Cámara y captura de imagen/vídeo.
- Control manual y RTH.

### Qué no funciona con una Mini 3

- Subida y ejecución de misiones WPML.

El planificador, la exportación WPML/KMZ y la biblioteca algorítmica siguen
sirviendo: se pueden generar y revisar los planes en el móvil y exportarlos,
aunque no se puedan ejecutar a bordo de este modelo.

### La app lo dice por sí misma

`WaypointMissionExecuteState.NOT_SUPPORTED` existe en el SDK y significa
"This aircraft does not support waypoint mission 3.0". La app registra ese
estado y, cuando aparece, muestra que el firmware no admite misiones, en lugar
de un motivo burocrático como "no hay perfil validado". Es la única señal de
capacidad que viene del aircraft en vez de de una tabla de este repositorio.

## Hardware declarado, sin validar

Registrado a petición del usuario, pendiente de prueba física. Esto no
habilita nada por sí solo: el allowlist empaquetado sigue vacío.

| Campo | Valor | Estado |
|---|---|---|
| Aircraft | DJI Mini 3 (normal, no Pro) | Sin conectar nunca |
| Control remoto | DJI RC-N3 | Sin conectar nunca |
| Firmware aircraft | Desconocido | Leer con el informe de validación |
| Firmware control remoto | Desconocido | Leer con el informe de validación |

El RC-N3 aparece como `DJI_RC_N3` en `RemoteControllerType` de MSDK 5.18.0, así
que el SDK lo identifica. Aun así, hace falta probarlo: que el enum lo conozca no
significa que el enlace funcione con esta unidad concreta.

## Por qué existe la separación

MSDK V5 y MSDK V4 tienen APIs, nativos y requisitos de registro diferentes. Mezclarlos en una sola APK puede producir conflictos de clases y de bibliotecas nativas. Por eso la carpeta de esta entrega contiene la variante V5 y debe contener una variante Legacy separada antes de operar un Phantom 4.

## Verificación recomendada

1. Registra la App Key de DJI para `com.djiflypro.app`. El build release aborta mientras esté vacía.
2. Identifica el modelo exacto, el control remoto exacto y las versiones de firmware de aircraft y control remoto con el informe de validación.
3. Usa el simulador o un área de prueba segura; no presupongas soporte por familia.
4. Genera primero un plan JSON y revísalo fuera del aircraft.
5. Exporta el KMZ y comprueba que el aircraft lo acepta.
6. Registra la prueba física y solo entonces añade un perfil exacto al asset de allowlist.
7. Ejecuta primero una misión pequeña con observador y plan de abortos.
