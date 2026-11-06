export const environment = {
  production: true,
  cognito: {
    // URL del Issuer del User Pool en Cognito
    issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_8cvtGp65F',

    // App Client ID configurado en AWS Cognito (Cliente Público con PKCE)
    clientId: '1id63767oljtbenjicv5f9sh7l',

    // Debe estar registrado como callback URL permitido en el App Client de Cognito
    redirectUri: `${window.location.origin}/`,

    // Debe estar registrado como sign-out URL permitido en el App Client de Cognito
    logoutUrl: `${window.location.origin}/`,

    // Flujo Authorization Code con PKCE
    responseType: 'code',

    // Scopes OIDC y del Resource Server pedidos360
    scope: 'openid email profile pedidos360/pedidos.read pedidos360/pedidos.write pedidos360/admin.manage',

    // Grupo de Cognito mapeado al rol de administrador de la aplicación
    adminGroup: 'Admin',

    // Dominio de Cognito Hosted UI para redirección de login y logout
    domain: 'https://us-east-18cvtgp65f.auth.us-east-1.amazoncognito.com'
  },
  api: {
    // Endpoint protegido en AWS API Gateway que enruta hacia el backend Spring Boot (PostgreSQL)
    pedidosUrl: 'https://3kmb7eji42.execute-api.us-east-1.amazonaws.com/dev/api/pedidos',

    // Endpoint directo para desarrollo local contra Spring Boot (puerto real del backend)
    localPedidosUrl: 'http://localhost:8888/api/pedidos'
  }
};
