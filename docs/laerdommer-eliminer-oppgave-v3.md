# Lærdommer fra forsøket på å eliminere oppgave_v3

## Bakgrunn

Branchen `chore/oppgave-v3-inmemory` implementerte store deler av planen i
`plan-eliminer-oppgave-v3.md` (fase 2-4) i én omgang: full replay, migrering av lesere,
fjerning av skriving og sletting av OppgaveV3Repository. Resultatet ble rundt 50 endrede filer
og netto 780 færre linjer, men endringene var for sammenvevde til å kunne merges trygt.

Denne dokumentasjonen oppsummerer hva vi lærte, og foreslår en ny, stegvis tilnærming
der hvert steg kan merges isolert.

---

## Del 1: Arkitekturelle innsikter

### "Replay fra 0" forener normalflyt og historikkvask

Den viktigste arkitekturelle gevinsten fra branchen er at når adapteren alltid replayer
alle events fra starten, blir historikkvask triviell: marker som dirty og kjør normal prosessering.
HistorikkvaskTjeneste ble redusert fra kompleks sletting og rebuild til `settDirty` pluss kall til adapteren.

**Implikasjon:** Full replay er den sentrale endringen som muliggjør alt annet. Den krever
at `OppgaveV3Tjeneste.byggOppgaveIMinnet` fungerer uten avhengighet til lagret mellomtilstand.

### In-memory versjonskjede eliminerer dual-write

Branchen viste at vi kan bygge hele versjonskjeden i minnet uten å persistere noe til `oppgave_v3`.
Sideeffekter (DVH, PEP-cache, reservasjoner) trigges bare for dirty events, mens ikke-dirty events
kun bygger kontekst.

### Lesing bør abstraheres fra konkret projeksjon

Direkte avhengighet til `oppgave_v3_part`-tabeller binder oss til en spesifikk lagringsform.
Et abstrakt `OppgaveReadService`-grensesnitt bør innføres, slik at fremtidige endringer
i datalageret (temporal-støtte, ny projeksjonsform) ikke krever endringer hos alle konsumenter.

### Outbox-pattern for DVH er riktig retning

Overgangen fra pull-basert DVH-query til push-basert outbox er arkitekturelt sunn.
DvhOutboxRepository og DvhOutboxTjeneste fra branchen er enkle og velfungerende.
Idempotens og resend-mekanismer krever likevel mer gjennomtenkning enn forventet
(jf. siste commit, som innfører lease-mekanisme og ON CONFLICT-logikk).

---

## Del 2: Fallgruver fra branchen

### Ortogonale endringer ble koblet sammen

Branchen kombinerte tre uavhengige endringer i samme endringsgruppe:
- Replay-logikk (hvordan events prosesseres)
- DVH-sending (hvordan statistikk produseres)
- Sletting av oppgave_v3 (fjerning av gammel lagring)

Konsekvens: Testfeil kunne skyldes hvilken som helst av de tre, og det var vanskelig
å identifisere årsaken. Lærdom: Hver av disse bør være en selvstendig PR med egne tester.

### Sletting og refaktorering i samme PR fjernet rollback-muligheten

OppgaveV3Repository (568 linjer) ble slettet i samme PR som adapteren ble endret.
Dersom den nye adapteren hadde en subtil feil i prod, var det ingen enkel vei tilbake.
Lærdom: La tabellen og repository-koden leve videre (ubrukt) i en overgangsperiode.
Slett i en separat PR etter at det er verifisert at ingenting trenger den.

### Observerbarhet ble svekket underveis

`tellAntall()` returnerte `Pair(0L, 0L)` etter migreringen, noe som skjuler
reell systemtilstand i logger. Lærdom: Implementer en fungerende erstatning
(f.eks. COUNT mot `oppgave_v3_part`) *før* eller *samtidig* med endringen som
fjerner den opprinnelige tellingen.

### DVH outbox-idempotens var underdesignet

