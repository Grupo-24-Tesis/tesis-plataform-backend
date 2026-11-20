# FaceCluster: backend

API REST de FaceCluster, la plataforma que agrupa las apariciones faciales de videos de
videovigilancia residencial. Recibe los videos que se cargan desde la interfaz web, los
envía al pipeline de visión por computador a través de una cola de SQS y guarda los
rostros que el pipeline devuelve por otra cola.

El backend y el pipeline no se conectan entre sí: solo hablan con SQS y S3. Pueden estar
en máquinas distintas, sin IP fija ni puertos abiertos.

Repositorios relacionados: `tesis-plataform-page` (frontend) y
`tesis_face_recognition_pipeline` (pipeline y worker).

## Stack

| Componente | Versión |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.3.2 (`spring-boot-starter-web`, `spring-boot-starter-data-mongodb`) |
| MongoDB | 7 |
| AWS SDK for Java v2 | 2.55.7 (S3 y SQS) |
| Maven | wrapper incluido (`mvnw`) |

`pom.xml` fija `httpclient5` 5.4.1 y `httpcore5` 5.3.2 para que las dos versiones sean
compatibles entre sí.

## Configuración

Toda la configuración llega por variables de entorno. `docker-compose.yml` las lee de un
archivo `.env` en la raíz del repositorio. Ese archivo nunca se sube a git.

| Variable | Uso |
| --- | --- |
| `MONGO_ROOT_USER`, `MONGO_ROOT_PASS` | Usuario administrador de MongoDB |
| `MONGO_APP_DB` | Base de datos de la aplicación |
| `MONGO_APP_USER`, `MONGO_APP_PASS` | Usuario con el que se conecta el backend |
| `APP_TIME_ZONE` | Zona horaria de toda la aplicación (`America/Bogota` por defecto). Un solo `Clock` la usa para `createdAt` de los reportes y para convertir `capturedAt` de los rostros |
| `AWS_REGION` | Región de S3 y SQS |
| `AWS_S3_BUCKET` | Bucket de los videos y de los rostros |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Credenciales de AWS |
| `AWS_S3_PRESIGNED_URL_DURATION_MINUTES` | Duración de las URL prefirmadas (15 por defecto) |
| `AWS_S3_FACES_PREFIX` | Carpeta del bucket donde el pipeline sube los rostros (`faces/` por defecto) |
| `AWS_SQS_QUEUE_URL` | Cola donde se publican los trabajos |
| `AWS_SQS_RESULTS_QUEUE_URL` | Cola donde el pipeline publica el avance, los resultados y los fallos. Sin ella el backend arranca, pero no recibe resultados |
| `VIDEO_JOB_UPLOADED_TIMEOUT_MINUTES` | Minutos que un trabajo puede quedarse sin enviarse a la cola antes de darse por fallido (5 por defecto) |
| `VIDEO_JOB_QUEUED_TIMEOUT_MINUTES` | Minutos que un trabajo puede esperar en la cola antes de darse por fallido (720 por defecto) |
| `VIDEO_JOB_PROCESSING_TIMEOUT_MINUTES` | Minutos sin avance del pipeline antes de dar por fallido un trabajo en curso (15 por defecto) |

Recursos de AWS necesarios:

- Un bucket de S3. Su configuración CORS debe permitir `PUT` desde el origen del frontend,
  porque el navegador sube el video directamente al bucket.
- Una cola de SQS para los trabajos y otra para los resultados.
- Credenciales con permiso para firmar `PutObject` y `GetObject`, listar el bucket,
  enviar mensajes a la cola de trabajos (`sqs:SendMessage`) y leer la de resultados
  (`sqs:ReceiveMessage` y `sqs:DeleteMessage`).

## Ejecución

```bash
docker compose up --build
```

Levanta dos contenedores:

| Servicio | Puerto | Contenido |
| --- | --- | --- |
| `mongodb` | 127.0.0.1:27017 | MongoDB con los datos iniciales de `docker/mongo/init` (tres vigilantes, las cámaras `CAM-01` a `CAM-03` y dos reportes de ejemplo, sin rostros ni marcas) |
| `backend` | 127.0.0.1:8080 | La API |

Comprobación:

```bash
curl http://localhost:8080/api/health
```

