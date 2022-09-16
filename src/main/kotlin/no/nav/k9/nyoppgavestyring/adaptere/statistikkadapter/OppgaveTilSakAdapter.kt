package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import no.nav.k9.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.statistikk.kontrakter.Sak

class OppgaveTilSakAdapter {

    fun lagSak(oppgaveversjoner: Set<Oppgave>): Sak {
        val sisteVersjon = oppgaveversjoner.last()
        return Sak(
            saksnummer = sisteVersjon.eksternId,
            sakId = null,
            funksjonellTid = null,
            tekniskTid = null,
            opprettetDato = null,
            aktorId = null,
            aktorer = listOf(),
            ytelseType = null,
            underType = null,
            sakStatus = null,
            ytelseTypeBeskrivelse = null,
            underTypeBeskrivelse = null,
            sakStatusBeskrivelse = null,
            avsender = null,
            versjon = null
        )
    }
}