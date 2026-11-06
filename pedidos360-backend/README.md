# Pedidos 360 - Sistema de Gestión de Inventario y Catálogo Tecnológico

> **Asignatura:** Desarrollo Cloud Native I (DSY1107) — Duoc UC  
> **Documento:** Especificación Arquitectónica y Auditoría Técnica Definitiva (EP3 / EP4)  
> **Estado del Repositorio:** Backend Spring Boot con CRUD PostgreSQL, OAuth2 Resource Server, RBAC y productor RabbitMQ con Transactional Outbox.  
> **Consumidor:** El microservicio independiente `pedidos360-consumer` persiste auditoría y mensajes fallidos de DLQ.

---

## 📌 PARTE 1: ESTADO ACTUAL DEL PROYECTO (LO IMPLEMENTADO)

### 1. Naturaleza Real del Sistema

El proyecto opera como un **Backoffice de Catálogo e Inventario de Productos Tecnológicos** (servidores, notebooks, tarjetas gráficas, periféricos).

- **Alcance Operativo Real:** Gestión de existencias y catálogo mediante operaciones CRUD (`GET`, `POST`, `PUT`, `DELETE`).
- **Límites Funcionales:** No incluye carrito de compras, catálogo público, pasarelas de pago, facturación electrónica, cálculo de impuestos ni logística de envíos.

### 2. Stack Tecnológico Real Verificado en Repositorio

- **Lenguaje:** Java 17.
- **Framework:** Spring Boot `4.1.1` (parent en `pom.xml`), compilando con **Spring Framework `7.0.9`** (según dependencias Maven resueltas).
  - _Compatibilidad comprobada:_ La clase `org.springframework.web.client.RestClient` (introducida en Spring 6.1) y Spring AMQP son plenamente compatibles.
- **Persistencia:** PostgreSQL 16 (`pedidos360_db` para pedidos/Outbox y `auditoria_db` para auditoría).
- **Seguridad e Identidad:** Spring Security configurado como OAuth 2.0 Resource Server, validando tokens JWT emitidos por Amazon Cognito User Pool `us-east-1_C1DQHPBOn`.
- **Frontend Vinculado (`pedidos360-frontend`):** SPA Angular 22 con `angular-oauth2-oidc` implementando Authorization Code Flow con PKCE.

### 3. Entidad Real `Pedido.java` (Comparación con la Base de Datos)

La clase `cl.duoc.pedidos360_backend.model.Pedido` posee exactamente los siguientes 6 atributos:

- `Long id` (`@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`).
- `String producto` (`@Column(name = "producto", nullable = false)`).
- `String imageUrl` (`@Column(name = "image_url", length = 2048, columnDefinition = "TEXT", nullable = false)`).
- `int cantidad` (`@Column(name = "cantidad", nullable = false)`).
- `int stock` (`@Column(name = "stock", nullable = false)`).
- `String estado` (`@Column(name = "estado", nullable = false, length = 50)`).

> **Ajuste Técnico Importante:** La entidad `Pedido.java` **no contiene columnas de auditoría** como `creado_en` o `actualizado_en`. La estructura SQL en PostgreSQL debe ser estrictamente idéntica a los atributos de la entidad:

```sql
CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    producto VARCHAR(255) NOT NULL,
    image_url TEXT NOT NULL,
    cantidad INTEGER NOT NULL,
    stock INTEGER NOT NULL,
    estado VARCHAR(50) NOT NULL
);
```

Las marcas temporales de trazabilidad pertenecen al dominio de auditoría y se gestionan en `outbox_eventos`, `auditoria_inventario`, `historico_bajas`, `auditoria_seguridad` y `rabbit_mensajes_fallidos`.

### 4. Endpoints REST Existentes (`/api/pedidos`)