La URI de MongoDB en `application.properties` usa el host `mongodb`, el nombre del
servicio en `docker-compose.yml`. Por eso el backend se ejecuta dentro de compose.

CORS solo admite el origen `http://localhost:4200` (`CorsConfig`).

Los puertos se publican solo en `127.0.0.1`. Las variables de `docker-compose.yml` usan la forma `${VAR:-valor}`, así que las de AWS y de tiempos de espera tienen valor por defecto si no están en `.env`.

## Flujo de un video

1. `POST /api/videos/upload-url` devuelve una URL prefirmada de S3 para subir el video a
   `videos/<uuid>.<extensión>`. Solo se aceptan archivos con `contentType` de video.
2. El navegador sube el archivo a S3 con esa URL.
3. `POST /api/videos` valida la petición y registra el video como un `VideoJob`. Exige `originalFileName`, `bucket` igual al configurado, `objectKey` que empiece por `videos/` y no contenga `..`, y un `cameraId` que exista; si algo falla responde 400. Después publica en SQS un mensaje
   `VIDEO_UPLOAD_READY` con `jobId`, `bucket`, `objectKey`, `cameraId`,
   `originalFileName`, `contentType` y `createdAt`.
4. El worker del pipeline toma el mensaje, procesa el video, sube a S3 el mejor rostro de
   cada persona y publica eventos en la cola de resultados.
5. `SqsVideoResultListener` consulta esa cola y aplica cada evento. Con
   `VIDEO_PROCESSING_COMPLETED` guarda una `FaceAppearance` por rostro, con `_id` igual a
   `faceId` y el `jobId` del trabajo. El `cameraId` de cada aparición se toma del
   `VideoJob`, no del pipeline. `capturedAt` debe ser una fecha ISO-8601 con desfase y se
   convierte a la zona de `APP_TIME_ZONE`.

Eventos de la cola de resultados:

| `eventType` | Efecto en el `VideoJob` |
| --- | --- |
| `VIDEO_PROCESSING_STARTED` | Pasa a `PROCESSING`. El worker lo repite cada 120 s mientras procesa |
| `VIDEO_PROCESSING_COMPLETED` | Se guardan los rostros de `appearances` y pasa a `COMPLETED`. Sin `appearances` el evento es inválido |
| `VIDEO_PROCESSING_FAILED` | Pasa a `FAILED` y `reason` queda en `failureReason` |

Todos llevan `eventVersion`, `jobId` y `sentAt`. Un mensaje que no es un evento válido o
que nombra un trabajo inexistente se borra de la cola. Si el evento no se puede aplicar
por un error del backend, el mensaje queda en la cola y se vuelve a intentar. El
backend lee `ApproximateReceiveCount` y, cuando el mensaje llega a
`video-jobs.results-max-receives` recepciones (5 por defecto), registra el error con el
`jobId` y el `eventType` y lo borra de la cola. Esa propiedad no tiene variable de entorno.

Estados de un `VideoJob`:

| Estado | Significado |
| --- | --- |
| `UPLOADED` | El video quedó registrado y todavía no se publicó en SQS |
| `QUEUED` | El mensaje se publicó en SQS |
| `QUEUE_ERROR` | No se pudo publicar el mensaje. `POST /api/videos` responde 503 |
| `PROCESSING` | El pipeline está procesando el video |
| `COMPLETED` | Se recibieron los resultados del pipeline |
| `FAILED` | El pipeline no pudo procesar el video o dejó de responder. El motivo queda en `failureReason` |

Ningún trabajo se queda esperando para siempre. `StaleVideoJobMonitor` revisa cada minuto
y pasa a `FAILED` los trabajos que llevan en `UPLOADED` más de
`VIDEO_JOB_UPLOADED_TIMEOUT_MINUTES`, los que llevan en `QUEUED` más de
`VIDEO_JOB_QUEUED_TIMEOUT_MINUTES` y los que llevan en `PROCESSING` más de
`VIDEO_JOB_PROCESSING_TIMEOUT_MINUTES` sin un evento nuevo.

