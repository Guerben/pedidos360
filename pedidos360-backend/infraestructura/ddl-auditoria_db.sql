-- =============================================================================
-- DDL para auditoria_db (Microservicio Consumidor / Auditoria)
-- =============================================================================

\connect auditoria_db;

-- Tabla de Auditoria de Inventario (creaciones y modificaciones)
CREATE TABLE IF NOT EXISTS auditoria_inventario (
    id BIGSERIAL PRIMARY KEY,
    evento_id UUID NOT NULL,
    tipo_evento VARCHAR(100) NOT NULL,
    accion VARCHAR(50),
    producto_id BIGINT,
    nombre_producto VARCHAR(200),
    cantidad INTEGER DEFAULT 0,
    stock INTEGER DEFAULT 0,
    estado VARCHAR(50),
    image_url VARCHAR(500),
    usuario_sub VARCHAR(100),
    usuario_email VARCHAR(150),
    fecha_evento VARCHAR(50),
    payload TEXT,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_auditoria_inventario_evento_id UNIQUE (evento_id)
);
-- Tabla de Historico de Bajas (eliminaciones con snapshot completo)
CREATE TABLE IF NOT EXISTS historico_bajas (
    id BIGSERIAL PRIMARY KEY,
    evento_id UUID NOT NULL,
    producto_id BIGINT,
    nombre_producto VARCHAR(200),
    tipo_evento VARCHAR(100) NOT NULL,
    snapshot_pedido TEXT NOT NULL,
    usuario_sub VARCHAR(100),
    usuario_email VARCHAR(150),
    fecha_evento VARCHAR(50),
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_historico_bajas_evento_id UNIQUE (evento_id)
);

-- Tabla de Auditoria de Seguridad (alertas de acceso 403)
CREATE TABLE IF NOT EXISTS auditoria_seguridad (
    id BIGSERIAL PRIMARY KEY,
    evento_id UUID NOT NULL,
    tipo_evento VARCHAR(100) NOT NULL,
    usuario_sub VARCHAR(100),
    usuario_email VARCHAR(150),
    ruta VARCHAR(255),
    metodo VARCHAR(20),
    motivo VARCHAR(500),
    fecha_evento VARCHAR(50),
    payload TEXT,
    procesado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_auditoria_seguridad_evento_id UNIQUE (evento_id)
);
