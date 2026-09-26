# Recetas algorítmicas

Una receta es un documento JSON con el esquema:

```json
{
  "schema": "djiflypro.algorithm/v1",
  "id": "example.v1",
  "name": "Ejemplo",
  "jobType": "field",
  "description": "Descripción auditable",
  "template": "FIELD",
  "routePattern": "PARALLEL",
  "defaults": {
    "lengthMeters": 100,
    "widthMeters": 60,
    "heightMeters": 10,
    "bearingDegrees": 0,
    "altitudeMeters": 40,
    "standoffMeters": 10,
    "lineSpacingMeters": 12,
    "photoSpacingMeters": 8,
    "overlapPercent": 70,
    "speedMps": 4,
    "gimbalPitchDegrees": -90
  },
  "outputs": ["KMZ", "mission.json", "audit.jsonl"]
}
```

La importación acepta un máximo de 1 MiB, valida el esquema y los rangos, calcula un SHA-256 y registra el evento. `overlapPercent` se conserva como parámetro auditable; la separación física se introduce explícitamente porque depende del tamaño de sensor, focal, altura y modelo de cámara. No se ejecutan scripts, bytecode, dex, AAR ni Kotlin descargados. Esto evita que una receta importada pueda ejecutar código arbitrario dentro del proceso de la aplicación.

Para añadir una receta built-in, edita `AlgorithmRepository.builtIns()` y añade un archivo de ejemplo en `algorithms/`. Para un algoritmo que necesite computer vision, LiDAR o un modelo de IA, la integración recomendada es un servicio externo firmado/autenticado que devuelva parámetros de misión validados; no un archivo ejecutable dentro del APK.