Første versjon av outbox-tabellen manglet unikhetsbegrensning og lease-mekanisme.
Siste commit på branchen la til ON CONFLICT og lease-ID, men dette ble ikke ferdigtestet.
Lærdom: Design outbox-skjema med idempotens fra dag 1 (unik constraint på
`(oppgavetype, ekstern_id, ekstern_versjon)`), og verifiser med en dedikert test
som simulerer dobbeltskriving.

---

## Del 3: Konkrete isolerte steg

Stegene under kan gjennomføres uavhengig av hverandre, med noen anbefalte avhengigheter
markert. Hvert steg skal kunne merges til master og deployes uten å bryte eksisterende
funksjonalitet.

### Steg A: Innfør `OppgaveReadService`-grensesnitt

**Hva:** Opprett et interface som abstraherer oppgavelesing. Første implementasjon delegerer
til eksisterende `OppgaveRepository` / `PartisjonertOppgaveRepository`. Konsumenter migreres
til å bruke grensesnittet.

```kotlin
interface OppgaveReadService {
    /** Hent aktiv/nåværende tilstand for en oppgave */
    fun hentAktivOppgave(eksternId: String, oppgavetype: String): Oppgave

    /** Hent aktiv oppgave hvis den finnes, null ellers */
    fun hentAktivOppgaveHvisFinnes(eksternId: String, oppgavetype: String): Oppgave?

    /** Hent full versjonshistorikk (tidsserie) for en oppgave */
    fun hentTidsserie(fagsystem: Fagsystem, eksternId: String): List<Oppgave>

    /** Hent alle åpne oppgaver for en gitt reservasjonsnøkkel */
    fun hentÅpneOppgaverForReservasjonsnøkkel(reservasjonsnøkkel: String): List<Oppgave>
}
```

Initiell implementasjon (`OppgaveReadServiceImpl`) delegerer `hentAktivOppgave` til
`PartisjonertOppgaveRepository`, og `hentTidsserie` kaster `UnsupportedOperationException`
(implementeres i Steg F med replay). Alternativt kan `hentTidsserie` initiellt lese fra
`oppgave_v3` så lenge den finnes.

**Konsumenter som bør migreres:**
- `OppgaveRepositoryTxWrapper` (erstattes helt av dette grensesnittet)
- `ForvaltningApis` (tidsserie)
- `StatistikkRepository` (visning av enkeltoppgave)

**Hvorfor isolert:** Ren refaktorering uten atferdsendring. Gjør det mulig å bytte
implementasjon (til replay-basert eller temporal) uten å endre konsumenter.

**Risiko:** Lav. Rent strukturelt grep.

---

### Steg B: Innfør `byggOppgaveIMinnet` i OppgaveV3Tjeneste

**Hva:** Legg til en ny metode som bygger en OppgaveV3 i minnet med feltutledning,
uten å persistere. Behold eksisterende `sjekkDuplikatOgProsesser` uendret.

```kotlin
// I OppgaveV3Tjeneste:
fun byggOppgaveIMinnet(
    oppgaveDto: OppgaveDto,
    tx: TransactionalSession,
    forrigeOppgaveversjon: OppgaveV3? = null,
): OppgaveV3 {
    val oppgavetype = oppgavetypeRepository.hentOppgavetype(
        område = oppgaveDto.område,
        eksternId = oppgaveDto.type,
        tx = tx
    )
    var oppgave = OppgaveV3(oppgaveDto, oppgavetype)

    val utledeteFelter = oppgavetype.oppgavefelter
        .mapNotNull { oppgavefelt ->
            oppgavefelt.feltutleder?.utled(oppgave, forrigeOppgaveversjon)
        }

    oppgave = OppgaveV3(oppgave, oppgave.felter + utledeteFelter)
    oppgave.valider()
    return oppgave
}
```

**Hvorfor isolert:** Legger grunnlaget for full replay uten å endre noe i eksisterende flyt.
Metoden kan brukes fra tester umiddelbart, og er en ren tilleggsfunksjon.

**Risiko:** Svært lav.

---

### Steg C: DVH outbox-infrastruktur (uten å fjerne gammel mekanisme)

