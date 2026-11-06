# Auditoría Técnica — Pedidos 360 (Frontend + Backend + Mensajería)

> **Fecha:** 05-Nov-2026  
> **Alcance:** `pedidos360-frontend`, `pedidos360-backend`, PDFs EP3/EP4, guías RabbitMQ/AWS, `MASTERPLAN.md`, `AUDITORIA.md`  
> **Restricción aplicada:** No se ejecutó la aplicación (AWS / broker aún no configurados por el estudiante).

---

## 1. Resumen ejecutivo

El **backend productor** ya tiene topología RabbitMQ, Outbox y admin REST, pero con fallos críticos de confiabilidad y seguridad. Faltaba el **microservicio consumidor** exigido por EP3 (`varios microservicios` con productores y consumidores). El **frontend Angular** funciona como SPA Cognito + CRUD, pero está desalineado con el contrato real del backend y no apunta a `localhost:8888` en desarrollo.

**Acciones realizadas en esta sesión (ver sección 6):**

1. Informe de hallazgos (este documento).
2. Corrección del productor Rabbit en `pedidos360-backend`.
3. Creación de `pedidos360-consumer` (microservicio de auditoría).
4. Ajustes mínimos del frontend para alinear URL local y contrato.

---

## 2. Contexto de rúbrica (PDFs)

### EP3 — Encargo

- Entrega: código **frontend (Angular)** + **backend (varios microservicios Spring Boot)** vía GitHub.
- Microservicios con **productores y consumidores** RabbitMQ.
- Mensajes no entregados → **DLQ** + logs.
- Admin RabbitMQ: crear/eliminar colas, exchanges y bindings.
- JWT desde IDaaS (Cognito) en front y back.

### EP4 — Presentación

- API Manager, frontend vía Gateway, JWT, Cognito, despliegue cloud.
- Clúster RabbitMQ de **2 nodos**, **3 colas + 3 DLQ**, exchanges **direct** y **topic**, Docker Compose en nube.

### Guía RabbitMQ EA2

- Patrón de referencia: **producer** (HTTP + `RabbitTemplate`) y **consumer** (listeners + ACK/NACK + DLQ) como proyectos independientes, inspirado en `EJEMPLO_DE_RABBIT`.

---

## 3. Hallazgos — Backend (`pedidos360-backend`)

### Críticos

| ID | Problema | Ubicación |
|----|----------|-----------|
| B-C1 | `/api/admin/**` con `permitAll()` — cualquiera puede crear/borrar colas | `SecurityConfig.java` |
| B-C2 | Outbox marca `PUBLICADO` aunque el confirm falle o haga timeout (`confirmado = true` por defecto) | `OutboxPublisherWorker.java` |
| B-C3 | Sin `ReturnsCallback` pese a `mandatory=true` — mensajes sin ruta se pierden | `RabbitConfig` / worker |
| B-C4 | Stub `InventarioConsumer` dentro del productor (sin persistencia en `auditoria_db`); roba mensajes si hay consumer real | `consumer/InventarioConsumer.java` |

### Altos

| ID | Problema |
|----|----------|
| B-A1 | No existía microservicio `pedidos360-consumer` (Fase 6 MASTERPLAN pendiente) |
| B-A2 | Sin `prefetch=1` en listener |
| B-A3 | CORS con `"*"` + `localhost:4200` inconsistente |
| B-A4 | Variables `.env` (`POSTGRES_*`) no alineadas con `DB_*` de Spring |

### Medios / bajos

- Props `rabbit.admin.*` sin uso (solo `AmqpAdmin`).
- Sin DDL versionado (`ddl-auto=update`).
- README obsoleto (aún habla de H2).
- MASTERPLAN marca como `[x]` ítems incompletos (`RAB-06`, `INF-02`, admin híbrido).

### Lo que ya estaba bien

- `RabbitNames` + `RabbitConfig` (Topic, Direct, DLX, 3 colas, 3 DLQ, bindings).
- Outbox en la misma TX que CRUD; snapshot en DELETE.
- Cognito JWT + `ROLE_Admin` en mutaciones de pedidos.
- Admin REST `POST/DELETE /api/admin/queues` con DTOs validados.

---

## 4. Hallazgos — Frontend (`pedidos360-frontend`)

### Críticos / altos

| ID | Problema | Ubicación |
|----|----------|-----------|
| F-C1 | Puerto local documentado/configurado como `:8080`; backend real `:8888`. `localPedidosUrl` no se usaba | `environment*.ts`, `pedidos.service.ts` |
| F-C2 | Campo `descripcion` en interfaz/payload; backend solo tiene 6 campos (`imageUrl`, sin descripción) | `pedidos.service.ts` |
| F-C3 | Estados UI (`En preparación`…) vs backend (`PENDIENTE`, `EN_PREPARACION`, `DISPONIBLE`, `ENTREGADO`) | `app.ts` / `app.html` |
| F-A1 | `environment.development.ts` sin `fileReplacements` en `angular.json` → no se carga en `ng serve` | `angular.json` |
| F-A2 | `isAuthenticated()` puede aceptar tokens expirados | `auth.service.ts` |
| F-A3 | Fallback de login sin PKCE si falla la librería OIDC | `auth.service.ts` |
| F-A4 | Tras POST sin `id` en body, se inventa ID `ORD-…` (rompe PUT/DELETE posteriores) | `app.ts` |

