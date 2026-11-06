package cl.duoc.pedidos360_backend.admin.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.pedidos360_backend.admin.dto.CrearColaRequest;
import cl.duoc.pedidos360_backend.admin.service.RabbitAdminService;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RabbitAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RabbitAdminService rabbitAdminService;

    @InjectMocks
    private RabbitAdminController rabbitAdminController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(rabbitAdminController).build();
    }

    @Test
    void crearCola_conDatosValidos_retorna201Created() throws Exception {
        when(rabbitAdminService.crearCola(any(CrearColaRequest.class)))
                .thenReturn("cola.productos.temporal");

        String payload = """
                {
                    "nombre": "cola.productos.temporal",
                    "durable": true,
                    "exclusive": false,
                    "autoDelete": false
                }
                """;

        mockMvc.perform(post("/api/admin/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.cola", is("cola.productos.temporal")))
                .andExpect(jsonPath("$.durable", is(true)));

        verify(rabbitAdminService).crearCola(any(CrearColaRequest.class));
    }

    @Test
    void crearCola_conNombreVacio_retorna400BadRequest() throws Exception {
        String payload = """
                {
                    "nombre": "",
                    "durable": true,
                    "exclusive": false,
                    "autoDelete": false
                }
                """;

        mockMvc.perform(post("/api/admin/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void crearCola_conCaracteresInvalidos_retorna400BadRequest() throws Exception {
        String payload = """
                {
                    "nombre": "cola invalida con espacios y *?",
                    "durable": true,
                    "exclusive": false,
                    "autoDelete": false
                }
                """;

        mockMvc.perform(post("/api/admin/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void eliminarCola_cuandoExiste_retorna200Ok() throws Exception {
        when(rabbitAdminService.eliminarCola(eq("cola.a.eliminar"))).thenReturn(true);

        mockMvc.perform(delete("/api/admin/queues/cola.a.eliminar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.mensaje").exists());

        verify(rabbitAdminService).eliminarCola("cola.a.eliminar");
    }

    @Test
    void eliminarCola_cuandoNoExiste_retorna404NotFound() throws Exception {
        when(rabbitAdminService.eliminarCola(eq("cola.no.existe"))).thenReturn(false);

        mockMvc.perform(delete("/api/admin/queues/cola.no.existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")))
                .andExpect(jsonPath("$.mensaje").exists());

        verify(rabbitAdminService).eliminarCola("cola.no.existe");
    }

    @Test
    void obtenerCola_cuandoExiste_retorna200Ok() throws Exception {
        Properties props = new Properties();
        props.put("QUEUE_MESSAGE_COUNT", 10);
        when(rabbitAdminService.obtenerPropiedadesCola("cola.activa")).thenReturn(props);

        mockMvc.perform(get("/api/admin/queues/cola.activa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.nombre", is("cola.activa")));
    }

    @Test
    void obtenerCola_cuandoNoExiste_retorna404NotFound() throws Exception {
        when(rabbitAdminService.obtenerPropiedadesCola("cola.fantasma")).thenReturn(null);

        mockMvc.perform(get("/api/admin/queues/cola.fantasma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")));
    }
}
