package no.nav.k9.los.nyoppgavestyring.jobbplanlegger

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

sealed class Tidsvindu {
    abstract fun nesteÅpningITidsvindu(nå: LocalDateTime): LocalDateTime
    abstract fun erInnenfor(nå: LocalDateTime): Boolean
    abstract fun komplement(): Tidsvindu

    data object ÅPENT : Tidsvindu() {
        override fun nesteÅpningITidsvindu(nå: LocalDateTime): LocalDateTime {
            return nå
        }

        override fun erInnenfor(nå: LocalDateTime): Boolean {
            return true
        }

        override fun komplement(): Tidsvindu {
            return LUKKET
        }
    }

    data object LUKKET : Tidsvindu() {
        override fun nesteÅpningITidsvindu(nå: LocalDateTime): LocalDateTime {
            throw IllegalStateException("Kan ikke finne neste tidspunkt i lukket tidsvindu")
        }

        override fun erInnenfor(nå: LocalDateTime): Boolean {
            return false
        }

        override fun komplement(): Tidsvindu {
            return ÅPENT
        }
    }

    companion object {
        fun hverdager(fraKl: Int = 0, tilKl: Int = 24): Tidsvindu {
            return TidsvinduMedPerioder(
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
                ).map {
                    DagligPeriode(
                        dag = it,
                        tidsperiode = Tidsperiode(
                            fraKl = fraKl,
                            tilKl = tilKl
                        )
                    )
                }
            )
        }

        fun alleDager(fraKl: Int = 0, tilKl: Int = 24): Tidsvindu {
            return TidsvinduMedPerioder(
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY
                ).map {
                    DagligPeriode(
                        dag = it,
                        tidsperiode = Tidsperiode(
                            fraKl = fraKl,
                            tilKl = tilKl
                        )
                    )
                }
            )
        }
    }
}

class TidsvinduMedPerioder(
    private val perioder: List<DagligPeriode>
) : Tidsvindu() {
    override fun nesteÅpningITidsvindu(nå: LocalDateTime): LocalDateTime {
        if (erInnenfor(nå)) {
            return nå
        }

        // Sjekk denne uken
        for (periode in perioder) {
            val startAvPeriode = periode.startAvPeriode(nå)
            if (nå.isBefore(startAvPeriode)) {
                return startAvPeriode
            }
        }

        // Hvis ingen periode funnet denne uken, gå til neste uke
        val startAvNesteUke = nå.plusWeeks(1)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return perioder.minOfOrNull { it.startAvPeriode(startAvNesteUke) }
            ?: throw IllegalStateException("Kan ikke finne neste tidspunkt i tidsvindu")
    }

    override fun erInnenfor(nå: LocalDateTime): Boolean {
        return perioder.any { periode ->
            periode.dag == nå.dayOfWeek &&
                    nå.hour >= periode.tidsperiode.fraKl &&
                    nå.hour < periode.tidsperiode.tilKl
        }
    }

    override fun komplement(): Tidsvindu {
        val alleDager = DayOfWeek.entries.toSet()
        val døgnet = Tidsperiode(0, 24)

        var resultatPerioder = alleDager.map { DagligPeriode(it, døgnet) }

        for (periode in perioder) {
            resultatPerioder = resultatPerioder.flatMap { it.subtraher(periode) }
        }

        return TidsvinduMedPerioder(resultatPerioder.filterNot {
            it.tidsperiode.fraKl == it.tidsperiode.tilKl
        })
    }
}

data class DagligPeriode(
    val dag: DayOfWeek,
    val tidsperiode: Tidsperiode
) {
    /**
     * Finner LocalDateTime for en gitt ukedag og klokkeslett i samme uke som referansetidspunktet.
     */
    fun startAvPeriode(referanse: LocalDateTime): LocalDateTime {
        val startAvUken = referanse.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dagerFraMandagTilØnsketDag = dag.value - DayOfWeek.MONDAY.value
        return startAvUken
            .plusDays(dagerFraMandagTilØnsketDag.toLong())
            .withHour(tidsperiode.fraKl)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
    }

    fun subtraher(other: DagligPeriode): List<DagligPeriode> {
        val resultat = mutableListOf<DagligPeriode>()

        // Dager som ikke overlapper kan beholdes som de er
        if (dag != other.dag) {
            resultat.add(DagligPeriode(dag, tidsperiode))
        }

        // For overlappende dager, må vi håndtere tidsperiodene
        if (dag == other.dag) {
            val tidsPerioder = tidsperiode.subtraher(other.tidsperiode)
            for (periode in tidsPerioder) {
                resultat.add(DagligPeriode(dag, periode))
            }
        }

        return resultat
    }
}

data class Tidsperiode(
    val fraKl: Int,
    val tilKl: Int
) {
    fun subtraher(other: Tidsperiode): List<Tidsperiode> {
        val resultat = mutableListOf<Tidsperiode>()

        // Hvis periodene ikke overlapper, behold original
        if (tilKl <= other.fraKl || fraKl >= other.tilKl) {
            resultat.add(this)
            return resultat
        }

        // Sjekk om vi har en del før other starter
        if (fraKl < other.fraKl) {
            resultat.add(Tidsperiode(fraKl, other.fraKl))
        }

        // Sjekk om vi har en del etter other slutter
        if (tilKl > other.tilKl) {
            resultat.add(Tidsperiode(other.tilKl, tilKl))
        }

        return resultat
    }
}