**Hva:** Opprett `dvh_outbox`-tabell, `DvhOutboxRepository` og `DvhOutboxTjeneste`.
Skriv til outbox i tillegg til eksisterende DVH-mekanisme (dual-write til outbox, gammel
sending fortsetter som før).

```sql
-- Migrasjon: V1.0_0103__dvh_outbox.sql
CREATE TABLE dvh_outbox (
    id BIGSERIAL PRIMARY KEY,
    ekstern_id TEXT NOT NULL,
    ekstern_versjon TEXT NOT NULL,
    oppgavetype TEXT NOT NULL,
    saksnummer TEXT NOT NULL,
    sak_json JSONB NOT NULL,
    behandlinger_json JSONB NOT NULL,
    opprettet TIMESTAMP NOT NULL DEFAULT now(),
    sendt_tidspunkt TIMESTAMP,
    under_behandling_tidspunkt TIMESTAMP,
    under_behandling_id UUID,
    UNIQUE (oppgavetype, ekstern_id, ekstern_versjon)
);
```

```kotlin
class DvhOutboxRepository(private val dataSource: DataSource) {
    /** Skriv melding til outbox innenfor callers transaksjon (atomisk med dirty-clearing) */
    fun leggTil(
        eksternId: String,
        eksternVersjon: String,
        oppgavetype: String,
        saksnummer: String,
        sak: Sak,
        behandlinger: List<Behandling>,
        tx: TransactionalSession,
    )

    /** Hent og reserver usente meldinger (lease-pattern) */
    fun hentOgReserverUsendteEntries(antall: Int = 100, leaseMinutter: Int = 15): List<DvhOutboxEntry>

    /** Kvitter melding som sendt */
    fun kvitterSendt(id: Long)

    /** Slett sendte meldinger eldre enn angitt antall dager */
    fun slettSendteEldreEnn(dager: Int = 30)
}
```

**Fordeler:**
- Outbox fylles med data i prod uten at noe annet endres.
- Kan verifisere at outbox-innholdet er korrekt ved å sammenligne med eksisterende DVH-sending.
- Ingen risiko for tapte meldinger.

**Ulemper:**
- To parallelle DVH-mekanismer å vedlikeholde midlertidig.
- Outbox-tabellen vokser uten at meldinger sendes (eller man innfører sending som alternativ
  sender som kan A/B-testes).

**Alternativ strategi:** Vent med outbox til full replay er på plass, og bytt direkte.
Risikoen da er at DVH-sending er en ny mekanisme som ikke er prodverifisert isolert.

**Anbefaling:** Innfør outbox-skriving tidlig (dual-write), men ikke aktiver outbox-sending
før replay-endringen er ferdig. Bruk sammenligningen som verifikasjon.

**Risiko:** Lav-medium. Ren tilleggsfunksjonalitet, men krever at skjemaet er gjennomtenkt
(idempotens fra dag 1 via UNIQUE constraint).

---

### Steg D: Full replay i EventTilOppgaveAdapter

**Hva:** Endre `oppdaterOppgaveForEksternId` til alltid å replaye fra event 0.
Fjern `hentStartversjon`-optimalisering og `hentEksisterendeVersjon`. Behold skriving til
`oppgave_v3` som før (dobbeltskriving for verifisering), men bygg versjonskjeden i minnet.

Kjernen i den nye løkken:

```kotlin
fun oppdaterOppgaveForEksternId(eventnøkkel: EventNøkkel, tx: TransactionalSession): Long {
    val eventerMedNummerering = hentEventerOgKorriger(eventnøkkel, tx)
    if (eventerMedNummerering.isEmpty()) return 0

    var forrigeOppgaveversjon: OppgaveV3? = null
    var sisteOppgaveversjon: OppgaveV3? = null
    var statistikkteller = 0L

    for ((eventnummer, eventLagret) in eventerMedNummerering) {
        val innsending = eventTilOppgaveMapper.mapOppgave(eventLagret, forrigeOppgaveversjon, eventnummer)
        val oppgave = oppgaveV3Tjeneste.byggOppgaveIMinnet(innsending.dto, tx, forrigeOppgaveversjon)

        if (eventLagret.dirty) {
            // Side-effekter kun for nye/dirty events
            oppgaveOppdatertHandler.håndterOppgaveOppdatert(eventLagret, oppgave, tx)
            dvhOutboxTjeneste.skrivTilOutbox(oppgave, eventnummer, tx)
            statistikkteller++
            sisteOppgaveversjon = oppgave
        }

        forrigeOppgaveversjon = oppgave
    }

    fjernDirtyOgAjourhold(eventerMedNummerering, forrigeOppgaveversjon!!, tx)
    sisteOppgaveversjon?.let { oppgaveOppdatertHandler.oppdaterPepCache(it, tx) }
    return statistikkteller
}
```

