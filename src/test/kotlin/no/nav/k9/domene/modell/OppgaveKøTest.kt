package no.nav.k9.domene.modell

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import no.nav.k9.tjenester.saksbehandler.merknad.Merknad
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())
        assertTrue(tilhørerOppgaveTilKø)
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())
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

        val tilhørerOppgaveTilKø = oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())
        assertFalse(tilhørerOppgaveTilKø)
    }

    @Test
    fun `skal ta med feilutbetaling intervall`() {
        assertTrue(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(70), null, emptyList()))
        assertFalse(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(200), null, emptyList()))
        assertTrue(lagOppgaveKø(Intervall(50, null)).tilhørerOppgaveTilKø(feilutb_oppg(200), null, emptyList()))
        assertFalse(lagOppgaveKø(Intervall(50, null)).tilhørerOppgaveTilKø(feilutb_oppg(49), null, emptyList()))
        assertTrue(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(50), null, emptyList()))
        assertTrue(lagOppgaveKø(Intervall(50, 100)).tilhørerOppgaveTilKø(feilutb_oppg(100), null, emptyList()))
    }

    @Test
    fun `feilutbetaling filter i kombinasjon med andre kriterie`() {
        assertTrue(
            lagOppgaveKø(Intervall(50, 100), kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(feilutb_oppg(70, true), null, emptyList())
        )

        assertFalse(
            lagOppgaveKø(Intervall(50, 100), kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(feilutb_oppg(200, true), null, emptyList())
        )

        assertFalse(
            lagOppgaveKø(Intervall(50, 100), kriterie(AndreKriterierType.AVKLAR_MEDLEMSKAP))
                .tilhørerOppgaveTilKø(feilutb_oppg(70, false), null, emptyList())
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
            mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
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
        assertTrue(oppgaveKø.tilhørerOppgaveTilKø(oppgave, null, emptyList()))
    }

    private val HASTESAK = "HASTESAK"
    private val VANSKELIG = "VANSKELIG"

    @Test
    fun `Skal inkludere markerte oppgaver hvis det finnes merknad i køen`() {
        val oppgave = merknadOppgave(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)

        val hastekø = lagOppgaveKø(merknadKoder = listOf(HASTESAK))
        assertTrue(hastekø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(HASTESAK)))
        assertTrue(hastekø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(HASTESAK, VANSKELIG)))
        assertFalse(hastekø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(VANSKELIG)))
        assertFalse(hastekø.tilhørerOppgaveTilKø(oppgave, null, emptyList()))

        val vanskeligeHasteKø = lagOppgaveKø(merknadKoder = listOf(HASTESAK, VANSKELIG))
        assertTrue(vanskeligeHasteKø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(VANSKELIG, HASTESAK)))
        assertFalse(vanskeligeHasteKø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(VANSKELIG)))
        assertFalse(vanskeligeHasteKø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(HASTESAK)))
        assertFalse(vanskeligeHasteKø.tilhørerOppgaveTilKø(oppgave, null, emptyList()))

    }

    @Test
    fun `markerte oppgaver i kombinasjon med andre kriterier`() {
        val psbOppgave = merknadOppgave(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        val psbHastekø = lagOppgaveKø(merknadKoder = listOf(HASTESAK), fagsakYtelseTyper = mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN))
        val pilsHastekø = lagOppgaveKø(merknadKoder = listOf(HASTESAK), fagsakYtelseTyper = mutableListOf(FagsakYtelseType.PPN))

        assertThat(psbHastekø.tilhørerOppgaveTilKø(psbOppgave, null, merknadMed(HASTESAK))).isTrue()
        assertThat(pilsHastekø.tilhørerOppgaveTilKø(psbOppgave, null, merknadMed(HASTESAK))).isFalse()

    }

    @Test
    fun `vanlige køer skal ikke ha med markerte oppgaver`() {
        val oppgave = merknadOppgave(FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        val vanligPsbKø = lagOppgaveKø(fagsakYtelseTyper = mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN))

        assertThat(vanligPsbKø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(VANSKELIG, HASTESAK))).isFalse()
        assertThat(vanligPsbKø.tilhørerOppgaveTilKø(oppgave, null, merknadMed(HASTESAK))).isFalse()
        assertThat(vanligPsbKø.tilhørerOppgaveTilKø(oppgave, null, emptyList())).isTrue()

    }

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

    private fun lagOppgaveKø(
        feilutbetaling: Intervall<Long>? = null,
        andreKriterierDto: AndreKriterierDto? = null,
        merknadKoder: List<String> = emptyList(),
        behandlingTyper: MutableList<BehandlingType> = mutableListOf(),
        fagsakYtelseTyper: MutableList<FagsakYtelseType> = mutableListOf(),
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
        mutableListOf(Saksbehandler("OJR", "OJR", "OJR", enhet = Enhet.NASJONAL.navn)),
        false,
        mutableListOf(),
        filtreringFeilutbetaling = feilutbetaling,
        merknadKoder = merknadKoder
    )

    private fun merknadMed(vararg merknadKoder: String) = listOf(
        Merknad(
            id = 123456L,
            merknadKoder = merknadKoder.toList(),
            oppgaveKoder = emptyList(),
            oppgaveIder = emptyList(),
            saksbehandler = "",
            opprettet = LocalDateTime.now()
        )
    )

}
