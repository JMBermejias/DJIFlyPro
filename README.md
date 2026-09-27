# DJIFlyPro

DJIFlyPro es una aplicación Android nativa para **cartografía con drones**:
planificación de bloques de vuelo orientada a resolución y solape, control
terrestre, y entrega de la planificación en los formatos que consume el resto
de la cadena cartográfica.

> **Estado: prerelease sin validar en vuelo.** Desde `v1.1.0-alpha.2` la App Key
> de DJI va incrustada en el binario, así que la aplicación puede registrarse
> con el SDK. Pero la conexión con un aircraft no se ha probado en hardware, y
> la ejecución automática de misiones WPML sigue bloqueada porque la allowlist
> de validación física se publica vacía. Compila, pasa 292 pruebas y no tiene
> errores de lint. Ver `docs/SAFETY.md` y `docs/PRUEBAS.md`.

## Releases

- [v1.1.0-alpha.6](https://github.com/JMBermejias/DJIFlyPro/releases/tag/v1.1.0-alpha.6) — **la que hay que instalar, y hay que reinstalarla**: App Key ya correcta para `com.djiflypro.app`, así que el SDK registra con DJI y por fin enlaza con el aircraft. Actualización desde la propia app, con la App Key incrustada.
- [v1.1.0-alpha.2](https://github.com/JMBermejias/DJIFlyPro/releases/tag/v1.1.0-alpha.2) — igual, pero además con un APK debug de otro certificado que no se puede instalar encima.
- [v1.1.0-alpha.1](https://github.com/JMBermejias/DJIFlyPro/releases/tag/v1.1.0-alpha.1) — compilada sin App Key: no puede registrarse con DJI.
- [v0.1.0-alpha.1](https://github.com/JMBermejias/DJIFlyPro/releases/tag/v0.1.0-alpha.1) — base de planificación de rutas.

Todas son prereleases. Cada build es una release nueva con su etiqueta; los
assets de una release publicada no se reemplazan. Instalación y estado:
`artifacts/release-notes.md`.

## Idioma

La aplicación se entrega **en español**, y el español es el idioma por defecto,
no una traducción: se ve en español aunque el dispositivo esté en otro idioma.
La capa de dominio (validadores, barrera de ejecución, motor de GSD, control
terrestre) también está en español, y las fichas de entrega que genera
—GeoJSON, KML, ficha de vuelo e informe de validación— salen con los textos en
español. Ver `docs/IDIOMA.md`.

## Actualizaciones

Al abrirse, la app comprueba si hay una versión nueva y, si la hay, lo avisa y
ofrece un botón para instalarla. La descarga se comprueba contra el hash que
publica la release **y** contra el certificado con el que está firmada esta
misma app: si las dos cosas no coinciden, no instala nada y lo dice. El
instalador es siempre el del sistema, que es quien pide la confirmación.

La comprobación se hace como mucho una vez cada seis horas, y también a mano
desde **Buscar actualizaciones**. Ver `docs/ACTUALIZACIONES.md`.

## Qué hace

### Cartografía

- **Motor de GSD.** Introduce la cámara y la resolución objetivo y obtén la
  altura de vuelo, la separación entre líneas y la separación entre fotos, con
  las relaciones fotogramétricas estándar. Ver `docs/CARTOGRAFIA.md`.
- **Siete payloads** de DJI descritos por sensor, objetivo y resolución de
  salida, más entrada de cámaras propias. El GSD se deriva de esos parámetros,
  no de una tabla, así que un payload desconocido da un número que el operador
  puede corregir.
- **Solape longitudinal y transversal** por separado, con la redundancia real
  calculada sobre la superficie de la parcela.
- **Referencia de altura declarable**: sobre el terreno, nivel del mar o desde
  el despegue. Un GSD sólo es cierto sobre terreno de altura conocida, así que
  el dato tiene que declararse y viaja con la entrega.
- **Puntos de control** con tipo de diana, exactitud medida y origen, y una
  comprobación de la red: número, reparto sobre los bordes, colinealidad,
  separación y exactitud.
- **WGS84 ↔ UTM** con el desarrollo de Snyder, más el polígono de la parcela y
  su superficie en hectáreas. La ficha de vuelo entrega las dos referencias.
- **Geometría de bloque correcta**: las líneas se reparten uniformemente para
  que la última caiga en el borde, y cada línea se alarga una separación de
  foto a cada extremo para que el perímetro quede cubierto entero. `Malla doble`
  y `Cruz oblicua 45°` añaden una segunda pasada.
- **Revisión en el mapa** de la huella, las líneas y el control, y colocación de
  un punto de control donde está realmente pintado.

### Planificación y vuelo

- Panel de estado del SDK y telemetría.
- Centro de control reutilizando el flujo de control de vuelo del SDK oficial,
  con todas las acciones bajo una política de seguridad.
- Planificador para fachada, cubierta, planta solar, campo, malla genérica y
  cruz oblicua.
- Validación de límites de seguridad y de capacidad del producto.
- Exportación de `mission.json`, GeoJSON, KML, KMZ/WPML y ficha de vuelo.
- Subida y ejecución de misiones WPML condicionadas a conexión, registro y a un
  perfil de validación física con coincidencia exacta de producto, control
  remoto y ambos firmwares. La allowlist incluida está vacía, de modo que la
  ejecución automática está bloqueada en esta entrega.
- Selección de waylines y confirmación del operador repetidas justo antes del
  inicio.
- Guía de vuelo manual: sigue una ruta con la telemetría, sin WPML, para los
  aircraft cuyo firmware no admite misiones waypoint.
- Recetas algorítmicas declarativas, cargadas desde los assets de la
  aplicación, importables desde `Algoritmos y recetas`.
- Registro local de auditoría en `audit.jsonl`, incluidos los intentos
  bloqueados por la barrera de ejecución, con rotación por tamaño.

### Techo de altura

Un P1 a 2 cm/px vuela a 160 m, por encima del techo de 120 m de una misión
waypoint automática de DJI. La aplicación no lo oculta ni lo sube:

- **120 m**: error duro para cualquier subida o inicio automático.
- **500 m**: techo de planificación. Por encima se puede planificar, guardar,
  exportar y volar con guía manual, donde la persona está al mando.

## Estructura

- `android-sdk-v5-as/`: raíz Gradle y wrapper.
- `android-sdk-v5-sample/`: aplicación Android y código DJIFlyPro.
  - `pro/cartography/`: GSD, cámaras, solución cartográfica, UTM, control
    terrestre, resumen, GeoJSON, KML y ficha de vuelo.
  - `pro/map/`: revisión en el mapa.
  - `pro/mission/`: geometría de ruta, validación, WPML, permiso de ejecución.
  - `pro/guidance/`: guía de vuelo manual.
  - `pro/algorithm/`: recetas declarativas.
- `android-sdk-v5-uxsdk/`: UX SDK de DJI.
- `algorithms/`: copias de las recetas de ejemplo.
- `legacy/`: variante MSDK V4 para Phantom 4, con `preflight.sh` y registro de
  hardware.
- `docs/`: [idioma](docs/IDIOMA.md), cartografía,
  [actualizaciones dentro de la app](docs/ACTUALIZACIONES.md), algoritmos,
  compatibilidad, seguridad, compilación y [pruebas en móvil](docs/PRUEBAS.md).
- `artifacts/`: APK/AAB generados, notas de release y manifiesto de hashes. Los
  binarios no se versionan; se publican como assets del release.
- `config/`: configuración local de firma (no publicar claves privadas).

## Compilar

```bash
cd android-sdk-v5-as
./gradlew :sample:testDebugUnitTest
./gradlew :sample:lintDebug
./gradlew :sample:assembleDebug
./gradlew :sample:bundleRelease
```

El `local.properties` apunta al SDK Android instalado en `tools/android-sdk`.
Ver `docs/BUILD.md`.

## DJI App Key

La clave de esta compilación está en `.local/api-key.properties`, que está en
`.gitignore` y con permisos `600`. `scripts/build-release.sh` la lee y la
exporta como `AIRCRAFT_API_KEY`. **No la escribas en `gradle.properties`**: ese
fichero está versionado y la clave acabaría en el repositorio.

Para reconstruir con la clave:

```bash
echo "AIRCRAFT_API_KEY=<tu clave>" > .local/api-key.properties
chmod 600 .local/api-key.properties
./scripts/build-release.sh
```

Para registrar una clave nueva hay que hacerlo en DJI Developer con el
`applicationId` exacto `com.djiflypro.app`, y usar el firmware y el control
remoto soportados por DJI para el modelo concreto.

Si la clave falta, el preflight de release aborta la build. Con
`DJIFLYPRO_ALLOW_MISSING_API_KEY=1` se salta, pero el resultado no puede
registrarse con DJI.

## Mapa

El mapa usa MapLibre a través del UX SDK de DJI. La aplicación funciona sin
llave: el mapa aparece vacío y el resto de la cartografía no depende de él.
Para ver teselas hay que configurar `MAPLIBRE_TOKEN` o `GMAP_API_KEY` en
`android-sdk-v5-as/gradle.properties`.

## Habilitar misiones WPML

La subida y la ejecución automática están bloqueadas hasta que exista un perfil
de validación física con coincidencia exacta de producto, control remoto,
firmware del aircraft y firmware del control remoto.

1. Conecta el hardware y usa **Exportar informe de validación** en el planificador
   para obtener las cadenas exactas que reporta MSDK.
2. Registra la prueba física en un entorno seguro y anota su referencia.
3. Sustituye `REPLACE_WITH_YOUR_TEST_RECORD_REFERENCE` en el informe por esa
   referencia.
4. Añade el perfil a
   `android-sdk-v5-sample/src/main/assets/validated_wpml_profiles.json` y revisa
   el cambio como parte del código fuente.
5. Compila: `ShippedWpmlAllowlistTest` valida el asset y falla si queda algún
   marcador sin rellenar.

Un perfil no válido, un asset ilegible o un esquema desconocido producen una
allowlist vacía. Un firmware conocido no es una certificación de seguridad. Ver
`docs/COMPATIBILITY.md` y `docs/SAFETY.md`.

## Variante Legacy (Phantom 4)

`legacy/` está reservada para una variante independiente basada en MSDK V4.
Ejecuta `legacy/preflight.sh` para ver qué falta. No se puede construir hasta
disponer del SDK V4 y de una App Key propia de `com.djiflypro.legacy`.

## Firmas

El repositorio no debe contener una clave privada de producción. Para una
compilación de distribución, configura `STORE_FILE`, `STORE_PASSWORD`,
`KEY_ALIAS` y `KEY_PASSWORD` mediante una keystore propia. Las firmas de prueba
generadas para este entorno no sirven para Google Play.

## Importante

Un planificador no puede detectar por sí solo todos los obstáculos, líneas
eléctricas, permisos, zonas reguladas o restricciones de espacio aéreo. La
persona operador debe verificar la misión y el entorno antes de cualquier vuelo.
No se debe utilizar esta aplicación como único sistema de seguridad.
