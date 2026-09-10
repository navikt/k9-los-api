package no.nav.k9.los.uttrekk

import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.lagretsok.LagretSøk
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.resultat.OppgaveQueryRad

class UttrekkTjeneste(
    private val uttrekkRepository: UttrekkRepository,
    private val lagretSøkRepository: LagretSøkRepository
) {
    fun opprett(opprettUttrekk: OpprettUttrekk, saksbehandlerId: Long, område: Områder, harTilgangTilKode6: Boolean): Long {
        val lagretSøk = krevTilgangTilLagretSøk(opprettUttrekk.lagretSokId, saksbehandlerId, område)
        require(område == Områder.K9) { "Uttrekk støttes foreløpig bare for K9" }

        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = lagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = harTilgangTilKode6,
            tittel = opprettUttrekk.tittel,
            limit = opprettUttrekk.limit,
            offset = opprettUttrekk.offset
        )
        return uttrekkRepository.opprett(uttrekk)
    }

    fun hent(id: Long): Uttrekk? {
        return uttrekkRepository.hent(id)
    }

    fun hentAlle(): List<Uttrekk> {
        return uttrekkRepository.hentAlle()
    }

    fun hentForSaksbehandler(saksbehandlerId: Long, område: Områder, harTilgangTilKode6: Boolean): List<Uttrekk> {
        return uttrekkRepository.hentForSaksbehandler(saksbehandlerId).filter {
            it.lagetAv == saksbehandlerId && it.område == område &&
                (it.harTilgangTilKode6 == null || it.harTilgangTilKode6 == harTilgangTilKode6)
        }
    }

    internal fun krevTilgang(id: Long, saksbehandlerId: Long, område: Områder, harTilgangTilKode6: Boolean): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id) ?: throw SecurityException("Ingen tilgang til uttrekk")
        if (uttrekk.lagetAv != saksbehandlerId || uttrekk.område != område ||
            (uttrekk.harTilgangTilKode6 != null && uttrekk.harTilgangTilKode6 != harTilgangTilKode6)) {
            throw SecurityException("Ingen tilgang til uttrekk")
        }
        return uttrekk
    }

    fun hentResultat(id: Long, saksbehandlerId: Long, område: Områder, harTilgangTilKode6: Boolean): String? {
        val uttrekk = krevTilgang(id, saksbehandlerId, område, harTilgangTilKode6)
        if (uttrekk.harTilgangTilKode6 == null) {
            throw SecurityException("Uttrekket mangler kjent beskyttelsesnivå. Opprett et nytt uttrekk.")
        }
        return uttrekkRepository.hentResultat(id)
    }

    internal fun krevTilgangTilLagretSøk(id: Long, saksbehandlerId: Long, område: Områder): LagretSøk {
        val søk = lagretSøkRepository.hent(id) ?: throw SecurityException("Ingen tilgang til lagret søk")
        if (søk.lagetAv != saksbehandlerId || søk.område != område) {
            throw SecurityException("Ingen tilgang til lagret søk")
        }
        return søk
    }

    fun slett(id: Long) {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        // Sjekk at uttrekk ikke kjører
        if (uttrekk.status == UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan ikke slette uttrekk som kjører")
        }

        uttrekkRepository.slett(id)
    }

    fun slettForLagretSøk(lagretSøkId: Long, saksbehandlerId: Long, område: Områder, harTilgangTilKode6: Boolean): Int {
        krevTilgangTilLagretSøk(lagretSøkId, saksbehandlerId, område)
        val uttrekk = hentForSaksbehandler(saksbehandlerId, område, harTilgangTilKode6)
            .filter { it.lagretSøkId == lagretSøkId && it.status != UttrekkStatus.KJØRER }
        uttrekk.forEach { slett(requireNotNull(it.id)) }
        return uttrekk.size
    }

    fun startUttrekk(id: Long): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.markerSomKjører()
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    fun fullførUttrekk(id: Long, resultat: List<OppgaveQueryRad>): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        val uttrekkRader = UttrekkResultatMapper.tilUttrekkRader(uttrekk.query.select, resultat)
        val resultatJson = LosObjectMapper.instance.writeValueAsString(uttrekkRader)
        uttrekk.markerSomFullført(uttrekkRader.size)
        uttrekkRepository.oppdater(uttrekk, resultatJson)
        return uttrekk
    }

    fun feilUttrekk(id: Long, feilmelding: String?): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.markerSomFeilet(feilmelding)
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    fun endreTittel(id: Long, tittel: String): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.endreTittel(tittel)
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }
}