Si el mensaje se publicó en SQS pero falla el guardado del estado `QUEUED`, el trabajo no
pasa a `QUEUE_ERROR`: `POST /api/videos` responde 500 y el trabajo queda en `UPLOADED`. Los eventos del worker
lo mueven de ahí; si no llega ninguno, el límite de `UPLOADED` lo pasa a `FAILED`, y un
evento posterior del worker puede revivirlo.

Los eventos pueden llegar repetidos o desordenados:

- Un trabajo `COMPLETED` no cambia con ningún evento posterior, así que los rostros no se
  guardan dos veces.
- Un trabajo `FAILED` pasa a `COMPLETED` si después llegan sus resultados, y vuelve a
  `PROCESSING` si el worker reporta avance con un `sentAt` estrictamente posterior al
  último evento. Un `sentAt` igual o anterior se ignora.
- Un evento `FAILED` con un `sentAt` igual o anterior al último evento del worker también se
  ignora, así que un fallo repetido de un intento anterior no deshace un reintento.

## Errores

Los errores se responden como JSON con la forma `{"status": 400, "message": "..."}`, que arma `ApiExceptionHandler`. Cubre los errores de validación, los parámetros obligatorios que faltan, los parámetros con valor inválido, los cuerpos que no se pueden leer (400) y los registros duplicados (409).

Los filtros de fecha (`startDate`, `endDate`, `date`) usan el formato `AAAA-MM-DD`. Aceptan un solo extremo, y una fecha inválida o un `startDate` posterior a `endDate` responde 400.

## Imágenes de rostros

S3 es la única fuente de las imágenes. MongoDB guarda en `frameUrl` la URL permanente del
objeto, que escribe el pipeline, con la forma
`https://<bucket>.s3.<región>.amazonaws.com/faces/<jobId>/<trackId>.jpg`.

Antes de responder al frontend, `FaceImageService` descarta las apariciones cuya imagen ya
no existe en el bucket o cuya URL no es de ese bucket, y reemplaza `frameUrl` por una URL
prefirmada de corta duración. Así el bucket puede ser privado y el navegador no reutiliza
imágenes borradas. Las personas sin ninguna imagen en el bucket no se muestran.

## Reintento de un video fallido

`POST /api/video-jobs/{jobId}/retry` vuelve a enviar a la cola un video que ya está en S3. Solo se aceptan trabajos en `FAILED` o `QUEUE_ERROR`; con otro estado responde 409 y si el trabajo no existe responde 404. El backend borra las apariciones del trabajo, vacía `failureReason`, `startedAt`, `completedAt` y `faceCount`, pone `lastWorkerEventAt` en la hora del reintento para que los eventos `STARTED` y `FAILED` repetidos del intento anterior se ignoren, publica el mismo mensaje `VIDEO_UPLOAD_READY` de la subida original y deja el trabajo en `QUEUED`. Si la publicación falla, el trabajo queda en `QUEUE_ERROR` y la respuesta es 503, igual que al subir un video; en ese caso el trabajo queda en `QUEUE_ERROR` sin rostros y se puede reintentar de nuevo. La respuesta es el `VideoJobResponse` actualizado.

Un evento `VIDEO_PROCESSING_COMPLETED` del intento anterior puede llegar después del reintento. Se acepta sin lógica adicional: los rostros son los mismos del mismo video.

## Endpoints

