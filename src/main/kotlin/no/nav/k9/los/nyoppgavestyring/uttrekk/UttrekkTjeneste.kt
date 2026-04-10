package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat

class UttrekkTjeneste(
    private val uttrekkRepository: UttrekkRepository,
    private val lagretSøkRepository: LagretSøkRepository
) {
    fun opprett(opprettUttrekk: OpprettUttrekk, saksbehandlerId: Long): Long {
        val lagretSøk = lagretSøkRepository.hent(opprettUttrekk.lagretSokId)
            ?: throw IllegalArgumentException("Lagret søk med id ${opprettUttrekk.lagretSokId} finnes ikke")

        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = lagretSøk,
            typeKjoring = opprettUttrekk.typeKjoring,
            lagetAv = saksbehandlerId,
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

    fun slettForLagretSøk(lagretSøkId: Long): Int {
        return uttrekkRepository.slettForLagretSøk(lagretSøkId)
    }

    fun startUttrekk(id: Long): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        uttrekk.markerSomKjører()
        uttrekkRepository.oppdater(uttrekk)
        return uttrekk
    }

    fun fullførUttrekk(id: Long, resultat: OppgaveQueryResultat): Uttrekk {
        val uttrekk = uttrekkRepository.hent(id)
            ?: throw IllegalArgumentException("Uttrekk med id $id finnes ikke")

        var resultatJson: String?
        var antall: Int
        when (resultat) {
            is OppgaveQueryResultat.AntallResultat -> {
                antall = resultat.antall.toInt()
                resultatJson = null
            }
            is OppgaveQueryResultat.SelectResultat,
            is OppgaveQueryResultat.GruppertResultat -> {
                val uttrekkRader = UttrekkResultatMapper.tilUttrekkRader(uttrekk.query.select, resultat)
                antall = uttrekkRader.size
                resultatJson = LosObjectMapper.instance.writeValueAsString(uttrekkRader)
            }
            else -> {
                uttrekk.markerSomFeilet("Ugyldig resultat")
                uttrekkRepository.oppdater(uttrekk, null)
                return uttrekk
            }
        }
        uttrekk.markerSomFullført(antall)
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