**Forutsetter:** Steg B (byggOppgaveIMinnet).

**Nøkkelpunkter fra branchen:**
- `sjekkMeldingIFeilRekkefølgeOgBestillVask` kan fjernes fordi full replay håndterer
  rekkefølge implisitt.
- `mapOgLagre` (som i dag kaller `sjekkDuplikatOgProsesser`) erstattes av
  `eventTilOppgaveMapper.mapOppgave` + `byggOppgaveIMinnet`.
- Row-level locking (`hentAlleEventerMedLås`) sikrer serialisert prosessering per eksternId.

**Strategi for gradvis overgang:**
1. Innfør full replay, men behold skriving til oppgave_v3 (dobbeltskriving). Verifiser at
   in-memory-resultatet matcher det som lagres.
2. Når tilliten er høy: fjern oppgave_v3-skriving (Steg G).

**Risiko:** Medium. Kall til k9SakBeriker under replay er en kjent bekymring (se planen).
Bør profileres i testmiljø. Kan mitigeres med caching av beriker-resultat per replay-sekvens.

---

### Steg E: Forenkle HistorikkvaskTjeneste

**Hva:** Når full replay er standard (steg D), forenkle historikkvask til:
`settDirty` + kall adapteren. Fjern `slettOppgave()`.

**Forutsetter:** Steg D.

**Bonusforbedringer fra branchen som kan tas med:**
- Cursor-basert batching (`sisteSetteEventNokkelId`)
- Parallell prosessering med `limitedParallelism`
- Disse forbedringene kan eventuelt merges uavhengig av full replay (som rene
  ytelsesforbedringer for eksisterende historikkvask).

**Risiko:** Lav (gitt at Steg D fungerer).

---

### Steg F: `replayAlleVersjoner` for forvaltning og tidsserie

**Hva:** Legg til en read-only-metode som replayer alle events og returnerer
versjonskjeden. Denne blir implementasjonen av `OppgaveReadService.hentTidsserie`.

```kotlin
// I EventTilOppgaveAdapter (eller en egen ReplayTjeneste):
fun replayAlleVersjoner(fagsystem: Fagsystem, eksternId: String, tx: TransactionalSession): List<OppgaveV3> {
    val eventer = eventRepository.hentAlleEventerUtenLås(fagsystem, eksternId, tx)
    val eventerMedNummerering = vaskeeventSerieutleder.korrigerEventnummerForVaskeeventer(eventer)
    if (eventerMedNummerering.isEmpty()) return emptyList()

    val versjoner = mutableListOf<OppgaveV3>()
    var forrigeOppgaveversjon: OppgaveV3? = null

    for ((eventnummer, eventLagret) in eventerMedNummerering) {
        val innsending = eventTilOppgaveMapper.mapOppgave(eventLagret, forrigeOppgaveversjon, eventnummer)
        val oppgave = oppgaveV3Tjeneste.byggOppgaveIMinnet(innsending.dto, tx, forrigeOppgaveversjon)
        versjoner.add(oppgave)
        forrigeOppgaveversjon = oppgave
    }

    return versjoner
}
```

Brukes fra ForvaltningApis for tidsserie-visning, og erstatter lesing fra `oppgave_v3`.

**Forutsetter:** Steg B og steg D (eller kan implementeres selvstendig som et forvaltningsverktøy
som gjør replay uavhengig av normal prosesseringsflyt).

**Risiko:** Lav. Ren lesefunksjonalitet, tar ingen låser, gjør ingen persistering.

