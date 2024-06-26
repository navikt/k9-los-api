package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9saktillos.k9saktillosadapter

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.modell.K9SakModell
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.utils.LosObjectMapper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class K9SakTilLosAdapterTjenesteTest : AbstractK9LosIntegrationTest() {

    @Disabled
    @Test
    fun `test avspilling av behandlings prosess events k9`() {
        val behandlingProsessEventK9Repository = get<BehandlingProsessEventK9Repository>()

        val behandlingProsessEventUUID = UUID.randomUUID()
        behandlingProsessEventK9Repository.lagre(behandlingProsessEventUUID) {
            return@lagre opprettK9SakModell()
        }

        //k9SakTilLosAdapterTjeneste.spillAvBehandlingProsessEventer()

        assert(størrelseErLik(6))
    }

    @Test
    fun `rydd unike eventer`() {
        val testJson = testJson()
        val objectMapper = LosObjectMapper.prettyInstance
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        val test = k9SakTilLosAdapterTjeneste.sjekkOgLagUnike(LosObjectMapper.instance.readValue<List<BehandlingProsessEventDto>>(testJson))
        assertThat(test.size).isEqualTo(3) //og at det ikke ble kastet exception
    }

    private fun testJson(): String {
        return  """
                [ {
                  "eksternId" : "7d889b8e-1449-4d11-a6e7-4ee9208248ba",
                  "fagsystem" : {
                    "kode" : "K9SAK",
                    "kodeverk" : "FAGSYSTEM",
                    "navn" : "K9-sak"
                  },
                  "saksnummer" : "6G2JW",
                  "vedtaksdato" : null,
                  "aktørId" : "test",
                  "behandlingId" : 1018510,
                  "behandlingstidFrist" : "2020-07-07",
                  "eventTid" : "2020-05-26T21:28:51.307734",
                  "eventHendelse" : "BEHANDLINGSKONTROLL_EVENT",
                  "behandlingStatus" : "UTRED",
                  "behandlingSteg" : "INREG",
                  "behandlendeEnhet" : null,
                  "resultatType" : null,
                  "ytelseTypeKode" : "FRISINN",
                  "behandlingTypeKode" : "BT-002",
                  "opprettetBehandling" : "2020-05-26T21:28:48",
                  "eldsteDatoMedEndringFraSøker" : null,
                  "href" : null,
                  "førsteFeilutbetaling" : null,
                  "feilutbetaltBeløp" : null,
                  "ansvarligSaksbehandlerIdent" : null,
                  "ansvarligSaksbehandlerForTotrinn" : null,
                  "ansvarligBeslutterForTotrinn" : null,
                  "fagsakPeriode" : null,
                  "aksjonspunktTilstander" : [ ],
                  "nyeKrav" : null,
                  "fraEndringsdialog" : null,
                  "søknadsårsaker" : [ ],
                  "behandlingsårsaker" : [ ],
                  "aksjonspunktKoderMedStatusListe" : {}
                }, {
                  "eksternId" : "7d889b8e-1449-4d11-a6e7-4ee9208248ba",
                  "fagsystem" : {
                    "kode" : "K9SAK",
                    "kodeverk" : "FAGSYSTEM",
                    "navn" : "K9-sak"
                  },
                  "saksnummer" : "6G2JW",
                  "vedtaksdato" : null,
                  "aktørId" : "test",
                  "behandlingId" : 1018510,
                  "behandlingstidFrist" : "2020-07-07",
                  "eventTid" : "2020-05-26T21:28:51.307734",
                  "eventHendelse" : "BEHANDLINGSKONTROLL_EVENT",
                  "behandlingStatus" : "UTRED",
                  "behandlingSteg" : "INREG",
                  "behandlendeEnhet" : null,
                  "resultatType" : null,
                  "ytelseTypeKode" : "FRISINN",
                  "behandlingTypeKode" : "BT-002",
                  "opprettetBehandling" : "2020-05-26T21:28:48",
                  "eldsteDatoMedEndringFraSøker" : null,
                  "href" : null,
                  "førsteFeilutbetaling" : null,
                  "feilutbetaltBeløp" : null,
                  "ansvarligSaksbehandlerIdent" : null,
                  "ansvarligSaksbehandlerForTotrinn" : null,
                  "ansvarligBeslutterForTotrinn" : null,
                  "fagsakPeriode" : null,
                  "aksjonspunktTilstander" : [ ],
                  "nyeKrav" : null,
                  "fraEndringsdialog" : null,
                  "søknadsårsaker" : [ ],
                  "behandlingsårsaker" : [ ],
                  "aksjonspunktKoderMedStatusListe" : {}
                }, {
                  "eksternId" : "7d889b8e-1449-4d11-a6e7-4ee9208248ba",
                  "fagsystem" : {
                    "kode" : "K9SAK",
                    "kodeverk" : "FAGSYSTEM",
                    "navn" : "K9-sak"
                  },
                  "saksnummer" : "6G2JW",
                  "vedtaksdato" : null,
                  "aktørId" : "test",
                  "behandlingId" : 1018510,
                  "behandlingstidFrist" : "2020-07-07",
                  "eventTid" : "2020-05-26T21:29:07.137202",
                  "eventHendelse" : "BEHANDLINGSKONTROLL_EVENT",
                  "behandlingStatus" : "IVED",
                  "behandlingSteg" : "IVEDSTEG",
                  "behandlendeEnhet" : null,
                  "resultatType" : null,
                  "ytelseTypeKode" : "FRISINN",
                  "behandlingTypeKode" : "BT-002",
                  "opprettetBehandling" : "2020-05-26T21:28:48",
                  "eldsteDatoMedEndringFraSøker" : null,
                  "href" : null,
                  "førsteFeilutbetaling" : null,
                  "feilutbetaltBeløp" : null,
                  "ansvarligSaksbehandlerIdent" : null,
                  "ansvarligSaksbehandlerForTotrinn" : null,
                  "ansvarligBeslutterForTotrinn" : null,
                  "fagsakPeriode" : null,
                  "aksjonspunktTilstander" : [ ],
                  "nyeKrav" : null,
                  "fraEndringsdialog" : null,
                  "søknadsårsaker" : [ ],
                  "behandlingsårsaker" : [ ],
                  "aksjonspunktKoderMedStatusListe" : {}
                }, {
                  "eksternId" : "7d889b8e-1449-4d11-a6e7-4ee9208248ba",
                  "fagsystem" : {
                    "kode" : "K9SAK",
                    "kodeverk" : "FAGSYSTEM",
                    "navn" : "K9-sak"
                  },
                  "saksnummer" : "6G2JW",
                  "vedtaksdato" : null,
                  "aktørId" : "test",
                  "behandlingId" : 1018510,
                  "behandlingstidFrist" : "2020-07-07",
                  "eventTid" : "2020-05-26T21:29:07.137202",
                  "eventHendelse" : "BEHANDLINGSKONTROLL_EVENT",
                  "behandlingStatus" : "IVED",
                  "behandlingSteg" : "IVEDSTEG",
                  "behandlendeEnhet" : null,
                  "resultatType" : null,
                  "ytelseTypeKode" : "FRISINN",
                  "behandlingTypeKode" : "BT-002",
                  "opprettetBehandling" : "2020-05-26T21:28:48",
                  "eldsteDatoMedEndringFraSøker" : null,
                  "href" : null,
                  "førsteFeilutbetaling" : null,
                  "feilutbetaltBeløp" : null,
                  "ansvarligSaksbehandlerIdent" : null,
                  "ansvarligSaksbehandlerForTotrinn" : null,
                  "ansvarligBeslutterForTotrinn" : null,
                  "fagsakPeriode" : null,
                  "aksjonspunktTilstander" : [ ],
                  "nyeKrav" : null,
                  "fraEndringsdialog" : null,
                  "søknadsårsaker" : [ ],
                  "behandlingsårsaker" : [ ],
                  "aksjonspunktKoderMedStatusListe" : {}
                }, {
                  "eksternId" : "7d889b8e-1449-4d11-a6e7-4ee9208248ba",
                  "fagsystem" : {
                    "kode" : "K9SAK",
                    "kodeverk" : "FAGSYSTEM",
                    "navn" : "K9-sak"
                  },
                  "saksnummer" : "6G2JW",
                  "vedtaksdato" : null,
                  "aktørId" : "test",
                  "behandlingId" : 1018510,
                  "behandlingstidFrist" : "2020-07-07",
                  "eventTid" : "2020-05-26T21:29:07.750160",
                  "eventHendelse" : "AKSJONSPUNKT_AVBRUTT",
                  "behandlingStatus" : "AVSLU",
                  "behandlingSteg" : null,
                  "behandlendeEnhet" : null,
                  "resultatType" : null,
                  "ytelseTypeKode" : "FRISINN",
                  "behandlingTypeKode" : "BT-002",
                  "opprettetBehandling" : "2020-05-26T21:28:48",
                  "eldsteDatoMedEndringFraSøker" : null,
                  "href" : null,
                  "førsteFeilutbetaling" : null,
                  "feilutbetaltBeløp" : null,
                  "ansvarligSaksbehandlerIdent" : null,
                  "ansvarligSaksbehandlerForTotrinn" : null,
                  "ansvarligBeslutterForTotrinn" : null,
                  "fagsakPeriode" : null,
                  "aksjonspunktTilstander" : [ ],
                  "nyeKrav" : null,
                  "fraEndringsdialog" : null,
                  "søknadsårsaker" : [ ],
                  "behandlingsårsaker" : [ ],
                  "aksjonspunktKoderMedStatusListe" : {}
                }, {
                  "eksternId" : "7d889b8e-1449-4d11-a6e7-4ee9208248ba",
                  "fagsystem" : {
                    "kode" : "K9SAK",
                    "kodeverk" : "FAGSYSTEM",
                    "navn" : "K9-sak"
                  },
                  "saksnummer" : "6G2JW",
                  "vedtaksdato" : null,
                  "aktørId" : "test",
                  "behandlingId" : 1018510,
                  "behandlingstidFrist" : "2020-07-07",
                  "eventTid" : "2020-05-26T21:29:07.750160",
                  "eventHendelse" : "AKSJONSPUNKT_AVBRUTT",
                  "behandlingStatus" : "AVSLU",
                  "behandlingSteg" : null,
                  "behandlendeEnhet" : null,
                  "resultatType" : null,
                  "ytelseTypeKode" : "FRISINN",
                  "behandlingTypeKode" : "BT-002",
                  "opprettetBehandling" : "2020-05-26T21:28:48",
                  "eldsteDatoMedEndringFraSøker" : null,
                  "href" : null,
                  "førsteFeilutbetaling" : null,
                  "feilutbetaltBeløp" : null,
                  "ansvarligSaksbehandlerIdent" : null,
                  "ansvarligSaksbehandlerForTotrinn" : null,
                  "ansvarligBeslutterForTotrinn" : null,
                  "fagsakPeriode" : null,
                  "aksjonspunktTilstander" : [ ],
                  "nyeKrav" : null,
                  "fraEndringsdialog" : null,
                  "søknadsårsaker" : [ ],
                  "behandlingsårsaker" : [ ],
                  "aksjonspunktKoderMedStatusListe" : {}
                } ]
            """.trimIndent()
    }

    private fun opprettK9SakModell(): K9SakModell {
        return jacksonObjectMapper().registerModule(JavaTimeModule()).readValue(
            javaClass.getResource("/prosessEventEksempel.json")!!,
            K9SakModell::class.java
        )
    }

    private fun størrelseErLik(forventetStørrelse: Long) : Boolean {
        val resultatStørrelse = using(sessionOf(dataSource)) {
            it.run(
                queryOf(
                    """select count(*) from oppgave_v3"""
                ).map { row -> row.long(1) }.asSingle
            )
        }
        return resultatStørrelse == forventetStørrelse
    }

}