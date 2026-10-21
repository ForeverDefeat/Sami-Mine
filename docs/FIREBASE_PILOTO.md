# Piloto Firebase de SamiMine

Proyecto autorizado: `samimine-dev`, plan Spark, Android `dev.studios.samimine`.
Documentación revisada el **21/10/2026**; véase la [arquitectura actual](ARQUITECTURA_ACTUAL.md) y la [guía de desarrollo](../README_DESARROLLO.md). Las comprobaciones de nube descritas abajo son antecedentes; no se repiten al actualizar estos documentos.

SDK ya verificado: Google Services 4.5.0, Firebase BoM 34.19.0 y Kotlin 2.2.
La base remota configurada es Firestore Standard `(default)`, `southamerica-east1`.

Actualización del 21/10/2026: sesión administrativa conectada; base `(default)` creada y confirmada como Standard, región `southamerica-east1`, `freeTier=true`. Despliegue de Authentication y Firestore completado; cinco cuentas ficticias precargadas. Las contraseñas de desarrollo están únicamente en `.local/pilot-accounts-*.json`, excluido de Git. Comprobación de nube completada: los cinco ingresos, las consultas de bienestar y administración y las denegaciones entre cuentas pasaron, incluidos los índices de seguimiento. La comprobación fue de solo lectura y no creó evaluaciones; queda el recorrido manual con la APK normal contra el proyecto real.

## Comportamiento

La aplicación inicia en correo y contraseña, sin registro público. Firebase Auth conserva la sesión; el perfil de `users/{uid}` decide el acceso. Tener una cuenta Auth sin perfil activo no habilita la app.

| Rol | Funciones |
| --- | --- |
| WORKER | Evaluaciones propias, historial Room privado y cola de entrega. |
| WELLBEING | Trabajadores activos asignados, resultados completos y respuestas, solo con servidor disponible. |
| ADMIN | Perfiles operativos de trabajadores, activación, asignación a bienestar y seguimiento de entregas. No recibe resultados ni respuestas. |

El administrador no crea cuentas Auth ni modifica roles desde el APK; tampoco puede desactivarse. Las cuentas de bienestar y administración se gestionan mediante consola o herramientas administrativas. Desactivar un perfil bloquea operaciones protegidas por reglas; desactivar únicamente Auth no garantiza revocar inmediatamente un ID token emitido previamente.

El trabajador puede continuar offline después de verificar su perfil online. La copia local de perfil solo se restaura si coincide el UID y es un trabajador activo. Esta autorización del piloto no tiene vencimiento propio: la revocación requiere recuperar conexión. Bienestar y administración no tienen consulta offline. No se trata de una política de autorización de producción.

Cada UID usa una base Room con nombre derivado de SHA-256. No se vincula ni importa `samimine-demo.db`. Cerrar sesión elimina la copia de perfil y las pantallas activas, y pausa los trabajos; conserva resultados pendientes en la base privada de su propietario. Solo esa misma cuenta puede volver a enviarlos.

## Entrega

Room v5 agrega `sync_queue`, con estados PENDING, SENDING, SYNCED y ATTENTION. Las escrituras terminales y el alta en cola comparten transacción. La migración 4→5 conserva registros y agrega entradas para registros cerrados. La recuperación cierra ejecuciones abandonadas y las encola. IGNORE evita reiniciar entregas confirmadas.

Firestore almacena documentos bajo `users/{uid}`:

- `results/{kind_id}`: resumen sensible, versión, estado, cantidad de fragmentos y marca `complete`.
- `results/{kind_id}/chunks/{index}`: ensayos o respuestas, divididos en fragmentos acotados e inmutables.
- `tracking/{kind_id}`: únicamente propietario, organización, tipo genérico, estado y fechas. Es la única colección de ejecuciones consultable por ADMIN.

WorkManager espera red, valida cuenta y perfil, carga fragmentos en lotes y confirma `complete` y seguimiento en una transacción final. Solo después de una lectura de servidor se marca SYNCED en Room. Si se interrumpe la entrega, reanuda con las mismas claves y contenido. Los pendientes nunca se borran por cuota o problemas de red; el botón Reintentar envíos vuelve a programar el trabajo. Las lecturas de bienestar filtran `complete == true`.

No se recalculan métricas en servidor. Los datos siguen siendo autodeclarados desde el cliente: las reglas protegen propiedad, permisos, campos, tamaños e inmutabilidad; no prueban que la prueba se haya ejecutado físicamente ni validan resultados clínicos.

## Preparación local y pruebas

Requisitos: JDK 25 configurado, SDK Android y Node.js compatible con las versiones fijadas en `package-lock.json` (los paquetes Firebase JS 13 / rules-unit-testing 6 declaran Node >=24.12). Instalar herramientas con `npm ci`.

