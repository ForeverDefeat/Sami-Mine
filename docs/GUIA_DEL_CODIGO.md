# Guía del código de SamiMine

Revisión: **21/10/2026**. Complementa la [arquitectura actual](ARQUITECTURA_ACTUAL.md). Las rutas del mapa parten de `app/src/main/java/dev/studios/samimine/`.

## Mapa para mantenimiento

| Archivo o carpeta | Responsabilidad |
|---|---|
| `MainActivity.kt` | Instala AccessRoot y transmite visibilidad y pérdida de foco al modelo del trabajador; conserva una rama local para regresión instrumentada |
| `app/SamiMineApplication.kt`, `app/di/AppContainer.kt` | Contenedor durante el proceso; Firebase, caché de perfil, Room por UID, base de demostración y migraciones 1→5 |
| `app/AccessViewModel.kt` | Login/restauración, vigilancia de permisos, logout, consultas por rol y gestión de trabajadores |
| `ui/screens/access/AccessScreens.kt` | Login, WorkerExperience y pantallas de bienestar/administración; crea y libera el ViewModel del UID |
| `app/SamiMineApp.kt`, `app/navigation/` | Destinos, pestañas y callbacks de la experiencia del trabajador; fija su identidad autenticada |
| `app/SamiMineViewModel.kt` | Estado, turno, ejecución, temporizadores, guardados/reintentos, historial y recuperación |
| `application/usecase/EvaluationUseCases.kt` | Casos de uso PVT; el resto utiliza contratos de repositorio directamente |
| `domain/model/Evaluation.kt`, `CognitiveTest.kt` | Configuraciones/versiones, semillas, registros y métricas PVT/cognitivas |
| `domain/model/Battery.kt`, `UserProfile.kt` | Métricas descriptivas, catálogo, instrumentos, autorreportes, recorrido breve, roles y sesión |
| `domain/evaluation/pvt/`, `domain/evaluation/cognitive/` | Motores PVT, ensayo cognitivo y plazo global DSST (TimedAssociationEngine) |
| `domain/repository/` | EvaluationRepository, CognitiveTestRepository, BatteryRepository y contratos AuthRepository, UserRepository y SyncRepository |
| `core/time/AndroidMonotonicClock.kt` | Reloj monotónico Android para PVT |
| `data/repository/RoomEvaluationRepository.kt` | Persistencia de evaluaciones, cognitivas y batería; transacciones, idempotencia, métricas y cola |
| `data/local/database/SamiMineDatabase.kt` | Esquema 5, migraciones no destructivas 1→2→3→4→5 |
| `data/local/dao/`, `data/local/entity/` | Tablas, relaciones, mapeadores, consultas y estados locales/de entrega |
| `data/remote/FirebaseAccess.kt` | Adaptadores Auth/perfiles, caché del trabajador y entorno del runner |
| `data/remote/FirebaseSyncRepository.kt` | Payloads, fragmentación, confirmación remota, reintentos y ResultSyncWorker |
| `data/demo/DemoWorkers.kt` | Perfiles ficticios de la composición local de tests |
| `ui/screens/home/`, `history/`, `cognitive/` | Inicio/catálogos, historial, seis tareas cognitivas y cuestionarios sujetos a habilitación |
| `ui/screens/pvt/`, `instructions/`, `result/`, `shift/` | Recorrido PVT y selección explícita del turno |
| `ui/components/`, `ui/theme/` | Primer contacto, semántica, estado de envío, componentes, tokens e iconos |

## Cómo seguir una operación

**Ingreso:** AccessViewModel consulta Auth y el perfil activo en servidor. Solo un trabajador previamente verificado puede restaurarse desde WorkerSessionCache sin red. AccessRoot selecciona experiencia según rol; WorkerExperience compone repositorios con database(uid). Los resultados y pendientes se mantienen por cuenta al cerrar sesión.

**Medición:** SamiMineViewModel prepara IDs, configuración y semilla antes de la operación reintentable. La UI informa el dibujo y entrega contacto con sesión/índice. Cada intento o ensayo se persiste antes de avanzar; la práctica no puntúa. DSST usa un plazo global y 1-back mantiene su ventana completa aunque reciba respuesta. La configuración concreta está en la [guía de batería](BATERIA_IMPLEMENTACION.md).

**Cierre e historial:** RoomEvaluationRepository valida completitud, guarda métricas y cierre junto con la entrada sync_queue. Las consultas locales verifican propietario. Las nuevas métricas cognitivas leen snapshots; los registros legacy mantienen su interpretación histórica. Las operaciones pendientes en memoria no se reproducen tras muerte del proceso: se recuperan los registros persistidos y se cierran ejecuciones abandonadas.

**Entrega:** WorkerExperience observa pendientes y programa WorkManager. ResultSyncWorker comprueba la cuenta; FirebaseSyncRepository valida perfil, serializa el registro terminal y envía documentos con claves estables. Confirma complete y tracking en transacción; lee servidor antes de marcar SYNCED local. Las consultas de bienestar exigen complete; ADMIN consulta tracking y no recibe respuestas. Los detalles de Firebase y aceptación están en la [guía del piloto](FIREBASE_PILOTO.md).

## Contratos al editar

- No mezclar epoch, uptime en milisegundos y elapsed realtime en nanosegundos. Una recomposición no debe reiniciar el RT ni el plazo global.
- Conservar ContactGate entre ensayos y validar ID/índice de callbacks; los motores no admiten acceso concurrente arbitrario.
- Persistir antes de avanzar; reintentar con el mismo ID y datos. Guardar cierre, métricas y cola en la misma transacción.
- Excluir prácticas de métricas; representar tiempos ausentes con null y distinguir inhibición NO-GO de omisión GO.
- Versionar cambios de protocolo/cálculo y conservar lectura histórica; exportar esquemas al modificar Room, sin migraciones destructivas.
- Obtener la base por UID en la experiencia autenticada. No importar la base demostrativa ni habilitar sus perfiles en la APK normal.
- Mantener resultados/fragmentos inmutables, confirmación de servidor y separación results/tracking al cambiar la entrega.
- Habilitar cuestionarios únicamente con definiciones autorizadas; las sintéticas de tests no son contenido de producción.

## Verificación por área

Las pruebas Kotlin están en `app/src/test/java/dev/studios/samimine/`; las instrumentadas, en `app/src/androidTest/java/dev/studios/samimine/`.

| Área | Pruebas |
|---|---|
| Motores, métricas y configuración | PvtEngineTest, CognitiveTestsTest, BatteryScoringTest |
| Coordinación, fallos y recorrido breve | SamiMineViewModelTest, CognitiveViewModelTest, BriefViewModelTest |
| Room, migraciones y cola | RoomEvaluationRepositoryTest, BatteryPersistenceTest, PilotPersistenceTest |
| Gestos, recorridos y navegación | ResponseGestureTest, OfflineFlowTest, CognitiveTestsFlowTest, ExtendedBatteryFlowTest, WorkerNavigationTest |
| Tamaños y fuentes | VisualReferenceTest |
| Login, roles y entrega con emuladores Firebase | PilotFirebaseFlowTest, argumento pilotAuth=true |
| Reglas Firestore | `scripts/firebase/rules.test.mjs` con proyecto demo-samimine |

SamiMineTestRunner separa regresión local y aceptación autenticada. Los comandos y resultados de esta revisión están en la [guía de desarrollo](../README_DESARROLLO.md). Los reportes y capturas permanecen locales.
