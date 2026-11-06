# Guía técnica de configuración y despliegue en un laboratorio AWS

**Proyecto:** Pedidos 360  
**Fecha:** 05-11-2026  
**Alcance:** preparación del entorno, despliegue de backend productor y consumer, configuración de Cognito/API Gateway y publicación del frontend.

## 1. Arquitectura y estado del repositorio

El sistema contiene tres aplicaciones independientes y dos dependencias de infraestructura:

| Componente | Función | Puerto configurado |
|---|---|---:|
| `pedidos360-frontend` | SPA Angular; login OAuth2 Authorization Code + PKCE | 4200 local / HTTPS en hosting |
| `pedidos360-backend` | API CRUD, OAuth2 Resource Server, Outbox y productor RabbitMQ | 8888 |
| `pedidos360-consumer` | Consumo de eventos, auditoría y persistencia de mensajes DLQ | 8889 |
| PostgreSQL 16 | Bases `pedidos360_db` y `auditoria_db` | 5432 |
| RabbitMQ 4 Management | Exchanges, colas y dead-letter queues | 5672 AMQP; 15672 Management |

El Compose existente levanta PostgreSQL y RabbitMQ, no las aplicaciones. No hay Terraform, CloudFormation, Dockerfiles ni manifiestos de despliegue AWS en los archivos revisados. Por eso este documento describe un despliegue de laboratorio en una instancia EC2 Linux; los recursos AWS se deben crear en la cuenta del laboratorio. Las tablas se crean/actualizan con Hibernate (`ddl-auto=update`).

Flujo: navegador → Cognito Hosted UI → API Gateway → productor Spring Boot → PostgreSQL/Outbox → RabbitMQ → consumer Spring Boot → base de auditoría.

## 2. Prerrequisitos

- Cuenta y región AWS del laboratorio; esta implementación está parametrizada actualmente para `us-east-1`.
- AWS CLI configurado con credenciales del laboratorio (o usar la consola AWS); Git.
- Instancia EC2 Linux compatible con Java 17, Docker Engine/Compose y Node.js/npm para compilar. Como referencia de laboratorio, usar una instancia de desarrollo con al menos 2 vCPU y 4 GiB RAM; ajustar a la cuota disponible.
- Repositorio clonado en EC2, o artefactos compilados y copiados a la instancia.
- Dominio HTTPS para publicar el frontend, recomendado para una integración real de Cognito. Para una prueba académica temporal se puede usar el endpoint HTTPS de hosting disponible en el lab.

## 3. Preparar Cognito

Los environments contienen actualmente estos valores de referencia: User Pool `us-east-1_C1DQHPBOn`, App Client `5f24qerscjusiu3apprn79gbdc`, dominio `us-east-1c1dqhpbon.auth.us-east-1.amazoncognito.com`. Confirma que existan y estén habilitados en la cuenta del laboratorio; si son de otra cuenta, reemplázalos en `pedidos360-frontend/src/environments/environment.ts`, `environment.development.ts` y en el issuer del backend.

En el User Pool configura:

1. Un App Client **público, sin client secret**, con Authorization Code grant y PKCE.
2. Scopes `openid`, `email`, `profile` y `rs-api-pedidos/pedidos-read` (este último requiere el Resource Server y scope correspondiente en Cognito).
3. Callback URL exacta del frontend publicado y logout URL exacta. Para desarrollo local agrega `http://localhost:4200/` en ambas listas.
4. Un grupo Cognito llamado `Admin`; asigna al menos un usuario. El backend mapea el claim `cognito:groups` a la autoridad `ROLE_Admin`.
5. Dominio de Hosted UI activo.

No pongas secretos en el frontend. La aplicación usa Access Token para llamar a la API y el ID Token solo para mostrar datos de identidad.

## 4. Preparar la instancia EC2

### Red y seguridad

Ubica EC2, PostgreSQL y RabbitMQ en una VPC/subred del laboratorio. Para una demo de un solo host, ejecuta Docker y ambos servicios de datos en EC2 junto a las aplicaciones.

Reglas de entrada recomendadas para el Security Group:

| Puerto | Origen | Uso |
|---:|---|---|
| 22 | IP pública del administrador `/32` | SSH administrativo |
| 80/443 | Internet o red de demostración | Frontend / proxy HTTPS si se sirve desde EC2 |
| 8888 | Security Group de API Gateway/VPC Link o red privada autorizada | API del productor |

No expongas `5432`, `5672` ni `15672` a Internet. El puerto `8889` no necesita entrada pública: el consumer inicia conexiones salientes a RabbitMQ y PostgreSQL. La forma de conectar API Gateway a una API privada depende del tipo de API y del diseño de red disponible en el laboratorio; para backend público de demo, limita 8888 al rango permitido por el curso y exige JWT también en Spring Security.

