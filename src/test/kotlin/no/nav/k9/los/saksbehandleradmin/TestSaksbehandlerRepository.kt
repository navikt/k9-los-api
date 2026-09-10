package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import javax.sql.DataSource
import java.time.LocalDateTime

/**
 * Testscope-repo som oppretter en ferdig utfylt saksbehandler i én operasjon.
 *
 * Produksjonsflyten er todelt med vilje: [SaksbehandlerRepository.opprettSaksbehandler] kobler epost
 * til område når avdelingsleder registrerer saksbehandleren, og
 * [SaksbehandlerRepository.vedlikeholdSaksbehandler] fyller ut navident, navn og enhet først når
 * saksbehandleren selv logger inn. Tester trenger sjelden å skille de to stegene, så de samles her
 * i stedet for at produksjonsrepoet får en opprettelsesvei som bare tester bruker.
 */
class TestSaksbehandlerRepository(
    private val dataSource: DataSource,
    områdeRepository: OmrådeRepository,
) {
    private val saksbehandlerRepository =
        SaksbehandlerRepository(dataSource, TransactionalManager(dataSource), områdeRepository)

    fun opprettSaksbehandler(
        opprettSaksbehandler: OpprettSaksbehandler,
        område: Områder = Områder.K9,
        skjermet: Boolean = false,
    ): Saksbehandler {
        val id = saksbehandlerRepository.opprettSaksbehandler(opprettSaksbehandler.epost, område, skjermet)
        saksbehandlerRepository.vedlikeholdSaksbehandler(
            saksbehandler = Saksbehandler(
                id = id,
                navident = opprettSaksbehandler.navident,
                navn = opprettSaksbehandler.navn,
                epost = opprettSaksbehandler.epost,
                enhet = opprettSaksbehandler.enhet,
                områder = listOf(område),
                skjermet = skjermet,
                sistOppdatert = LocalDateTime.now(),
            ),
        )
        return saksbehandlerRepository.finnSaksbehandlerMedId(id)!!
    }

    fun finnSaksbehandlerMedEpost(epost: String, skjermet: Boolean = false): Saksbehandler? =
        saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, skjermet)

    fun hentAlleSaksbehandlere(
        område: Områder = Områder.K9,
        skjermet: Boolean = false,
    ): List<Saksbehandler> = saksbehandlerRepository.hentAlleSaksbehandlere(område, skjermet)
}
