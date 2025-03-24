package no.nav.k9.los.spi.felter

import no.nav.k9.los.nyoppgavestyring.query.db.Spørringstrategi
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import java.time.LocalDateTime

/**
 *
 */
interface TransientFeltutleder {

    fun hentVerdi(input: HentVerdiInput): List<String>

    fun where(input: WhereInput): SqlMedParams

    fun orderBy(input: OrderByInput): SqlMedParams

    companion object {
        fun hentId(it: TransientFeltutleder): String {
            return it.javaClass.canonicalName
        }
    }
}

data class HentVerdiInput(
    val now: LocalDateTime,
    val oppgave: Oppgave,
    val feltområde: String,
    val feltkode: String
)

data class WhereInput(
    val spørringstrategi: Spørringstrategi,
    val now: LocalDateTime,
    val feltområde: String,
    val feltkode: String,
    val operator: FeltverdiOperator, // TODO: Egen eksponert enum her.
    val feltverdi: Any?
)

data class OrderByInput(
    val spørringstrategi: Spørringstrategi,
    val now: LocalDateTime,
    val feltområde: String,
    val feltkode: String,
    val økende: Boolean
)