- `GET /api/pedidos`: Lista de existencias.
- `GET /api/pedidos/{id}`: Detalle de producto por ID (404 si no existe).
- `POST /api/pedidos`: Registra un producto (201 Created, 400 Bad Request si `imageUrl` falta).
- `PUT /api/pedidos/{id}`: Actualiza campos de producto, cantidad, stock o estado (200 OK, 404 si no existe).
- `DELETE /api/pedidos/{id}`: Elimina físicamente el producto del inventario (204 No Content).

---

## 🔐 PARTE 2: SEGURIDAD, ROLES Y AUDITORÍA DE IDENTIDAD COGNITO

### 1. Autorización Real en el Backend (Grupo `Admin`)

Actualmente el backend utiliza `.authenticated()`, dejando desprotegidas las mutaciones si se invocan directamente vía Postman.  
Para asegurar el sistema, se implementa autorización basada en roles (RBAC) en `SecurityConfig`:

```
[ Petición HTTP con JWT ] ──► [ JwtAuthenticationConverter ]
                                        │
                         Extrae claim 'cognito:groups'
                         Mapea grupo "Admin" ──► "ROLE_Admin"
                                        │
                                        ▼
                      [ SecurityFilterChain (Spring Security) ]
                         ├── GET /api/pedidos/**  ──► .authenticated()
                         └── POST/PUT/DELETE      ──► .hasRole("Admin")
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
             Tiene ROLE_Admin                       NO tiene ROLE_Admin
                    │                                       │
            Permite Ejecución                        HTTP 403 Forbidden
              (200 / 201)                                   │
                                                            ▼
                                              [ AccessDeniedHandler ]
                                            (Publica seguridad.acceso_denegado)
```

- `GET /api/pedidos/**` ➔ Permitido para cualquier usuario autenticado (`.authenticated()`).
- `POST`, `PUT`, `DELETE /api/pedidos/**` ➔ Restringido estrictamente a `.hasRole("Admin")`.

### 2. Identificación Estable del Usuario en Auditoría: `sub` vs `email`

- **Identificador Estable (`sub`):** El estándar OpenID Connect y Cognito definen el claim `sub` (Subject - UUID) como el identificador único, inmutable y garantizado de un usuario. **La auditoría utiliza `sub` como identificador principal.**
- **Correo Electrónico (`email`):** Es un atributo opcional y mutable. En tokens de acceso (`token_use=access`), el claim `email` comúnmente no está presente a menos que se configure explícitamente en el scope. Por ello, la auditoría solo extrae `email` de forma condicional (`jwt.hasClaim("email") ? jwt.getClaimAsString("email") : null`) sin asumir su presencia obligatoria.

### 3. Tratamiento de Violaciones de Seguridad: HTTP 403 vs HTTP 401

- **HTTP 403 Forbidden (Acceso Denegado):** Ocurre cuando el usuario está autenticado válidamente en Cognito, pero su token no tiene el rol `Admin`. Se captura mediante `AccessDeniedHandler`.  
  _Datos disponibles:_ `sub` del usuario, método HTTP, URI intentada, IP de origen y timestamp.
- **HTTP 401 Unauthorized (No Autenticado):** Ocurre por ausencia de token, firma inválida o expiración. Se captura mediante `AuthenticationEntryPoint`.  
  _Datos disponibles:_ IP de origen, método, URI y motivo del rechazo (`TOKEN_EXPIRADO`, `TOKEN_FALTANTE`). No existe identidad de usuario que registrar.
- **Privacidad:** Ningún evento almacena contraseñas, hashes ni tokens JWT completos.

---

## 🧱 PARTE 3: PERSISTENCIA Y MENSAJERÍA ASÍNCRONA (FASE EP3 / EP4)

### 1. Modelo de Persistencia: PostgreSQL (Dos Bases de Datos Lógicas)

Se utiliza **PostgreSQL 16 en Docker**, separando responsabilidades:

