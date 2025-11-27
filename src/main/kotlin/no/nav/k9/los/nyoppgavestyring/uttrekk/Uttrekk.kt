package no.nav.k9.los.nyoppgavestyring.uttrekk

import java.time.OffsetDateTime

enum class UttrekkStatus {
    OPPRETTET,
    KJØRER,
    FULLFØRT,
    FEILET
}

class Uttrekk private constructor(
    val id: Long?,
    val opprettetTidspunkt: OffsetDateTime,
    status: UttrekkStatus,
    val lagretSøkId: Long,
    val kjøreplan: String?,
    resultat: String?
) {
    var status: UttrekkStatus = status
        private set

    var resultat: String? = resultat
        private set

    fun markerSomKjører() {
        if (status != UttrekkStatus.OPPRETTET) {
            throw IllegalStateException("Kan kun starte uttrekk som er i status OPPRETTET")
        }
        status = UttrekkStatus.KJØRER
    }

    fun markerSomFullført(resultatData: String) {
        if (status != UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan kun fullføre uttrekk som er i status KJØRER")
        }
        status = UttrekkStatus.FULLFØRT
        resultat = resultatData
    }

    fun markerSomFeilet(feilmelding: String) {
        if (status != UttrekkStatus.KJØRER) {
            throw IllegalStateException("Kan kun feile uttrekk som er i status KJØRER")
        }
        status = UttrekkStatus.FEILET
        resultat = feilmelding
    }

    companion object {
        fun opprettUttrekk(lagretSokId: Long, kjoreplan: String?): Uttrekk {
            return Uttrekk(
                id = null,
                opprettetTidspunkt = OffsetDateTime.now(),
                status = UttrekkStatus.OPPRETTET,
                lagretSøkId = lagretSokId,
                kjøreplan = kjoreplan,
                resultat = null
            )
        }

        fun fraEksisterende(
            id: Long,
            opprettetTidspunkt: OffsetDateTime,
            status: UttrekkStatus,
            lagretSokId: Long,
            kjoreplan: String?,
            resultat: String?
        ): Uttrekk {
            return Uttrekk(id, opprettetTidspunkt, status, lagretSokId, kjoreplan, resultat)
        }
    }
}