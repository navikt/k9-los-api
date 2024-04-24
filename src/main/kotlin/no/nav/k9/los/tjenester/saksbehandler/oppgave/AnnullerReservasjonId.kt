package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto

data class AnnullerReservasjonId(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val begrunnelse: String,
)