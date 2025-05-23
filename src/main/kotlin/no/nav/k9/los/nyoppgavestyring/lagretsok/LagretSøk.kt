package no.nav.k9.los.nyoppgavestyring.lagretsok

import kotliquery.Row
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler

class LagretSøk private constructor(
    val id: Long?,
    val lagetAv: Long,
    versjon: Long,
    tittel: String,
    beskrivelse: String,
    query: OppgaveQuery
) {
    var versjon: Long = versjon
        private set

    var tittel: String = tittel
        private set

    var beskrivelse: String = beskrivelse
        private set

    var query: OppgaveQuery = query
        private set

    fun endre(endreLagretSøk: EndreLagretSøk, saksbehandler: Saksbehandler) {
        if (saksbehandler.id != lagetAv) {
            throw IllegalStateException("Kan ikke endre lagret søk som ikke er opprettet av seg selv")
        }
        versjon += 1
        tittel = endreLagretSøk.tittel
        beskrivelse = endreLagretSøk.beskrivelse
        query = endreLagretSøk.query
    }

    fun sjekkOmKanSlette(saksbehandler: Saksbehandler) {
        if (saksbehandler.id != lagetAv) {
            throw IllegalStateException("Kan ikke slette lagret søk som ikke er opprettet av seg selv")
        }
    }

    companion object {
        // For nye søk som ikke er lagret ennå
        fun opprettSøk(
            opprettLagretSøk: OpprettLagretSøk,
            saksbehandler: Saksbehandler
        ): LagretSøk {
            return LagretSøk(
                id = null,
                lagetAv = saksbehandler.id ?: throw IllegalStateException("Saksbehandler må ha id"),
                versjon = 1,
                tittel = opprettLagretSøk.tittel,
                beskrivelse = opprettLagretSøk.beskrivelse,
                query = OppgaveQuery()
            )
        }

        fun fraRow(row: Row): LagretSøk {
            return LagretSøk(
                id = row.long("id"),
                lagetAv = row.long("laget_av"),
                versjon = row.long("versjon"),
                tittel = row.string("tittel"),
                beskrivelse = row.string("beskrivelse"),
                query = LosObjectMapper.instance.readValue(row.string("query"), OppgaveQuery::class.java)
            )
        }
    }
}