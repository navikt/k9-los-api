package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDateTime

data class Journalposttilstand(
    val journalpostId: String,
    @JsonAlias("behandlingUuid")
    val eksternId: String,
    val ytelseType: String?,
)