| Método y ruta | Uso |
| --- | --- |
| `GET /api/health` | Estado del servicio |
| `GET /api/guards`, `POST /api/guards` | Vigilantes |
| `GET /api/cameras`, `POST /api/cameras` | Cámaras |
| `POST /api/videos/upload-url` | URL prefirmada para subir un video |
| `POST /api/videos` | Registra el video subido y lo envía a la cola |
| `GET /api/video-jobs` | Lista los trabajos, del más reciente al más antiguo. Filtro opcional: `cameraId` |
| `GET /api/video-jobs/{jobId}/faces` | Rostros de un trabajo con URL prefirmadas. 404 si el trabajo no existe |
| `GET /api/video-jobs/{jobId}` | Estado de un trabajo, con `failureReason` si falló |
| `GET /api/metrics/video-jobs` | Métricas de procesamiento de los videos. Filtro opcional: `cameraId` |
| `GET /api/metrics/activity` | Detecciones por hora del día, por cámara y por video, y su seguimiento (marcas y reportes). Filtros opcionales: `cameraId`, `startDate`, `endDate` |
| `POST /api/video-jobs/{jobId}/retry` | Reintenta un video fallido sin volver a subirlo. 404 si el trabajo no existe, 409 si su estado no es `FAILED` ni `QUEUE_ERROR`, 503 si no se puede publicar en SQS |
| `POST /api/video-jobs/{jobId}/results` | Registra resultados a mano. El pipeline no lo usa: envía sus resultados por SQS |
| `GET /api/face-appearances` | Apariciones de rostros con URL prefirmadas |
| `GET /api/face-appearances/by-ids?faceIds=a,b,c` | Apariciones de esos `faceId` con URL prefirmadas. Omite los ids desconocidos y las imágenes que no están en S3. Máximo 50 ids por consulta (400 si se supera). Lista S3 solo bajo `faces/<jobId>/` de los videos involucrados |
| `GET /api/face-appearances/unique` | Apariciones agrupadas por `faceId`. Filtros: `startDate`, `endDate`, `hasReports`, `suspect` |
| `GET /api/reports`, `POST /api/reports` | Reportes de incidentes. `POST` acepta `createdByGuardId` opcional; si se envía y el vigilante no existe responde 400 `El vigilante no existe` |
| `GET /api/reports/{id}` | Detalle de un reporte. 404 `Reporte no encontrado` si no existe |
| `GET /api/reports/filter` | Filtros: `status`, `date`, `startDate`, `endDate`, `dateType`. Con `dateType=resolucion` filtra por `resolvedAt`; en otro caso, por `incidentDate` |
| `PUT /api/reports/{id}/status` | Cambia el estado de un reporte. 404 si no existe |
| `GET /api/marked`, `POST /api/marked`, `DELETE /api/marked/{id}` | Detecciones marcadas. `POST` exige `faceId` (400), acepta `createdByGuardId` opcional (400 si el vigilante no existe) y responde 409 si ya está marcada |

Los reportes tienen dos estados, `investigacion` y `solucionado`; otro valor responde 400. Un reporte `solucionado` exige `resolvedAt` al crearse; si el estado no es `solucionado`, se vacían `resolvedAt` y `resolutionDescription`. `createdAt` lo pone el servidor.

Las marcas son únicas por `faceId`. Al arrancar, `MarkedIndexInitializer` elimina las marcas duplicadas, conserva la más antigua de cada `faceId` y crea el índice único.

`faceId` tiene la forma `<jobId>_<trackId>`: identifica a una persona dentro de un video.
No hay reconocimiento de la misma persona entre videos.

## Métricas de procesamiento

Cada trabajo guarda `startedAt` (la primera vez que pasa a `PROCESSING`, con el `sentAt` del evento; los `STARTED` posteriores no lo cambian), `completedAt` (el `sentAt` del evento `COMPLETED`, o la hora del reloj si no lo trae) y `faceCount` (detecciones guardadas al completarse). Un trabajo que falla conserva su `startedAt`. Al publicarse en la cola, en la subida y en cada reintento, se guarda `queuedAt`. Los trabajos antiguos sin estos campos siguen funcionando: los nulos se omiten en los promedios. Al arrancar, `FaceCountInitializer` completa el `faceCount` de los trabajos `COMPLETED` que no lo tienen contando sus detecciones por `jobId`, después de que `FaceJobIdInitializer` complete el `jobId` de las detecciones antiguas.

`GET /api/video-jobs` y `GET /api/video-jobs/{jobId}` añaden a cada trabajo `startedAt`, `completedAt`, `faceCount`, `queueWaitSeconds` (`startedAt` menos `queuedAt`, o menos `createdAt` en los trabajos antiguos sin `queuedAt`; nulo si no ha empezado) y `processingSeconds` (`completedAt` menos `startedAt`, nulo si no ha terminado). Los segundos son enteros.

`GET /api/metrics/video-jobs?cameraId=` (la cámara es opcional) responde:

