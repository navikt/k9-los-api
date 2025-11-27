package no.nav.k9.los.nyoppgavestyring.uttrekk

import java.time.LocalDateTime

enum class UttrekkStatus {
    OPPRETTET,
    KJØRER,
    FULLFØRT,
    FEILET
}

class Uttrekk private constructor(
    val id: Long?,
    val opprettetTidspunkt: LocalDateTime,
    status: UttrekkStatus,
    val lagretSøkId: Long,
    val kjøreplan: String?,
    resultat: String?,
    startetTidspunkt: LocalDateTime?,
    fullførtTidspunkt: LocalDateTime?,
) {
    var status: UttrekkStatus = status
        private set

    var resultat: String? = resultat
        private set

    var startetTidspunkt: LocalDateTime? = startetTidspunkt
        private set
    var fullførtTidspunkt: LocalDateTime? = fullførtTidspunkt
        private set

    fun markerSomKjører() {
        if (status != UttrekkStatus.OPPRETTET) {
            throw IllegalStateException("Kan kun starte uttrekk som er i status OPPRETTET")
        }
        status = UttrekkStatus.KJØRER
        startetTidspunkt = LocalDateTime.now()
    }

    fun markerSomFullført(resultatData: String) {
        if (status != UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan kun fullføre uttrekk som er i status KJØRER")
        }
        status = UttrekkStatus.FULLFØRT
        resultat = resultatData
        fullførtTidspunkt = LocalDateTime.now()
    }

    fun markerSomFeilet(feilmelding: String) {
        if (status != UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan kun feile uttrekk som er i status KJØRER")
        }
        status = UttrekkStatus.FEILET
        resultat = feilmelding
        fullførtTidspunkt = LocalDateTime.now()
    }

    companion object {
        fun opprettUttrekk(lagretSokId: Long, kjoreplan: String?): Uttrekk {
            return Uttrekk(
                id = null,
                opprettetTidspunkt = LocalDateTime.now(),
                status = UttrekkStatus.OPPRETTET,
                lagretSøkId = lagretSokId,
                kjøreplan = kjoreplan,
                resultat = null,
                startetTidspunkt = null,
                fullførtTidspunkt = null
            )
        }

        fun fraEksisterende(
            id: Long,
            opprettetTidspunkt: LocalDateTime,
            status: UttrekkStatus,
            lagretSokId: Long,
            kjoreplan: String?,
            resultat: String?,
            startetTidspunkt: LocalDateTime?,
            fullførtTidspunkt: LocalDateTime?,
        ): Uttrekk {
            return Uttrekk(id, opprettetTidspunkt, status, lagretSokId, kjoreplan, resultat, startetTidspunkt, fullførtTidspunkt)
        }
    }
}