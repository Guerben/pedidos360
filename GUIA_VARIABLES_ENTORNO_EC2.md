# Guía de variables de entorno para backend y consumer en EC2

Esta guía explica qué valores configurar en EC2 antes de ejecutar
`pedidos360-backend` y `pedidos360-consumer`. Los dos servicios comparten las
variables de PostgreSQL y RabbitMQ; Cognito y CORS solo aplican al backend.

## 1. Variables que debes completar

| Variable | Se usa en | Valor |
|---|---|---|
| `DB_HOST` | Backend y consumer | Host o DNS privado de PostgreSQL. Usa `127.0.0.1` si PostgreSQL corre en Docker en la misma EC2. |
| `DB_PORT` | Backend y consumer | Puerto PostgreSQL, normalmente `5432`. |
| `DB_USER` / `DB_PASSWORD` | Backend y consumer | Credenciales PostgreSQL con acceso a ambas bases. |
| `RABBITMQ_HOST` | Backend y consumer | Host o DNS privado de RabbitMQ. Usa `127.0.0.1` si está en Docker en la misma EC2. |
| `RABBITMQ_PORT` | Backend y consumer | Puerto AMQP, normalmente `5672`. |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Backend y consumer | Credenciales del broker. |
| `COGNITO_ISSUER_URI` | Solo backend | Issuer del User Pool, con formato `https://cognito-idp.<region>.amazonaws.com/<user-pool-id>`. |
| `CORS_ALLOWED_ORIGINS` | Solo backend | Origen del frontend publicado, por ejemplo `https://app.ejemplo.cl`. Si hay varios, sepáralos por coma. |
| `RABBIT_ADMIN_URL` | Solo backend | URL HTTP(S) de la API de administración RabbitMQ, normalmente `http://<host>:15672/api`. Opcional si se usa ese valor predeterminado. |

El backend usa la base `pedidos360_db` y el consumer usa `auditoria_db`; esos
nombres están definidos en sus archivos `application.properties`. No es
necesario crear una variable para el nombre de la base.

## 2. Guardar la configuración en EC2

En EC2, crea un archivo fuera del repositorio para no incluir credenciales en
Git. Si usarás Docker Compose para PostgreSQL y RabbitMQ, define también las
variables `POSTGRES_*` que aparecen en el ejemplo:

```bash
sudo mkdir -p /etc/pedidos360
sudo nano /etc/pedidos360/pedidos360.env
```

Contenido de ejemplo para PostgreSQL y RabbitMQ en Docker dentro de la misma
EC2:

```dotenv
POSTGRES_USER=pedidos360_app
POSTGRES_PASSWORD=REEMPLAZAR_POR_UNA_CLAVE_ALEATORIA
APP_DATABASES=pedidos360_db,auditoria_db

DB_HOST=127.0.0.1
DB_PORT=5432
DB_USER=pedidos360_app
DB_PASSWORD=REEMPLAZAR_POR_UNA_CLAVE_ALEATORIA

RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_USER=pedidos360_app
RABBITMQ_PASSWORD=REEMPLAZAR_POR_OTRA_CLAVE_ALEATORIA

COGNITO_ISSUER_URI=https://cognito-idp.REGION.amazonaws.com/USER_POOL_ID
CORS_ALLOWED_ORIGINS=https://DOMINIO-PUBLICO-DEL-FRONTEND
RABBIT_ADMIN_URL=http://127.0.0.1:15672/api
```

Reemplaza todos los valores de ejemplo. `POSTGRES_USER` debe coincidir con
`DB_USER`, y `POSTGRES_PASSWORD` con `DB_PASSWORD`. Lo mismo aplica a
`RABBITMQ_USER` y `RABBITMQ_PASSWORD` entre Compose y las aplicaciones. Usa
contraseñas aleatorias fuertes; por ejemplo, puedes generar cada una con
`openssl rand -hex 32`. Las claves hexadecimales también evitan problemas de
interpretación en los archivos de entorno. No reutilices las del ejemplo del
repositorio.

Si PostgreSQL o RabbitMQ son servicios administrados o están en otra máquina,
usa sus DNS privados en `DB_HOST` y `RABBITMQ_HOST`. En ese caso,
`POSTGRES_USER`, `POSTGRES_PASSWORD` y `APP_DATABASES` solo son necesarios si
vas a levantar PostgreSQL local con Compose. `RABBIT_ADMIN_URL` debe apuntar a
la API de administración del broker; no abras esa API a Internet.

Restringe el archivo a root:

```bash
sudo chown root:root /etc/pedidos360/pedidos360.env
sudo chmod 600 /etc/pedidos360/pedidos360.env
```

