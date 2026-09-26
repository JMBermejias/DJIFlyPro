# DJIFlyPro v1.1.0-alpha.2

**La App Key de DJI ya está en el binario.** Esta prerelease sí puede registrarse
con el SDK. Es un cambio respecto a `v1.1.0-alpha.1`, cuyos binarios se
compilaron sin key y no podían conectarse a ningún aircraft.

`applicationId com.djiflypro.app`, DJI Mobile SDK V5.18.0,
`versionName 1.1.0-alpha.2`, `versionCode 3`.

## Qué sigue sin estar hecho

- **La conexión con un aircraft no se ha probado en un dispositivo.** La key está
  en el manifiesto, pero que DJI la acepte para `com.djiflypro.app` sólo se
  comprueba volando. La interfaz y toda la cartografía se pueden probar sin
  hardware.
- **La ejecución automática de misiones WPML sigue bloqueada.** La allowlist de
  validación física se publica vacía a propósito. Aunque la app se registre, no
  puede subir ni iniciar una misión automática hasta que haya validación
  física documentada.
- La firma es la keystore de desarrollo local. No publicable en Google Play.
- El mapa abre sin teselas: `GMAP_API_KEY` y `MAPLIBRE_TOKEN` siguen vacías.
  El resto de la cartografía no depende del mapa.

## Novedad de esta versión: cartografía

### Motor de resolución

La aplicación trabaja en la dirección en que llega un encargo de cartografía: la
resolución y el solape se declaran, la altura se deduce. Un P1 a 2 cm/px son
160 m de altura, con una huella de 164 m: 49 m entre líneas al 70 % de solape
transversal y 33 m entre fotos al 80 % de solape longitudinal.

- Siete payloads de DJI descritos por sensor, objetivo y resolución de salida.
  El GSD se deriva de esos parámetros, no de una tabla, así que un payload
  desconocido se puede describir y el resultado es un número que el operador
  puede ver y corregir.
- Solape longitudinal y transversal por separado.
- Redundancia real calculada sobre la superficie de la parcela.
- La ficha avisa cuando la resolución es demasiado fina o demasiado gruesa para
  ser práctica, y cuando el solape es bajo para el tipo de terreno.

### Techo de altura, sin ocultarlo

Un P1 a 2 cm/px vuela a 160 m, por encima del techo de 120 m de una misión
waypoint automática de DJI. La aplicación no lo sube:

- **120 m** es un error duro para cualquier subida o inicio automático, y se
  comprueba otra vez justo antes de subir y otra vez justo antes de iniciar.
- **500 m** es el techo de planificación. Por encima se puede planificar,
  guardar, exportar y volar con la guía manual, donde la persona está al mando.

La solución indica en cuál de los dos casos está.

### Referencia de altura

Un GSD sólo es cierto sobre terreno de altura conocida, así que el dato se
declara: sobre el terreno, nivel del mar o desde el despegue. Viaja en
`mission.json`, en el GeoJSON, en el KML y en la ficha, y en el KML se traduce
al `altitudeMode` que lo representa.

### Control terrestre

- Puntos de control con tipo de diana, exactitud horizontal medida y origen de
  la coordenada.
- Comprobación de la red: al menos 4 puntos de control y 1 de verificación,
  reparto que llega a los bordes del bloque, no colinealidad, separación mínima
  entre puntos y aviso cuando ningún punto tiene altura.
- Un elemento del terreno no es válido como control, sólo como verificación.

### Sistemas de referencia

WGS84 geográfico ↔ UTM con el desarrollo en serie de Snyder, incluidas las
ensanchaciones de Noruega y Svalbard. La ficha de vuelo y el GeoJSON entregan las
dos referencias con su código EPSG, más la superficie en hectáreas.

### Geometría de bloque corregida

- Las líneas de vuelo se reparten uniformemente sobre el ancho, de modo que la
  última cae exactamente en el borde y la separación real nunca es menor que la
  pedida. Antes la última línea podía dejar una franja sin cubrir.
- Cada línea se alarga una separación de foto a cada extremo. Antes el
  perímetro del bloque solo lo cubría el canto de la primera y la última
  fotografía, y quedaban huecos.
- Nuevo patrón `Cruz oblicua 45°`: dos pasadas sobre las diagonales del bloque.

### Formatos de entrega

