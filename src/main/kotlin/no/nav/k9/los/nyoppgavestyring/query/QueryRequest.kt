package no.nav.k9.los.nyoppgavestyring.query

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery

data class QueryRequest(
    val oppgaveQuery: OppgaveQuery,
    val fjernReserverte: Boolean = false,

    val avgrensning: Avgrensning? = null,
)

data class Avgrensning (
    val limit: Long = -1,
    val offset: Long = -1
) {
     companion object {
         fun maxAntall(antall: Long) : Avgrensning {
             return Avgrensning(
                 limit = antall
             )
         }

         fun paginert(sidestørrelse: Long, sidetall: Long) : Avgrensning {
             if (sidetall < 1) throw IllegalArgumentException("Paginering kriver sidetall 1 eller høyere!")
             return Avgrensning(
                 limit = sidestørrelse,
                 offset = (sidetall-1)*sidestørrelse
             )
         }
     }
}