package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository

class UttrekkTjeneste(
    private val uttrekkRepository: UttrekkRepository,
    private val lagretSøkRepository: LagretSøkRepository
) {
    fun opprett(opprettUttrekk: OpprettUttrekk): Long {
        lagretSøkRepository.hent(opprettUttrekk.lagretSokId)
            ?: throw IllegalArgumentException("Lagret søk med id ${opprettUttrekk.lagretSokId} finnes ikke")

        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSokId = opprettUttrekk.lagretSokId,
            kjoreplan = opprettUttrekk.kjoreplan,
            typeKjoring = opprettUttrekk.typeKjoring
        )
        return uttrekkRepository.opprett(uttrekk)
    }

    fun hent(id: Long): Uttrekk? {
        return uttrekkRepository.hent(id)
    }

    fun hentAlle(): List<Uttrekk> {
        return uttrekkRepository.hentAlle()
    }

    fun hentForLagretSok(lagretSokId: Long): List<Uttrekk> {
        return uttrekkRepository.hentForLagretSok(lagretSokId)
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