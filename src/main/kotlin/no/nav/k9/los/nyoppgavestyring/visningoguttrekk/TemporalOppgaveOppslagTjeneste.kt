package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import java.time.LocalDateTime

interface TemporalOppgaveOppslagTjeneste {
    fun hentTidsserie(fagsystem: Fagsystem, eksternId: String): List<Oppgave>
    fun hentOppgaveForTidspunkt(fagsystem: Fagsystem, eksternId: String, tidspunkt: LocalDateTime): Oppgave?
}
