package no.nav.k9.los.integrasjon.pdl

enum class Behandlingsnummer(val behandlingsnummer: String) {
    /**
     * Behandlingstype for saksbehandling av søknader og refusjonskrav om pleiepenger for syke barn
     * etter folketrygdloven kap. 9.
     *
     * @see [NAV Behandlingskatalog](https://behandlingskatalog.intern.nav.no/process/purpose/PLEIE_OMSORGS_OG_OPPLAERINGSPENGER/daa46292-de92-4924-9f60-2490b29d0c30)
     */
    PLEIEPENGER_SYKT_BARN("B249"),

    /**
     * Behandlingstype for saksbehandling av søknader og refusjonskrav om pleiepenger i livets sluttfase
     * etter folketrygdloven kap. 9.
     *
     * @see [NAV Behandlingskatalog](https://behandlingskatalog.intern.nav.no/process/purpose/PLEIE_OMSORGS_OG_OPPLAERINGSPENGER/e2630d73-2013-4654-bc6e-e08281b1167e)
     */
    PLEIEPENGER_I_LIVETS_SLUTTFASE("B566"),

    /**
     * Behandlingstype for saksbehandling av personopplysninger relatert til omsorgspenger fra bruker.
     *
     * @see [NAV Behandlingskatalog](https://behandlingskatalog.intern.nav.no/process/purpose/PLEIE_OMSORGS_OG_OPPLAERINGSPENGER/d0f205b9-01cc-437d-9528-9aaf65931ba0)
     */
    OMSORGSPENGERUTBETALING("B135"),

    /**
     * Behandlingstype for saksbehandling av søknader om flere omsorgsdager og meldinger om deling av omsorgsdager.
     *
     * @see [NAV Behandlingskatalog](https://behandlingskatalog.intern.nav.no/process/purpose/PLEIE_OMSORGS_OG_OPPLAERINGSPENGER/4a1c9324-9c5e-4ddb-ac7f-c55d1dcd9736)
     */
    OMSORGSPENGER_RAMMEMELDING("B142")
}
