package cl.duoc.pedidos360_backend.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Conversor de roles y autoridades para tokens JWT emitidos por AWS Cognito.
 *
 * Cumple con el requisito SEC-01 del Masterplan:
 * Extrae los grupos de usuario del claim 'cognito:groups' y los transforma en autoridades
 * de Spring Security con prefijo 'ROLE_', habilitando reglas de autorización como hasRole("Admin").
 * Además preserva los scopes OAuth2 estándar ('SCOPE_...').
 */
@Component
public class CognitoRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    private final String adminGroup;

    public CognitoRoleConverter(@Value("${app.security.cognito-admin-group:Admin}") String adminGroup) {
        this.adminGroup = adminGroup;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // 1. Conservar scopes estándar de OAuth2 (ej. SCOPE_pedidos/read)
        Collection<GrantedAuthority> defaultAuthorities = defaultAuthoritiesConverter.convert(jwt);
        if (defaultAuthorities != null) {
            authorities.addAll(defaultAuthorities);
        }

        // 2. Extraer grupos de AWS Cognito desde el claim 'cognito:groups'
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            for (String group : groups) {
                if (group != null && !group.trim().isEmpty()) {
                    String normalizedGroup = group.trim();
                    if (normalizedGroup.equalsIgnoreCase(adminGroup.trim())) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_Admin"));
                        if (!normalizedGroup.equalsIgnoreCase("Admin")) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedGroup));
                        }
                    } else if (!normalizedGroup.equalsIgnoreCase("Admin")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedGroup));
                    }
                }
            }
        }

        // 3. Determinar el identificador principal estable ('sub')
        String principal = jwt.getClaimAsString("sub");
        if (principal == null || principal.isBlank()) {
            principal = jwt.getSubject();
        }

        return new JwtAuthenticationToken(jwt, authorities, principal);
    }
}
