package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object Duplikatfilter {

    private val log = LoggerFactory.getLogger(Duplikatfilter::class.java)

    fun fjernDuplikater(eventer : Collection<K9SakEventDto>) : Set<K9SakEventDto> {
        val gruppert = eventer.groupBy { it.eventTid }
        val resultat : MutableSet<K9SakEventDto> = mutableSetOf()
        val tidspunktMedFlere : MutableSet<LocalDateTime> = mutableSetOf()
        for ((tidspunkt, eventerMedLikTid) in gruppert) {
            if (eventerMedLikTid.size == 1){
                resultat.addAll(eventerMedLikTid)
            } else {
                val leggesTil: MutableSet<K9SakEventDto> = mutableSetOf()
                for (event in eventerMedLikTid) {
                    if (leggesTil.any { erFunksjoneltLike(event, it) }) {
                        log.info("Ignorerer en funksjonelt duplikat hendelse. Gjaldt eksternId ${event.eksternId} eventTid ${event.eventTid}")
                    } else {
                        if (leggesTil.isNotEmpty()){
                            val ulikeFelter = ulikeFelter(medSorterteDedupliserteLister(leggesTil.first()), medSorterteDedupliserteLister(event))
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

    fun erFunksjoneltLike(a : K9SakEventDto, b: K9SakEventDto) : Boolean{
        if (a == b) {
            return true
        }
        if (a.eventTid != b.eventTid){
            return false
        }
        if (a.eventTid.year < 2021){
            //ignorer duplikater fra 2020 eller tidligere
            return true
        }
        val eventA = medSorterteDedupliserteLister(a)
        val eventB = medSorterteDedupliserteLister(b)
        if (eventA == eventB){
            return true
        }

        if (eventA.resultatType != null && eventB.resultatType == null){
            return eventA.copy(resultatType = null) == eventB
        }
        if (eventB.resultatType != null && eventA.resultatType == null){
            return eventB.copy(resultatType = null) == eventA
        }

        return false
    }

    fun medSorterteDedupliserteLister(event : K9SakEventDto) : K9SakEventDto {
        return event
            .copy(søknadsårsaker = event.søknadsårsaker.distinct().sorted().toList())
            .copy(behandlingsårsaker = event.behandlingsårsaker.distinct().sorted().toList())
            .copy(aksjonspunktTilstander = event.aksjonspunktTilstander.distinct().sortedBy { it.opprettetTidspunkt }.toList())
    }

    fun ulikeFelter(a : K9SakEventDto, b: K9SakEventDto) : List<String> {
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
        if (a.vedtaksdato != b.vedtaksdato) {
            avvikendeFelt.add("vedtaksdato")
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
        if (a.eldsteDatoMedEndringFraSøker != b.eldsteDatoMedEndringFraSøker) {
            avvikendeFelt.add("eldsteDatoMedEndringFraSøker")
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
        if (a.ansvarligSaksbehandlerForTotrinn != b.ansvarligSaksbehandlerForTotrinn) {
            avvikendeFelt.add("ansvarligSaksbehandlerForTotrinn")
        }
        if (a.ansvarligBeslutterForTotrinn != b.ansvarligBeslutterForTotrinn) {
            avvikendeFelt.add("ansvarligBeslutterForTotrinn")
        }
        if (a.fagsakPeriode != b.fagsakPeriode) {
            avvikendeFelt.add("fagsakPeriode")
        }
        if (a.pleietrengendeAktørId != b.pleietrengendeAktørId) {
            avvikendeFelt.add("pleietrengendeAktørId")
        }
        if (a.relatertPartAktørId != b.relatertPartAktørId) {
            avvikendeFelt.add("relatertPartAktørId")
        }
        if (a.aksjonspunktTilstander != b.aksjonspunktTilstander) {
            avvikendeFelt.add("aksjonspunktTilstander")
        }
        if (a.nyeKrav != b.nyeKrav) {
            avvikendeFelt.add("nyeKrav")
        }
        if (a.fraEndringsdialog != b.fraEndringsdialog) {
            avvikendeFelt.add("fraEndringsdialog")
        }
        if (a.søknadsårsaker != b.søknadsårsaker) {
            avvikendeFelt.add("søknadsårsaker")
        }
        if (a.behandlingsårsaker != b.behandlingsårsaker) {
            avvikendeFelt.add("behandlingsårsaker")
        }
        return avvikendeFelt
    }
}
