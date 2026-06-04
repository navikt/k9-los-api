package no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.db.Spørringstrategi
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.Oppgave
import java.time.LocalDateTime

interface TransientFeltutleder {

    fun hentVerdi(input: HentVerdiInput): List<String>

    fun where(input: WhereInput): SqlMedParams

    fun orderBy(input: OrderByInput): SqlMedParams

    fun select(input: SelectInput): SqlMedParams

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

data class SelectInput(
    val spørringstrategi: Spørringstrategi,
    val now: LocalDateTime,
    val feltområde: String,
    val feltkode: String
)


