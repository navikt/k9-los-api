package no.nav.k9.los.jobbplanlegger

import java.time.DayOfWeek
import java.time.LocalDateTime

data class Tidsvindu(
    val perioder: List<DagligPeriode>
) {
    companion object {
        fun hverdager(fraKl: Int = 0, tilKl: Int = 24): Tidsvindu {
            return Tidsvindu(
                listOf(
                    DagligPeriode(
                        dager = setOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                        ),
                        tidsperiode = Tidsperiode(
                            fraKl = fraKl,
                            tilKl = tilKl
                        )
                    )
                )
            )
        }
    }

    fun erInnenfor(nå: LocalDateTime): Boolean {
        return perioder.any { periode ->
            periode.dager.contains(nå.dayOfWeek) &&
                    nå.hour >= periode.tidsperiode.fraKl &&
                    nå.hour < periode.tidsperiode.tilKl
        }
    }

    fun komplement(): Tidsvindu {
        val alleDager = DayOfWeek.entries.toSet()
        val døgnet = Tidsperiode(0, 24)

        // Lag først en periode som dekker hele døgnet for alle dager
        val fullPeriode = DagligPeriode(alleDager, døgnet)

        // For hver eksisterende periode, splitt opp fullPeriode
        var resultatPerioder = listOf(fullPeriode)

        for (periode in perioder) {
            resultatPerioder = resultatPerioder.flatMap { it.subtraher(periode) }
        }

        return Tidsvindu(resultatPerioder)
    }
}

data class DagligPeriode(
    val dager: Set<DayOfWeek>,
    val tidsperiode: Tidsperiode
) {
    fun subtraher(other: DagligPeriode): List<DagligPeriode> {
        val resultat = mutableListOf<DagligPeriode>()

        // Dager som ikke overlapper kan beholdes som de er
        val ikkeOverlappendeDager = dager - other.dager
        if (ikkeOverlappendeDager.isNotEmpty()) {
            resultat.add(DagligPeriode(ikkeOverlappendeDager, tidsperiode))
        }

        // For overlappende dager, må vi håndtere tidsperiodene
        val overlappendeDager = dager.intersect(other.dager)
        if (overlappendeDager.isNotEmpty()) {
            val tidsPerioder = tidsperiode.subtraher(other.tidsperiode)
            for (periode in tidsPerioder) {
                resultat.add(DagligPeriode(overlappendeDager, periode))
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

