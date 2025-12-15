package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.Row
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveId
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator

interface OppgaveQuerySqlBuilder {
    fun filterRens(felter: Map<OmrådeOgKode, OppgavefeltMedMer>, filtere: List<Oppgavefilter>): List<Oppgavefilter>

    fun medFeltverdi(
        combineOperator: CombineOperator,
        feltområde: String?,
        feltkode: String,
        operator: FeltverdiOperator,
        feltverdier: List<Any?>
    )
    fun medBlokk(combineOperator: CombineOperator, defaultTrue: Boolean, blokk: () -> Unit)
    fun medEnkelOrder(feltområde: String?, feltkode: String, økende: Boolean)

    fun utenReservasjoner()
    fun medPaging(limit: Long, offset: Long)
    fun medAntallSomResultat()

    /**
     * Konfigurerer hvilke felter som skal hentes i resultatet.
     * Brukes for effektive spørringer som returnerer OppgaveResultat.
     */
    fun medSelectFelter(selectFelter: List<EnkelSelectFelt>)

    fun mapRowTilId(row: Row): OppgaveId
    fun mapRowTilEksternId(row: Row): EksternOppgaveId

    /**
     * Mapper en rad til OppgaveResultat med ekstern ID og de konfigurerte feltene.
     * Krever at medSelectFelter() er kalt først.
     */
    fun mapRowTilOppgaveResultat(row: Row): OppgaveResultat

    /** Skal bare brukes til debugging, siden parametrene settes inn ukritisk */
    fun unsafeDebug(): String {
        var queryWithParams = getQuery()

        // erstatter placeholdere reversert siden f.eks. ':feltverdi1' også matcher ':feltverdi10'
        for ((key, value) in getParams().toSortedMap().reversed()) {
            queryWithParams = queryWithParams.replace(":$key", if (value is Number || value == null) value.toString() else "'$value'")
        }

        return queryWithParams
    }

    // Det som bygges
    fun getQuery(): String
    fun getParams(): Map<String, Any?>
}
