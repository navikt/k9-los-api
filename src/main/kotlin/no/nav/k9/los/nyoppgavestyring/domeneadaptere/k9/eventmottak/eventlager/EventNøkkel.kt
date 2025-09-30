package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

//For å slippe å bruke Pair
data class EventNøkkel(
    val nøkkelId: Long,
    val fagsystem: Fagsystem,
    val eksternId: String,
)