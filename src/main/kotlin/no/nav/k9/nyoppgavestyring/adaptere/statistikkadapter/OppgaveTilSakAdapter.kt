package no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter

import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.statistikk.kontrakter.Sak

class OppgaveTilSakAdapter {

    fun lagSak(oppgaveV3: OppgaveV3): Sak {
        return Sak(
            saksnummer = "",
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