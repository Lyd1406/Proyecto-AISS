# Proyecto-AISS

Proyecto de integración desarrollado con Spring Boot para obtener información desde PeerTubeMiner y DailyMotionMiner y almacenarla en VideoMiner.

## Repositorio GitHub

https://github.com/carmenropue/Proyecto-AISS

## Servicios y Puertos

| Servicio | Puerto |
|---|---|
| VideoMiner | 8080 |
| PeerTubeMiner | 8081 |
| DailyMotionMiner | 8082 |

## Demo interactiva de VideoMiner

La demo permite elegir un canal, consultar sus vídeos y abrir la ficha de uno de ellos. El inspector muestra la ruta GET, el estado HTTP y la respuesta JSON de cada paso.

### Abrir el ejemplo sin instalar nada

Abre [demo.html](VideoMiner/src/main/resources/static/demo.html) desde tu copia local con un navegador. El modo **Respuestas guardadas** reproduce las respuestas obtenidas al ejecutar VideoMiner con el canal `tv` de la colección de Postman. Pulsa **Ver vídeos** y abre el vídeo `21856` para ver el autor, un comentario y una pista de subtítulos. **Ver JSON completo** muestra los datos que alimentan la ficha.

### Consultar la API en directo

1. Con un JDK 17 o 21 instalado, ejecuta desde la raíz del repositorio:

   ```sh
   cd VideoMiner
   sh mvnw spring-boot:run
   ```

   En Windows, usa `mvnw.cmd spring-boot:run` dentro de la carpeta `VideoMiner`.
