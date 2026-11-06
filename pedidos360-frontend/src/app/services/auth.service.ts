import { Injectable, inject } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { BehaviorSubject } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private oauthService = inject(OAuthService);

  /**
   * Estado reactivo de autenticación para sincronización inmediata de la interfaz
   */
  public autenticado$ = new BehaviorSubject<boolean>(false);

  /**
   * Promesa que resuelve una vez completada la carga del Discovery Document
   * y el intercambio de código PKCE por tokens
   */
  public tryLoginPromise!: Promise<boolean>;

  /**
   * Flujo de eventos observables de angular-oauth2-oidc
   */
  get events() {
    return this.oauthService.events;
  }

  constructor() {
    this.configureOAuth();
  }

  private configureOAuth(): void {
    const authConfig: AuthConfig = {
      // Emisor OpenID Connect de Cognito
      issuer: environment.cognito.issuer,

      // Endpoint explícito de autorización en Cognito Hosted UI para redirección inmediata
      loginUrl: `${environment.cognito.domain}/oauth2/authorize`,

      // Endpoint para canje de tokens con PKCE
      tokenEndpoint: `${environment.cognito.domain}/oauth2/token`,

      // Endpoint de información de usuario
      userinfoEndpoint: `${environment.cognito.domain}/oauth2/userInfo`,

      // URL a la que se redirige tras el login
      redirectUri: environment.cognito.redirectUri,

      // Identificador del App Client en Cognito (Público, sin Secret, con PKCE activado)
      clientId: environment.cognito.clientId,

      // Flujo Authorization Code Grant con PKCE (code_verifier / code_challenge)
      responseType: environment.cognito.responseType,

      // Scopes solicitados (OIDC + Scope de Resource Server para el backend)
      scope: environment.cognito.scope,

      // Registra eventos útiles en consola durante desarrollo
      showDebugInformation: !environment.production,

      // Cognito publica el issuer del User Pool en el discovery document. Se mantienen
      // las validaciones OIDC aunque los endpoints del Hosted UI usen otro dominio.
      strictDiscoveryDocumentValidation: false,

      // Limpia parámetros y fragmentos (?code=...) de la URL tras el intercambio de código
      clearHashAfterLogin: true
    };

    this.oauthService.configure(authConfig);

    // Escucha eventos del servicio para emitir reactivamente el estado de autenticación
    this.oauthService.events.subscribe(event => {
      console.log('[AuthService Event]', event.type);
      if (
        event.type === 'token_received' ||
        event.type === 'token_refreshed' ||
        event.type === 'discovery_document_loaded'
      ) {
        const isAuth = this.isAuthenticated();
        console.log('[AuthService] Evento detectado:', event.type, '-> isAuthenticated:', isAuth);
        this.autenticado$.next(isAuth);
      }
    });

    // Carga el documento .well-known/openid-configuration de Cognito
    // y si la URL actual contiene el código (?code=...), ejecuta el intercambio por tokens (PKCE)
    this.tryLoginPromise = this.oauthService.loadDiscoveryDocumentAndTryLogin()
      .then(() => {
        const isAuth = this.isAuthenticated();
        console.log('[AuthService] Proceso tryLogin completado. Autenticado:', isAuth);
        this.autenticado$.next(isAuth);
        return isAuth;
      })
      .catch(error => {
        this.autenticado$.next(false);
        console.error('[AuthService] No se pudo validar la sesión OIDC:', error);
        throw error;
      });
  }

  /**
   * Inicia el flujo OAuth 2.0 PKCE redirigiendo a la pantalla de login de Cognito (Hosted UI)
   */
  initCodeFlow(): void {
    console.log('[AuthService] Ejecutando oauthService.initCodeFlow() hacia AWS Cognito...');
    this.oauthService.initCodeFlow();
  }

  /**
   * Inicia el flujo de autenticación delegando en initCodeFlow()
   */
  login(): void {
    this.initCodeFlow();
  }

  /**
   * Cierra la sesión limpiando el almacenamiento de tokens
   * y redirigiendo al endpoint /logout de Cognito para invalidar la sesión del navegador
   */
  logout(): void {
    this.oauthService.logOut();

    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem('access_token');
      sessionStorage.removeItem('id_token');
      sessionStorage.removeItem('expires_at');
      sessionStorage.removeItem('id_token_claims_obj');
      sessionStorage.removeItem('id_token_expires_at');
    }

    this.autenticado$.next(false);

    if (environment.cognito.domain) {
      const logoutUrl = `${environment.cognito.domain}/logout?client_id=${environment.cognito.clientId}&logout_uri=${encodeURIComponent(environment.cognito.logoutUrl)}`;
      window.location.href = logoutUrl;
    }
  }

  /**
   * Verifica si existe una sesión válida (Access Token o ID Token vigentes)
   */
  isAuthenticated(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  /**
   * Verifica si el Access Token actual es válido y no ha expirado
   */
  hasValidAccessToken(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  /**
   * Retorna el Access Token.
   * FUNDAMENTAL para consumir APIs protegidas en Spring Boot Resource Server y AWS API Gateway,
   * ya que transporta los OAuth scopes y token_use = 'access'.
   */
  getAccessToken(): string {
    return this.oauthService.hasValidAccessToken() ? this.oauthService.getAccessToken() : '';
  }

  /**
   * Retorna el ID Token (utilizado principalmente para datos de identidad y claims del usuario OIDC).
   */
  getIdToken(): string {
    return this.oauthService.hasValidIdToken() ? this.oauthService.getIdToken() : '';
  }

  /**
   * Retorna los claims del ID Token decodificado
   */
  getIdentityClaims(): Record<string, any> | null {
    const claims = this.oauthService.getIdentityClaims() as Record<string, any> | null;
    if (claims && Object.keys(claims).length > 0) {
      return claims;
    }

    const rawToken = this.getIdToken() || this.getAccessToken();
    if (rawToken) {
      try {
        const parts = rawToken.split('.');
        if (parts.length === 3) {
          return JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
        }
      } catch (err) {
        console.warn('[AuthService] Error decodificando claims JWT:', err);
      }
    }

    return null;
  }

  /**
   * Obtiene el identificador o correo del usuario decodificado desde los claims de Cognito.
   * Prioriza el correo electrónico ('email') para la visualización en la interfaz.
   */
  getUsername(): string {
    const claims = this.getIdentityClaims();
    if (!claims) return '';
    return claims['email'] || claims['cognito:username'] || claims['username'] || claims['sub'] || '';
  }

  /**
   * Valida si el usuario autenticado pertenece al grupo 'Admin' en AWS Cognito
   * leyendo el claim 'cognito:groups' tanto en el ID Token como en el Access Token.
   * Soporta variantes comunes ('Admin', 'admin', 'ADMIN', 'ROLE_ADMIN') para total
   * compatibilidad con los roles y authorities de Spring Security.
   */
  isAdmin(): boolean {
    const configuredAdminGroup = environment.cognito.adminGroup.trim().toLowerCase();
    const isMatchingAdmin = (groupName: any): boolean => {
      if (typeof groupName !== 'string') return false;
      const clean = groupName.trim().toLowerCase();
      return clean === configuredAdminGroup
        || clean === `role_${configuredAdminGroup}`;
    };

    const hasAdminGroup = (groups: any): boolean => {
      if (!groups) return false;
      if (Array.isArray(groups)) {
        return groups.some(isMatchingAdmin);
      }
      return isMatchingAdmin(groups);
    };

    const claims = this.getIdentityClaims();
    if (claims && hasAdminGroup(claims['cognito:groups'])) {
      return true;
    }

    try {
      const accessToken = this.getAccessToken();
      if (accessToken) {
        const parts = accessToken.split('.');
        if (parts.length === 3) {
          const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
          if (hasAdminGroup(payload['cognito:groups'])) {
            return true;
          }
        }
      }
    } catch {
      // Ignorar si el token no tiene formato JWT válido
    }

    return false;
  }
}
