package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav

import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object Duplikatfilter {

    private val log = LoggerFactory.getLogger(Duplikatfilter::class.java)

    fun fjernDuplikater(eventer : Collection<K9TilbakeEventDto>) : Set<K9TilbakeEventDto> {
        val gruppert = eventer.groupBy { it.eventTid }
        val resultat : MutableSet<K9TilbakeEventDto> = mutableSetOf()
        val tidspunktMedFlere : MutableSet<LocalDateTime> = mutableSetOf()
        for ((tidspunkt, eventerMedLikTid) in gruppert) {
            if (eventerMedLikTid.size == 1){
                resultat.addAll(eventerMedLikTid)
            } else {
                val leggesTil: MutableSet<K9TilbakeEventDto> = mutableSetOf()
                for (event in eventerMedLikTid) {
                    if (leggesTil.any { erFunksjoneltLike(event, it) }) {
                        log.info("Ignorerer en funksjonelt duplikat hendelse. Gjaldt eksternId ${event.eksternId} eventTid ${event.eventTid}")
                    } else {
                        if (leggesTil.isNotEmpty()){
                            val ulikeFelter = ulikeFelter(leggesTil.first(), event)
                            log.warn("Har ulike eventer på samme tidspunkt. Gjelder eksternId ${event.eksternId} og tidspunkt ${tidspunkt}. Avvik i felt: $ulikeFelter")
                        }
                        leggesTil.add(event)
                    }
                }
                resultat.addAll(leggesTil)
                if (leggesTil.size > 1) {
                    tidspunktMedFlere.add(tidspunkt)
                }
            }
        }
        if (tidspunktMedFlere.isNotEmpty()){
            log.warn("Har ulike eventer på samme tidspunkt. Gjelder eksternId ${resultat.first().eksternId} og tidspunkt ${tidspunktMedFlere}")
        }
        return resultat
    }

    fun erFunksjoneltLike(a : K9TilbakeEventDto, b: K9TilbakeEventDto) : Boolean{
        if (a == b) {
            return true
        }
        if (a.eventTid != b.eventTid){
            return false
        }

        return false
    }


    fun ulikeFelter(a : K9TilbakeEventDto, b: K9TilbakeEventDto) : List<String> {
        val avvikendeFelt: MutableList<String> = mutableListOf()
        if (a.eksternId != b.eksternId) {
            avvikendeFelt.add("eksternId")
        }
        if (a.fagsystem != b.fagsystem) {
            avvikendeFelt.add("fagsystem")
        }
        if (a.saksnummer != b.saksnummer) {
            avvikendeFelt.add("saksnummer")
        }
        if (a.aktørId != b.aktørId) {
            avvikendeFelt.add("aktørId")
        }
        if (a.behandlingId != b.behandlingId) {
            avvikendeFelt.add("behandlingId")
        }
        if (a.behandlingstidFrist != b.behandlingstidFrist) {
            avvikendeFelt.add("behandlingstidFrist")
        }
        if (a.eventTid != b.eventTid) {
            avvikendeFelt.add("eventTid")
        }
        if (a.eventHendelse != b.eventHendelse) {
            avvikendeFelt.add("eventHendelse")
        }
        if (a.behandlingStatus != b.behandlingStatus) {
            avvikendeFelt.add("behandlingStatus")
        }
        if (a.behandlingSteg != b.behandlingSteg) {
            avvikendeFelt.add("behandlingSteg")
        }
        if (a.behandlendeEnhet != b.behandlendeEnhet) {
            avvikendeFelt.add("behandlendeEnhet")
        }
        if (a.resultatType != b.resultatType) {
            avvikendeFelt.add("resultatType")
        }
        if (a.ytelseTypeKode != b.ytelseTypeKode) {
            avvikendeFelt.add("ytelseTypeKode")
        }
        if (a.behandlingTypeKode != b.behandlingTypeKode) {
            avvikendeFelt.add("behandlingTypeKode")
        }
        if (a.opprettetBehandling != b.opprettetBehandling) {
            avvikendeFelt.add("opprettetBehandling")
        }
        if (a.aksjonspunktKoderMedStatusListe != b.aksjonspunktKoderMedStatusListe) {
            avvikendeFelt.add("aksjonspunktKoderMedStatusListe")
        }
        if (a.href != b.href) {
            avvikendeFelt.add("href")
        }
        if (a.førsteFeilutbetaling != b.førsteFeilutbetaling) {
            avvikendeFelt.add("førsteFeilutbetaling")
        }
        if (a.feilutbetaltBeløp != b.feilutbetaltBeløp) {
            avvikendeFelt.add("feilutbetaltBeløp")
        }
        if (a.ansvarligSaksbehandlerIdent != b.ansvarligSaksbehandlerIdent) {
            avvikendeFelt.add("ansvarligSaksbehandlerIdent")
        }
        if (a.ansvarligBeslutterIdent != b.ansvarligBeslutterIdent) {
            avvikendeFelt.add("ansvarligBeslutterIdent")
        }
        return avvikendeFelt
    }
}
