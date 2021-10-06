package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import java.time.LocalDate


data class AlleOppgaverHistorikk(
    val fagsakYtelseType: FagsakYtelseType,
    val behandlingType: BehandlingType,
    val dato: LocalDate,
    val fagsytem: Fagsystem,
    val antall: Int
)
