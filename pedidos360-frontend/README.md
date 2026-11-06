# Pedidos 360 - Frontend (Admin Console)

Panel de administración y control de pedidos tecnológicos para la plataforma **Pedidos 360**, desarrollado con **Angular 22**, **TypeScript** y autenticación corporativa con **AWS Cognito**.

Este repositorio corresponde exclusivamente al **Frontend**.

---

## 🏗️ Arquitectura del Sistema

* **Frontend:** Single Page Application (SPA) desarrollada en Angular 22 (Standalone Components) y estilos modernos Dark Mode con Glassmorphism.
* **Autenticación & Autorización:** OpenID Connect (OIDC) / OAuth 2.0 con flujo **Authorization Code + PKCE** mediante AWS Cognito User Pools y Hosted UI.
* **API Gateway:** AWS API Gateway para enrutamiento y protección de endpoints mediante autorización OAuth2 Bearer Tokens.
* **Backend Definitivo:** Java 17 + Spring Boot (OAuth2 Resource Server) + Base de Datos Relacional PostgreSQL.

---

## 🚀 Requisitos Previos

* **Node.js:** Versión 18+ o superior (recomendado Node 20 LTS o 24).
* **Gestor de Paquetes:** `npm` versión 10+.
* **Angular CLI:** Versión 22.1.7 (`npm install -g @angular/cli`).

---

## 🛠️ Instalación y Ejecución

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Vi8let/pedidos360-frontend.git
   cd pedidos360-frontend
   ```

2. Instalar dependencias:
   ```bash
   npm install
   ```

3. Iniciar el servidor de desarrollo:
   ```bash
   npm start
   # O alternativamente:
   ng serve
   ```
   Abrir `http://localhost:4200/` en el navegador.

El profile de desarrollo de Angular apunta al productor REST en `http://localhost:8888/api/pedidos`. Los estados válidos son `PENDIENTE`, `EN_PREPARACION`, `DISPONIBLE` y `ENTREGADO`. El frontend utiliza el ID devuelto por PostgreSQL y no mantiene un catálogo de pedidos simulado.

---

## 🧪 Pruebas y Compilación

* **Ejecutar Pruebas Unitarias:**
  ```bash
  npm test -- --no-watch
  ```

* **Compilar para Producción:**
  ```bash
  npm run build
  ```
  Los archivos compilados y optimizados se generarán en el directorio `dist/pedidos360-front`.

---

## 🔐 Configuración de Seguridad y Ambientes

Los parámetros de integración con AWS Cognito y el API Gateway se encuentran en:
* `src/environments/environment.ts` (Producción / Default)
* `src/environments/environment.development.ts` (Desarrollo Local)

Al cambiar de cuenta o ambiente AWS, actualiza issuer, Client ID público, dominio Hosted UI,
scopes, grupo administrador y URL de API Gateway en el environment Angular correspondiente.
`npm start` usa el environment de desarrollo; `npm run build` genera el build productivo.
El build productivo calcula callback y cierre de sesión con el origen actual del sitio, que
debe estar registrado en el App Client de Cognito. No agregues client secrets ni credenciales
AWS al frontend.

El Resource Server Cognito de este ambiente debe tener Identifier `pedidos360` y tres
custom scopes: `pedidos.read`, `pedidos.write` y `admin.manage`. El Access Token pide los
scopes completos `pedidos360/pedidos.read`, `pedidos360/pedidos.write` y
`pedidos360/admin.manage`; API Gateway debe asociar read a los GET de pedidos, write a
POST/PUT/DELETE de pedidos y admin.manage a las rutas `/api/admin/**`. El backend verifica
además el grupo Cognito `Admin` para escrituras y administración.

Para más detalles sobre los contratos de datos, roles (`Admin`), endpoints y validaciones con el backend Spring Boot, consultar [`AUDITORIA.md`](./AUDITORIA.md).
