export const environment = {
  production: true,
  cognito: {
    // URL del Issuer del User Pool en Cognito
    issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_C1DQHPBOn',

    // App Client ID configurado en AWS Cognito (Cliente Público con PKCE)
    clientId: '5f24qerscjusiu3apprn79gbdc',

    // URI a la cual Cognito redirige tras el login exitoso (lista blanca en Cognito)
    redirectUri: 'http://localhost:4200/',

    // URI a la cual Cognito redirige tras cerrar sesión
    logoutUrl: 'http://localhost:4200/',

    // Flujo Authorization Code con PKCE
    responseType: 'code',

    // Scopes requeridos (OIDC + Scope de Resource Server para la API de pedidos)
    scope: 'openid email profile rs-api-pedidos/pedidos-read',

    // Dominio de Cognito Hosted UI para redirección de login y logout
    domain: 'https://us-east-1c1dqhpbon.auth.us-east-1.amazoncognito.com'
  },
  api: {
    // Endpoint protegido en AWS API Gateway que enruta hacia el backend Spring Boot (PostgreSQL)
    pedidosUrl: 'https://2c0yqndsn0.execute-api.us-east-1.amazonaws.com/test/api/pedidos',

    // Endpoint directo para desarrollo local contra Spring Boot (puerto real del backend)
    localPedidosUrl: 'http://localhost:8888/api/pedidos'
  }
};