No subas este archivo a GitHub, no pegues sus valores secretos en tickets o
chats y no guardes credenciales reales en `.env.example`.

## 3. Levantar PostgreSQL y RabbitMQ con Docker Compose

Si ambos servicios de infraestructura se ejecutan en Docker en esta instancia,
pasa el archivo al Compose del backend:

```bash
sudo docker compose \
  --env-file /etc/pedidos360/pedidos360.env \
  -f /opt/pedidos360/pedidos360-backend/docker-compose.yml \
  up -d

sudo docker compose \
  --env-file /etc/pedidos360/pedidos360.env \
  -f /opt/pedidos360/pedidos360-backend/docker-compose.yml \
  ps
```

Actualiza `/opt/pedidos360` si clonaste el repositorio en otra ruta. Espera a
que PostgreSQL y RabbitMQ estén saludables antes de iniciar las aplicaciones.
Las bases se crean con el script de inicialización la primera vez que se crea
el volumen de PostgreSQL.

Compose usa las credenciales de PostgreSQL para inicializar un volumen vacío.
Si el volumen ya contiene datos, cambiar `POSTGRES_PASSWORD` no cambia por sí
solo la contraseña existente en la base. Del mismo modo, si cambias credenciales
de RabbitMQ en una instancia que ya tiene datos, actualiza el usuario del
broker y coordina el cambio con las aplicaciones; no elimines volúmenes para
forzar una inicialización porque podrías perder datos.

## 4. Compilar y ejecutar manualmente

Compila ambos proyectos desde la carpeta del repositorio:

```bash
cd /opt/pedidos360/pedidos360-backend
./mvnw clean package

cd /opt/pedidos360/pedidos360-consumer
./mvnw clean package
```

Para una prueba manual, carga las variables en la terminal antes de arrancar
los servicios. Hazlo en cada sesión/terminal que vayas a usar:

```bash
set -a
source /etc/pedidos360/pedidos360.env
set +a
```

Inicia cada aplicación en una terminal distinta:

```bash
cd /opt/pedidos360/pedidos360-backend
java -jar target/pedidos360-backend-0.0.1-SNAPSHOT.jar
```

```bash
cd /opt/pedidos360/pedidos360-consumer
java -jar target/pedidos360-consumer-0.0.1-SNAPSHOT.jar
```

La exportación de una terminal no sobrevive al cerrar sesión o reiniciar EC2.
Para operar continuamente, configura cada servicio `systemd` con el mismo
archivo, incluyendo estas directivas en su sección `[Service]` y ajustando las
rutas según la instalación:

```ini
EnvironmentFile=/etc/pedidos360/pedidos360.env
WorkingDirectory=/opt/pedidos360/pedidos360-backend
ExecStart=/usr/bin/java -jar /opt/pedidos360/pedidos360-backend/target/pedidos360-backend-0.0.1-SNAPSHOT.jar
Restart=on-failure
```

Para el consumer, cambia `WorkingDirectory` y `ExecStart` a
`/opt/pedidos360/pedidos360-consumer`. Después de crear las unidades, recarga
systemd y habilita/inicia cada una:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pedidos360-backend
sudo systemctl enable --now pedidos360-consumer
sudo systemctl status pedidos360-backend pedidos360-consumer
```

## 5. Valores AWS que no van en estas variables

- `COGNITO_ISSUER_URI` es el issuer del **User Pool**, no el Client ID ni el
  dominio Hosted UI. El backend usa el issuer para validar los JWT.
- El **Client ID**, el dominio Hosted UI y las URLs de callback/salida se
  configuran en `pedidos360-frontend/src/environments/environment.ts` (y en
  `environment.development.ts` para desarrollo local). Cognito también debe
  tener las URLs de callback y salida registradas exactamente en la
  configuración del App Client. El Client ID de una SPA es público; no agregues
  un client secret al frontend.
- La URL de API Gateway se configura en el environment de producción del
  frontend. Actualiza ese valor cuando exista el nuevo API/stage.
- `CORS_ALLOWED_ORIGINS` debe ser el origen del frontend, sin la ruta de una
  página ni `/api`. Configura el mismo origen en CORS de API Gateway si aplica.
- La EC2 necesita salida HTTPS para que Spring Security pueda consultar la
  configuración/JWKS pública de Cognito al validar JWT. No necesita credenciales
  AWS para esa validación.

El backend escucha en `8888`. El consumer escucha en `8889`, pero no requiere
acceso público. Mantén `5432`, `5672` y `15672` fuera de las reglas de entrada
públicas del Security Group.
