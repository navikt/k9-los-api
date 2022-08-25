package no.nav.k9.domene.lager.oppgave.v3.omraade

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

class OmrådeService(private val områdeRepository: OmrådeRepository) {

    fun hentOmrådeFraFil(): Område {
        val mapper = jacksonObjectMapper()
        return mapper.readValue(File("k9-område-v1.json"), Område::class.java)
    }

    // TODO kommer til å ta inn område fra restkall
    fun lagreOmrådedefinisjon(): Område {
        val område = hentOmrådeFraFil()

        val persistertOmråde = områdeRepository.hent(område.område)

        persistertOmråde.sjekkEndringer(område)
        TODO()
    }

}