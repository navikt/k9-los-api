package no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte

import kotlinx.coroutines.CoroutineScope
import no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.DagensTallKortDto
import no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.K9DagensTallService

class K9InngangOgFerdigstilteService(
    private val kilde: K9DagensTallService,
) : InngangOgFerdigstilte {
    override fun hentCachetVerdi(): InngangOgFerdigstilteResponse {
        val verdi = kilde.hentCachetVerdi()
        return InngangOgFerdigstilteResponse(
            oppdatertTidspunkt = verdi.oppdatertTidspunkt,
            hovedgrupper = verdi.hovedgrupper,
            undergrupper = verdi.undergrupper,
            tall = verdi.tall.map { rad ->
                InngangOgFerdigstilteDto(
                    hovedgruppe = rad.hovedgruppe.name,
                    undergruppe = rad.undergruppe.name,
                    serier = rad.serier.mapValues { (_, kort) -> kort.first.tilKort() to kort.second.tilKort() },
                    månedSerier = rad.månedSerier.mapValues { (_, kort) -> kort.first.tilKort() to kort.second.tilKort() },
                )
            },
        )
    }

    override fun oppdaterCache(scope: CoroutineScope) = kilde.oppdaterCache(scope)

    private fun DagensTallKortDto.tilKort() = InngangOgFerdigstilteKortDto(
        hovedtall = InngangOgFerdigstilteLinjeDto(hovedtall.visningsnavn, hovedtall.verdi),
        linjer = linjer.map { InngangOgFerdigstilteLinjeDto(it.visningsnavn, it.verdi) },
    )
}
