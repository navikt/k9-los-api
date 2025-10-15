package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

data class HistorikkvaskBestilling(
    val eventlager_nokkel: Long,
    val eksternId: String,
    val fagsystem: Fagsystem,
)