```
┌────────────────────────────────────────────────────────┐
│             Servidor PostgreSQL (Docker)               │
├──────────────────────────┬─────────────────────────────┤
│   Base: pedidos360_db    │     Base: auditoria_db      │
│  (Microservicio Backend) │   (Microservicio Consumer)  │
├──────────────────────────┼─────────────────────────────┤
│  - pedidos (Catálogo)    │  - auditoria_inventario     │
│  - outbox_eventos        │  - historico_bajas          │
│    (Garantía de entrega) │  - auditoria_seguridad      │
└──────────────────────────┴─────────────────────────────┘
```

#### Script de Inicialización Oficial (`init-databases.sh`)

La imagen oficial `postgres:16-alpine` solo crea por defecto la base indicada en `POSTGRES_DB`. Para crear ambas bases lógicas al inicio, se utiliza un script en `/docker-entrypoint-initdb.d/init-databases.sh`:

_Distinción de variables de entorno:_

- **Nativas de PostgreSQL:** `POSTGRES_USER`, `POSTGRES_PASSWORD` (reconocidas directamente por el motor).
- **Personalizada del Script:** `APP_DATABASES="pedidos360_db,auditoria_db"` (leída por el script para iterar la creación).

```bash
#!/bin/bash
set -e
set -u
function create_database() {
    local db=$1
    echo "  [INIT] Creando base de datos '$db'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        SELECT 'CREATE DATABASE $db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
}
if [ -n "${APP_DATABASES:-}" ]; then
    for database in $(echo $APP_DATABASES | tr ',' ' '); do
        create_database $database
    done
fi
```

---

### 2. Confiabilidad y Patrón Outbox: Entrega At-Least-Once

En sistemas distribuidos **no existe la "cero pérdida absoluta" de eventos** ante fallos catastróficos de hardware. La arquitectura garantiza **At-Least-Once Delivery** (entrega al menos una vez):

```
[ Petición Admin ] ──► [ Transacción ACID en pedidos360_db ]
                            ├── 1. INSERT/UPDATE/DELETE en 'pedidos'
                            └── 2. INSERT en 'outbox_eventos' (Estado: PENDIENTE)
                                                │ (Commit exitoso)
                                                ▼
                                    [ Outbox Publisher Worker ]
                                                │
                                    (Publisher Confirms + Mandatory)
                                                ▼
                                    [ Publica en RabbitMQ ]
                                                │
                             ┌──────────────────┴──────────────────┐
                             │ ACK Broker                          │ NACK / Sin ruta
                             ▼                                     ▼
                  UPDATE 'outbox_eventos'                 Mantiene 'PENDIENTE'
                  (Estado: PUBLICADO)                     (Reintenta con backoff)
```

1. **Publisher Confirms y Mensajes no Enrutados:**
   - RabbitMQ confirma con ACK cuando el mensaje persiste en cola duradera.
   - Se activa `spring.rabbitmq.template.mandatory=true` con un `ReturnsCallback`; un mensaje retornado no se marca como publicado en el Outbox aunque el broker confirme su publicación al exchange.
2. **Posibilidad de Duplicados:** Si RabbitMQ guarda el mensaje pero el ACK se pierde por un corte de red, el Outbox worker reintentará el envío. El mensaje se entregará duplicado a RabbitMQ.
3. **Idempotencia en el Consumidor:** El consumidor evita registros duplicados en `auditoria_db` comprobando la clave única `evento_id` (UUID). Si el ID ya existe, emite `basicAck` y descarta la inserción repetida.

---

### 3. Preservación de Datos en Eliminaciones (`DELETE`)

En `PedidoService.java`, el método `eliminarPedido(Long id)` recupera la entidad completa antes de la eliminación:

```java
@Transactional
public void eliminarPedido(Long id) {
    // 1. Se obtiene la entidad con todos sus datos en memoria
    Pedido pedido = obtenerPorId(id);

    // 2. Se serializa la instantánea del producto en el payload del evento Outbox
    outboxService.registrarEvento(
        "bajas.producto.eliminado",
        new ProductoEliminadoDTO(pedido.getId(), pedido.getProducto(), pedido.getStock(), usuarioSub)
    );

    // 3. Se ejecuta el borrado físico en la tabla pedidos
    pedidoRepository.delete(pedido);
}
```