| Campo | Cálculo |
| --- | --- |
| `total` | Trabajos considerados |
| `completed` | Trabajos en `COMPLETED` |
| `failed` | Trabajos en `FAILED` o `QUEUE_ERROR` |
| `inProgress` | Trabajos en `UPLOADED`, `QUEUED` o `PROCESSING` |
| `failureRate` | `failed / (completed + failed)`, entre 0 y 1; 0 si ambos son 0. Mide el último intento de cada video: un trabajo reintentado que luego se completa ya no cuenta como fallido |
| `averageQueueWaitSeconds` | Promedio de `queueWaitSeconds` de los trabajos que lo tienen; en un reintento se mide desde el reintento, no desde la subida original; nulo si ninguno |
| `averageProcessingSeconds` | Promedio de `processingSeconds` de los trabajos que lo tienen; nulo si ninguno |
| `totalFaces` | Suma de `faceCount` de los trabajos completados |
| `averageFacesPerVideo` | Promedio de `faceCount` de los trabajos completados que lo tienen; nulo si ninguno |

## Actividad por hora

`GET /api/metrics/activity?cameraId=&startDate=&endDate=` (todos opcionales; las fechas son `AAAA-MM-DD`, admiten un solo extremo y una fecha inválida responde 400) cuenta las detecciones por hora del día, por cámara y por video con una agregación de MongoDB sobre `face_appearances`, agrupada por hora, `cameraId` y `jobId`. Las detecciones sin `capturedAt` se ignoran. Responde:

| Campo | Contenido |
| --- | --- |
| `hours` | Exactamente 24 entradas `{hour, count}` de la hora 0 a la 23, con ceros donde no hay detecciones |
| `cameras` | Entradas `{cameraId, cameraName, count}` ordenadas por `count` descendente; si la cámara ya no existe, `cameraName` es su id |
| `videos` | Entradas `{jobId, fileName, uploadedAt, count}` ordenadas por `count` descendente y luego por `fileName`. `fileName` es el nombre original del video y `uploadedAt` el `createdAt` del trabajo; si el trabajo ya no existe, `fileName` es su `jobId` y `uploadedAt` es `null`. Las detecciones sin `jobId` cuentan en `hours` y `cameras` pero no en `videos` |
| `followUp` | `{investigation, resolved, markedOnly, none}`: las detecciones contadas repartidas en cuatro grupos excluyentes que suman `total`. `investigation`: la detección está en al menos un reporte `investigacion`. `resolved`: está en reportes y todos son `solucionado`. `markedOnly`: está marcada y no está en ningún reporte. `none`: el resto. Las marcas y los reportes de detecciones fuera del filtro no cuentan |
| `total` | Suma de las detecciones contadas |

Spring Data MongoDB guarda un `LocalDateTime` como fecha BSON (UTC) interpretándolo en la zona horaria por defecto de la JVM, y `$hour` sin zona devuelve la hora UTC. Por eso la agregación pasa esa misma zona a `$hour`, de modo que la hora devuelta es la hora local guardada.

## Estructura

| Paquete | Contenido |
| --- | --- |
| `api` | Salud, formato de error (`ApiExceptionHandler`) y lectura de filtros de fecha |
| `controller` | Controladores REST |
| `service` | Lógica de trabajos, métricas de procesamiento, actividad por hora, carga a S3, imágenes de rostros y revisión de trabajos sin respuesta |
| `messaging` | Publicación de trabajos y lectura de resultados en SQS |
| `repository` | Repositorios de Spring Data MongoDB |
| `domain`, `dto` | Documentos de MongoDB y objetos de entrada y salida |
| `config` | Clientes de AWS, CORS, `Clock`, tareas programadas e índice único de marcas |

Colecciones de MongoDB: `video_jobs`, `face_appearances`, `guards`, `cameras`, `reports`
y `marked`.

## Pruebas

```bash
mvn test
```

Ejecuta todas las pruebas, incluida `contextLoads`, que arranca el contexto completo de
Spring con `src/test/resources/application.properties`. No necesitan MongoDB ni AWS, pero
sí Java 21.

## Limitaciones conocidas

- No hay autenticación. La selección de vigilante del frontend no es un inicio de sesión, y cualquier cliente que llegue al puerto del backend puede usar la API. Por eso `docker-compose.yml` publica los puertos solo en `127.0.0.1`.
- La identidad es por video. El `faceId` es `<jobId>_<trackId>`: el sistema no reconoce a la misma persona en videos distintos y no identifica a las personas.
