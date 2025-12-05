package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository

class UttrekkTjeneste(
    private val uttrekkRepository: UttrekkRepository,
    private val lagretSøkRepository: LagretSøkRepository
) {
    fun opprett(opprettUttrekk: OpprettUttrekk, saksbehandlerId: Long): Long {
        val lagretSøk = lagretSøkRepository.hent(opprettUttrekk.lagretSokId)
            ?: throw IllegalArgumentException("Lagret søk med id ${opprettUttrekk.lagretSokId} finnes ikke")

        val uttrekk = Uttrekk.opprettUttrekk(
            query = lagretSøk.query,
            typeKjoring = opprettUttrekk.typeKjoring,
            lagetAv = saksbehandlerId,
            timeout = opprettUttrekk.timeout
        )
        return uttrekkRepository.opprett(uttrekk)
    }

    fun hent(id: Long): Uttrekk? {
        return uttrekkRepository.hent(id)
    }

    fun hentAlle(): List<Uttrekk> {
        return uttrekkRepository.hentAlle()
    }

    fun hentForSaksbehandler(saksbehandlerId: Long): List<Uttrekk> {
        return uttrekkRepository.hentForSaksbehandler(saksbehandlerId)
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

    fun startUttrekk(id: Long): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.markerSomKjører()
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    fun fullførUttrekk(id: Long, antall: Int, resultat: String? = null): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.markerSomFullført(antall)
        uttrekkRepository.oppdater(uttrekk, resultat)
        return uttrekk
    }

    fun feilUttrekk(id: Long, feilmelding: String?): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.markerSomFeilet(feilmelding)
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }
}
