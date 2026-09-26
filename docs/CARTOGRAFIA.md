# Cartografía

DJIFlyPro está orientado a cartografía: el objetivo es un producto cartográfico
(orto, DSM, nubes de puntos, cartografía de inspección), no un vuelo.

Este documento explica qué hace la aplicación, qué matemáticas hay detrás y
qué decisiones tiene que tomar la persona operador.

## El principio: la resolución manda, la altura se deduce

En un vuelo de inspección se diseña una ruta a una altura cómoda. En cartografía
no: el encargo llega con una resolución o una escala, y la altura de vuelo es una
consecuencia.

Para una cámara en nadir:

```
GSD = H · sensor / (focal · anchoPx)
W   = H · sensor / focal = anchoPx · GSD
separación fotos   = W · (1 − solape longitudinal)
separación líneas  = W · (1 − solape transversal)
```

donde `H` es la altura sobre el terreno, `W` la huella de la foto en el suelo y
el GSD se expresa en centímetros por píxel.

La aplicación trabaja en esa dirección. En **Cartografía** se introducen la
cámara, la resolución objetivo y los dos solapes, y se obtienen la altura, las
dos separaciones, el número de líneas y el número de fotos. Esos valores se
aplican al planificador, que construye la ruta con ellos. La ruta y la ficha de
vuelo no pueden discrepar porque la ficha se calcula de la ruta.

`GroundSampleDistance` y `CartographySolution` implementan estas relaciones sin
ninguna dependencia de Android ni del SDK de DJI, y están cubiertas por pruebas
que las contrastan con los valores publicados de los payloads reales.

## Cámaras

`SurveyCamera` describe un payload por sus parámetros físicos: sensor,
longitud focal y resolución de salida. GSD se deriva de ellos, no de una tabla
de "cámaras DJI", así que una cámara que la aplicación no conoce se puede
describir y el resultado es un número que el operador puede ver y corregir.

| id | Sensor | Objetivo | Salida | 2 cm/px a |
|----|--------|----------|--------|-----------|
| `zenmuse-p1` | 35,9 × 24,0 mm | 35 mm | 8192 × 5460 | 160 m |
| `zenmuse-h20t` | 18,4 × 12,3 mm | 11 mm | 5280 × 3956 | 63 m |
| `zenmuse-l2` | 17,4 × 13,1 mm | 7 mm | 4000 × 3000 | 32 m |
| `matrice-4e` | 17,3 × 13,0 mm | 10,5 mm | 5280 × 3956 | 64 m |
| `mavic-3e` | 17,3 × 13,0 mm | 12 mm | 5280 × 3956 | 73 m |
| `phantom-4-pro` | 13,2 × 8,8 mm | 10 mm | 5472 × 3648 | 83 m |
| `iphone-15-pro` | 9,52 × 6,35 mm | 6,86 mm | 8064 × 6048 | 116 m |

Una cámara con objetivo más corto cubre más suelo por metro de altura, así que
alcanza la misma resolución más bajo. Por eso el P1, con 35 mm, necesita 160 m
para 2 cm/px y el Mavic 3E, con 12 mm, sólo 73 m.

Las cifras son las publicadas por el fabricante para el payload. Conviene
contrastarlas con el payload que se tiene delante: `SurveyCamera.custom`
permite entrar sensor, objetivo y resolución reales.

## Techo de altura: por qué 2 cm/px no se puede automatizar

Un P1 a 2 cm/px vuela a 160 m. El techo por defecto de una misión waypoint
automática de DJI es 120 m. La aplicación no lo oculta ni lo sube:

- `MissionValidator.MAX_ALTITUDE_METERS = 120` es un **error duro** para
  cualquier subida o inicio automático. Se comprueba otra vez justo antes de
  subir y otra vez justo antes de iniciar.
- `MissionValidator.MAX_GUIDED_ALTITUDE_METERS = 500` es el techo de
  **planificación**. Un bloque por encima de 120 m se puede planear, guardar,
  exportar y volar con la guía manual, donde la persona está al mando.

`CartographySolutionResult.isFlyableAutomatically` dice en cuál de los dos casos
está la solución, y la aplicación lo muestra.

## La ruta: por qué las líneas se reparten y se alargan

`MissionGeometry` construye las pasadas de forma que el bloque quede cubierto de
verdad:

- **Reparto exacto.** El número de líneas es `ceil(ancho / separación)` y luego
  se reparten uniformemente sobre el ancho. La separación real nunca es menor
  que la pedida y la última línea cae justo en el borde, sin una franja
  descubierta.
- **Margen de borde.** Cada línea se alarga una separación de foto a cada
  extremo. Sin ese margen el borde del bloque solo lo cubre el canto de la
  primera y la última fotografía, y aparecen huecos perimetrales.
- **Doble pasada y cruz.** `Malla doble` vuela el bloque en dos direcciones
  perpendiculares. `Cruz oblicua 45°` lo vuela sobre sus diagonales: cuesta el
  doble de fotos y da dos geometrías de vista, lo que ayuda con sombras y con
  bordes de fachada.

## Solape y redundancia

