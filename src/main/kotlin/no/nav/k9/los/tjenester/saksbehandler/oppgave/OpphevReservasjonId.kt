package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto

data class OpphevReservasjonId(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val begrunnelse: String,
    )