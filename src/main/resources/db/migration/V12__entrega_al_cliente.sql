-- Entrega del comprobante al cliente: enlace público de descarga y envío por correo.

-- Código aleatorio (imposible de adivinar) con que el cliente descarga su comprobante sin iniciar
-- sesión: va en el enlace que se le envía por correo o WhatsApp.
alter table comprobante add column codigo_consulta uuid not null default gen_random_uuid();
create unique index ux_comprobante_codigo_consulta on comprobante (codigo_consulta);

-- Cada correo enviado (o por enviar) con un comprobante: deja constancia de cuándo y a quién se
-- entregó, y se reintenta si el servidor de correo falla.
create table envio_correo (
    id              bigserial primary key,
    comprobante_id  bigint       not null references comprobante (id),
    destinatario    varchar(120) not null,
    -- PENDIENTE (por enviar o reintentar), ENVIADO o ERROR (no se pudo tras varios intentos).
    estado          varchar(10)  not null check (estado in ('PENDIENTE', 'ENVIADO', 'ERROR')),
    -- Verdadero si lo encoló el sistema al aceptar SUNAT el comprobante; falso si lo pidió un usuario.
    automatico      boolean      not null default false,
    intentos        integer      not null default 0,
    proximo_intento timestamptz,
    fecha_envio     timestamptz,
    error           varchar(500),
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    created_by      varchar(50),
    updated_by      varchar(50)
);
create index ix_envio_correo_comprobante on envio_correo (comprobante_id);
create index ix_envio_correo_pendientes on envio_correo (estado, proximo_intento);
