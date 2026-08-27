package no.nav.k9.los.domeneadaptere.eventlager

import no.nav.k9.los.kodeverk.Fagsystem

data class HistorikkvaskBestilling(
    val eventlagerNøkkel: Long?,
    val eksternId: String,
    val fagsystem: Fagsystem,
)