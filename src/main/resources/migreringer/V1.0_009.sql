hmmm,drop table if exists FERDIGSTILTE_BEHANDLINGER;
create table if not exists FERDIGSTILTE_BEHANDLINGER
(
    behandlingType VARCHAR(100) NOT NULL,
    dato           DATE         NOT NULL,
    data           jsonb,
    primary key (behandlingType, dato),
    unique (behandlingType, dato)
);
