package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

data class HistorikkvaskBestilling(
    val fagsystem: Fagsystem,
    val eksternId: String,
)