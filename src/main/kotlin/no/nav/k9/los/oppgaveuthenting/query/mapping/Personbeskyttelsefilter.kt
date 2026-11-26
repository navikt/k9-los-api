package no.nav.k9.los.oppgaveuthenting.query.mapping

import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.oppgaveuthenting.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.dto.query.Oppgavefilter

internal object Personbeskyttelsefilter {
    const val FELTKODE = "personbeskyttelse"
    private val verdierSomSkalSplittes = listOf(PersonBeskyttelseType.KODE7.kode, PersonBeskyttelseType.EGEN_ANSATT.kode)

    fun standard(kode6: Boolean) = FeltverdiOppgavefilter(
        område = null,
        kode = FELTKODE,
        operator = if (kode6) EksternFeltverdiOperator.IN else EksternFeltverdiOperator.NOT_IN,
        verdi = if (kode6) listOf(PersonBeskyttelseType.KODE6.kode) else verdierSomSkalSplittes,
    )

    fun migrer(query: OppgaveQuery): OppgaveQuery = query.copy(filtere = query.filtere.map(::migrer))

    private fun migrer(filter: Oppgavefilter): Oppgavefilter = when (filter) {
        is CombineOppgavefilter -> filter.copy(filtere = filter.filtere.map(::migrer))
        is FeltverdiOppgavefilter -> migrerFeltverdi(filter)
    }

    private fun migrerFeltverdi(filter: FeltverdiOppgavefilter): Oppgavefilter {
        val kombinert = PersonBeskyttelseType.KODE7_ELLER_EGEN_ANSATT.kode
        val ugradert = PersonBeskyttelseType.UGRADERT.kode
        if (filter.område != null || filter.kode != FELTKODE || filter.verdi.none { it == kombinert || it == ugradert }) {
            return filter
        }

        val negativ = when (filter.operator) {
            EksternFeltverdiOperator.EQUALS, EksternFeltverdiOperator.IN -> false
            EksternFeltverdiOperator.NOT_EQUALS, EksternFeltverdiOperator.NOT_IN -> true
            else -> error("Kan ikke migrere operator ${filter.operator} for $FELTKODE")
        }
        val andreVerdier = filter.verdi.filterNot { it == ugradert }.flatMap { verdi ->
            if (verdi == kombinert) verdierSomSkalSplittes else listOf(verdi)
        }.distinct()
        val nyttFilter = filter.copy(
            operator = if (negativ) EksternFeltverdiOperator.NOT_IN else EksternFeltverdiOperator.IN,
            verdi = andreVerdier,
        )
        if (ugradert !in filter.verdi) return nyttFilter

        val ugradertFilter = standard(kode6 = false).let {
            if (negativ) it.copy(operator = EksternFeltverdiOperator.IN) else it
        }
        if (andreVerdier.isEmpty()) return ugradertFilter

        return CombineOppgavefilter(
            combineOperator = if (negativ) CombineOperator.AND else CombineOperator.OR,
            filtere = listOf(nyttFilter, ugradertFilter),
        )
    }
}