Instala Java 17, Docker Engine con Compose plugin y Node.js/npm, según la distribución del host. Verifica con `java -version`, `docker --version`, `docker compose version`, `node --version` y `npm --version`.

## 5. Configurar y levantar PostgreSQL y RabbitMQ

Desde `pedidos360-backend`, crea un archivo `.env` (no lo subas al repositorio):

```dotenv
POSTGRES_USER=pedidos360_app
POSTGRES_PASSWORD=<password-largo-aleatorio>
APP_DATABASES=pedidos360_db,auditoria_db
RABBITMQ_USER=pedidos360_app
RABBITMQ_PASSWORD=<otro-password-largo-aleatorio>
```

En laboratorio, restringe permisos del archivo (`chmod 600 .env`). El Compose da persistencia mediante volúmenes nombrados y ejecuta `infraestructura/init-databases.sh` para crear las dos bases. El script de inicialización solo corre al crear un volumen de datos vacío; cambiar `APP_DATABASES` sobre un volumen ya inicializado no crea bases nuevas automáticamente.

```bash
cd pedidos360-backend
docker compose up -d
docker compose ps
docker compose logs --tail=100 postgres rabbitmq
```

Espera los healthchecks en estado healthy. El panel RabbitMQ, si se consulta desde la propia instancia, está en `http://127.0.0.1:15672`; usa el usuario configurado. El Compose lo enlaza a loopback de la instancia, así que no es accesible remotamente por defecto. Para acceso remoto usa túnel SSH, por ejemplo `ssh -L 15672:127.0.0.1:15672 ec2-user@<IP>` y abre `http://localhost:15672`.

> El Compose expone 5432 y 5672 en las interfaces de la instancia. El Security Group y firewall del host deben bloquear acceso público a esos puertos.

## 6. Configurar y ejecutar los microservicios

Ambos servicios leen las mismas variables de conexión; en EC2 establece variables de entorno o usa un gestor de secretos del laboratorio. No uses credenciales de ejemplo `postgres/postgres` ni `guest/guest` en un despliegue compartido.

```bash
export DB_HOST=127.0.0.1
export DB_PORT=5432
export DB_USER=pedidos360_app
export DB_PASSWORD='<password de PostgreSQL>'
export RABBITMQ_HOST=127.0.0.1
export RABBITMQ_PORT=5672
export RABBITMQ_USER=pedidos360_app
export RABBITMQ_PASSWORD='<password de RabbitMQ>'
```

El productor valida JWT contra el issuer configurado en `pedidos360-backend/src/main/resources/application.properties`. Confirma que el issuer corresponda al User Pool usado arriba.

Compila y ejecuta en procesos/terminales separados para la primera puesta en marcha:

```bash
cd pedidos360-backend
./mvnw clean package -DskipTests
java -jar target/*.jar
```

```bash
cd pedidos360-consumer
./mvnw clean package -DskipTests
java -jar target/*.jar
```

En Windows, el wrapper es `mvnw.cmd`; en Linux usa `./mvnw`. Para operación continua, gestiona los JAR como servicios systemd o contenedores con política de reinicio y logs persistentes. No ejecutes los dos JAR con el mismo puerto: productor 8888, consumer 8889. El consumer no expone una API que deba publicarse; su trabajo es escuchar RabbitMQ.

Al iniciar, Spring crea/actualiza tablas en `pedidos360_db` y `auditoria_db`. Revisa logs de ambos procesos y que RabbitMQ muestre conexiones/canales. Los exchanges, colas y bindings se declaran por la configuración de RabbitMQ de los servicios.

## 7. API Gateway hacia EC2

El frontend referencia `https://2c0yqndsn0.execute-api.us-east-1.amazonaws.com/test/api/pedidos`; es un endpoint existente según los README, pero la implementación de infraestructura de API Gateway no está versionada aquí. En el laboratorio:

