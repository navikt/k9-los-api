package no.nav.k9.nyoppgavestyring.adapter

import no.nav.k9.nyoppgavestyring.oppgave.OppgaveV3
import no.nav.k9.statistikk.kontrakter.Sak

class OppgaveV3TilSakAdapter {

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