package cl.duoc.pedidos360_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoRoleConverterTest {

        private CognitoRoleConverter converter;

        @BeforeEach
        void setUp() {
                converter = new CognitoRoleConverter("Admin");
        }

        @Test
        void convert_conGrupoAdmin_asignaRoleAdminYConservaScopes() {
                Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                                .header("alg", "RS256")
                                .claim("sub", "cognito-user-uuid-1234")
                                .claim("cognito:groups", List.of("Admin"))
                                .claim("scope", "pedidos:read pedidos:write")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build();

                AbstractAuthenticationToken token = converter.convert(jwt);

                assertNotNull(token);
                assertEquals("cognito-user-uuid-1234", token.getName());

                List<String> authorities = token.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();

                assertTrue(authorities.contains("ROLE_Admin"), "Debe contener ROLE_Admin derivado de cognito:groups");
                assertTrue(authorities.contains("SCOPE_pedidos:read"), "Debe conservar los scopes OAuth2");
                assertTrue(authorities.contains("SCOPE_pedidos:write"), "Debe conservar los scopes OAuth2");
        }

        @Test
        void convert_conMultiplesGrupos_asignaTodosLosRoles() {
                Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                                .header("alg", "RS256")
                                .claim("sub", "usuario-multi-rol")
                                .claim("cognito:groups", List.of("Admin", "Operador", "Auditor"))
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build();

                AbstractAuthenticationToken token = converter.convert(jwt);

                assertNotNull(token);
                List<String> authorities = token.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();

                assertTrue(authorities.contains("ROLE_Admin"));
                assertTrue(authorities.contains("ROLE_Operador"));
                assertTrue(authorities.contains("ROLE_Auditor"));
        }

        @Test
        void convert_conGrupoAdministradorPersonalizado_mapeaAlRolAdminDeLaAplicacion() {
                converter = new CognitoRoleConverter("PedidosAdministradores");
                Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                                .header("alg", "RS256")
                                .claim("sub", "usuario-admin")
                                .claim("cognito:groups", List.of("PedidosAdministradores"))
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build();

                AbstractAuthenticationToken token = converter.convert(jwt);

                List<String> authorities = token.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();
                assertTrue(authorities.contains("ROLE_Admin"));
                assertTrue(authorities.contains("ROLE_PedidosAdministradores"));
        }

        @Test
        void convert_conGrupoAdminNoConfigurado_noConcedeRolAdministrador() {
                converter = new CognitoRoleConverter("PedidosAdministradores");
                Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                                .header("alg", "RS256")
                                .claim("sub", "usuario-no-admin")
                                .claim("cognito:groups", List.of("Admin"))
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build();

                AbstractAuthenticationToken token = converter.convert(jwt);

                List<String> authorities = token.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();
                assertFalse(authorities.contains("ROLE_Admin"));
        }

        @Test
        void convert_sinGrupos_noAsignaRolesPeroConservaScopes() {
                Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                                .header("alg", "RS256")
                                .claim("sub", "usuario-sin-grupos")
                                .claim("scope", "pedidos:read")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build();

                AbstractAuthenticationToken token = converter.convert(jwt);

                assertNotNull(token);
                List<String> authorities = token.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList();

                assertFalse(authorities.stream().anyMatch(a -> a.startsWith("ROLE_")));
                assertTrue(authorities.contains("SCOPE_pedidos:read"));
        }

        @Test
        void convert_sinClaimSub_utilizaSubjectComoFallback() {
                Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                                .header("alg", "RS256")
                                .subject("subject-fallback-id")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(3600))
                                .build();

                AbstractAuthenticationToken token = converter.convert(jwt);

                assertNotNull(token);
                assertEquals("subject-fallback-id", token.getName());
        }
}