El solape se pide por separado en longitudinal y transversal, que es como se
mide. `CartographicSummary` calcula la **redundancia** real: cuántas veces se
fotografía el suelo de media. Con 80 % longitudinal y 70 % transversal el suelo
se fotografía unas 16,7 veces; ese es el coste de la calidad que se ha pedido, y
tiene que aparecer en la ficha.

`overlapPercent` sigue existiendo como campo declarado porque es lo que se
registra, pero la derivación de las separaciones usa los dos solapes del perfil.

## Referencia de altura

Un GSD sólo es cierto sobre un terreno de altura conocida, así que el dato de
altura tiene que declararse:

- `Sobre el terreno (AGL)`: es lo que hace que el GSD planificado se cumpla.
- `Nivel del mar (AMSL)`: sobre el elipsoide. Necesita un modelo de geoide para
  convertirse en altura real.
- `Desde el despegue`: sobre el punto de despegue. En terreno con relieve la
  resolución efectiva se desvía.

La referencia viaja en `mission.json`, en el GeoJSON, en el KML y en la ficha de
vuelo. En el KML se traduce al `altitudeMode` que lo representa.

## Sistemas de referencia

`CoordinateReferenceSystem` convierte WGS84 geográfico a UTM y de vuelta con el
desarrollo en serie de Snyder (*Map Projections - A Working Manual*, NOAA/NCES
165). El residuo está muy por debajo de la resolución a la que vuela el mismo
plan, que es la precisión que importa aquí.

- `toUtm` / `fromUtm` con las ensanchaciones de Noruega y Svalbard.
- `zoneFor` devuelve la zona y el hemisferio, con el código EPSG.
- `blockPolygon` da el polígono de la parcela; `localAreaSquareMeters` da la
  superficie en metros cuadrados por el teorema de la cuerda, y de ahí las
  hectáreas.
- `clusterByTolerance` agrupa escalares con tolerancia. Existe porque
  redondear sobre una rejilla parte una línea de vuelo en dos cuando cae
  exactamente en un borde de celda, que es un fallo silencioso de conteo.

La ficha de vuelo y el GeoJSON dan **las dos** representaciones: `EPSG:4326` y
el EPSG de UTM con este y norte del centro del bloque.

## Puntos de control

Un bloque sin control no se puede georreferenciar. `GroundControlPoint` guarda
además de la coordenada la **exactitud horizontal medida** y el **origen** de la
coordenada, porque el producto final no puede ser más exacto que su control, y
eso tiene que poder demostrarse.

`ControlNetwork.assess` comprueba lo que decide si un bloque se puede
georreferenciar:

- Al menos 4 puntos de control y al menos 1 de verificación. Sin verificación
  el bloque se ajusta pero no se valida.
- Reparto: el control tiene que llegar a los bordes. Se mide la cobertura del
  radio del bloque, no la dispersión interna.
- Colinealidad: cuatro puntos en una línea no pueden resolver la rotación y
  dejan el bloque inclinado.
- Separación mínima entre puntos.
- Exactitud: avisa cuando el peor control es peor que 50 mm.
- Aviso cuando ningún punto tiene altura: el bloque sólo se puede georreferenciar
  en 2D.

`ControlPointTarget` distingue tipos de diana con su exactitud típica. Una
elemento del terreno no es válido como control, sólo como verificación.

## Entregas

| Formato | Para qué |
|---------|----------|
| `mission.json` | Formato interno completo, incluida la geometría de la ruta |
| `.geojson` | RFC 7946. QGIS, Pix4D, Agisoft, portales cartográficos |
| `.kml` | Revisión sobre el globo. Google Earth, DJI FlightHub, GIS de escritorio |
| KMZ/WPML | Subida al aircraft |
| ficha de vuelo (JSON) | El documento que acompaña al producto entregado |
| informe de validación | Evidencia de la identidad de aircraft y RC para el allowlist |

El GeoJSON lleva la huella, una `LineString` por línea de vuelo, un punto por
punto de control y un punto de inicio, más las propiedades cartográficas que el
software receptor no puede deducir: GSD, solapes, redundancia, líneas, altura y
su referencia, y el EPSG de UTM.

La ficha de vuelo declara explícitamente `"status": "planned"` y una lista de
`limitations` que dice, entre otras cosas, que el bloque no se ha volado y que
laExactitud del producto la limita su control.

## Revisión en el mapa

`MissionMapActivity` pinta la huella, las líneas y el control sobre un mapa
MapLibre del UX SDK. Es una vista de apoyo: no mueve el aircraft, no verifica
nada y lo dice en pantalla. Sirve para que la persona operador y el cliente
vean el mismo bloque en un globo antes de volar, y para colocar un punto de
control donde realmente está pintado en vez de escribirlo de memoria.

## Lo que la aplicación no hace

- No comprueba espacio aéreo, obstáculos, líneas eléctricas, permisos ni
  propiedad ajena.
- No verifica el modelo ni el firmware: eso lo hace la barrera de ejecución de
  WPML, y viene bloqueada en esta entrega.
- No genera el orto, el DSM ni la nube de puntos. Genera la planificación y la
  documentación que hace falta para generarlos.
- No sustituye a un sistema de seguridad ni a la judgement de la persona
  operador.
