package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
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
    val query: OppgaveQuery,
    val typeKjøring: TypeKjøring,
    val lagetAv: Long,
    val timeout: Int,
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

    fun skalRyddesOpp(): Boolean {
        // Legger på et ekstra sekund for å la vanlig timeout gå ut
        return status == UttrekkStatus.KJØRER &&
            startetTidspunkt!!.plusSeconds(1L + timeout) < LocalDateTime.now()
    }

    companion object {
        fun opprettUttrekk(
            query: OppgaveQuery,
            typeKjoring: TypeKjøring,
            lagetAv: Long,
            timeout: Int
        ): Uttrekk {
            return Uttrekk(
                id = null,
                opprettetTidspunkt = LocalDateTime.now(),
                status = UttrekkStatus.OPPRETTET,
                query = query,
                typeKjøring = typeKjoring,
                lagetAv = lagetAv,
                timeout = timeout,
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
            query: OppgaveQuery,
            typeKjoring: TypeKjøring,
            lagetAv: Long,
            timeout: Int,
            feilmelding: String?,
            startetTidspunkt: LocalDateTime?,
            fullførtTidspunkt: LocalDateTime?,
            antall: Int?
        ): Uttrekk {
            return Uttrekk(
                id, opprettetTidspunkt, status, query, typeKjoring, lagetAv, timeout, feilmelding,
                startetTidspunkt, fullførtTidspunkt, antall
            )
        }
    }
}
