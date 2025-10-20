package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

data class HistorikkvaskBestilling(
    val eventlagerNøkkel: Long?,
    val eksternId: String,
    val fagsystem: Fagsystem,
)