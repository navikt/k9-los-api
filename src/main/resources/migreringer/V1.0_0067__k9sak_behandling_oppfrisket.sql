create table k9sak_behandling_oppfrisket (
    behandling_uuid uuid not null primary key,
    tidspunkt timestamp not null
);

comment on table k9sak_behandling_oppfrisket is 'Tar vare på tidspunkt for når k9sak behandling sist ble oppfrisket med registeropplysninger, for å unngå å oppfriske oftere enn nødvendig';
comment on column k9sak_behandling_oppfrisket.behandling_uuid is 'k9sak behandlingUuid';
comment on column k9sak_behandling_oppfrisket.tidspunkt is 'Tidspunkt for sist behandlingen ble oppfrisket med registeropplysninger';
