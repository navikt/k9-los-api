package no.nav.k9.los.domene.lager.oppgave

import no.nav.k9.los.domene.modell.AndreKriterierType

data class OppgaveEgenskap(
    val id: Long,
    val andreKriterierType: AndreKriterierType,
    val sisteSaksbehandlerForTotrinn: String,
    val aktiv: Boolean
)