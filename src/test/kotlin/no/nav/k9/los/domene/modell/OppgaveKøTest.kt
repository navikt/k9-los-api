package no.nav.k9.los.domene.modell

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


internal class OppgaveKøTest {
    @Test
    fun `Punsj oppgave skal med i køen med dette filter oppsettet`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(
                FagsakYtelseType.OMSORGSPENGER,
                FagsakYtelseType.OMSORGSDAGER,
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.UKJENT
            ),
            mutableListOf(AndreKriterierDto("1", AndreKriterierType.FRA_PUNSJ, checked = true, inkluder = true)),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(id = null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val oppgave = Oppgave(
            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})
        assertTrue(tilhørerOppgaveTilKø)
    }

    @Test
    fun `Ikke journalført punsjoppgave skal med i køen med dette filter oppsettet`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "Ikke journalførte punsjoppgaver",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(
                FagsakYtelseType.OMSORGSPENGER,
                FagsakYtelseType.OMSORGSDAGER,
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.UKJENT
            ),
            mutableListOf(
                AndreKriterierDto("1", AndreKriterierType.IKKE_JOURNALFØRT, checked = true, inkluder = true)
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(id = null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val ikkeJournalførtPunsjoppgave = Oppgave(
            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = "PUNSJ",
            journalførtTidspunkt = null,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false,
            utenlands = false,
        )

        val ikkeJournalførtPunsjoppgaveErIKøen = oppgaveKø.tilhørerOppgaveTilKø(ikkeJournalførtPunsjoppgave, erOppgavenReservertSjekk = {false})
        assertTrue(ikkeJournalførtPunsjoppgaveErIKøen)
    }

    @Test
    fun `Journalført punsjoppgave skal ikke med i køen med dette filter oppsettet`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "Ikke journalførte punsjoppgaver",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(
                FagsakYtelseType.OMSORGSPENGER,
                FagsakYtelseType.OMSORGSDAGER,
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.UKJENT
            ),
            mutableListOf(
                AndreKriterierDto("1", AndreKriterierType.IKKE_JOURNALFØRT, checked = true, inkluder = true)
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(id = null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val ikkeJournalførtPunsjoppgave = Oppgave(
            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = "PUNSJ",
            journalførtTidspunkt = LocalDateTime.now(),
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false,
            utenlands = false,
        )

        val journalførtPunsjoppgaveErIKøen = oppgaveKø.tilhørerOppgaveTilKø(ikkeJournalførtPunsjoppgave, erOppgavenReservertSjekk = {false})
        assertFalse(journalførtPunsjoppgaveErIKøen)
    }

    @Test
    fun `skal ta med oppgaver som ligger til beslutter og som inneholder en av 9005, 9008 elle 9007`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(
                AndreKriterierDto(
                    "1",
                    AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD,
                    checked = true,
                    inkluder = true
                )
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(null,"OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val oppgave = Oppgave(

            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                mapOf(
                    "5015" to "UTFO",
                    "5016" to "OPPR",
                    "5040" to "AVBR",
                    "9001" to "UTFO",
                    "9005" to "UTFO",
                    "9007" to "AVBR",
                    "9200" to "UTFO",
                    "9201" to "UTFO"
                )
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            eksternId = UUID.randomUUID(),
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})
        assertTrue(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal ikke ta me hvis den ikke inneholder 9007, 9005 eller 9008`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(
                AndreKriterierDto(
                    "1",
                    AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD,
                    checked = true,
                    inkluder = true
                )
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val oppgave = Oppgave(

            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                mapOf(
                    "5016" to "OPPR"

                )
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            eksternId = UUID.randomUUID(),
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})
        assertFalse(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal ta me hvis den ikke inneholder 9007, 9005 eller 9008`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(
                AndreKriterierDto(
                    "1",
                    AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD_AKSJONSPUNKT,
                    checked = true,
                    inkluder = true
                )
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val oppgave = Oppgave(

            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                mapOf(
                    "9005" to "OPPR"
                )
            ),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            eksternId = UUID.randomUUID(),
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})
        assertTrue(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal fjerne 9005, 9008, 9007 fra vanlig beslutter kø`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN),
            mutableListOf(
                AndreKriterierDto(
                    "1",
                    AndreKriterierType.TIL_BESLUTTER,
                    checked = true,
                    inkluder = true
                ),
                AndreKriterierDto(
                    "2",
                    AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD,
                    checked = true,
                    inkluder = false
                )
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val oppgave = Oppgave(

            fagsakSaksnummer = "12423",
            aktorId = "273857",
            journalpostId = "",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                mapOf(
                    "9001" to "UTFO",
                    "9007" to "AVBR",
                    "9005" to "UTFO",
                    "5015" to "UTFO",
                    "5016" to "OPPR",
                    "5040" to "UTFO"
                )
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            eksternId = UUID.randomUUID(),
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})
        assertFalse(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal fjerne fra vanlig beslutter kø`() {
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(
                AndreKriterierDto(
                    "1",
                    AndreKriterierType.FORLENGELSER_FRA_INFOTRYGD,
                    checked = true,
                    inkluder = false
                )
            ),
            Enhet.NASJONAL,
            null,
            null,
            mutableListOf(Saksbehandler(null,"OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf()
        )

        val oppgave = Oppgave(

            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                mapOf(
                    "5016" to "OPPR",
                    "9005" to "UTFO"
                )
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            eksternId = UUID.randomUUID(),
        )

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})
        assertFalse(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal ta med feilutbetaling intervall`() {
        assertTrue(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(70), erOppgavenReservertSjekk = {false}))
        assertFalse(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(200), erOppgavenReservertSjekk = {false}))
        assertTrue(lagOppgaveKø(Intervall(50, null)).tilhørerOppgaveTilKø(feilutb_oppg(200), erOppgavenReservertSjekk = {false}))
        assertFalse(lagOppgaveKø(Intervall(50, null)).tilhørerOppgaveTilKø(feilutb_oppg(49), erOppgavenReservertSjekk = {false}))
        assertTrue(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(50), erOppgavenReservertSjekk = {false}))
        assertTrue(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(100), erOppgavenReservertSjekk = {false}))
    }

    @Test
    fun `skal håndtere med nytt krav, med og uten kombinasjon med andre kriterier`() {

        assertTrue(lagOppgaveKø(nyeKrav = true).tilhørerOppgaveTilKø(nye_krav_oppg(true), erOppgavenReservertSjekk = {false}))
        assertTrue(lagOppgaveKø(nyeKrav = false).tilhørerOppgaveTilKø(nye_krav_oppg(false), erOppgavenReservertSjekk = {false}))
        assertFalse(lagOppgaveKø(nyeKrav = false).tilhørerOppgaveTilKø(nye_krav_oppg(true), erOppgavenReservertSjekk = {false}))
        assertFalse(lagOppgaveKø(nyeKrav = true).tilhørerOppgaveTilKø(nye_krav_oppg(false), erOppgavenReservertSjekk = {false}))

        assertTrue(
            lagOppgaveKø(nyeKrav = null, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(true, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )

        assertTrue(
            lagOppgaveKø(nyeKrav = null, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(false, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )

        assertFalse(
            lagOppgaveKø(nyeKrav = true, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(true, avklarMedlemskap = false), erOppgavenReservertSjekk = {false})
        )

        assertFalse(
            lagOppgaveKø(nyeKrav = true, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(false, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )

        assertTrue(
            lagOppgaveKø(nyeKrav = true, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(true, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )

        assertTrue(
            lagOppgaveKø(nyeKrav = false, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(false, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )

        assertFalse(
            lagOppgaveKø(nyeKrav = true, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(null, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )

        assertFalse(
            lagOppgaveKø(nyeKrav = false, andreKriterierDto = kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(nye_krav_oppg(null, avklarMedlemskap = true), erOppgavenReservertSjekk = {false})
        )




    }

    @Test
    fun `feilutbetaling filter i kombinasjon med andre kriterie`() {
        assertTrue(
            lagOppgaveKø(Intervall(50, 100), kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(feilutb_oppg(70, true), erOppgavenReservertSjekk = {false})
        )

        assertFalse(
            lagOppgaveKø(Intervall(50, 100), kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(feilutb_oppg(200, true), erOppgavenReservertSjekk = {false})
        )

        assertFalse(
            lagOppgaveKø(Intervall(50, 100), kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(feilutb_oppg(70, false), erOppgavenReservertSjekk = {false})
        )
    }

    @Test
    fun `filtrering av behandlig opprettet dato skal inkludere fra og med`() {
        val køFom = LocalDate.now().minusDays(10)
        val køTom = LocalDate.now().minusDays(5)
        val oppgaveKø = OppgaveKø(
            UUID.randomUUID(),
            "test",
            LocalDate.now(),
            KøSortering.OPPRETT_BEHANDLING,
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            Enhet.NASJONAL,
            køFom,
            køTom,
            mutableListOf(Saksbehandler(null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
            false,
            mutableListOf(),
            filtreringFeilutbetaling = null
        )

        val oppgave = Oppgave(

            fagsakSaksnummer = "",
            aktorId = "273857",
            journalpostId = "234234535",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.of(køFom, LocalTime.now()),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.UKJENT,
            fagsakYtelseType = FagsakYtelseType.UKJENT,
            aktiv = true,
            system = Fagsystem.K9SAK.kode,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(
                mapOf(
                    "5016" to "OPPR",
                    "9005" to "UTFO"
                )
            ),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            eksternId = UUID.randomUUID(),
            feilutbetaltBeløp = null
        )
        assertTrue(oppgaveKø.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false}))
    }

    @Test
    fun `kø med oppgavekoder skal inneholde oppgaver med matchende aksjonspunkt`() {
        val oppgave = oppgaveMedAksjonspunkter(Aksjonspunkter(liste = mapOf("9001" to "OPPR")))
        val køMedSykdomKriterie = lagOppgaveKø(fagsakYtelseTyper = mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN), oppgaveKoder = listOf("9001"))

        assertThat(køMedSykdomKriterie.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})).isTrue()
    }

    @Test
    fun `kø med oppgavekoder skal ikke inneholde oppgaver uten aksjonspunkt`() {
        val oppgave = oppgaveMedAksjonspunkter(Aksjonspunkter(liste = emptyMap()))
        val køMedSykdomKriterie = lagOppgaveKø(fagsakYtelseTyper = mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN), oppgaveKoder = listOf("9001"))

        assertThat(køMedSykdomKriterie.tilhørerOppgaveTilKø(oppgave, erOppgavenReservertSjekk = {false})).isFalse()
    }

    @Test
    fun `kø med oppavekoder skal ikke inneholde oppgaver som ikke har mathende aktive aksjonspunkt`() {
        val oppgaveMedUtførtSykdomsAksjonsPunkt = oppgaveMedAksjonspunkter(Aksjonspunkter(liste = mapOf("9001" to "UTFO")))
        val oppgaveMedEtterlysIMAksjonsPunkt = oppgaveMedAksjonspunkter(Aksjonspunkter(liste = mapOf("9068" to "OPPR")))
        val køMedSykdomKriterie = lagOppgaveKø(fagsakYtelseTyper = mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN), oppgaveKoder = listOf("9001"))

        assertThat(køMedSykdomKriterie.tilhørerOppgaveTilKø(oppgaveMedUtførtSykdomsAksjonsPunkt, erOppgavenReservertSjekk = {false})).isFalse()
        assertThat(køMedSykdomKriterie.tilhørerOppgaveTilKø(oppgaveMedEtterlysIMAksjonsPunkt, erOppgavenReservertSjekk = {false})).isFalse()
    }

    private fun oppgaveMedAksjonspunkter(aksjonspunkter: Aksjonspunkter) = Oppgave(
        fagsakSaksnummer = "",
        aktorId = "273857",
        journalpostId = "234234535",
        behandlendeEnhet = "Enhet",
        behandlingsfrist = LocalDateTime.now(),
        behandlingOpprettet = LocalDateTime.now().minusDays(23),
        forsteStonadsdag = LocalDate.now().plusDays(6),
        behandlingStatus = BehandlingStatus.OPPRETTET,
        behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
        fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        aktiv = true,
        system = "K9SAK",
        oppgaveAvsluttet = null,
        utfortFraAdmin = false,
        eksternId = UUID.randomUUID(),
        oppgaveEgenskap = emptyList(),
        aksjonspunkter = aksjonspunkter,
        tilBeslutter = false,
        utbetalingTilBruker = false,
        selvstendigFrilans = false,
        kombinert = false,
        søktGradering = false,
        årskvantum = false,
        avklarArbeidsforhold = false,
        avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
    )

    private fun merknadOppgave(fagsakYtelseType: FagsakYtelseType) = Oppgave(
        fagsakSaksnummer = "",
        aktorId = "273857",
        journalpostId = "234234535",
        behandlendeEnhet = "Enhet",
        behandlingsfrist = LocalDateTime.now(),
        behandlingOpprettet = LocalDateTime.now().minusDays(23),
        forsteStonadsdag = LocalDate.now().plusDays(6),
        behandlingStatus = BehandlingStatus.OPPRETTET,
        behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
        fagsakYtelseType = fagsakYtelseType,
        aktiv = true,
        system = "K9SAK",
        oppgaveAvsluttet = null,
        utfortFraAdmin = false,
        eksternId = UUID.randomUUID(),
        oppgaveEgenskap = emptyList(),
        aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
        tilBeslutter = false,
        utbetalingTilBruker = false,
        selvstendigFrilans = false,
        kombinert = false,
        søktGradering = false,
        årskvantum = false,
        avklarArbeidsforhold = false,
        avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
    )

    private fun kriterie(type: AndreKriterierType) = AndreKriterierDto("1", type, checked = true, inkluder = true)


    private fun feilutb_oppg(feilutbetaling: Long, avklarMedlemskap: Boolean = false) = Oppgave(

        fagsakSaksnummer = "",
        aktorId = "273857",
        journalpostId = "234234535",
        behandlendeEnhet = "Enhet",
        behandlingsfrist = LocalDateTime.now(),
        behandlingOpprettet = LocalDateTime.now().minusDays(23),
        forsteStonadsdag = LocalDate.now().plusDays(6),
        behandlingStatus = BehandlingStatus.OPPRETTET,
        behandlingType = BehandlingType.UKJENT,
        fagsakYtelseType = FagsakYtelseType.UKJENT,
        aktiv = true,
        system = Fagsystem.K9SAK.kode,
        oppgaveAvsluttet = null,
        utfortFraAdmin = false,
        oppgaveEgenskap = emptyList(),
        aksjonspunkter = Aksjonspunkter(
            mapOf(
                "5016" to "OPPR",
                "9005" to "UTFO"
            )
        ),
        tilBeslutter = true,
        utbetalingTilBruker = false,
        selvstendigFrilans = false,
        kombinert = false,
        søktGradering = false,
        årskvantum = false,
        avklarArbeidsforhold = false,
        avklarMedlemskap = avklarMedlemskap, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
        eksternId = UUID.randomUUID(),
        feilutbetaltBeløp = feilutbetaling
    )
    private fun nye_krav_oppg(nyeKrav: Boolean? = false, avklarMedlemskap: Boolean = false) = Oppgave(
        fagsakSaksnummer = "",
        aktorId = "273857",
        journalpostId = "234234535",
        behandlendeEnhet = "Enhet",
        behandlingsfrist = LocalDateTime.now(),
        behandlingOpprettet = LocalDateTime.now().minusDays(23),
        forsteStonadsdag = LocalDate.now().plusDays(6),
        behandlingStatus = BehandlingStatus.OPPRETTET,
        behandlingType = BehandlingType.UKJENT,
        fagsakYtelseType = FagsakYtelseType.UKJENT,
        aktiv = true,
        system = Fagsystem.K9SAK.kode,
        oppgaveAvsluttet = null,
        utfortFraAdmin = false,
        oppgaveEgenskap = emptyList(),
        aksjonspunkter = Aksjonspunkter(
            mapOf(
                "5016" to "OPPR",
                "9005" to "UTFO"
            )
        ),
        tilBeslutter = true,
        utbetalingTilBruker = false,
        selvstendigFrilans = false,
        kombinert = false,
        søktGradering = false,
        årskvantum = false,
        avklarArbeidsforhold = false,
        avklarMedlemskap = avklarMedlemskap, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
        eksternId = UUID.randomUUID(),
        nyeKrav = nyeKrav
    )


    private fun lagOppgaveKø(
        feilutbetaling: Intervall<Long>? = null,
        andreKriterierDto: AndreKriterierDto? = null,
        merknadKoder: List<String> = emptyList(),
        behandlingTyper: MutableList<BehandlingType> = mutableListOf(),
        fagsakYtelseTyper: MutableList<FagsakYtelseType> = mutableListOf(),
        oppgaveKoder: List<String> = emptyList(),
        nyeKrav: Boolean? = null
    ) = OppgaveKø(
        UUID.randomUUID(),
        "test",
        LocalDate.now(),
        if (feilutbetaling != null) KøSortering.FEILUTBETALT else KøSortering.OPPRETT_BEHANDLING,
        behandlingTyper,
        fagsakYtelseTyper,
        andreKriterierDto?.let {mutableListOf(it)} ?: mutableListOf(),
        Enhet.NASJONAL,
        null,
        null,
        mutableListOf(Saksbehandler(null, "OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
        false,
        mutableListOf(),
        filtreringFeilutbetaling = feilutbetaling,
        merknadKoder = merknadKoder,
        oppgaveKoder = oppgaveKoder,
        nyeKrav = nyeKrav
    )

}
