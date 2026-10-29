-- Comprobantes enviados al cliente por WhatsApp con la API oficial de Meta (WhatsApp Cloud API).
-- Sin la API configurada no se registra nada: la caja comparte el enlace wa.me a mano.
create table envio_whatsapp (
    id             bigserial primary key,
    comprobante_id bigint       not null references comprobante (id),
    -- Con código de país, solo dígitos (ej. 51987654321).
    telefono       varchar(20)  not null,
    -- ENVIADO (Meta lo aceptó; la entrega la confirma Meta después) o ERROR.
    estado         varchar(10)  not null check (estado in ('ENVIADO', 'ERROR')),
    -- Identificador del mensaje en Meta (wamid...).
    mensaje_id     varchar(120),
    error          varchar(500),
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    created_by     varchar(50),
    updated_by     varchar(50)
);
create index ix_envio_whatsapp_comprobante on envio_whatsapp (comprobante_id);
