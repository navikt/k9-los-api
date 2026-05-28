package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

interface TemporalOppgaveOppslagTjeneste {
    /** Hent full versjonshistorikk (tidsserie) for en oppgave */
    fun hentTidsserie(fagsystem: Fagsystem, eksternId: String): List<Oppgave>

    /** Hent tilstanden til en oppgave på et gitt tidspunkt */
    fun hentOppgaveForTidspunkt(fagsystem: Fagsystem, eksternId: String, oppgavetype: String): Oppgave?
}
