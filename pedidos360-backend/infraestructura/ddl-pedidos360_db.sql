-- =============================================================================
-- DDL para pedidos360_db (Microservicio Productor / Backend Principal)
-- =============================================================================

\connect pedidos360_db;

-- Tabla de Pedidos / Productos de inventario
CREATE TABLE IF NOT EXISTS pedidos (
    id BIGSERIAL PRIMARY KEY,
    producto VARCHAR(255) NOT NULL,
    image_url VARCHAR(500),
    cantidad INTEGER NOT NULL DEFAULT 0,
    stock INTEGER NOT NULL DEFAULT 0,
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE'
);

-- Tabla de Transactional Outbox
CREATE TABLE IF NOT EXISTS outbox_eventos (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    tipo_evento VARCHAR(100) NOT NULL,
    exchange VARCHAR(150) NOT NULL,
    routing_key VARCHAR(150) NOT NULL,
    payload TEXT NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE',
    reintentos INTEGER NOT NULL DEFAULT 0,
    creado_en TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    publicado_en TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_estado_creado ON outbox_eventos(estado, creado_en ASC);