---

### Steg G: Stopp skriving til oppgave_v3

**Hva:** Fjern kall til `nyOppgaveversjon()`, `lagreFeltverdier()` og `deaktiverVersjon()`
fra `sjekkDuplikatOgProsesser` (eller fjern hele metoden hvis adapteren ikke lenger kaller den).
La tabellen stå med historiske data, men slutt å skrive til den.

Konkret: Kallet fra adapteren som i dag gjør
`oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(nyOppgaveversjon, tx, forrigeOppgaveversjon)`
fjernes. Adapteren bruker i stedet `byggOppgaveIMinnet` (allerede innført i Steg D).

**Forutsetter:** Alle lesere er migrert (Steg A, F), og DVH bruker outbox (Steg C med aktiv sending).

**Risiko:** Medium. Sikkerhetsnettet er at tabellen fortsatt eksisterer med historiske data,
og at man kan reaktivere skriving ved rollback.

---

### Steg H: Slett oppgave_v3, oppgavefelt_verdi og OppgaveV3Repository

**Hva:**
- SQL-migrasjon: `DROP TABLE oppgavefelt_verdi; DROP TABLE oppgave_v3; DROP TABLE OPPGAVE_V3_SENDT_DVH_EKSTERN;`
- Fjern `OppgaveV3Repository` (eller reduser til kun _part-relatert logikk hvis den fortsatt brukes).
- Fjern relaterte tester og truncate-lister i test-setup.

**Forutsetter:** Steg G har kjørt i prod uten problemer over en periode (anbefalt 2-4 uker).

**Risiko:** Irreversibel uten backup. Bør kjøres etter en observasjonsperiode der ingen
leser fra eller skriver til disse tabellene.

---

## Del 4: Anbefalt rekkefølge

```
Steg A (OppgaveReadService)         -- kan starte umiddelbart
Steg B (byggOppgaveIMinnet)         -- kan starte umiddelbart
Steg C (DVH outbox dual-write)      -- kan starte umiddelbart
         |
         v
Steg D (Full replay i adapter)      -- forutsetter B
         |
         v
Steg E (Forenkle historikkvask)     -- forutsetter D
Steg F (replayAlleVersjoner)        -- forutsetter B+D
         |
         v
Steg G (Stopp skriving)             -- forutsetter A, C (aktiv sending), D, F
         |
         v
Steg H (Slett tabeller)             -- forutsetter G + observasjonsperiode
```

Steg A, B, og C har ingen innbyrdes avhengigheter og kan gjøres parallelt.

---

## Del 5: Risikomatrise

| Steg | Størrelse | Risiko | Rollback-strategi |
|------|-----------|--------|-------------------|
| A | Liten-medium | Lav | Revert PR |
| B | Liten | Svært lav | Revert PR |
| C | Medium | Lav-medium | Slett outbox-tabell, revert kode |
| D | Medium-stor | Medium | Feature toggle / revert til gammel adapter-logikk |
| E | Liten | Lav | Revert PR |
| F | Liten | Lav | Revert PR |
| G | Medium | Medium | Reaktiver skriving (tabell finnes fortsatt) |
| H | Liten (kode), stor (data) | Høy (irreversibel) | Gjenopprett fra backup |

---

## Del 6: Ubesvarte spørsmål

1. **k9SakBeriker under full replay:** Hvor ofte trigges `ryddOppObsoleteOgResultatfeilFra2020`
   i praksis? Bør profileres før steg D merges. Mulig mitigering: flagg på event som
   indikerer om det er "siste i replay-kjeden", og bare kall beriker for det siste.

2. **Outbox-idempotens:** Siste commit på branchen innfører ON CONFLICT + lease-mekanisme.
   Designet bør modnes utenfor denne branchen, kanskje som en spike/PoC med lasttest.

3. **Ytelse for oppgaver med mange events:** Typisk 5-50, men finnes det outliers?
   Historikkvask gjør allerede full replay for tusenvis av oppgaver, så dette er trolig ok.
   Bør likevel verifiseres med en prodlik datamengde.
