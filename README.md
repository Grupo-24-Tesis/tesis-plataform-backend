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
| `mongodb` | 27017 | MongoDB con los datos iniciales de `docker/mongo/init` (vigilantes, cámaras, reportes y personas marcadas de ejemplo) |
| `backend` | 8080 | La API |

Comprobación:

```bash
curl http://localhost:8080/api/health
```

La URI de MongoDB en `application.properties` usa el host `mongodb`, el nombre del
servicio en `docker-compose.yml`. Por eso el backend se ejecuta dentro de compose.

CORS solo admite el origen `http://localhost:4200` (`CorsConfig`).

## Flujo de un video

1. `POST /api/videos/upload-url` devuelve una URL prefirmada de S3 para subir el video a
   `videos/<uuid>.<extensión>`. Solo se aceptan archivos con `contentType` de video.
2. El navegador sube el archivo a S3 con esa URL.
3. `POST /api/videos` registra el video como un `VideoJob` y publica en SQS un mensaje
   `VIDEO_UPLOAD_READY` con `jobId`, `bucket`, `objectKey`, `cameraId`,
   `originalFileName`, `contentType` y `createdAt`.
4. El worker del pipeline toma el mensaje, procesa el video, sube a S3 el mejor rostro de
   cada persona y publica eventos en la cola de resultados.
5. `SqsVideoResultListener` consulta esa cola y aplica cada evento. Con
   `VIDEO_PROCESSING_COMPLETED` guarda una `FaceAppearance` por rostro. El `cameraId` de
   cada aparición se toma del `VideoJob`, no del pipeline.

Eventos de la cola de resultados:

| `eventType` | Efecto en el `VideoJob` |
| --- | --- |
| `VIDEO_PROCESSING_STARTED` | Pasa a `PROCESSING`. El worker lo repite cada 120 s mientras procesa |
| `VIDEO_PROCESSING_COMPLETED` | Se guardan los rostros de `appearances` y pasa a `COMPLETED` |
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
pasa a `QUEUE_ERROR`: `POST /api/videos` responde 500 y los eventos del worker siguen
moviendo el trabajo.

Los eventos pueden llegar repetidos o desordenados:

- Un trabajo `COMPLETED` no cambia con ningún evento posterior, así que los rostros no se
  guardan dos veces.
- Un trabajo `FAILED` pasa a `COMPLETED` si después llegan sus resultados, y vuelve a
  `PROCESSING` si el worker reporta avance con un `sentAt` estrictamente posterior al
  último evento. Un `sentAt` igual o anterior se ignora.

## Imágenes de rostros

S3 es la única fuente de las imágenes. MongoDB guarda en `frameUrl` la URL permanente del
objeto, que escribe el pipeline, con la forma
`https://<bucket>.s3.<región>.amazonaws.com/faces/<jobId>/<trackId>.jpg`.

Antes de responder al frontend, `FaceImageService` descarta las apariciones cuya imagen ya
no existe en el bucket o cuya URL no es de ese bucket, y reemplaza `frameUrl` por una URL
prefirmada de corta duración. Así el bucket puede ser privado y el navegador no reutiliza
imágenes borradas. Las personas sin ninguna imagen en el bucket no se muestran.

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
| `POST /api/video-jobs/{jobId}/results` | Registra resultados a mano. El pipeline no lo usa: envía sus resultados por SQS |
| `GET /api/face-appearances`, `POST /api/face-appearances` | Apariciones de rostros |
| `GET /api/face-appearances/unique` | Apariciones agrupadas por `faceId`. Filtros: `startDate`, `endDate`, `hasReports`, `suspect` |
| `GET /api/reports`, `POST /api/reports` | Reportes de incidentes |
| `GET /api/reports/filter` | Filtros: `status`, `date`, `startDate`, `endDate`, `dateType` |
| `PUT /api/reports/{id}/status` | Cambia el estado de un reporte |
| `GET /api/marked`, `POST /api/marked`, `DELETE /api/marked/{id}` | Personas marcadas |

`faceId` tiene la forma `<jobId>_<trackId>`: identifica a una persona dentro de un video.
No hay reconocimiento de la misma persona entre videos.

## Estructura

| Paquete | Contenido |
| --- | --- |
| `api`, `controller` | Controladores REST |
| `service` | Lógica de trabajos, carga a S3, imágenes de rostros y revisión de trabajos sin respuesta |
| `messaging` | Publicación de trabajos y lectura de resultados en SQS |
| `repository` | Repositorios de Spring Data MongoDB |
| `domain`, `dto` | Documentos de MongoDB y objetos de entrada y salida |
| `config` | Clientes de AWS, CORS y tareas programadas |

Colecciones de MongoDB: `video_jobs`, `face_appearances`, `guards`, `cameras`, `reports`
y `marked`.

## Pruebas

```bash
./mvnw test -Dtest='VideoJobServiceTest,SqsVideoResultListenerTest'
```

Son pruebas unitarias de los estados de un trabajo y de la lectura de la cola de
resultados. No necesitan MongoDB ni AWS, pero sí Java 21.

`./mvnw test` también ejecuta `contextLoads`, que arranca el contexto completo de Spring y
falla si no están definidas las variables de MongoDB.
