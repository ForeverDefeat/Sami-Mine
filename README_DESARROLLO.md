# Desarrollo de SamiMine

Documentación revisada el **21/10/2026**. Aplicación Android de piloto con seis tareas cognitivas, acceso autenticado por roles y sincronización Firebase. Usar datos ficticios; DSST y 1-back son adaptaciones experimentales y no hay clasificación de aptitud laboral.

## Documentación mantenida

- [Arquitectura actual](docs/ARQUITECTURA_ACTUAL.md): componentes, acceso, almacenamiento y entrega.
- [Guía del código](docs/GUIA_DEL_CODIGO.md): entradas y contratos de mantenimiento.
- [Batería implementada](docs/BATERIA_IMPLEMENTACION.md): protocolos, métricas y cuestionarios deshabilitados.
- [Piloto Firebase](docs/FIREBASE_PILOTO.md): roles, emuladores y configuración remota.

Las propuestas, investigación, referencias de diseño y capturas locales no forman parte de la documentación publicada.

## Preparación

1. Instalar JDK 25 y Android SDK con plataforma 37. Configurar JAVA_HOME y la ruta sdk.dir en local.properties, sin versionar rutas personales.
2. Obtener la configuración Android autorizada para `dev.studios.samimine` y colocarla en `app/google-services.json`. Ese archivo se entrega por un canal privado y está excluido de Git. No se incluyen claves administrativas ni cuentas/contraseñas de piloto.
3. Instalar Node.js >=24.12 para satisfacer los requisitos declarados de las dependencias Firebase JS. Ejecutar `npm ci`; package-lock.json fija las herramientas del proyecto.
4. Usar el Gradle Wrapper del repositorio. El primer build requiere descargar dependencias; añadir `--offline` solo cuando estén disponibles en caché.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
npm run test:rules
```

Las reglas se prueban únicamente con el proyecto ficticio `demo-samimine` y emuladores Auth/Firestore. No hace falta desplegar a la nube para esta comprobación.

## Ejecutar y probar Android

La APK normal requiere una cuenta Auth y un perfil activo. El primer ingreso necesita conexión; solo el trabajador previamente verificado puede continuar offline. El turno se elige expresamente. Bienestar y administración requieren servidor. Los cuestionarios KSS, Borg y Chalder permanecen deshabilitados mientras falte contenido autorizado.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Para instalarla en un dispositivo o emulador autorizado:

```powershell
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

El runner instrumentado activa la composición local de regresión de medición y excluye el flujo autenticado por defecto. Para aceptación de roles y entregas usar el procedimiento con emuladores Firebase y argumento `pilotAuth=true` de la [guía del piloto](docs/FIREBASE_PILOTO.md). Los tests de flujo pueden agregar registros ficticios a la base de demostración; no usarlos sobre datos reales.

Reportes locales: `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/androidTests/connected` y `app/build/reports/lint-results-debug.html`. No se suben compilaciones, reportes ni capturas a GitHub.

## Reglas de mantenimiento

- Mantener el paquete `dev.studios.samimine` en fuentes, tests, runner y esquemas Room.
- Todo cambio de persistencia requiere migración no destructiva y esquemas exportados. La versión actual es 5.
- Preservar versiones históricas de protocolos y cálculos; un cambio de parámetros requiere versión explícita.
- Revisar aislamiento por UID, transacciones y entrega idempotente cuando cambien repositorios o sincronización.
- Antes de publicar, revisar el diff preparado y comprobar que no incluya credenciales, configuración privada, material de contexto ni imágenes de referencia. Los recursos gráficos utilizados por Android sí se versionan.
- Los comandos de despliegue y precarga son operaciones explícitas sobre la nube; no forman parte de la compilación ni de los tests locales.

## Verificación del 21/10/2026

- Compilación debug y Android Lint: correctos. Lint informa 0 errores y 27 advertencias, incluidas recomendaciones de dependencias, recursos e implementación.
- Pruebas unitarias: **63 aprobadas, 0 fallos y 0 errores**, ejecutadas nuevamente con `--rerun-tasks`.
- `npm run test:rules`: **9 aprobadas**, contra emuladores Auth/Firestore de `demo-samimine` y con la versión local fijada de Firebase CLI.
- Regresión instrumentada local: **60 aprobadas, 0 fallos y 0 omisiones**, en Medium_Phone, Android 17/API 37; incluye persistencia, seis tareas, gestos, navegación y seis configuraciones visuales.
- Aceptación autenticada por ADB: **4 aprobadas**, con Firebase local; verifica credenciales/perfiles, aislamiento por cuenta, recuperación offline, entrega y acceso por rol. Las tres pruebas específicas de persistencia del piloto también pasaron por Gradle.
- Enlaces Markdown y diff preparados: comprobados; material de diseño y configuración sensible excluidos.

Entorno usado: JDK 25, Android SDK 37 y Node.js 24.11.1 disponible en la máquina. Las pruebas de reglas pasaron en ese entorno; para instalaciones nuevas se mantiene Node >=24.12 por el requisito declarado de las dependencias.

No se realizaron despliegues ni cambios en Firebase real. Las pruebas de software no validan precisión física, accesibilidad en teléfonos reales ni uso clínico.
