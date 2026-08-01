# Fluxy Mock

Servicio intermediario de HTTP mocks configurable. Permite registrar respuestas HTTP estáticas, proxies o capturas cacheadas por endpoint y método, con soporte de templates dinámicos, latencia simulada, matchers de request, post-acciones asíncronas (SQS) y triggers por eventos.

Ahora los mocks también pueden configurarse con `triggerType = EVENT` para reaccionar a mensajes SQS o Kafka, y publicar la respuesta en un target parametrizable (`SQS` o `KAFKA`).

El `mode` del mock decide si se renderiza la respuesta estática, si se reenvía la request al servicio destino o si se captura la primera respuesta upstream para reutilizarla después. `SECURE_CAPTURE` solo persiste respuestas 2xx. Si el mock está desactivado, el servicio actúa como proxy transparente cuando tiene `targetBaseUrl`.
En modo `STATIC_JSON`, si ninguna response activa hace match, el servicio responde `404`.

## Arquitectura

```
                         ┌──────────────────────────┐
  HTTP Request ──────►   │   /fluxy/mock/**          │
                         │   ProxyController         │
                         └──────────┬───────────────┘
                                    │
                         ┌──────────▼───────────────┐
                         │   MockResolverService     │
                         │   1. AntPathMatcher match │
                         │   2. Check mode           │
                         │   3. Evaluate matchers    │
                         │   4. Apply latency        │
                         │   5. Render or proxy      │
                         └──────┬──────────┬────────┘
                                │          │
                   mock found   │          │  no match / disabled
                                ▼          ▼
                         ┌──────────┐  ┌──────────────┐
                         │  Mock    │  │ FeignProxy   │
                         │ Response │  │ → Real Svc   │
                         └──────────┘  └──────────────┘
                                │
                         ┌──────▼──────────────────┐
                         │  PostActionExecutor      │
                         │  (async: delay + SQS)    │
                         └─────────────────────────┘
```

## Requisitos

- Java 25+
- Docker & Docker Compose
- Gradle (wrapper incluido)

## Levantar el entorno

```bash
# 1. Levantar PostgreSQL
docker compose -f fluxy-mock/docker-compose.yml up -d

# 2. Compilar y ejecutar
./gradlew bootRun
```

La aplicación arranca en `http://localhost:8081`.

## Interfaz gráfica

Además de la API de administración, la aplicación expone una UI en la raíz del sitio para:

- listar mocks existentes,
- crear mocks nuevos,
- editar mocks completos,
- activar/desactivar o eliminar endpoints.

Abrila en `http://localhost:8081/` una vez levantado el servicio.

## Guía rápida de uso

### 1. Crear un mock completo en un solo request

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Get User by ID",
    "httpMethod": "GET",
    "mode": "STATIC_JSON",
    "pathPattern": "/users/{id}",
    "enabled": true,
    "responses": [
      {
        "description": "Success response",
        "active": true,
        "httpStatus": 200,
        "responseHeaders": { "X-Mock": "true" },
        "bodyTemplate": "{\"id\": \"{{path.id}}\", \"name\": \"John Doe\", \"uuid\": \"{{uuid}}\"}",
        "latencyMs": 100,
        "requestMatcher": null,
        "postActions": []
      },
      {
        "description": "Not found",
        "active": false,
        "httpStatus": 404,
        "bodyTemplate": "{\"error\": \"User {{path.id}} not found\"}",
        "latencyMs": 0
      }
    ]
  }'
```

### 2. Invocar el mock

```bash
# Mock activo → retorna la respuesta configurada con status 200
curl http://localhost:8081/fluxy/mock/users/42

# Respuesta:
# {"id": "42", "name": "John Doe", "uuid": "a1b2c3d4-..."}
```

### 3. Desactivar el mock → proxy al servicio real

```bash
# Toggle del endpoint (cambia enabled a false)
curl -X PATCH http://localhost:8081/api/mock/admin/endpoints/1/toggle

