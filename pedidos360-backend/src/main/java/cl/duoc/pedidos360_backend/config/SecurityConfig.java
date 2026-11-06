package cl.duoc.pedidos360_backend.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración central de Seguridad, OAuth2 Resource Server y control de
 * acceso (RBAC) con AWS Cognito.
 *
 * Cumple con la Fase 3 del Masterplan:
 * - SEC-01: Conversión de claim 'cognito:groups' en autoridades Spring Security
 * con prefijo 'ROLE_'.
 * - SEC-02: Reglas granulares de autorización:
 * - GET /api/pedidos/** : Permitido a cualquier usuario autenticado con JWT
 * válido.
 * - POST, PUT, DELETE /api/pedidos/** : Restringido exclusivamente al grupo
 * 'Admin' (ROLE_Admin).
 * - SEC-03: Captura de HTTP 403 Forbidden mediante CustomAccessDeniedHandler
 * con emisión de eventos de alerta.
 * - SEC-04: Captura de HTTP 401 Unauthorized mediante
 * CustomAuthenticationEntryPoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CognitoRoleConverter cognitoRoleConverter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(CognitoRoleConverter cognitoRoleConverter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
        this.cognitoRoleConverter = cognitoRoleConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // Solicitudes preflight (OPTIONS) permitidas para negociación CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Administracion RabbitMQ: solo Admin (antes permitAll — riesgo critico)
                        .requestMatchers("/api/admin/**").hasRole("Admin")

                        // Reglas de negocio para /api/pedidos
                        // GET: acceso de solo lectura para cualquier usuario autenticado
                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/**").authenticated()

                        // POST, PUT, DELETE: mutaciones reservadas exclusivamente para administradores
                        // (ROLE_Admin)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**").hasRole("Admin")

                        // Cualquier otro endpoint requiere autenticación por defecto
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoRoleConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
