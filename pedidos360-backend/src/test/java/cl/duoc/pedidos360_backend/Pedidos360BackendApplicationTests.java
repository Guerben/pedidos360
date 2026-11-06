package cl.duoc.pedidos360_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "COGNITO_ISSUER_URI=https://cognito-idp.example.com/test-pool",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/test-jwks",
                "spring.datasource.url=jdbc:h2:mem:pedidos360-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.rabbitmq.dynamic=false"
        })
class Pedidos360BackendApplicationTests {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AmqpAdmin amqpAdmin;

    @LocalServerPort
    private int port;

    @Test
    void applicationStartsWithJsonConfigurationAndProtectsApi() throws Exception {
        assertThat(objectMapper.writeValueAsString(java.util.Map.of("status", "ok")))
                .isEqualTo("{\"status\":\"ok\"}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/pedidos"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED.value());
        assertThat(response.body()).contains("\"status\":\"UNAUTHORIZED\"");
    }
}