# Ahora la request va al servicio real (jsonplaceholder)
curl http://localhost:8081/fluxy/mock/users/42
```

### 4. Activar otra respuesta (ej: 404)

```bash
# Activar la respuesta de 404 (responseId = 2)
curl -X PATCH http://localhost:8081/api/mock/admin/endpoints/1/responses/2/activate

# Re-habilitar el endpoint
curl -X PATCH http://localhost:8081/api/mock/admin/endpoints/1/toggle

# Ahora retorna 404
curl http://localhost:8081/fluxy/mock/users/999
# {"error": "User 999 not found"}
```

## Ejemplos por método HTTP

### GET con path variable

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Get Product",
    "httpMethod": "GET",
    "mode": "STATIC_JSON",
    "pathPattern": "/products/{id}",
    "enabled": true,
    "responses": [{
      "active": true, "httpStatus": 200,
      "bodyTemplate": "{\"productId\": \"{{path.id}}\", \"name\": \"Widget\"}"
    }]
  }'

curl http://localhost:8081/fluxy/mock/products/77
# {"productId": "77", "name": "Widget"}
```

### POST con body template y post-acción SQS

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Create Order",
    "httpMethod": "POST",
    "mode": "STATIC_JSON",
    "pathPattern": "/orders",
    "enabled": true,
    "responses": [{
      "active": true, "httpStatus": 201,
      "bodyTemplate": "{\"orderId\": \"{{uuid:orderId}}\", \"status\": \"created\", \"timestamp\": \"{{now}}\"}",
      "latencyMs": 200,
      "requestMatcher": { "matchBodyContains": "\"product\"" },
      "postActions": [{
        "delayMs": 5000,
        "sqsQueueUrl": "http://localhost:4566/000000000000/order-events",
        "sqsMessageTemplate": "{\"event\": \"ORDER_CREATED\", \"orderId\": \"{{orderId}}\"}"
      }]
    }]
  }'

curl -X POST http://localhost:8081/fluxy/mock/orders \
  -H "Content-Type: application/json" \
  -d '{"product": "laptop", "qty": 1}'
# {"orderId": "a1b2c3d4-...", "status": "created", "timestamp": "2026-04-27T..."}
# → 5 seg después se envía mensaje SQS con el MISMO orderId: a1b2c3d4-...
```

### Trigger por evento

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Order event",
    "httpMethod": "POST",
    "triggerType": "EVENT",
    "triggerBinding": "sqs:fluxy-mock-queue",
    "eventTargetType": "KAFKA",
    "eventTargetDestination": "orders-events",
    "mode": "STATIC_JSON",
    "pathPattern": "/events/orders",
    "enabled": true,
    "responses": [{
      "active": true,
      "httpStatus": 200,
      "bodyTemplate": "{\"event\":\"ORDER_CREATED\",\"orderId\":\"{{event.payload}}\"}"
    }]
  }'
```

> `{{uuid:orderId}}` genera el UUID la primera vez y lo guarda como `orderId`.
> El `sqsMessageTemplate` usa `{{orderId}}` para recuperar el mismo valor del contexto compartido.

### PATCH

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Update Product",
    "httpMethod": "PATCH",
    "mode": "STATIC_JSON",
    "pathPattern": "/products/{id}",
    "enabled": true,
    "responses": [{
      "active": true, "httpStatus": 200,
      "bodyTemplate": "{\"id\": \"{{path.id}}\", \"updated\": true, \"at\": \"{{now}}\"}"
    }]
  }'

curl -X PATCH http://localhost:8081/fluxy/mock/products/5 \
  -H "Content-Type: application/json" \
  -d '{"price": 99.99}'