### Medios

- Catálogo mock hardcodeado (contradice `AUDITORIA.md` §6).
- Errores leen `error.error.message`; backend usa `{ "error": "..." }`.
- `aws-amplify` en `package.json` sin uso.
- Checks OIDC relajados (`skipIssuerCheck`, etc.).

### Nota sobre RabbitMQ en el frontend

Según EP3, **Angular no es consumidor AMQP**. El frontend dispara operaciones REST; el **backend productor** publica eventos y el **consumer** los procesa de forma asíncrona. La integración “Rabbit para el frontend” es: el CRUD del front debe poder alcanzar el backend productor (URL/puerto correctos) para que el Outbox genere mensajes.

---

## 5. Cumplimiento aproximado EP3 (antes de los cambios)

| Indicador | % | Antes |
|-----------|---|--------|
| 1. Nombres centralizados | 12% | Cumple |
| 2. Beans Queue/Exchange/Binding | 13% | Cumple |
| 3. Separación de responsabilidades | 10% | Parcial (consumer stub en productor) |
| 4. Consumidores por dominio | 15% | No cumplía |
| 5. ACK/NACK y DLQ | 20% | Topología OK; consumo real incompleto |
| 6. Admin REST | 13% | Endpoints OK; seguridad rota |
| 7. RabbitAdminService | 10% | Cumple |
| 8. Validación DTO | 7% | Cumple |

---

## 6. Cambios realizados en esta sesión

### Backend productor

- Corregida lógica de Publisher Confirms + `ReturnsCallback`.
- `/api/admin/**` restringido a `ROLE_Admin`.
- Eliminado stub `InventarioConsumer` del productor (los listeners viven en el consumer).
- `prefetch=1`, CORS endurecido a `localhost:4200`, `.env.example` con `DB_*` / `RABBITMQ_*`.

### Nuevo: `pedidos360-consumer`

Microservicio Spring Boot 4.1.1 / Java 17, puerto **8889**, BD `auditoria_db`:

- `RabbitNames` + `RabbitConfig` alineados al productor.
- Listeners: inventario, eliminaciones, seguridad.
- ACK manual, reintentos con backoff, NACK → DLQ.
- Idempotencia por `evento_id` UNIQUE.

### Frontend

- `localPedidosUrl` → `http://localhost:8888/api/pedidos`.
- `environment.development.ts` usa URL local como `pedidosUrl`.
- `fileReplacements` en `angular.json` para cargar el environment de desarrollo.
- Interfaz `Pedido` alineada al contrato backend (sin depender de `descripcion`).

---

## 7. Pendiente (no ejecutado / fuera de alcance actual)

- [ ] Configurar AWS (Cognito/Gateway/EC2) — a cargo del estudiante.
- [ ] `docker compose up` y pruebas con RabbitMQ/PostgreSQL locales.
- [ ] Clúster de 2 nodos (`compose.cluster.yaml`) para EP4.

### Estado del trabajo de integración (actualización 05-Nov-2026)

- [x] Endurecer autenticación frontend: no se aceptan tokens expirados, ID Tokens como credencial de API ni fallback de login fuera de PKCE.
- [x] Unificar estados UI/backend: `PENDIENTE`, `EN_PREPARACION`, `DISPONIBLE`, `ENTREGADO`; el backend rechaza valores ajenos al contrato.
- [x] Alinear el contrato de pedidos (seis campos JPA; GET devuelve lista validada; POST/PUT requieren respuesta con ID persistido).
- [x] Eliminar datos mock del frontend y leer errores con el formato `{ "error": "..." }` del backend.
- [x] Corregir el Outbox para que un `Publisher Confirm ACK` con retorno `NO_ROUTE` no se marque como publicado.
- [x] Persistir mensajes de DLQ y sus metadatos en `auditoria_db.rabbit_mensajes_fallidos`; ACK solo tras guardar.
- [x] Actualizar README de backend y consumer para reflejar PostgreSQL, RabbitMQ y el flujo real.

**Validación ejecutada:** build Angular exitoso; 3 pruebas frontend aprobadas; 39 pruebas backend aprobadas (1 omitida); 3 pruebas consumer aprobadas.

Las tres tareas iniciales de la lista son validaciones/infraestructura externa y no se ejecutaron: requieren Docker y la configuración local/cloud que el estudiante debe aportar. No se modificaron environments ni recursos de AWS.

---

## 8. Cómo levantar (cuando la infra local esté lista)

```bash
# 1) Infra
cd pedidos360-backend
docker compose up -d

# 2) Productor (puerto 8888)
./mvnw spring-boot:run

# 3) Consumidor (puerto 8889)
cd ../pedidos360-consumer
./mvnw spring-boot:run

# 4) Frontend
cd ../pedidos360-frontend
npm start
```

Management UI RabbitMQ: `http://localhost:15672` (guest/guest).