Incluso tras desaparecer el registro de la tabla `pedidos`, los datos quedan íntegros en el payload JSON de `outbox_eventos` y viajan a `historico_bajas`.

---

### 4. Topología RabbitMQ: Exchanges, Routing Keys, Colas y DLQ

```
                                  [ EVENTO EN BACKEND ]
                                            │
               ┌────────────────────────────┴────────────────────────────┐
               ▼                                                         ▼
  ┌───────────────────────────────┐                       ┌───────────────────────────────┐
  │         TOPIC EXCHANGE        │                       │        DIRECT EXCHANGE        │
  │    (pedidos.auditoria.topic)  │                       │   (pedidos.seguridad.direct)  │
  └───────────────┬───────────────┘                       └───────────────┬───────────────┘
                  │                                                       │
      Routing Keys dinámicas:                                     Routing Key exacta:
  - inventario.producto.creado                                - seguridad.acceso_denegado
  - inventario.producto.modificado                                        │
  - bajas.producto.eliminado                                              │
                  │                                                       │
        ┌─────────┴─────────┐                                             │
        │ Binding:          │ Binding:                                    │ Binding:
        │ "inventario.      │ "bajas.                                     │ "seguridad.
        │  producto.*"      │  producto.*"                                │  acceso_denegado"
        ▼                   ▼                                             ▼
┌──────────────────┐ ┌──────────────────┐                       ┌──────────────────┐
│  cola.auditoria  │ │  cola.auditoria  │                       │  cola.seguridad  │
│    .inventario   │ │  .eliminaciones  │                       │     .alertas     │
└─────────┬────────┘ └─────────┬────────┘                       └─────────┬────────┘
          │                    │                                          │
          └────────────────────┼──────────────────────────────────────────┘
                               │ NACK (requeue=false tras falla irrecuperable)
                               ▼
                    ┌─────────────────────┐
                    │ DIRECT EXCHANGE DLX │
                    │    (pedidos.dlx)    │
                    └──────────┬──────────┘
                               │
            ┌──────────────────┼──────────────────┐
            │ dlq.inventario   │ dlq.eliminaciones│ dlq.seguridad
            ▼                  ▼                  ▼
     ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
     │cola.inven.  │    │cola.elim.   │    │cola.alertas.│
     │     dlq     │    │     dlq     │    │     dlq     │
     └─────────────┘    └─────────────┘    └─────────────┘
```

#### Especificación Detallada de Enrutamiento y DLQ

| Cola Principal                     | Argumentos de Cola (DLX / DLQ)                                                                    | Routing Key de Publicación                                       | Cola DLQ Asociada                  | Binding DLQ                                   |
| :--------------------------------- | :------------------------------------------------------------------------------------------------ | :--------------------------------------------------------------- | :--------------------------------- | :-------------------------------------------- |
| **`cola.auditoria.inventario`**    | `x-dead-letter-exchange: pedidos.dlx`<br>`x-dead-letter-routing-key: dlq.auditoria.inventario`    | `inventario.producto.creado`<br>`inventario.producto.modificado` | `cola.auditoria.inventario.dlq`    | `pedidos.dlx` ➔ `dlq.auditoria.inventario`    |
| **`cola.auditoria.eliminaciones`** | `x-dead-letter-exchange: pedidos.dlx`<br>`x-dead-letter-routing-key: dlq.auditoria.eliminaciones` | `bajas.producto.eliminado`                                       | `cola.auditoria.eliminaciones.dlq` | `pedidos.dlx` ➔ `dlq.auditoria.eliminaciones` |
| **`cola.seguridad.alertas`**       | `x-dead-letter-exchange: pedidos.dlx`<br>`x-dead-letter-routing-key: dlq.seguridad.alertas`       | `seguridad.acceso_denegado`                                      | `cola.seguridad.alertas.dlq`       | `pedidos.dlx` ➔ `dlq.seguridad.alertas`       |

