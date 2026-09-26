# Registro de hardware pendiente

Este fichero debe completarse antes de escribir cualquier código específico de dispositivo en la variante Legacy.

No se debe adivinar ningún valor. La variante exacta de Phantom 4 determina la API de MSDK V4 disponible y qué telemetría se puede leer.

## Aircraft

| Campo | Valor |
|---|---|
| Variante | P4 / P4P / P4A / P4R / (otra: indicar) |
| Nombre comercial exacto | |
| Versión de firmware | |
| Estado de la prueba física | no realizada |

## Control remoto

| Campo | Valor |
|---|---|
| Modelo exacto | |
| Versión de firmware | |
| Estado de la prueba física | no realizada |

## Verificaciones pendientes

- [ ] Identificación del modelo y del control remoto leída del hardware, no deducida.
- [ ] Versiones de firmware anotadas tal como las reporta el SDK.
- [ ] Conexión, telemetría, cámara, takeoff y RTH probados en zona segura.
- [ ] Upload y ejecución de misión probados con tamaño pequeño y observador.
- [ ] Registro de prueba con referencia citable.

## Nota

Un firmware identificado no es una certificación de compatibilidad ni de seguridad. La referencia de este registro es lo que exigirá la allowlist de la variante Legacy, que será independiente de la de V5.