1. Crea o adapta una API y el stage esperado por la URL configurada.
2. Enruta `/api/pedidos` y `/api/pedidos/{id}` con `GET`, `POST`, `PUT`, `DELETE` hacia el backend en puerto 8888. Configura integración proxy/rutas para que el path final recibido por Spring sea `/api/pedidos...` (sin duplicar ni eliminar `/api`).
3. Añade autorización JWT/Cognito si el tipo de API lo soporta en el diseño del curso. Spring Resource Server mantiene su propia validación de firma, issuer y autorización, por lo que API Gateway no reemplaza la seguridad del backend.
4. Configura método `OPTIONS`/CORS para el dominio frontend, permitiendo `Authorization`, `Content-Type` y los métodos CRUD usados. El backend permite actualmente el origen local `http://localhost:4200`; ajusta la configuración CORS del backend para el origen HTTPS definitivo del frontend antes de usarlo desde navegador.
5. Si EC2 no es alcanzable directamente desde API Gateway, crea una integración privada apropiada (por ejemplo, VPC Link con un balanceador interno) de acuerdo con los recursos habilitados en el laboratorio. No publiques PostgreSQL ni RabbitMQ para resolver conectividad.

La URL de API Gateway escrita en environment es un valor de referencia, no evidencia de que el stage o integración estén activos en la cuenta actual. Reemplázala al crear los recursos.

## 8. Compilar y publicar Angular

Actualiza `environment.ts` con:

- issuer, client ID y dominio del User Pool del laboratorio;
- callback/logout URL del hosting;
- URL HTTPS de API Gateway terminada en `/api/pedidos`.

En desarrollo local, `environment.development.ts` apunta a `http://localhost:8888/api/pedidos`. En `pedidos360-frontend`:

```bash
npm ci
npm run build
```

Publica el contenido generado en `dist/` en el hosting HTTPS aprobado por el laboratorio (por ejemplo, S3 con CloudFront si están habilitados). Configura fallback SPA a `index.html`, TLS y el mime type correcto. Añade el dominio de distribución a callback/logout URLs de Cognito y al CORS del backend/API Gateway. `ng serve` es solo para desarrollo:

```bash
npm start
```

## 9. Arranque local de referencia

Para desarrollar con backend, consumer y frontend en el mismo equipo, primero levanta Docker Compose como en la sección 5. Luego:

```bash
# Terminal 1, pedidos360-backend
./mvnw spring-boot:run

# Terminal 2, pedidos360-consumer
./mvnw spring-boot:run

# Terminal 3, pedidos360-frontend
npm ci
npm start
```

En Windows reemplaza `./mvnw` por `mvnw.cmd`. El environment de desarrollo usa Cognito real y requiere callback `http://localhost:4200/` configurado en el User Pool. El frontend local consume directamente `localhost:8888`, no API Gateway.

## 10. Comprobaciones de puesta en marcha

- EC2: `docker compose ps` muestra PostgreSQL y RabbitMQ healthy.
- Backend: logs indican conexión a `pedidos360_db` y RabbitMQ; el proceso escucha 8888.
- Consumer: logs indican conexión a `auditoria_db` y RabbitMQ; escucha eventos sin errores de autorización de cola.
- Cognito: login redirige al callback exacto, Access Token vigente y grupo Admin visible en claims.
- API: `GET /api/pedidos` sin token debe responder 401; token válido no-admin puede consultar; POST/PUT/DELETE requieren grupo Admin.
- Gateway/CORS: la petición desde el navegador alcanza el backend y el preflight OPTIONS no es rechazado.
- Mensajería: una mutación autorizada escribe Outbox; el worker publica el evento y el consumer persiste auditoría. Un fallo rechazado se registra por el flujo DLQ.

No se incluye aquí una prueba ejecutada: requiere credenciales, red y recursos AWS de la cuenta del laboratorio.

## 11. Operación, limpieza y notas de seguridad

- Mantén credenciales fuera de Git y limita IAM, Security Groups y acceso SSH a lo necesario.
- Conserva respaldos de los volúmenes PostgreSQL; `docker compose down` conserva volúmenes, mientras `docker compose down -v` los elimina junto con datos.
- Para detener los servicios de datos sin borrar datos: `docker compose down`.
- Revisa logs de Spring, Docker y RabbitMQ; configura rotación de logs y alarmas básicas si el laboratorio lo permite.
- No uses `guest/guest`, contraseñas de ejemplo, HTTP público ni credenciales compartidas en una demostración accesible por Internet.
- El repositorio no contiene automatización para crear AWS, TLS, API Gateway, red privada ni hosting. Documenta los IDs/ARN, stage y dominios creados por el laboratorio en un registro separado, sin secretos.

## 12. Referencias internas

- `AUDITORIA_HALLAZGOS.md`: hallazgos, cambios y validaciones registrados.
- `pedidos360-backend/README.md` y `pedidos360-backend/MASTERPLAN.md`: contratos, seguridad, Outbox y topología.
- `pedidos360-consumer/README.md`: consumidores y DLQ.
- `pedidos360-frontend/README.md` y `pedidos360-frontend/AUDITORIA.md`: configuración Angular, Cognito y API.
