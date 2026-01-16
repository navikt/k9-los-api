package no.nav.k9.los.nyoppgavestyring.query

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator

fun lagBeskrivelse(query: OppgaveQuery): String = buildList {
    lagOppgavestatusBeskrivelse(query.filtere, this)
    lagYtelsestypeBeskrivelse(query.filtere, this)
    lagOppgavetypeBeskrivelse(query.filtere, this)
    lagLiggerHosBeslutterBeskrivelse(query.filtere, this)
    lagBehandlingTypeBeskrivelse(query.filtere, this)
    lagAntallAndreKriterier(query.filtere, this)

    if (this.isEmpty()) {
        this.add("Alle oppgaver")
    }
}.joinToString(", ")

private fun lagOppgavestatusBeskrivelse(filtere: List<Oppgavefilter>, beskrivelser: MutableList<String>) {
    val (statusVerdier, nektelse) = finnVerdierForFelt(filtere, "oppgavestatus")
    if (statusVerdier.isEmpty()) return

    val alleStatuser = Oppgavestatus.entries.map { it.kode }.toSet()
    if (statusVerdier.toSet() == alleStatuser) return

    val statusNavn = statusVerdier.map { kode ->
        Oppgavestatus.fraKode(kode).visningsnavn.lowercase()
    }

    if (statusNavn.isEmpty()) return

    beskrivelser.add(statusNavn.joinToString("/").hensyntaNektelse(nektelse))
}

private fun lagYtelsestypeBeskrivelse(filtere: List<Oppgavefilter>, beskrivelser: MutableList<String>) {
    val (ytelseVerdier, nektelse) = finnVerdierForFelt(filtere, "ytelsestype")
    if (ytelseVerdier.isEmpty()) return

    val ytelseNavn = ytelseVerdier.mapNotNull { kode ->
        runCatching { FagsakYtelseType.fraKode(kode) }.getOrNull()?.navn
    }

    if (ytelseNavn.isEmpty()) return

    beskrivelser.add(ytelseNavn.joinToString("/").hensyntaNektelse(nektelse))
}

private fun lagOppgavetypeBeskrivelse(filtere: List<Oppgavefilter>, beskrivelser: MutableList<String>) {
    val (oppgavetypeVerdier, nektelse) = finnVerdierForFelt(filtere, "oppgavetype")
    if (oppgavetypeVerdier.isEmpty()) return

    beskrivelser.add(oppgavetypeVerdier.joinToString("/").hensyntaNektelse(nektelse, titleCase = false))
}

private fun lagLiggerHosBeslutterBeskrivelse(filtere: List<Oppgavefilter>, beskrivelser: MutableList<String>) {
    val (verdier, _) = finnVerdierForFelt(filtere, "liggerHosBeslutter")
    if (verdier.isEmpty()) return

    when {
        verdier.any { it == "true" } -> beskrivelser.add("Til beslutter")
        verdier.any { it == "false" } -> beskrivelser.add("Ikke til beslutter")
    }
}

private fun lagBehandlingTypeBeskrivelse(filtere: List<Oppgavefilter>, beskrivelser: MutableList<String>) {
    val (behandlingTypeVerdier, nektelse) = finnVerdierForFelt(filtere, "behandlingTypekode")
    if (behandlingTypeVerdier.isEmpty()) return

    val behandlingTypeNavn = behandlingTypeVerdier.mapNotNull { kode ->
        runCatching { BehandlingType.fraKode(kode) }.getOrNull()?.navn
    }

    if (behandlingTypeNavn.isEmpty()) return

    val tekst = when {
        behandlingTypeNavn.size > 4 -> "${behandlingTypeNavn.size} behandlingstyper"
        else -> behandlingTypeNavn.joinToString("/")
    }

    beskrivelser.add(tekst.hensyntaNektelse(nektelse))
}

private fun lagAntallAndreKriterier(filtere: List<Oppgavefilter>, beskrivelser: MutableList<String>) {
    val kjenteFelter = setOf(
        "personbeskyttelse",
        "oppgavestatus",
        "ytelsestype",
        "oppgavetype",
        "liggerHosBeslutter",
        "behandlingTypekode"
    )

    val antallAndreKriterier =
        filtere.count { it is CombineOppgavefilter || it is FeltverdiOppgavefilter && it.kode !in kjenteFelter }
    when (antallAndreKriterier) {
        0 -> null
        1 -> beskrivelser.add("$antallAndreKriterier ${if (beskrivelser.isEmpty()) "" else "annet"} kriterie")
        else -> beskrivelser.add("$antallAndreKriterier ${if (beskrivelser.isEmpty()) "" else "andre"} kriterier")
    }
}

private fun finnVerdierForFelt(filtere: List<Oppgavefilter>, feltkode: String): Pair<List<String>, Boolean> {
    for (filter in filtere) {
        if (filter is FeltverdiOppgavefilter && filter.kode == feltkode) {
            return filter.verdi.mapNotNull { it?.toString() } to (filter.operator == EksternFeltverdiOperator.NOT_EQUALS || filter.operator == EksternFeltverdiOperator.NOT_IN)
        }
    }
    return Pair(emptyList(), false)
}

private fun String.hensyntaNektelse(nektelse: Boolean, titleCase: Boolean = true): String {
    return if (nektelse) {
        "Ikke $this"
    } else if (titleCase) {
        this.replaceFirstChar { it.uppercase() }
    } else {
        this
    }
}
