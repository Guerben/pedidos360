# Pedidos 360 — Consumer (Auditoría asíncrona)

Microservicio Spring Boot independiente que **consume** eventos RabbitMQ publicados por `pedidos360-backend` y los persiste en `auditoria_db`.

## Rol en la arquitectura

```
Frontend Angular  →  Backend (productor + Outbox)  →  RabbitMQ  →  este consumer
```

| Cola | Listener | Tabla |
|------|----------|-------|
| `cola.auditoria.inventario` | `InventarioConsumer` | `auditoria_inventario` |
| `cola.auditoria.eliminaciones` | `EliminacionesConsumer` | `historico_bajas` |
| `cola.seguridad.alertas` | `SeguridadAlertasConsumer` | `auditoria_seguridad` |

Cada cola principal tiene su DLQ (`*.dlq`) vía exchange `pedidos.dlx`.
Antes del NACK, el consumer persiste el payload y el motivo detallado del fallo en `rabbit_mensajes_fallidos` de `auditoria_db`. Al llegar a DLQ, otro listener completa los metadatos de RabbitMQ y solo confirma el mensaje después de guardar. Si el registro falla, no se emite ACK. Los mensajes que esperan un consumidor permanecen en colas durables de RabbitMQ.

## Requisitos

- Java 17, Maven Wrapper incluido
- PostgreSQL con base `auditoria_db` (creada por `docker-compose` del backend)
- RabbitMQ en `localhost:5672`

## Ejecución (cuando la infra esté lista)

```bash
# Desde pedidos360-backend (si aún no está arriba)
docker compose up -d

# Este microservicio (puerto 8889)
./mvnw spring-boot:run
```

## Política de consumo

- ACK manual solo tras persistencia exitosa
- Hasta 3 reintentos locales con backoff 1s / 2s / 4s
- Errores permanentes o agotamiento → `basicNack(requeue=false)` → DLQ
- Consumidor de DLQ persiste fallos antes de ACK; un fallo de PostgreSQL mantiene el mensaje sin confirmar
- Idempotencia por `evento_id` UNIQUE

## Probar DLQ

Publicar un mensaje cuyo payload contenga `FORCE_ERROR` (o `"ERROR"` en inventario). El listener enviará el mensaje a la DLQ correspondiente.
