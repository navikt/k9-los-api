package no.nav.k9.los.nyoppgavestyring.uttrekk

import java.time.LocalDateTime

enum class UttrekkStatus {
    OPPRETTET,
    KJØRER,
    FULLFØRT,
    FEILET,
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
    val typeKjøring: TypeKjøring,
    feilmelding: String?,
    startetTidspunkt: LocalDateTime?,
    fullførtTidspunkt: LocalDateTime?,
    antall: Int?
) {
    var status: UttrekkStatus = status
        private set

    var feilmelding: String? = feilmelding
        private set

    var startetTidspunkt: LocalDateTime? = startetTidspunkt
        private set

    var fullførtTidspunkt: LocalDateTime? = fullførtTidspunkt
        private set

    var antall: Int? = antall
        private set

    fun markerSomKjører() {
        require(status == UttrekkStatus.OPPRETTET) { "Kan kun starte uttrekk som er i status OPPRETTET" }

        status = UttrekkStatus.KJØRER
        startetTidspunkt = LocalDateTime.now()
    }

    fun markerSomFullført(antall: Int) {
        require(status == UttrekkStatus.KJØRER) { "Kan kun fullføre uttrekk som er i status KJØRER" }

        status = UttrekkStatus.FULLFØRT
        this.antall = antall
        fullførtTidspunkt = LocalDateTime.now()
    }

    fun markerSomFeilet(feilmelding: String?) {
        require(status == UttrekkStatus.KJØRER) { "Kan kun feile uttrekk som er i status KJØRER" }

        status = UttrekkStatus.FEILET
        this.feilmelding = feilmelding
        fullførtTidspunkt = LocalDateTime.now()
    }

    companion object {
        fun opprettUttrekk(lagretSokId: Long, typeKjoring: TypeKjøring = TypeKjøring.OPPGAVER): Uttrekk {
            return Uttrekk(
                id = null,
                opprettetTidspunkt = LocalDateTime.now(),
                status = UttrekkStatus.OPPRETTET,
                lagretSøkId = lagretSokId,
                typeKjøring = typeKjoring,
                feilmelding = null,
                startetTidspunkt = null,
                fullførtTidspunkt = null,
                antall = null
            )
        }

        fun fraEksisterende(
            id: Long,
            opprettetTidspunkt: LocalDateTime,
            status: UttrekkStatus,
            lagretSokId: Long,
            typeKjoring: TypeKjøring,
            feilmelding: String?,
            startetTidspunkt: LocalDateTime?,
            fullførtTidspunkt: LocalDateTime?,
            antall: Int?
        ): Uttrekk {
            return Uttrekk(
                id, opprettetTidspunkt, status, lagretSokId, typeKjoring, feilmelding,
                startetTidspunkt, fullførtTidspunkt, antall
            )
        }
    }
}