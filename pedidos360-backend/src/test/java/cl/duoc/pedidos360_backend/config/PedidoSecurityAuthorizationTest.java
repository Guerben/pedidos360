package cl.duoc.pedidos360_backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.pedidos360_backend.controller.PedidoController;
import cl.duoc.pedidos360_backend.model.Pedido;
import cl.duoc.pedidos360_backend.service.OutboxService;
import cl.duoc.pedidos360_backend.service.PedidoService;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas unitarias de autorización RBAC y control de acceso de Spring Security.
 * Cumple con el requisito SEC-05 del Masterplan:
 * Verifica el rechazo HTTP 403 ante mutaciones (POST, PUT, DELETE) por usuarios sin rol Admin,
 * el acceso HTTP 200 a lectura (GET) para usuarios autenticados,
 * el rechazo HTTP 401 para peticiones no autenticadas,
 * y el éxito HTTP 201/204 para usuarios con rol Admin.
 */
@WebMvcTest(PedidoController.class)
@Import({SecurityConfig.class, CognitoRoleConverter.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class PedidoSecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private OutboxService outboxService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getPedidos_sinAutenticacion_retorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.codigo").value(401));
    }

    @Test
    void getPedidos_conUsuarioAutenticadoSinAdmin_retorna200Ok() throws Exception {
        when(pedidoService.obtenerTodos()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/pedidos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User"))))
                .andExpect(status().isOk());
    }

    @Test
    void postPedidos_conUsuarioSinRolAdmin_retorna403Forbidden() throws Exception {
        String payload = """
                {
                    "producto": "Router Cisco",
                    "cantidad": 2,
                    "stock": 50,
                    "estado": "DISPONIBLE",
                    "imageUrl": "https://img.com/cisco.jpg"
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_User"))
                                .jwt(j -> j.claim("sub", "operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.codigo").value(403))
                .andExpect(jsonPath("$.usuario").value("operador"));
    }

    @Test
    void putPedidos_conUsuarioSinRolAdmin_retorna403Forbidden() throws Exception {
        String payload = """
                {
                    "producto": "Router Cisco Actualizado",
                    "cantidad": 5,
                    "stock": 45,
                    "estado": "EN_PREPARACION"
                }
                """;

        mockMvc.perform(put("/api/pedidos/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void deletePedidos_conUsuarioSinRolAdmin_retorna403Forbidden() throws Exception {
        mockMvc.perform(delete("/api/pedidos/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void postPedidos_conRolAdmin_retorna201Created() throws Exception {
        Pedido pedidoCreado = new Pedido(1L, "Router Cisco", "https://img.com/cisco.jpg", 2, 50, "DISPONIBLE");
        when(pedidoService.crearPedido(any(Pedido.class))).thenReturn(pedidoCreado);

        String payload = """
                {
                    "producto": "Router Cisco",
                    "cantidad": 2,
                    "stock": 50,
                    "estado": "DISPONIBLE",
                    "imageUrl": "https://img.com/cisco.jpg"
                }
                """;

        mockMvc.perform(post("/api/pedidos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.producto").value("Router Cisco"));
    }

    @Test
    void deletePedidos_conRolAdmin_retorna204NoContent() throws Exception {
        doNothing().when(pedidoService).eliminarPedido(eq(1L));

        mockMvc.perform(delete("/api/pedidos/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isNoContent());
    }
}