| Formato | Para qué |
|---------|----------|
| `mission.json` | Formato interno completo |
| `.geojson` | RFC 7946. QGIS, Pix4D, Agisoft, portales cartográficos |
| `.kml` | Revisión sobre el globo. Google Earth, DJI FlightHub, GIS de escritorio |
| KMZ/WPML | Subida al aircraft |
| Ficha de vuelo (JSON) | Documento que acompaña al producto entregado |

El GeoJSON lleva la huella, una `LineString` por línea de vuelo, un punto por
punto de control y las propiedades que el software receptor no puede deducir.
La ficha de vuelo declara `"status": "planned"` y una lista de limitaciones que
dice, entre otras cosas, que el bloque no se ha volado y que la exactitud del
producto la limita la de su control.

### Revisión en el mapa

Huella, líneas de vuelo y control sobre un mapa MapLibre del UX SDK, y
colocación de un punto de control donde está realmente pintado en vez de
escribirlo de memoria. Es una vista de apoyo: no mueve el aircraft y no verifica
nada, y lo dice en pantalla.

## Correcciones

- El planificador ya no fija la acción de fin a "regreso a casa": hay selector.
- Las recetas algorítmicas se cargan de `assets/algorithms/` en vez de estar
  duplicadas en el código, así que se pueden corregir o ampliar sin recompilar.
  Nueva receta `cartography.cross.v1.json`.
- El registro de auditoría rota por tamaño (2 MiB) en `audit.previous.jsonl`,
  siempre en un límite de línea, en vez de crecer sin límite.
- Corregida una división por 100 en el cálculo de separaciones a partir del
  solape, que producía separaciones casi iguales a la huella de la foto.
- Corregido el conteo de líneas de vuelo, que podía partir una línea en dos
  cuando su posición cruzaba exactamente un límite de la rejilla de agrupación.
- `mission.json` conserva el perfil cartográfico y los puntos de control.
- `scripts/build-release.sh` construye también el APK debug, en la misma pasada
  y con el mismo entorno, en vez de copiar el que hubiera de una build anterior.

## Verificaciones

- 292 pruebas unitarias, 0 fallos.
- `lintDebug`: 0 errores.
- `com.dji.sdk.API_KEY` presente en el manifiesto de los tres binarios.
- `apksigner verify`: correcto, APK Signature Scheme v2.
- `jarsigner -verify` sobre el AAB: código 0.
- `sha256sum -c` sobre los binarios recién construidos, dentro del script.
- El asset `validated_wpml_profiles.json` va empaquetado y vacío, así que la
  ejecución automática está bloqueada.
- La App Key no aparece en ningún fichero versionado: vive en
  `.local/api-key.properties`, que está en `.gitignore`.

## Barrera de ejecución WPML

Sin cambios. La subida y la ejecución automática siguen **bloqueadas**: hacen
falta conexión, registro, firmware versionado, modelo de control remoto, firmware
del control remoto y un perfil de validación física con coincidencia exacta. La
allowlist se publica vacía y el build falla si el asset contiene marcadores sin
rellenar. Ver `docs/COMPATIBILITY.md` y `docs/SAFETY.md`.

## Binarios

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0-alpha.2.apk` | 205,002,230 bytes | `9d33fb2b78f12afef345a321fcaff9eaf8346b1fcb7a70c3bf657e29e0cdf19f` |
| `DJIFlyPro-1.1.0-alpha.2.aab` | 198,917,609 bytes | `469872bc57db2514795fe742623be14199762ea3fb2fdf365d9189e0bc86f3d7` |
| `DJIFlyPro-1.1.0-alpha.2-debug.apk` | 238,562,076 bytes | `60f6fcd6f18e050f152fc741d401578ccb48af5bfa96724e2fc32a9117231643` |

Los nombres llevan la `versionName` que declara el propio build. Verifica con:

```bash
sha256sum -c SHA256SUMS.txt
```

## Documentación

- `docs/CARTOGRAFIA.md`: el modelo cartográfico completo, las matemáticas y las
  decisiones que tiene que tomar el operador.
- `docs/SAFETY.md`: límites y lo que la aplicación no comprueba.
- `docs/COMPATIBILITY.md`: aircraft, firmware y control remoto.
- `docs/BUILD.md`: compilación, firma y publicación.
- `docs/PRUEBAS.md`: protocolo de pruebas en dispositivo.
- `docs/ALGORITHMS.md`: recetas declarativas.
