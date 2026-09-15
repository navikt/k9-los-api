package no.nav.k9.los.uttrekk

import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.resultat.OppgaveQueryRad
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository

class UttrekkTjeneste(
    private val uttrekkRepository: UttrekkRepository,
    private val lagretSøkRepository: LagretSøkRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
) {
    // Kall fra API: alltid område + innlogget saksbehandler

    suspend fun opprett(område: Områder, navIdent: String, opprettUttrekk: OpprettUttrekk): Long {
        val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(navIdent)
            ?: throw IllegalStateException("Innlogget bruker er ikke i saksbehandler-tabellen")
        val lagretSøk = lagretSøkRepository.hent(område, navIdent, opprettUttrekk.lagretSokId)
            ?: throw IllegalArgumentException("Lagret søk med id ${opprettUttrekk.lagretSokId} finnes ikke")

        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = lagretSøk,
            lagetAv = saksbehandler.id,
            tittel = opprettUttrekk.tittel,
            limit = opprettUttrekk.limit,
            offset = opprettUttrekk.offset
        )
        return uttrekkRepository.opprett(uttrekk)
    }

    fun hent(område: Områder, navIdent: String, id: Long): Uttrekk? {
        return uttrekkRepository.hent(område, navIdent, id)
    }

    fun hentAlle(område: Områder, navIdent: String): List<Uttrekk> {
        return uttrekkRepository.hentAlle(område, navIdent)
    }

    fun hentResultat(område: Områder, navIdent: String, id: Long): String? {
        return uttrekkRepository.hentResultat(område, navIdent, id)
    }

    fun endreTittel(område: Områder, navIdent: String, id: Long, tittel: String): Uttrekk {
        val uttrekk = hentEllerKast(område, navIdent, id)
        uttrekk.endreTittel(tittel)
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    fun slett(område: Områder, navIdent: String, id: Long) {
        val uttrekk = hentEllerKast(område, navIdent, id)
        if (uttrekk.status == UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan ikke slette uttrekk som kjører")
        }
        uttrekkRepository.slett(uttrekk)
    }

    fun slettForLagretSøk(lagretSøkId: Long): Int {
        return uttrekkRepository.slettForLagretSøk(lagretSøkId)
    }

    // Kall fra administrasjon og jobb: ingen innlogget bruker å sjekke mot

    fun hentForSaksbehandler(område: Områder, saksbehandlerId: Long): List<Uttrekk> {
        return uttrekkRepository.hentForSaksbehandler(område, saksbehandlerId)
    }

    fun slettUtenTilgangssjekk(id: Long) {
        val uttrekk = uttrekkRepository.hentForJobb(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")
        uttrekkRepository.slett(uttrekk)
    }

    fun hentAlleForJobb(): List<Uttrekk> {
        return uttrekkRepository.hentAlleForJobb()
    }

    fun startUttrekk(id: Long): Uttrekk {
        val uttrekk = hentForJobbEllerKast(id)
        uttrekk.markerSomKjører()
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    fun fullførUttrekk(id: Long, resultat: List<OppgaveQueryRad>): Uttrekk {
        val uttrekk = hentForJobbEllerKast(id)
        val uttrekkRader = UttrekkResultatMapper.tilUttrekkRader(uttrekk.query.select, resultat)
        uttrekk.markerSomFullført(uttrekkRader.size)
        uttrekkRepository.oppdater(uttrekk, LosObjectMapper.instance.writeValueAsString(uttrekkRader))
        return uttrekk
    }

    fun feilUttrekk(id: Long, feilmelding: String?): Uttrekk {
        val uttrekk = hentForJobbEllerKast(id)
        uttrekk.markerSomFeilet(feilmelding)
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    private fun hentEllerKast(område: Områder, navIdent: String, id: Long): Uttrekk =
        uttrekkRepository.hent(område, navIdent, id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

    private fun hentForJobbEllerKast(id: Long): Uttrekk =
        uttrekkRepository.hentForJobb(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")
}
