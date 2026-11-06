-- Script de inicializacion SQL para crear las dos bases de datos logicas
SELECT 'CREATE DATABASE pedidos360_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pedidos360_db')\gexec
SELECT 'CREATE DATABASE auditoria_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auditoria_db')\gexec

