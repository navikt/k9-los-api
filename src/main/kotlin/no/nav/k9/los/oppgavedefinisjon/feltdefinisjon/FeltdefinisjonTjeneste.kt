package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

import kotliquery.TransactionalSession
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

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

            val (sletteListe, oppdaterListe, leggTilListe) = eksisterendeFeltdefinisjoner.finnForskjeller(innkommendeFeltdefinisjoner)
            feltdefinisjonRepository.fjern(sletteListe, tx)
            feltdefinisjonRepository.oppdater(oppdaterListe, innkommendeFeltdefinisjoner.område, tx)
            feltdefinisjonRepository.leggTil(leggTilListe, innkommendeFeltdefinisjoner.område, tx)
        }
    }

    fun oppdater(kodeverkDtoListe: List<KodeverkDto>) {
        transactionalManager.transaction { tx ->
            kodeverkDtoListe.forEach { oppdater(it, tx) }
        }
    }

    fun oppdater(kodeverkDto: KodeverkDto) {
        transactionalManager.transaction { tx ->
            oppdater(kodeverkDto, tx)
        }
    }

    private fun oppdater(kodeverkDto: KodeverkDto, tx: TransactionalSession) {
        val område = områdeRepository.hentOmråde(kodeverkDto.område, tx)
        val kodeverk = Kodeverk(kodeverkDto, område)

        feltdefinisjonRepository.tømVerdierHvisKodeverkFinnes(kodeverk, tx)
        feltdefinisjonRepository.lagre(kodeverk, tx)
    }

    fun hent(område: String): Feltdefinisjoner {
        return transactionalManager.transaction { tx ->
            feltdefinisjonRepository.hent(områdeRepository.hentOmråde(område, tx), tx)
        }
    }

    fun hent(område: Områder): Feltdefinisjoner {
        return transactionalManager.transaction { tx ->
            feltdefinisjonRepository.hent(områdeRepository.hentOmråde(område, tx), tx)
        }
    }

    fun hent(område: Område): Feltdefinisjoner {
        return transactionalManager.transaction { tx ->
            feltdefinisjonRepository.hent(område, tx)
        }
    }
}