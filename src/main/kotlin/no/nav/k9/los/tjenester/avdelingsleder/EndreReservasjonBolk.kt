package no.nav.k9.los.tjenester.avdelingsleder

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDate

data class EndreReservasjonBolk(
    val oppgaveNøkler: Set<OppgaveNøkkelDto>,
    val brukerIdent: String? = null,
    val reserverTil: LocalDate? = null,
    val begrunnelse: String? = null
)