2. Abre la [demo en directo](http://localhost:8080/demo.html). También puedes acceder desde el [cliente API](http://localhost:8080/api-client.html). Para este recorrido basta con arrancar VideoMiner.
3. Si la lista está vacía, importa [la colección de Postman](VideoMiner-postman.json) y ejecuta **Create Channel**. Envía el canal de ejemplo a `POST /videominer/v1/channels`. Tras recibir `201 Created`, recarga la demo. Con la configuración por defecto, H2 guarda los datos en memoria y tendrás que volver a cargarlos al reiniciar.
4. Pulsa **Ver vídeos** en el canal `tv`. El inspector mostrará `GET /videominer/v1/channels/80/videos` y la lista de ocho vídeos. Al seleccionar el vídeo `21856`, verás la respuesta de `GET /videominer/v1/videos/21856`.

Puedes alternar entre **Abrir el ejemplo guardado** y **Consultar la API en directo** desde la propia demo. Para detener el servicio, pulsa `Ctrl+C` en la terminal.

## Funcionalidades implementadas

- Obtención de canales desde PeerTubeMiner
- Obtención de vídeos desde PeerTubeMiner
- Obtención de canales desde DailyMotionMiner
- Obtención de vídeos desde DailyMotionMiner
- Envío de información a VideoMiner
- Persistencia en base de datos H2
- Mappers entre modelos externos y modelos VideoMiner
- Soporte para comments
- Soporte para captions
- Generación automática de IDs en VideoMiner para DailyMotionMiner

## Control de acceso a los subtítulos de Dailymotion

DailyMotionMiner consulta los metadatos públicos de un vídeo mediante la API REST legacy de Dailymotion. Para la consulta de subtítulos, actúa como cliente de servidor con **OAuth 2.0 Client Credentials**. Dailymotion valida la clave al emitir el token y comprueba su acceso al recurso cuando recibe el encabezado `Authorization: Bearer …`. El token se conserva en memoria hasta poco antes de su vencimiento. Tras un `401` en subtítulos, la siguiente consulta solicita un token nuevo.

| Paso | Petición que hace DailyMotionMiner |
| --- | --- |
| Metadatos públicos | `GET https://api.dailymotion.com/video/{id}`, sin token |
| Obtener el token | `POST https://partner.api.dailymotion.com/oauth/v1/token`, con la clave privada y su secreto |
| Consultar subtítulos | `GET https://partner.api.dailymotion.com/rest/video/{id}/subtitles`, con el token Bearer |

La referencia de Dailymotion clasifica `id`, `language` y `url` como campos de lectura pública. La autenticación estudiada aquí corresponde al acceso de servidor mediante la API de partners, que es la ruta que utiliza el minero para esa consulta. [Client Credentials en la API legacy](https://developers.dailymotion.com/v0/docs/authenticate-using-client-credentials) · [Campos de subtítulos](https://developers.dailymotion.com/v0/reference/subtitles-fields).

### Configuración y prueba en directo

1. En Dailymotion Studio, crea una clave de API **privada** para los canales que vas a consultar y asígnale los permisos necesarios. Si la clave que se había guardado en el repositorio es real, revócala y usa una nueva. Git puede conservar los valores anteriores en su historial.
2. Configura `DAILYMOTION_CLIENT_ID` y `DAILYMOTION_CLIENT_SECRET` como variables de entorno del proceso. Si tu clave necesita solicitar ámbitos concretos, configura también `DAILYMOTION_SCOPE` como una lista separada por espacios. En IntelliJ puedes definirlas en **Run → Edit Configurations → Environment variables**.
3. Con JDK 21, ejecuta desde la raíz del proyecto:

   ```sh
   cd DailyMotionMiner
   sh mvnw spring-boot:run
   ```

4. En Postman, haz un `GET` a `http://localhost:8082/dailymotion/v1/video/{id}` con el identificador de un vídeo público de un canal autorizado para tu clave. El servicio realiza el intercambio OAuth. Si la consulta termina bien, `captions` contiene las pistas devueltas por Dailymotion o queda como `[]` si la lista de pistas está vacía.

### Qué devuelve el minero cuando falla el acceso

| Situación | HTTP de DailyMotionMiner | Campo `code` |
| --- | --- | --- |
| Faltan las variables de la clave | `503` | `OAUTH_NOT_CONFIGURED` |
| Dailymotion rechaza la solicitud del token | `502` | `OAUTH_TOKEN_REJECTED` |
| Dailymotion responde `401` al consultar subtítulos | `502` | `CAPTIONS_UNAUTHENTICATED` |
| Dailymotion responde `403` al consultar subtítulos | `502` | `CAPTIONS_FORBIDDEN` |
| Dailymotion responde `429` al pedir el token o consultar subtítulos | `503` | `DAILYMOTION_RATE_LIMITED` |

Cuando Dailymotion responde con un error, el JSON conserva el código HTTP del proveedor en `upstreamStatus`. Para `429`, DailyMotionMiner transmite `Retry-After` si el proveedor envía un valor válido. Si falla la consulta de subtítulos durante la importación de un canal, el minero detiene ese envío a VideoMiner.

Para reproducir la comparación sin credenciales ni llamadas a Dailymotion, ejecuta `sh mvnw -Dtest=videoServiceTests test` desde `DailyMotionMiner`. Las pruebas simulan una consulta con pistas, una lista vacía, el rechazo de credenciales o del token, la falta de permisos y el límite de peticiones. La demo de VideoMiner sirve para enseñar los datos que ya están almacenados en H2.

## Documentación

DailyMotion Miner: localhost:8082/swagger-ui/index.html

Peertube Miner: localhost:8081/swagger-ui/index.html

VideoMiner: localhost:8080/swagger-ui/index.html

## Tecnologías

- Java
- Spring Boot
- Maven
- H2 Database
- Postman

## Endpoints principales

### PeerTubeMiner

Obtiene un canal de PeerTube.

GET: http://localhost:8081/peertubeminer/v1/{channelHandle}?maxVideos=5&maxComments=2

Ejemplo: http://localhost:8081/peertubeminer/v1/transport_evolved_main?maxVideos=5&maxComments=2


Obtiene un vídeo concreto de PeerTube.

GET: http://localhost:8081/peertubeminer/v1/video/{id}?maxComments=2

Ejemplo: http://localhost:8081/peertubeminer/v1/video/21856?maxComments=2


Envía el canal de PeerTube a VideoMiner.

POST: http://localhost:8081/peertubeminer/v1/{id}?maxVideos=5&maxComments=2

Ejemplo: http://localhost:8081/peertubeminer/v1/transport_evolved_main?maxVideos=5&maxComments=2

### DailyMotionMiner

Obtiene un canal de DailyMotion.

GET: http://localhost:8082/dailymotion/v1/{id}?maxVideos=10&maxPages=2

Ejemplo: http://localhost:8082/dailymotion/v1/x43py0?maxVideos=10&maxPages=2


Envía el canal de DailyMotion a VideoMiner.

POST: http://localhost:8082/dailymotion/v1/{id}?maxVideos=10&maxPages=2

Ejemplo: http://localhost:8082/dailymotion/v1/x43py0?maxVideos=10&maxPages=2


Obtiene un vídeo concreto de DailyMotion.

GET: http://localhost:8082/dailymotion/v1/video/{id}

Ejemplo: http://localhost:8082/dailymotion/v1/video/x89abc

### VideoMiner

#### VideoMiner - Captions

Obtiene todas las captions almacenadas.

GET:
http://localhost:8080/videominer/v1/captions

Obtiene una caption por ID.

GET:
http://localhost:8080/videominer/v1/captions/{id}

Obtiene las captions de un vídeo concreto.

GET:
http://localhost:8080/videominer/v1/videos/{videoId}/captions

Crea una nueva caption para un vídeo.

POST:
http://localhost:8080/videominer/v1/videos/{videoId}/captions

Elimina una caption.

DELETE:
http://localhost:8080/videominer/v1/captions/{id}

#### VideoMiner - Channels

Obtiene todos los canales almacenados.

GET:
http://localhost:8080/videominer/v1/channels

Obtiene un canal por ID.

GET:
http://localhost:8080/videominer/v1/channels/{id}

Crea un nuevo canal.

POST:
http://localhost:8080/videominer/v1/channels

Actualiza un canal existente.

PUT:
http://localhost:8080/videominer/v1/channels/{id}

Elimina un canal.

DELETE:
http://localhost:8080/videominer/v1/channels/{id}

#### VideoMiner - Comments

Obtiene todos los comentarios almacenados.

GET:
http://localhost:8080/videominer/v1/comments

Obtiene un comentario por ID.

GET:
http://localhost:8080/videominer/v1/comments/{id}

Obtiene los comentarios de un vídeo concreto.

GET:
http://localhost:8080/videominer/v1/videos/{videoId}/comments

Crea un nuevo comentario para un vídeo.

POST:
http://localhost:8080/videominer/v1/videos/{videoId}/comments

Elimina un comentario.

DELETE:
http://localhost:8080/videominer/v1/comments/{id}

#### VideoMiner - Videos

Obtiene todos los vídeos almacenados.

GET:
http://localhost:8080/videominer/v1/videos

Obtiene un vídeo por ID.

GET:
http://localhost:8080/videominer/v1/videos/{id}

Obtiene todos los vídeos de un canal concreto.

GET:
http://localhost:8080/videominer/v1/channels/{channelId}/videos

Crea un nuevo vídeo en un canal.

POST:
http://localhost:8080/videominer/v1/channels/{channelId}/videos

Actualiza un vídeo existente.

PUT:
http://localhost:8080/videominer/v1/videos/{id}

Elimina un vídeo.

DELETE:
http://localhost:8080/videominer/v1/videos/{id}

Obtiene el usuario de un vídeo.

GET:
http://localhost:8080/videominer/v1/videos/{id}/user

Actualiza el usuario de un vídeo.

PUT:
http://localhost:8080/videominer/v1/videos/{id}/user


## Notas

- Algunos vídeos de DailyMotion pueden no tener captions disponibles y devolver una lista vacía.

