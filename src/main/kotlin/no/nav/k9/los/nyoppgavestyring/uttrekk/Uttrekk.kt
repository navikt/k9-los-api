package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøk
import no.nav.k9.los.nyoppgavestyring.query.Avgrensning
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.lagBeskrivelse
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
    tittel: String,
    val query: OppgaveQuery,
    val typeKjøring: TypeKjøring,
    val lagetAv: Long,
    val limit: Int?,
    val offset: Int?,
    feilmelding: String?,
    startetTidspunkt: LocalDateTime?,
    fullførtTidspunkt: LocalDateTime?,
    antall: Int?
) {
    var status: UttrekkStatus = status
        private set

    var tittel: String = tittel
        private set

    var feilmelding: String? = feilmelding
        private set

    var startetTidspunkt: LocalDateTime? = startetTidspunkt
        private set

    var fullførtTidspunkt: LocalDateTime? = fullførtTidspunkt
        private set

    var antall: Int? = antall
        private set

    val avgrensning: Avgrensning?
        get() = if (limit != null || offset != null)
            Avgrensning(limit = limit?.toLong() ?: -1, offset = offset?.toLong() ?: -1)
        else
            null

    val queryBeskrivelse: String
        get() = lagBeskrivelse(query)

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
        return status == UttrekkStatus.KJØRER &&
                startetTidspunkt!!.plusMinutes(30) < LocalDateTime.now()
    }

    fun endreTittel(nyTittel: String) {
        tittel = nyTittel
    }

    companion object {
        fun opprettUttrekk(
            lagretSøk: LagretSøk,
            typeKjoring: TypeKjøring,
            lagetAv: Long,
            limit: Int? = null,
            offset: Int? = null
        ): Uttrekk {
            return Uttrekk(
                id = null,
                opprettetTidspunkt = LocalDateTime.now(),
                status = UttrekkStatus.OPPRETTET,
                tittel = lagretSøk.tittel.takeIf { it.isNotEmpty() } ?: lagBeskrivelse(lagretSøk.query),
                query = lagretSøk.query,
                typeKjøring = typeKjoring,
                lagetAv = lagetAv,
                limit = limit,
                offset = offset,
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
            tittel: String,
            query: OppgaveQuery,
            typeKjoring: TypeKjøring,
            lagetAv: Long,
            limit: Int?,
            offset: Int?,
            feilmelding: String?,
            startetTidspunkt: LocalDateTime?,
            fullførtTidspunkt: LocalDateTime?,
            antall: Int?
        ): Uttrekk {
            return Uttrekk(
                id, opprettetTidspunkt, status, tittel, query, typeKjoring, lagetAv, limit, offset,
                feilmelding, startetTidspunkt, fullførtTidspunkt, antall
            )
        }
    }
}
