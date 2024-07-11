package no.nav.k9.los.domene.repository

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import org.slf4j.LoggerFactory

object BehandlingProsessEventK9DuplikatUtil {

    private val log = LoggerFactory.getLogger(BehandlingsmigreringTjeneste::class.java)

    fun fjernDuplikater(eventer : Collection<BehandlingProsessEventDto>) : Set<BehandlingProsessEventDto> {
        val gruppert = eventer.groupBy { it.eventTid }
        val resultat : MutableSet<BehandlingProsessEventDto> = mutableSetOf()
        var flerePåSammeTid = false
        for ((_, eventerMedLikTid) in gruppert) {
            if (eventerMedLikTid.size == 1){
                resultat.addAll(eventerMedLikTid)
            } else {
                val leggesTil: MutableSet<BehandlingProsessEventDto> = mutableSetOf()
                for (event in eventerMedLikTid) {
                    if (leggesTil.any { erFunksjoneltLike(event, it) }) {
                        //ignorerer duplikat
                    } else {
                        leggesTil.add(event)
                    }
                }
                resultat.addAll(leggesTil)
                if (leggesTil.size > 1){
                    flerePåSammeTid = true

                }
            }
        }
        if (flerePåSammeTid){
            log.warn("Har ulike eventer på samme tidspunkt. Gjelder eksternId ${resultat.first().eksternId}")
        }
        return resultat
    }

    fun erFunksjoneltLike(a : BehandlingProsessEventDto, b: BehandlingProsessEventDto) : Boolean{
        if (a == b) {
            return true;
        }
        if (a.eventTid != b.eventTid){
            return false;
        }

        //behandle resultattype IKKE_FASTSATT og null som like
        if (a.resultatType == BehandlingResultatType.IKKE_FASTSATT.kode && b.resultatType == null){
            return a.copy(resultatType = null) == b;
        }
        if (b.resultatType == BehandlingResultatType.IKKE_FASTSATT.kode && a.resultatType == null){
            return b.copy(resultatType = null) == a;
        }

        return false;
    }
}