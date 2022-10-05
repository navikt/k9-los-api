package no.nav.k9.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste

class OppgavetypeTjeneste(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val feltdefinisjonRepository: FeltdefinisjonRepository,
    private val transactionalManager: TransactionalManager
) {

    fun oppdater(innkommendeOppgavetyperDto: OppgavetyperDto) {
        transactionalManager.transaction { tx ->
            val område = områdeRepository.hentOmråde(innkommendeOppgavetyperDto.område, tx)
            // lås feltdefinisjoner for område og hent opp
            val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
            val innkommendeOppgavetyper = Oppgavetyper(innkommendeOppgavetyperDto, område, eksisterendeFeltdefinisjoner)

            val eksisterendeOppgavetyper = oppgavetypeRepository.hent(område, tx)
            val (sletteListe, leggtilListe, oppdaterListe) = eksisterendeOppgavetyper.finnForskjell(
                innkommendeOppgavetyper
            )
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