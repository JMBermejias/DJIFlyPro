# Variante Legacy (Phantom 4 / MSDK V4)

Esta carpeta está reservada para una variante de aplicación independiente. No se incluyen aquí las clases de MSDK V4 ni se las mezclan con `android-sdk-v5-sample`.

## Motivo

Phantom 4 y sus controladores se gestionan con la generación MSDK V4. La aplicación V5 usa MSDK V5.18.0; las dos generaciones tienen contratos, bibliotecas nativas y procesos de registro distintos. Una APK que mezcle ambos SDK no es una configuración soportada.

## Estado: no construible todavía

Ejecuta `./preflight.sh` para ver el estado exacto. Actualmente falla en:

1. **SDK MSDK V4 ausente.** DJI lo distribuye como archivo comprimido a cuentas de desarrollador registradas; no está en un repositorio Maven público, así que no se puede resolver automáticamente. Extráelo en `legacy/libs`.
2. **Módulo Gradle no registrado.** Debe ser un módulo aparte con `applicationId` distinto, por ejemplo `com.djiflypro.legacy`.
3. **Variante de Phantom 4 sin identificar.** Anótala en `legacy/hardware.md` antes de escribir código específico de dispositivo.

## Pasos para entregarla

1. Obtener el SDK MSDK V4.18 y sus AAR/JAR oficiales; extraerlos en `legacy/libs`.
2. Confirmar la variante exacta de Phantom 4 (P4, P4P, P4A o P4R) y el control remoto en `legacy/hardware.md`.
3. Crear un módulo Gradle separado con `applicationId` `com.djiflypro.legacy` y registrarlo en `settings.gradle`.
4. Registrar esa aplicación por separado en DJI Developer. Una clave V4 no es intercambiable con la clave V5 de `com.djiflypro.app`.
5. Adaptar telemetría, cámara, control manual y waypoint solo después de probarlos con hardware y firmware reales.
6. Repetir `./preflight.sh` hasta que devuelva 0.

## Lo que esta variante no hereda

La barrera de ejecución WPML de la variante V5 (`DroneCapabilities` más la allowlist `validated_wpml_profiles.json`) es específica de MSDK V5. La variante Legacy necesita su propia barrera, con su propia allowlist y sus propias pruebas físicas. No se debe copiar la allowlist de V5 ni asumir que un perfil validado en V5 vale para V4.

La app V5 bloquea la ejecución waypoint para un producto que no declare capacidad compatible; no intenta enviar un WPML a un Phantom 4 mediante una API que no pertenece a su SDK.