# {"id": "5", "updated": true, "at": "2026-04-19T..."}
```

### DELETE

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Delete Item",
    "httpMethod": "DELETE",
    "mode": "STATIC_JSON",
    "pathPattern": "/items/{id}",
    "enabled": true,
    "responses": [{
      "active": true, "httpStatus": 204, "bodyTemplate": null
    }]
  }'

curl -X DELETE http://localhost:8081/fluxy/mock/items/9
# HTTP 204 No Content
```

### Respuesta con error HTTP (500)

```bash
curl -X POST http://localhost:8081/api/mock/admin/full \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Failing Service",
    "httpMethod": "GET",
    "mode": "STATIC_JSON",
    "pathPattern": "/unstable/{id}",
    "enabled": true,
    "responses": [{
      "active": true, "httpStatus": 500,
      "bodyTemplate": "{\"error\": \"Internal Server Error\", \"traceId\": \"{{uuid}}\"}"
    }]
  }'

curl http://localhost:8081/fluxy/mock/unstable/1
# HTTP 500 → {"error": "Internal Server Error", "traceId": "..."}
```

## Templates dinámicos

| Variable | Descripción | Ejemplo de salida |
|---|---|---|
| `{{path.id}}` | Variable extraída del path | `42` |
| `{{path.orderId}}` | Otra variable del path | `abc-123` |
| `{{query.page}}` | Query param `?page=2` | `2` |
| `{{query.status}}` | Query param `?status=active` | `active` |
| `{{uuid}}` | UUID v4 fijo para el request | `a1b2c3d4-e5f6-...` |
| `{{timestamp}}` | Epoch millis fijo para el request | `1713528000000` |
| `{{now}}` | ISO-8601 timestamp fijo para el request | `2026-04-19T12:00:00Z` |
| `{{id}}` | Shortcut para `{{path.id}}` | `42` |

Las variables del path tienen prioridad sobre las de query params en caso de colisión de nombres.

> **Nota:** `{{uuid}}`, `{{timestamp}}` y `{{now}}` se generan **una sola vez por request** y se comparten entre el body template y todos los templates de post-acciones. Así todos los templates de un mismo request siempre reciben el mismo valor.

### Variables nombradas — `{{generator:alias}}`

Cuando necesitas **múltiples valores generados distintos** y reutilizarlos en varios lugares del mismo request (por ejemplo, el mismo `orderId` en el body HTTP y en el mensaje SQS), usa la sintaxis `{{generator:alias}}`.

| Sintaxis | Comportamiento |
|---|---|
| `{{uuid:orderId}}` | Genera un UUID, lo guarda bajo el nombre `orderId` y lo retorna |
| `{{uuid:itemId}}` | Genera **otro** UUID independiente, guardado como `itemId` |
| `{{orderId}}` | Reutiliza el valor ya generado con alias `orderId` |
| `{{timestamp:ts}}` | Genera un epoch-millis y lo guarda como `ts` |
| `{{now:eventTime}}` | Genera un ISO-8601 y lo guarda como `eventTime` |

**Reglas:**
- Si el alias **no existe** aún en el contexto → genera el valor y lo almacena.
- Si el alias **ya existe** → devuelve el valor cacheado sin generar uno nuevo.
- El contexto es compartido entre el body template y todos los `sqsMessageTemplate` del mismo request.

**Ejemplo — `orderId` consistente entre HTTP body y SQS:**

```json
"bodyTemplate":       "{\"orderId\": \"{{uuid:orderId}}\", \"status\": \"created\"}",
"sqsMessageTemplate": "{\"event\": \"ORDER_CREATED\", \"orderId\": \"{{orderId}}\"}"
```

Ambos templates producirán el **mismo** UUID para `orderId`.

## Request Matchers

Los matchers permiten que un mismo endpoint tenga varias respuestas activas y se elija la correcta según la request entrante. Todos los campos son opcionales — solo se evalúan los definidos.

| Campo | Tipo | Descripción |
|---|---|---|
| `matchHeaders` | `Map<String,String>` | Headers que debe contener la request (subset) |
| `matchQueryParams` | `Map<String,String>` | Query params que debe contener la request |
| `matchBodyContains` | `String` | Substring que debe existir en el body |
| `matchPathVariables` | `Map<String,String>` | Variables del path que deben coincidir exactamente |