Las pruebas de reglas levantan un proyecto ficticio; no escriben en Firebase real:

```powershell
npm run test:rules
```

Para probar Android contra Firebase local, mantener estos emuladores abiertos:

```powershell
npx --no-install firebase emulators:start --project demo-samimine --only auth,firestore
```

En otra terminal:

```powershell
npm run seed:emulator
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:9099 tcp:9099
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am instrument -w -e pilotAuth true -e class 'dev.studios.samimine.data.PilotPersistenceTest,dev.studios.samimine.PilotFirebaseFlowTest' dev.studios.samimine.test/dev.studios.samimine.SamiMineTestRunner
```

La invocación explícita por ADB garantiza que el runner reciba pilotAuth=true. En la revisión del 21/10/2026, la invocación Gradle con propiedades android.testInstrumentationRunnerArguments ejecutó solo persistencia y excluyó PilotFirebaseFlowTest, incluso sin caché de configuración. Comprobar siempre el número y las clases realmente ejecutadas; BUILD SUCCESSFUL por sí solo no acredita esa aceptación.

El runner de aceptación usa una instancia Firebase exclusiva con proyecto `demo-samimine` y endpoints `127.0.0.1:9099/8080` mediante túneles `adb reverse` para el emulador Android. La app normal no expone un interruptor para emuladores ni perfiles ficticios. La regresión de medición usa un runner con composición local de pruebas, sin saltarse el login en producción.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Configurar el proyecto real de desarrollo

1. Iniciar sesión administrativa con el plugin Firebase o `npx --no-install firebase login`. Verificar la cuenta y el proyecto `samimine-dev`; no habilitar facturación.
2. Comprobar que no existe otra base. Crear `(default)` Standard en `southamerica-east1`, con reglas cerradas, desde consola o CLI autenticada. No crear una segunda base ni sustituir una existente sin revisar su configuración.
3. Habilitar proveedor correo/contraseña en Authentication. Publicar reglas e índices antes de precargar perfiles. Esperar que los índices estén listos.
4. Ejecutar `npm run deploy:rules` y verificar que el destino sea `samimine-dev`.
5. Para el script Admin SDK, configurar Application Default Credentials con una identidad autorizada, o `GOOGLE_APPLICATION_CREDENTIALS` apuntando a un archivo administrativo fuera del repositorio. El login del CLI por sí solo no configura necesariamente ADC.
6. Ejecutar `npm run seed:dev`. El script se limita a `samimine-dev`, no sobrescribe cuentas existentes ni reactiva perfiles al repetirlo. Las nuevas contraseñas aleatorias se escriben únicamente en `.local/pilot-accounts-*.json`, excluido de Git; nunca se imprimen.
7. Instalar la APK normal, ingresar como trabajador A, guardar y sincronizar una evaluación; comprobar su consulta en bienestar y seguimiento en administración.

Como alternativa para el entorno de desarrollo, `node scripts/firebase/seed-cli.mjs --project samimine-dev` reutiliza la cuenta explícitamente conectada en Firebase CLI mediante ADC temporal en el directorio temporal del sistema. El archivo administrativo se elimina al terminar; no se crea una clave de cuenta de servicio ni se escribe en el repositorio. Este helper depende de la versión de firebase-tools fijada en `package-lock.json`.

La comprobación de nube de solo lectura se ejecuta con `node scripts/firebase/check-dev.mjs <archivo-local-de-cuentas>`. Verifica inicio de sesión, perfiles, consultas por rol y denegaciones; no crea evaluaciones ni imprime contraseñas. Es complementaria a las pruebas Android con emuladores y al recorrido manual sobre la app normal.

Cuentas previstas: `pilot-worker-a`, `pilot-worker-b`, `pilot-worker-c`, `pilot-wellbeing` y `pilot-admin`, con correo `<uid>@samimine.example`. A y B se asignan a bienestar; C queda sin asignar. Son direcciones ficticias, por lo que recuperación por correo no es un flujo de prueba disponible. No usar con personas reales.

## Revisión de seguridad

Las reglas del piloto restringen escrituras al propietario, lecturas de bienestar a trabajadores asignados y administración a gestión operativa y seguimiento. Los registros entregados son inmutables. Estas garantías de acceso no validan la ejecución física ni los resultados clínicos de las pruebas.

Las pruebas incluyen accesos permitidos y denegados, límites de consulta, elevación de privilegios, aislamiento entre organizaciones, desactivación, retiro de asignación y campos inesperados. Antes de un piloto con datos reales se requieren revisión de reglas, política de conservación, protección del dispositivo y aprobación de finalidad y protocolos.
