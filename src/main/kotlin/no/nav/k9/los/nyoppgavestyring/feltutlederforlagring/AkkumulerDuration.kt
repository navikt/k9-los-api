package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import java.time.Duration
import java.time.temporal.ChronoUnit

abstract class AkkumulerDuration(
    val beslutningsfelt: String,
    val målfelt: String
) : Feltutleder {

    override fun utled(
        innkommendeOppgave: OppgaveV3,
        aktivOppgaveVersjon: OppgaveV3?
    ): OppgaveFeltverdi? {

        if (aktivOppgaveVersjon == null) {
            return null
        }

        val akkumulertFraTidligere =
            aktivOppgaveVersjon.hentVerdi(målfelt)?.let { Duration.parse(it) }
                ?: Duration.ZERO
        val akkumulertVentetidSaksbehandler =
            if (aktivOppgaveVersjon.hentVerdi(beslutningsfelt).toBoolean()) {
                Duration.between(aktivOppgaveVersjon.endretTidspunkt, innkommendeOppgave.endretTidspunkt)
                    .plus(akkumulertFraTidligere)
            } else {
                Duration.ZERO.plus(akkumulertFraTidligere)
            }
        val avrundet = akkumulertVentetidSaksbehandler.truncatedTo(ChronoUnit.SECONDS)

        return OppgaveFeltverdi(
            oppgavefelt = innkommendeOppgave.hentFelt(målfelt),
            verdi = tilIso8601MedDager(avrundet),
            verdiBigInt = null
        )
    }

    companion object {
        /**
         * Formaterer en Duration til ISO-8601-streng som inkluderer dager eksplisitt,
         * f.eks. P79DT18H41M10S i stedet for PT1914H41M10S.
         *
         * Dette gir mer lesbare verdier i databasen. Begge formater er gyldige
         * ISO-8601 og forstås av både Duration.parse() og PostgreSQL sin
         * CAST(... AS interval).
         */
        fun tilIso8601MedDager(duration: Duration): String {
            val dager = duration.toDays()
            val rest = duration.minusDays(dager)
            val timer = rest.toHours()
            val minutter = rest.toMinutesPart()
            val sekunder = rest.toSecondsPart()

            return buildString {
                append('P')
                if (dager != 0L) append("${dager}D")
                if (timer != 0L || minutter != 0 || sekunder != 0 || dager == 0L) {
                    append('T')
                    if (timer != 0L) append("${timer}H")
                    if (minutter != 0) append("${minutter}M")
                    if (sekunder != 0 || (timer == 0L && minutter == 0)) append("${sekunder}S")
                }
            }
        }
    }
}