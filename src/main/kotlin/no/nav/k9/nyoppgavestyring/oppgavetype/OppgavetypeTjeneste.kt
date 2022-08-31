package no.nav.k9.nyoppgavestyring.oppgavetype

import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.nyoppgavestyring.omraade.OmrådeRepository

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
            // TODO: oppdaterListe - for hvert element: sjekk diff oppgavefelter og insert/delete på deltalister
            if (oppdaterListe.oppgavetyper.size > 0) {
                throw IllegalArgumentException("Endring av oppgavefeltliste til oppgavetype er ikke implementert ennå. Oppgavetypen må slettes og lages på nytt inntil videre")
            }
        }
    }
}