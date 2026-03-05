package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import java.time.LocalDateTime

class LagretSøk private constructor(
    val id: Long?,
    val lagetAv: Long,
    tittel: String,
    beskrivelse: String,
    sistEndret: LocalDateTime,
    query: OppgaveQuery,
    antall: Long?,
    antallOppdatert: LocalDateTime?,
) {
    var tittel: String = tittel
        private set

    var beskrivelse: String = beskrivelse
        private set

    var query: OppgaveQuery = query
        private set

    var sistEndret: LocalDateTime = sistEndret
        private set

    var antall: Long? = antall
        private set

    var antallOppdatert: LocalDateTime? = antallOppdatert
        private set

    fun endre(endreLagretSøk: EndreLagretSøkRequest, saksbehandler: Saksbehandler) {
        if (saksbehandler.id != lagetAv) {
            throw IllegalStateException("Kan ikke endre lagret søk som ikke er opprettet av seg selv")
        }
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

    fun kopier(tittel: String, saksbehandler: Saksbehandler): LagretSøk {
        return LagretSøk(
            id = null,
            lagetAv = saksbehandler.id ?: throw IllegalStateException("Saksbehandler må ha id"),
            tittel = tittel,
            beskrivelse = "",
            sistEndret = LocalDateTime.now(),
            query = this.query,
            antall = null,
            antallOppdatert = null,
        )
    }

    fun oppdaterAntall(antall: Long) {
        this.antall = antall
        this.antallOppdatert = LocalDateTime.now()
    }

    companion object {
        fun defaultQuery(kode6: Boolean): OppgaveQuery = OppgaveQuery(
            filtere = listOf(
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "oppgavestatus",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = listOf(Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode)
                ),
                FeltverdiOppgavefilter(
                    område = null,
                    kode = "personbeskyttelse",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = listOf(if (kode6) PersonBeskyttelseType.KODE6.kode else PersonBeskyttelseType.UGRADERT.kode)
                ),
                FeltverdiOppgavefilter(
                    område = "K9",
                    kode = "ytelsestype",
                    operator = EksternFeltverdiOperator.IN,
                    verdi = emptyList()
                )
            ),
            order = emptyList()
        )

        // For nye søk som ikke er lagret ennå
        fun nyttSøk(
            nyttLagretSøk: NyttLagretSøkRequest,
            saksbehandler: Saksbehandler,
        ): LagretSøk {
            return LagretSøk(
                id = null,
                lagetAv = saksbehandler.id ?: throw IllegalStateException("Saksbehandler må ha id"),
                tittel = nyttLagretSøk.tittel,
                beskrivelse = "",
                sistEndret = LocalDateTime.now(),
                query = nyttLagretSøk.query,
                antall = null,
                antallOppdatert = null
            )
        }

        // For å konstruere fra database, eller i tester
        fun fraEksisterende(
            id: Long,
            lagetAv: Long,
            tittel: String,
            beskrivelse: String,
            sistEndret: LocalDateTime,
            query: OppgaveQuery,
            antall: Long?,
            antallOppdatert: LocalDateTime?,
        ): LagretSøk {
            return LagretSøk(id, lagetAv, tittel, beskrivelse, sistEndret, query, antall, antallOppdatert)
        }
    }
}