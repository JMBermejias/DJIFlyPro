# Configuración local de firma

No incluyas una clave privada de producción en el proyecto.

Para una compilación de prueba, genera una keystore fuera de Git y crea `.local/signing.properties` con:

```properties
STORE_FILE=/ruta/absoluta/a/djiflypro-test.jks
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

Para distribución, usa una clave propia y entrega la AAB por un canal seguro. La clave de prueba no permite publicar una aplicación estable en Google Play.