#### ¿Cómo opera el desvío a DLQ con `basicNack`?

Cuando el consumidor ejecuta:

```java
channel.basicNack(deliveryTag, false, false); // requeue = false
```

RabbitMQ detecta que `requeue=false` y que la cola tiene definido el argumento `x-dead-letter-exchange`. En lugar de eliminar el mensaje, lo transfiere automáticamente a `pedidos.dlx`, reemplazando su routing key por la configurada en `x-dead-letter-routing-key`. De esta forma, el mensaje se deposita en la DLQ correspondiente sin bucles infinitos de reintento.

### 5. Política de Reintentos en el Consumidor

- **Errores Transitorios (ej. PostgreSQL temporalmente saturado):** Se ejecutan hasta 3 reintentos locales con pausas escalonadas (1s, 2s, 4s). Si la base de datos se recupera, se confirma con `basicAck`.
- **Errores Permanentes (ej. JSON malformado, violación de tipos, o agotamiento de los 3 reintentos):** Se rechaza el mensaje con `basicNack(requeue=false)`. El mensaje va a la DLQ. **Bajo ninguna circunstancia se utiliza `requeue=true`**, evitando saturación al 100% de CPU por bucles infinitos (_poison pill_).

---

## 📋 PARTE 4: DIFERENCIACIÓN ENTRE RÚBRICA EP3 Y PRESENTACIÓN EP4

| Criterio                                              | Evaluado en EP3 (Código y Arquitectura) | Evaluado en EP4 (Presentación e Infraestructura Cloud)  |
| :---------------------------------------------------- | :-------------------------------------: | :-----------------------------------------------------: |
| **Colas, Exchanges y Bindings declarados como Beans** |              **SÍ (13%)**               |         Evidencia visual en RabbitMQ Management         |
| **Nombres Centralizados (`RabbitNames.java`)**        |              **SÍ (12%)**               |                Revisión de código fuente                |
| **Consumidores con `@RabbitListener`**                |              **SÍ (15%)**               |              Ejecución activa de listeners              |
| **Confirmación Manual ACK/NACK y DLQ**                |              **SÍ (20%)**               |           Prueba de desvío con error simulado           |
| **Administración REST (Controller + Service + DTOs)** |         **SÍ (30% combinado)**          |            Ejecución de endpoints en Postman            |
| **Exactamente 3 Colas con sus 3 DLQs**                |            Diseño compatible            |  **SÍ (Requisito explícito en pauta de presentación)**  |
| **Clúster de 2 Nodos RabbitMQ**                       |           No evaluado en EP3            |    **SÍ (Demostración de `cluster_status` en EC2)**     |
| **Despliegue integral en AWS (EC2 + Gateway)**        |         Opcional/En desarrollo          | **SÍ (Demostración con frontend y backend en la nube)** |

---

## 🛠️ Ejecución local

```bash
# Desde pedidos360-backend: PostgreSQL y RabbitMQ
docker compose up -d

# Backend productor (puerto 8888)
./mvnw spring-boot:run

# En otra terminal, desde pedidos360-consumer (puerto 8889)
./mvnw spring-boot:run

# En otra terminal, desde pedidos360-frontend
npm start
```

En Windows se puede usar `mvnw.cmd` en lugar de `./mvnw`. El frontend de desarrollo usa `http://localhost:8888/api/pedidos`; la URL productiva de AWS permanece en el environment de producción y no forma parte de esta configuración local.

El consumidor registra en `auditoria_db.rabbit_mensajes_fallidos` el payload y el motivo detallado antes de enviar el NACK; el listener de DLQ añade la cola de destino, routing key y motivo de RabbitMQ. Solo confirma el mensaje de DLQ después de persistir el registro; si PostgreSQL no está disponible, el ACK no se emite. Los mensajes que aún no tengan consumidor siguen retenidos por las colas durables de RabbitMQ y el volumen `rabbitmq_data`.
