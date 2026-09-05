package no.nav.k9.los.søkeboks

import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto

/**
 * Søk etter og tolkning av oppgaver for ett område.
 *
 * Feltdefinisjonene er uavhengige per område: at K9 har feltet `saksnummer` sier ingenting om
 * at AKTIVITETSPENGER har det, eller om at det betyr det samme. Implementasjonene av dette grensesnittet er
 * derfor de eneste stedene feltkoder skal forekomme — felleskode skal aldri kalle
 * [Oppgave.hentVerdi] eller bygge et
 * [no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter] direkte.
 *
 * Konsekvensen er bevisst duplisering mellom implementasjonene: to områder som tilfeldigvis har
 * like feltkoder skal likevel ha hver sin implementasjon. Å trekke ut fellesnevneren og
 * parametrisere den på område ville gjenopprette nettopp den skjulte koblingen dette
 * grensesnittet finnes for å hindre.
 *
 * Implementasjonen for et område velges av [Oppgavesøkere].
 */
interface Oppgavesøk {
    /**
     * Bygger spørringen som besvarer [søkeord] for dette området, eller null dersom området
     * ikke har felt som kan besvare varianten (f.eks. et område uten journalpostbegrep).
     *
     * Returnerer en query framfor et resultat, slik at implementasjonen holdes fri for
     * database- og PDL-avhengigheter og kan enhetstestes uten oppsett.
     */
    fun lagQuery(søkeord: Søkeord): OppgaveQuery?

    /** AktørId-en oppgaven gjelder, brukt til personoppslag. Null om området ikke fører den. */
    fun aktørId(oppgave: Oppgave): String?

    /** Identifikator for saken oppgaven hører til, brukt til å gruppere oppgaver per sak. */
    fun saksnummer(oppgave: Oppgave): String?

    /** Om oppgaven skal vises for sluttbruker. K9 skjuler f.eks. oppgaver med ytelsestype OBSOLETE. */
    fun erSynlig(oppgave: Oppgave): Boolean

    fun tilSammendrag(oppgave: Oppgave, person: PersonPdl?): OppgaveSammendragDto
}
