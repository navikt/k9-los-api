package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj

import com.fasterxml.jackson.annotation.JsonAlias

data class Journalposttilstand(
    val journalpostId: String,
    @JsonAlias("behandlingUuid")
    val eksternId: String,
    val ytelseType: String?,
)