## Post-acciones (SQS)

Cada respuesta puede tener N post-acciones que se ejecutan **después** de enviar la respuesta HTTP al cliente:

| Campo | Tipo | Descripción |
|---|---|---|
| `delayMs` | `long` | Milisegundos de espera antes de ejecutar la acción |
| `sqsQueueUrl` | `String` | URL completa de la cola SQS |
| `sqsMessageTemplate` | `String` | Template del mensaje con soporte `{{variable}}` |

## Triggers

| Campo | Tipo | Descripción |
|---|---|---|
| `triggerType` | `HTTP` \| `EVENT` | `HTTP` usa el proxy/controller actual; `EVENT` consume mensajes de listeners |
| `triggerBinding` | `String` | Binding del origen, por ejemplo `sqs:fluxy-mock-queue` o `kafka:orders` |
| `eventTargetType` | `SQS` \| `KAFKA` | Target al que se publica la respuesta en modo evento |
| `eventTargetDestination` | `String` | Cola URL o topic destino para la respuesta |

## API de Administración

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/api/mock/admin/full` | Crear endpoint completo con responses, matchers y post-actions |
| `GET` | `/api/mock/admin/endpoints` | Listar todos los endpoints |
| `GET` | `/api/mock/admin/endpoints/{id}` | Obtener endpoint por ID |
| `PUT` | `/api/mock/admin/endpoints/{id}` | Actualizar endpoint completo |
| `PATCH` | `/api/mock/admin/endpoints/{id}/toggle` | Toggle enabled/disabled |
| `DELETE` | `/api/mock/admin/endpoints/{id}` | Eliminar endpoint |
| `POST` | `/api/mock/admin/endpoints/{id}/responses` | Agregar respuesta |
| `GET` | `/api/mock/admin/endpoints/{id}/responses` | Listar respuestas |
| `PATCH` | `/api/mock/admin/endpoints/{eid}/responses/{rid}/activate` | Activar respuesta |
| `PATCH` | `/api/mock/admin/endpoints/{eid}/responses/{rid}/deactivate` | Desactivar respuesta |
| `DELETE` | `/api/mock/admin/endpoints/{eid}/responses/{rid}` | Eliminar respuesta |
| `POST` | `/api/mock/admin/responses/{rid}/matchers` | Crear/reemplazar matcher |
| `DELETE` | `/api/mock/admin/matchers/{mid}` | Eliminar matcher |
| `POST` | `/api/mock/admin/responses/{rid}/post-actions` | Agregar post-acción |
| `DELETE` | `/api/mock/admin/post-actions/{aid}` | Eliminar post-acción |

`mode` admite `STATIC_JSON`, `PROXY`, `CAPTURE` y `SECURE_CAPTURE`. En modo `STATIC_JSON` se usa `bodyTemplate`; en modo `PROXY` se reenvía la request al `targetBaseUrl`; en modo `CAPTURE` se proxya la primera request y luego se sirve la respuesta cacheada; en modo `SECURE_CAPTURE` solo se cachean respuestas 2xx.

Para modos proxy-style también podés definir `responseJsonFieldOverrides`, un mapa de rutas JSON a templates. Ejemplo: `"user.status": "\"active\""`, `"meta.traceId": "{{uuid}}"`.

## Colección Postman

Importar `docs/fluxy-mock.postman_collection.json` en Postman. Incluye ejemplos para todos los métodos HTTP, creación completa, matchers, post-acciones y requests al proxy. La variable `baseUrl` por defecto apunta a `http://localhost:8081`.

Las variables de colección (`baseUrl`, `endpointId`, `responseId`) se actualizan automáticamente con scripts de test al ejecutar los requests de creación.

## Licencia

Ver [LICENSE](LICENSE).
