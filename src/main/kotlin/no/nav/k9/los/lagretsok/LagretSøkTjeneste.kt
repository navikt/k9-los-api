package no.nav.k9.los.lagretsok

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository

class LagretSøkTjeneste(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val lagretSøkRepository: LagretSøkRepository,
    private val oppgaveQueryService: OppgaveQueryService,
    private val transactionalManager: TransactionalManager
) {
    fun hent(bruker: BrukerkontekstMedOmråde, lagretSøkId: Long): LagretSøk {
        val lagretSøk = lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        bruker.krevOmråde(lagretSøk.område)
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
        require(bruker.harBasisTilgang && saksbehandler?.id == lagretSøk.lagetAv) {
            "Mangler tilgang til lagret søk"
        }
        return lagretSøk
    }

    fun hentAlle(bruker: BrukerkontekstMedOmråde): List<LagretSøk> {
        val saksbehandler =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
                ?: return emptyList()
        return lagretSøkRepository.hentAlle(saksbehandler, bruker.område)
    }

    fun nytt(bruker: BrukerkontekstMedOmråde, nyttLagretSøk: NyttLagretSøkRequest): Long {
        val saksbehandler =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
                ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = LagretSøk.nyttSøk(nyttLagretSøk, saksbehandler, bruker.område)
        return lagretSøkRepository.opprett(lagretSøk)
    }

    fun endre(bruker: BrukerkontekstMedOmråde, endreLagretSøk: EndreLagretSøkRequest): LagretSøk {
        val saksbehandler =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
                ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(endreLagretSøk.id)
            ?: throw IllegalStateException("Lagret søk med id ${endreLagretSøk.id} finnes ikke")
        bruker.krevOmråde(lagretSøk.område)
        lagretSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endre(lagretSøk)
        return lagretSøk
    }

    fun slett(bruker: BrukerkontekstMedOmråde, lagretSøkId: Long) {
        val saksbehandler =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
                ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        bruker.krevOmråde(lagretSøk.område)
        lagretSøk.sjekkOmKanSlette(saksbehandler)
        transactionalManager.transactionContext {
            lagretSøkRepository.slett(lagretSøk)
        }
    }

    fun slettAlle(bruker: BrukerkontekstMedOmråde) {
        val saksbehandler =
            saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
                ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val alleLagredeSøk = lagretSøkRepository.hentAlle(saksbehandler, bruker.område)
        transactionalManager.transactionContext {
            alleLagredeSøk.forEach {
                lagretSøkRepository.slett(it)
            }
        }
    }

    fun slettAlle(saksbehandler: Saksbehandler, områder: List<Områder> = saksbehandler.områder) {
        val alleLagredeSøk = områder.flatMap { lagretSøkRepository.hentAlle(saksbehandler, it) }
        transactionalManager.transactionContext {
            alleLagredeSøk.forEach { lagretSøkRepository.slett(it) }
        }
    }

    fun hentAntall(bruker: BrukerkontekstMedOmråde, lagretSøkId: Long): Long {
        val lagretSøk = hent(bruker, lagretSøkId)
        return oppgaveQueryService.queryForAntall(QueryRequest(lagretSøk.query, område = lagretSøk.område, harTilgangTilKode6 = bruker.harTilgangTilKode6))
    }

    fun kopier(bruker: BrukerkontekstMedOmråde, lagretSøkId: Long, tittel: String, saksbehandler: Saksbehandler): Long {
        // Eksisterende delingsregel: andre kan kopiere et søk innen samme område, men ikke lese eller endre originalen.
        require(bruker.harBasisTilgang && saksbehandler.navident == bruker.navIdent &&
            saksbehandler.skjermet == bruker.harTilgangTilKode6 && bruker.område in saksbehandler.områder) {
            "Mangler tilgang til å kopiere lagret søk"
        }
        val lagretSøk = lagretSøkRepository.hent(lagretSøkId)
            ?: throw IllegalStateException("Lagret søk med id $lagretSøkId finnes ikke")
        bruker.krevOmråde(lagretSøk.område)
        val nyttLagretSøk = lagretSøk.kopier(tittel, saksbehandler)
        return lagretSøkRepository.opprett(nyttLagretSøk)
    }
}
