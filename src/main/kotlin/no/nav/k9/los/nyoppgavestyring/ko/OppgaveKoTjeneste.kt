package no.nav.k9.los.nyoppgavestyring.ko

import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo

class OppgaveKoTjeneste(
    private val oppgaveKoRepository: OppgaveKoRepository
) {
    fun hentOppgaveKo(id: Long) : OppgaveKo {
        return oppgaveKoRepository.hent(id)
    }
}