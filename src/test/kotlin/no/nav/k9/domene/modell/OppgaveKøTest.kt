package no.nav.k9.domene.modell

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import org.junit.Assert
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertTrue(tilhørerOppgaveTilKø)
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertTrue(tilhørerOppgaveTilKø)
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertFalse(tilhørerOppgaveTilKø)
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertTrue(tilhørerOppgaveTilKø)
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertFalse(tilhørerOppgaveTilKø)
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null)
        Assert.assertFalse(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal sortere på størrelse på feil utbetalingsbeløp`() {
        val tilbakeKrevingsKø = OppgaveKø(
            id = UUID.randomUUID(),
            navn = "test",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.FEILUTBETALT,
            saksbehandlere = mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn))
        )
        val oppgaveId1 = UUID.randomUUID()
        val oppgaveId2 = UUID.randomUUID()
        val oppgaveId3 = UUID.randomUUID()
        val oppgaveId4 = UUID.randomUUID()


        val o1 = lagOppgave(oppgaveId1, 10000L)
        val o2 = lagOppgave(oppgaveId2, 1000L)
        val o3 = lagOppgave(oppgaveId3, 100L)
        val o4 = lagOppgave(oppgaveId4, 10L)

        tilbakeKrevingsKø.leggOppgaveTilEllerFjernFraKø(o2)
        tilbakeKrevingsKø.leggOppgaveTilEllerFjernFraKø(o3)
        tilbakeKrevingsKø.leggOppgaveTilEllerFjernFraKø(o4)
        tilbakeKrevingsKø.leggOppgaveTilEllerFjernFraKø(o1)

        //sjekk at køen er sorter etter høyest feilutbetaling
        Assert.assertEquals(tilbakeKrevingsKø.oppgaverOgDatoer[0].id, oppgaveId1)
        Assert.assertEquals(tilbakeKrevingsKø.oppgaverOgDatoer[1].id, oppgaveId2)
        Assert.assertEquals(tilbakeKrevingsKø.oppgaverOgDatoer[2].id, oppgaveId3)
        Assert.assertEquals(tilbakeKrevingsKø.oppgaverOgDatoer[3].id, oppgaveId4)
    }

    private fun lagOppgave(uuid: UUID, beløp: Long): Oppgave {
        return Oppgave(
            eksternId = uuid,
            feilutbetaltBeløp = beløp,
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
                    "5015" to "OPPR"
                )
            ),
            tilBeslutter = false,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }
}
