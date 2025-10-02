package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import java.time.LocalDateTime

class LagretSøk private constructor(
    val id: Long?,
    val lagetAv: Long,
    versjon: Long,
    tittel: String,
    beskrivelse: String,
    sistEndret: LocalDateTime,
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

    var sistEndret: LocalDateTime = sistEndret
        private set

    fun endre(endreLagretSøk: EndreLagretSøk, saksbehandler: Saksbehandler) {
        if (saksbehandler.id != lagetAv) {
            throw IllegalStateException("Kan ikke endre lagret søk som ikke er opprettet av seg selv")
        }
        if (endreLagretSøk.versjon != versjon) {
            throw IllegalStateException("Kan ikke endre lagret søk med feil versjon.")
        }
        versjon += 1
        tittel = endreLagretSøk.tittel
        beskrivelse = endreLagretSøk.beskrivelse
        query = endreLagretSøk.query
        sistEndret = LocalDateTime.now()
    }

    fun sjekkOmKanSlette(saksbehandler: Saksbehandler) {
        if (saksbehandler.id != lagetAv) {
            throw IllegalStateException("Kan ikke slette lagret søk som ikke er opprettet av seg selv")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LagretSøk) return false
        
        // Hvis begge har id, sammenlign på id
        if (id != null && other.id != null) {
            return id == other.id
        }
        
        // Hvis en har id og den andre ikke, er de ikke like
        if (id != null || other.id != null) {
            return false
        }
        
        // Hvis ingen har id, bruk object identity
        return false
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: System.identityHashCode(this)
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
                beskrivelse = "",
                sistEndret = LocalDateTime.now(),
                query = OppgaveQuery(),
            )
        }

        // For å konstruere fra database, eller i tester
        fun fraEksisterende(
            id: Long,
            lagetAv: Long,
            versjon: Long,
            tittel: String,
            beskrivelse: String,
            sistEndret: LocalDateTime,
            query: OppgaveQuery = OppgaveQuery()
        ): LagretSøk {
            return LagretSøk(id, lagetAv, versjon, tittel, beskrivelse, sistEndret, query)
        }
    }
}