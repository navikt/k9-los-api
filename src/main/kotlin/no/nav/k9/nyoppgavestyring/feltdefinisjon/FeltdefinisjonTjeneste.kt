package no.nav.k9.nyoppgavestyring.feltdefinisjon

import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.omraade.Område
import no.nav.k9.nyoppgavestyring.omraade.OmrådeRepository

class FeltdefinisjonTjeneste(
    private val feltdefinisjonRepository: FeltdefinisjonRepository,
    private val områdeRepository: OmrådeRepository,
    private val transactionalManager: TransactionalManager
) {

    fun oppdater(innkommendeFeltdefinisjonerDto: FeltdefinisjonerDto) {
        transactionalManager.transaction { tx ->
            val område = områdeRepository.hentOmråde(innkommendeFeltdefinisjonerDto.område, tx)
            val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
            val innkommendeFeltdefinisjoner = Feltdefinisjoner(innkommendeFeltdefinisjonerDto, område)

            val (sletteListe, leggTilListe) = eksisterendeFeltdefinisjoner.finnForskjeller(innkommendeFeltdefinisjoner)
            feltdefinisjonRepository.fjern(sletteListe, tx)
            feltdefinisjonRepository.leggTil(leggTilListe, innkommendeFeltdefinisjoner.område, tx)
        }
    }

    fun hent(område: Område): Feltdefinisjoner {
        return transactionalManager.transaction { tx ->
            feltdefinisjonRepository.hent(område, tx)
        }
    }
}