package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OppgavetypeTjeneste(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonRepository: FeltdefinisjonRepository,
    private val transactionalManager: TransactionalManager
) {

    private val log: Logger = LoggerFactory.getLogger(OppgavetypeTjeneste::class.java)

    fun oppdater(innkommendeOppgavetyperDto: OppgavetyperDto) {
        log.info("mottatt oppgavetypeDto, med behandlingsurlTemplate: ${innkommendeOppgavetyperDto.oppgavetyper.elementAt(0).oppgavebehandlingsUrlTemplate}")
        transactionalManager.transaction { tx ->
            val område = områdeRepository.hentOmråde(innkommendeOppgavetyperDto.område, tx)
            // lås feltdefinisjoner for område og hent opp
            val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
            val innkommendeOppgavetyper = Oppgavetyper(innkommendeOppgavetyperDto, område, eksisterendeFeltdefinisjoner)

            val eksisterendeOppgavetyper = oppgavetypeRepository.hent(område, innkommendeOppgavetyperDto.definisjonskilde, tx)
            val (sletteListe, leggtilListe, oppdaterListe) = eksisterendeOppgavetyper.finnForskjell(innkommendeOppgavetyper)
            log.info("antall sletteliste oppgavetypedto: ${sletteListe.oppgavetyper.size}")
            log.info("antall leggtilListe oppgavetypedto: ${leggtilListe.oppgavetyper.size}")
            log.info("urltemplate i oppdaterListe: ${oppdaterListe.get(0).oppgavetype.oppgavebehandlingsUrlTemplate}")
            oppgavetypeRepository.fjern(sletteListe, tx)
            oppgavetypeRepository.leggTil(leggtilListe, tx)
            oppdaterListe.forEach { endring ->
                oppgavetypeRepository.endre(endring, tx)
            }
            invaliderCache()
        }
    }

    fun hent(område: Område) = (
            transactionalManager.transaction { tx ->
                oppgavetypeRepository.hent(område, tx)
            }
    )

    private fun invaliderCache() {
        områdeRepository.invaliderCache()
        oppgavetypeRepository.invaliderCache()
    }
}