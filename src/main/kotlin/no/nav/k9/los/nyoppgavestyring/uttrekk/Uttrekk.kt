package no.nav.k9.los.nyoppgavestyring.uttrekk

import java.time.LocalDateTime

enum class UttrekkStatus {
    OPPRETTET,
    KJØRER,
    FULLFØRT,
    FEILET
}

enum class TypeKjøring {
    ANTALL,
    OPPGAVER
}

class Uttrekk private constructor(
    val id: Long?,
    val opprettetTidspunkt: LocalDateTime,
    status: UttrekkStatus,
    val lagretSøkId: Long,
    val kjøreplan: String?,
    val typeKjøring: TypeKjøring,
    resultat: String?,
    feilmelding: String?,
    startetTidspunkt: LocalDateTime?,
    fullførtTidspunkt: LocalDateTime?,
) {
    var status: UttrekkStatus = status
        private set

    var resultat: String? = resultat
        private set

    var feilmelding: String? = feilmelding
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

    fun markerSomFullført(resultat: String) {
        if (status != UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan kun fullføre uttrekk som er i status KJØRER")
        }
        status = UttrekkStatus.FULLFØRT
        this.resultat = resultat
        fullførtTidspunkt = LocalDateTime.now()
    }

    fun markerSomFeilet(feilmelding: String) {
        if (status != UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan kun feile uttrekk som er i status KJØRER")
        }
        status = UttrekkStatus.FEILET
        resultat = null
        this.feilmelding = feilmelding
        fullførtTidspunkt = LocalDateTime.now()
    }

    companion object {
        fun opprettUttrekk(lagretSokId: Long, kjoreplan: String?, typeKjoring: TypeKjøring = TypeKjøring.OPPGAVER): Uttrekk {
            return Uttrekk(
                id = null,
                opprettetTidspunkt = LocalDateTime.now(),
                status = UttrekkStatus.OPPRETTET,
                lagretSøkId = lagretSokId,
                kjøreplan = kjoreplan,
                typeKjøring = typeKjoring,
                resultat = null,
                feilmelding = null,
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
            typeKjoring: TypeKjøring,
            resultat: String?,
            feilmelding: String?,
            startetTidspunkt: LocalDateTime?,
            fullførtTidspunkt: LocalDateTime?,
        ): Uttrekk {
            return Uttrekk(id, opprettetTidspunkt, status, lagretSokId, kjoreplan, typeKjoring,
                resultat, feilmelding, startetTidspunkt, fullførtTidspunkt)
        }
    }
}