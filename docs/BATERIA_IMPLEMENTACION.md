# Batería completa de SamiMine

Revisión: **21/10/2026**. Consultar también la [arquitectura actual](ARQUITECTURA_ACTUAL.md) y el [piloto Firebase](FIREBASE_PILOTO.md).

## Comportamiento entregado

El catálogo conserva la identidad visual de SamiMine y presenta seis tareas cognitivas y tres cuestionarios. PVT sigue disponible individualmente. El recorrido breve KSS → PVT se habilita solamente cuando hay contenido autorizado de KSS. Los módulos pendientes explican su disponibilidad; no muestran escalas inventadas ni aceptan respuestas.

| Protocolo | Parámetros de esta entrega |
|---|---|
| `demo-pvt-v1` | Tres intentos; configuración existente sin cambios. |
| `demo-flanker-v2` | Dos ejemplos, doce ensayos equilibrados, ventana de 3.000 ms. |
| `demo-gonogo-v2` | Dos ejemplos, veinte ensayos (16 GO / 4 NO-GO), ventana de 3.000 ms. |
| `demo-visual-search-v1` | Un ejemplo, ocho tableros 4×4, ventana de 4.000 ms. |
| `experimental-dsst-v1` | Seis ejemplos; seis símbolos vectoriales propios y asignación a dígitos permutada por semilla; una ejecución global de 60.000 ms. El flujo se amplía por respuesta, sin número fijo de ensayos. |
| `experimental-1back-v1` | Seis comparaciones de práctica; símbolo inicial durante 500 ms; 24 comparaciones (8 coincidencias y 16 diferencias), fijación 400 ms, estímulo 500 ms dentro de una ventana total de 2.500 ms, intervalo 400 ms. La respuesta no adelanta la siguiente comparación. |

DSST y 1-back son adaptaciones experimentales propias. Estos parámetros permiten verificar software demostrativo y no representan instrumentos clínicos validados. No se calculan lapsos PVT sin umbral de protocolo, pendiente de búsqueda visual, d-prime ni aptitud laboral.

## Métricas e historial

Las nuevas ejecuciones usan `descriptive-v2`. Los registros anteriores conservan `legacy-v1`, las ventanas históricas y sus medias originales. Flanker añade medianas y precisión por condición; la diferencia de medianas requiere al menos tres aciertos por condición y se etiqueta exploratoria. GO/NO-GO separa tasas, comisiones, omisiones y anticipaciones. Búsqueda Visual incluye precisión y mediana de aciertos. Las prácticas se excluyen; los tiempos ausentes permanecen nulos.

PVT muestra mediana y número de respuestas como valores principales, con velocidad y media en detalles. La velocidad excluye tiempos cero para evitar divisiones inválidas. Las métricas cognitivas nuevas se guardan con valor, unidad, denominador y método; se leen sus snapshots al consultar el historial. Las sesiones incompletas o inválidas no tienen métricas finales.

## Persistencia y recuperación

Room usa esquema 5 con migraciones no destructivas 1 → 2 → 3 → 4 → 5. La versión 5 incorpora sync_queue y encola registros cerrados para su entrega a Firebase. Se añaden `assessment_sessions`, `self_reports` y `metric_results`, además de versión de cálculo y tiempos de ejecución. La experiencia autenticada usa una base privada por UID; los historiales y detalles también comprueban el propietario. Los perfiles ficticios se conservan en la composición de regresión instrumentada. La vinculación del recorrido comprueba perfil y turno.

Los cierres guardan métricas, estado y entrada de entrega pendiente en una transacción. La cola conserva PENDING, SENDING, SYNCED o ATTENTION; el envío remoto no recalcula métricas. PVT cierra también su recorrido asociado en esa misma transacción. Los reintentos conservan identificadores y registros, y rechazan escrituras conflictivas. Las sesiones activas tras cierre del proceso quedan incompletas; pérdida de foco, suspensión o recreación de pantalla invalidan las mediciones activas. Abandonar voluntariamente conserva una sesión incompleta. No se reanudan mediciones interrumpidas.

DSST conserva un único plazo monotónico desde el primer dibujo. Una respuesta en el límite de 60 segundos queda fuera de ventana. La espera o el reintento de una escritura no reinicia el plazo. El cierre comprueba un minuto completo y que las respuestas guardadas pertenecen a ese intervalo.

## Habilitación de cuestionarios

`InstrumentCatalog.definitions` contiene KSS, Borg y Chalder deshabilitados. Para habilitar un instrumento hay que aportar una definición revisada con versión, idioma `es-PE`, instrucciones, período de referencia, preguntas, opciones, referencia de permiso y referencia de aprobación. No existe un interruptor de usuario que omita estas condiciones.

- **KSS:** una pregunta y valores ordinales 1–9; puede omitirse explícitamente. El recorrido registra la ausencia y continúa a PVT.
- **Borg:** una pregunta con valores 6–20; requiere actividad realizada y momento. No admite omisión ni se incluye en el recorrido breve.
- **Chalder:** conserva respuestas por ítem y período autorizado. El total se calcula únicamente cuando la definición aprobada declara `AUTHORIZED_ORDINAL_SUM`; con `ORDINAL_ONLY` se conservan los valores sin agregarlos. Una versión con otro algoritmo necesita su implementación y pruebas específicas antes de activarse.

Las definiciones sintéticas de los tests solo comprueban la infraestructura. No son contenido de producción ni traducciones de instrumentos.

## Diseño y comprobaciones

Se reutilizan los tokens existentes: azul `#1447E6`, fondo `#F8FAFC`, tarjetas blancas, márgenes de 24 dp y botones con relieve. Solo PVT usa fondo oscuro. Las respuestas de asociación emplean seis botones y las comparaciones dos; los cuestionarios presentan opciones verticales completas. Resultados e instrucciones se desplazan y las métricas se apilan con fuente ampliada.

La matriz visual incluye 360, 390 y 412 dp, fuente 100 % y 150 %. Los tests de dominio cubren semillas, balance, medianas, denominadores nulos y bordes temporales. Los de ViewModel incluyen el plazo global, reintentos, ventanas 1-back y recorrido con KSS sintético. Las pruebas Android comprueban migraciones, aislamiento por perfil, persistencia, estados pendientes y navegación real.

Comando de validación:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline
```

Los reportes ejecutados están en `app/build/reports`. La validación de latencia táctil, calibración y accesibilidad con usuarios en teléfonos físicos sigue pendiente; una ejecución en emulador no la sustituye.
