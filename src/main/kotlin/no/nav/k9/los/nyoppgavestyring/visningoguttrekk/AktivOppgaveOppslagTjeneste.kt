package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

interface AktivOppgaveOppslagTjeneste {
    /** Hent aktiv/nåværende tilstand for en oppgave */
    fun hentAktivOppgave(eksternId: String, oppgavetype: String): Oppgave

    /** Hent aktiv oppgave hvis den finnes, null ellers */
    fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetype: String): Oppgave?
}
