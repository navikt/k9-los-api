package no.nav.k9.los.integrasjon.gosys


class GosysKonstanter private constructor() {
    enum class TemaGruppe(val dto: String) {
        FAMILIE("FMLI");
    }

    enum class OppgaveType(val dto: String) {
        JOURNALFØRING("JFR"),
        BEHANDLE("BEH_SAK");
    }

    enum class Prioritet(val dto: String) {
        NORMAL("NORM");
    }

    enum class Fagsaksystem(val dto: String) {
        INFOTRYGD("IT00"),
        K9SAK("IT01");
    }

    enum class OppgaveStatus(val dto: String) {
        OPPRETTET("OPPRETTET"),
        AAPNET("AAPNET"),
        UNDER_BEHANDLING("UNDER_BEHANDLING"),
        FERDIGSTILT("FERDIGSTILT"),
        FEILREGISTRERT("FEILREGISTRERT");
    }

    enum class JournalpostSystem(val dto: String) {
        JOARK("AS36");
    }

    enum class BehandlingsTema(val dto: String) {
        PLEIEPENGER_SYKT_BARN_NY_ORDNING("ab0320"), OMSORGSPENGER("ab0149");
    }

    enum class BehandlingsType(val dto: String) {
        DIGITAL_SØKNAD("ae0227");
    }

    enum class Tema(val dto: String) {
        KAPITTEL_9_YTELSER("OMS");
    }
}
