export const environment = {
  production: false,
  cognito: {
    // URL del Issuer del User Pool en Cognito
    issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_8cvtGp65F',

    // App Client ID configurado en AWS Cognito (Cliente Público con PKCE)
    clientId: '1id63767oljtbenjicv5f9sh7l',

    // URI a la cual Cognito redirige tras el login exitoso (lista blanca en Cognito)
    redirectUri: 'http://localhost:4200/',

    // URI a la cual Cognito redirige tras cerrar sesión
    logoutUrl: 'http://localhost:4200/',

    // Flujo Authorization Code con PKCE
    responseType: 'code',

    // Scopes OIDC y del Resource Server pedidos360
    scope: 'openid email profile pedidos360/pedidos.read pedidos360/pedidos.write pedidos360/admin.manage',

    // Grupo de Cognito mapeado al rol de administrador de la aplicación
    adminGroup: 'admin',

    // Dominio de Cognito Hosted UI para redirección de login y logout
    domain: 'https://us-east-18cvtgp65f.auth.us-east-1.amazoncognito.com'
  },
  api: {
    // El frontend corre localmente; backend y consumer están en EC2 detrás de API Gateway.
    pedidosUrl: 'https://3kmb7eji42.execute-api.us-east-1.amazonaws.com/dev/api/pedidos'
  }
};
