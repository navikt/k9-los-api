# Plan: eksplisitt Kallkontekst erstatter implisitt kontekstpropagering

Status: planlagt, ikke påbegynt.
Skrevet mot branch-tilstand ved commit `b1c23139`.

## Hvorfor

Los har fem konkurrerende måter å sende påkrevde parametre nedover i kall-stacken. To av dem kom inn på denne branchen. Resultatet er at koden ikke viser hvilke avhengigheter den har, og at det ikke går an å se om en kodesti kjører som en innlogget nav-ansatt eller som system (skedulert jobb eller Kafka).

| # | Mekanisme | Sted | Omfang |
|---|---|---|---|
| 1 | `CoroutineRequestContext` (idToken + område) | `infrastruktur/rest/RequestContextService.kt:22-54` | 175 `withRequestContext`, 54 `idToken()`, 12 `område()` |
| 2 | `call.attributes` + route-scoped plugin | `OmrådeRoute.kt:10-59` | 59 `call.område` |
| 3 | Eksplisitt `Områder`-parameter | repository/tjeneste | 100+ |
| 4 | Manuell `coroutineContext`-videreføring | `ko/OppgaveKoTjeneste.kt:214-246`, `infrastruktur/db/TransactionalManager.kt:22-33` | 4 |
| 5 | Systemtoken + hardkodet `Områder.K9` | `infrastruktur/abac/cache/PepCacheService.kt:52`, `*SystemKlient*` | ~20 |

Mekanisme 1 og 2 er koblet: `RequestContextService.kt:53` leser `call.områdeOrNull` og kopierer verdien inn i coroutine-konteksten. Område reiser dermed gjennom tre hopp — path-parameter, `call.attributes`, `coroutineContext` — uten å vises i en eneste signatur.

### Feil som allerede finnes i koden

Ingen av disse fanges av kompilatoren eller av testene.

1. **`innloggetbruker/InnloggetBrukerApi.kt:29`** bruker `call.coroutineContext.idToken()`. `RoutingCall.coroutineContext` er engine-konteksten, ikke `withContext`-konteksten. Kaster `IllegalStateException("Request Context ikke satt.")` i alle profiler unntatt LOCAL. Kom inn i commit `d898f0b7`. De 53 andre kallstedene bruker riktig `kotlin.coroutines.coroutineContext`.

2. **`K9Los.kt:301-302`** registrerer `driftsmeldinger` og `brukersområder` utenfor `områdeApi { }`. Begge treffer områdeavhengig tilgangskontroll og kaster:
   - `innloggetbruker/BrukersområderApi.kt:19` → `saksbehandleradmin/SaksbehandlerRepository.kt:137` → `pepClient.harTilgangTilKode6()`
   - `driftsmelding/DriftsmeldingerApis.kt:19` (GET) → `harBasisTilgang()`

3. **`SaksbehandlerRepository.kt:77,119,137,345`** velger `saksbehandler`-rad med den områdeavhengige `harTilgangTilKode6()`. Samme brukerkonto resolverer til forskjellig rad avhengig av hvilken URL kallet kom inn på. En kode6-konto med kode6 kun i K9 finner ikke sin egen rad når den treffer en UNG-rute, og møter deretter `!!` i `reservasjon/ReservasjonApisNy.kt:112` og `ko/OppgaveKoSaksbehandlerpanelApisNy.kt:150-152`. Maskert til UNG går i produksjon.

4. **~50 forekomster** av `val område = call.område // TODO: bruk område når tjenesten er oppdatert`, kombinert med `oppgaveuthenting/query/QueryRequest.kt:10` `val område: Områder = Områder.K9`. Et UNG-kall kjører K9-spørringer uten feilmelding.

5. **`SaksbehandlerRepository.hentAlleSaksbehandlere` (`:344-358`)** filtrerer bare på `skjermet` og har ingen områdefiltrering. En avdelingsleder i K9 ser UNG-saksbehandlere.

## Prinsipper

| Data | Natur | Behandling |
|---|---|---|
| `område` | domenedata, partisjonsnøkkel for SQL | eksplisitt parameter til og med repository |
| `idToken` | credential | konverteres til beslutninger i tjenestelaget, når aldri repository |
| bruker vs. system | kontrollflyt | uttrykkes i typesystemet |
| `skjermet` | egenskap ved brukerkontoen | global, aldri områdeavhengig |
| saksbehandleres områdetilhørighet | domenedata | `saksbehandler_omrade`, aldri utledet fra `skjermet` |

Token er en credential, ikke domenedata. Bare fire konsumenter trenger den: `SifAbacPdpKlientK9`, `PdlService`, `AzureGraphService` og `PepClient` — alle i klientlaget. Løsningen er å korte ned avstanden token må reise, ikke å tråkle JWT-en gjennom alle lag.

### Kode6-semantikk

Kode6-brukere har egne brukerkontoer. En konto kan ha kode6-tilgang i K9, i UNG eller i begge, men kan aldri være kode6 i ett område og vanlig i et annet. `skjermet` er derfor en egenskap ved kontoen.

Dagens `harTilgangTilKode6()` besvarer to forskjellige spørsmål med samme metode. De skal skilles:

```kotlin
/** Er dette en kode6-konto? Global egenskap ved brukerkontoen. Styrer hvilken saksbehandler-rad som gjelder. */
suspend fun erKode6Bruker(): Boolean          // union over Områder.entries

/** Har brukeren tilgang til kode6-saker i dette området? */
suspend fun harTilgangTilKode6(): Boolean     // per område
```

### Områdesegregering

Områder skal aldri blandes. En avdelingsleder som ser på avdelingslederpanelet i K9 skal bare se K9-saksbehandlere, også hvis vedkommende selv har tilgang til UNG.

## Åpent punkt: context parameters

Context parameters er Stable i Kotlin 2.4.0 (unntatt context arguments og callable references). Prosjektet kjører 2.4.10, så basisfunksjonaliteten krever ikke kompilatorflagg.

De passer for `IPepClient` og OBO-klientene: ~200 kallsteder som alle sender samme verdi som allerede er i scope, og `context(kontekst: Brukerkontekst)` er kompilatorsjekket. Det fjerner feilmodusen der konteksten mangler i produksjon.

De passer ikke for `område` i tjeneste- og repository-laget. Der er det en query-parameter som havner i `where`-klausulen, og den skal være synlig i signaturen.

**Anbefaling: eksplisitte parametre først, context parameters som vurdert oppfølging.** Poenget med commit 3-8 er å finne stedene hvor område er stille feil. Med eksplisitte parametre må hvert kallsted gjennomgås bevisst. Med context parameters kompilerer de fleste kallstedene videre uendret, og den tvungne gjennomgangen forsvinner. Konvertering av `IPepClient` til context parameters etterpå er en isolert signaturendring uten arkitekturarbeid.

**Spike i commit 2 (30 min):** verifiser om `medBrukerkontekst` kan deklarere lambdaen sin med context parameter:

```kotlin
suspend fun <T> RoutingContext.medBrukerkontekst(
    block: context(Brukerkontekst) suspend () -> T
): T
```

Fungerer det, blir kallstedene rene. Fungerer det ikke, må hvert handler-legeme pakkes i `context(kontekst) { … }`, og gevinsten spises opp. Rapporter resultatet før commit 3.

## Gjennomføring

Én branch, ordnede commits. Rekkefølgen er valgt for bisectability — commit 6-8 er de eneste som endrer observerbar atferd.

### Commit 1 — Stopp blødningen

- `innloggetbruker/InnloggetBrukerApi.kt:29` — `call.coroutineContext.idToken()` → `kotlin.coroutines.coroutineContext.idToken()`
- Skill kode6-spørsmålene i `IPepClient`, `PepClient` og `PepClientLocal`. Legg til `erKode6Bruker()` som unionerer over `Områder.entries`. Bytt til den i `SaksbehandlerRepository:77,119,137,345`
- Legg til `harBasisTilgangIEttEllerFlereOmråder()` og bruk den i GET `/driftsmeldinger` (`DriftsmeldingerApis.kt:19`). Driftsmeldinger er globale, se kommentaren i `infrastruktur/abac/Gruppeoppsett.kt:20-21`. POST-endepunktene bruker allerede globale `kanLeggeUtDriftsmelding()`
- Ny `RuteSmokeTest`: traverser Ktors routing-tre, kall hver registrerte rute med gyldig token, assert at ingen svarer 500 med `IllegalStateException`

Testen skal feile før de tre endringene over og passere etter.

### Commit 2 — Kontekst-typene

Bare tillegg, ingen kallsteder endres.

`infrastruktur/kontekst/Kallkontekst.kt`:

```kotlin
data class InnloggetBruker(
    val navIdent: String,
    val grupper: Set<UUID>,   // fra JWT
    val idToken: IdToken,     // kun for OBO-veksling i klientlaget
)

sealed interface Kallkontekst { val område: Områder }

data class Brukerkontekst(override val område: Områder, val bruker: InnloggetBruker) : Kallkontekst
data class Systemkontekst(override val område: Områder, val kilde: String) : Kallkontekst
```

`infrastruktur/kontekst/KtorKallkontekst.kt` med `medBrukerkontekst { }` og `medInnloggetBruker { }`. Begge wrapper i `withContext(Span.current().asContextElement())`. `call.område` leses bare i `medBrukerkontekst`.

`Systemkontekst` konstrueres eksplisitt i `konfigurerJobber` (`K9Los.kt:335+`) med jobbnavnet som `kilde`, og i Kafka-konsumentene via `Områder.fraFagsystem`.

`InnloggetBruker` er skilt ut som eget verdiobjekt fordi de globale endepunktene trenger bruker uten område. `Kallkontekst.område` forblir non-null.

Kjør context-parameter-spiken her.

### Commit 3-5 — IPepClient blir eksplisitt

Kompilatordrevet. Endre interfacet først, arbeid deretter utenfra og inn: api-laget, så tjeneste- og klientlaget.

| Metode | Kontekst |
|---|---|
| `erKode6Bruker`, `kanLeggeUtDriftsmelding`, `harBasisTilgangIEttEllerFlereOmråder` | `InnloggetBruker` |
| `harBasisTilgang`, `erOppgaveStyrer`, `harTilgangTilReserveringAvOppgaver`, `harTilgangTilKode6()`, `harTilgangTilKode6(ident)`, `harTilgangTilOppgaveV3(oppgave, action)` | `Brukerkontekst` |
| `diskresjonskoderForSak/Person`, `erSakKode6/7`, `erAktørKode6/7` | `Kallkontekst` |
| `harTilgangTilOppgaveV3(oppgave, saksbehandler, action)` | `Systemkontekst` + `Saksbehandler`, blir `suspend` |

Følger av dette:

- Slett `CoroutineContext.område()` og `område`-feltet i `CoroutineRequestContext`. Koblingen `RequestContextService.kt:53` → `call.attributes` brytes
- Duplikat-overloadene `PepClient.kt:78-94` kollapser til én
- Nøstet `runBlocking`-kjede `OppgaveKoTjeneste.kt:246` → `ReservasjonV3Tjeneste.kt:329` → `PepClient.kt:134` forsvinner, sammen med risikoen for tråd-sult
- `taReservasjonFraKø(…, coroutineContext: CoroutineContext)` (`:214-217`) tar `Brukerkontekst`. Kallstedene i `OppgaveKoSaksbehandlerpanelApis.kt:93-97`, `…ApisNy.kt:153-157` og `OppgaveKoApis.kt:226` slutter å sende `kotlin.coroutines.coroutineContext`
- De ~50 `// TODO: bruk område`-linjene fjernes ved å sende `kontekst` videre
- `PepCacheService.kt:52` får `Systemkontekst`
- Rett KDoc-en i `IPepClient.kt`, som i dag påstår at alle operasjoner allerede tar område

### Commit 6-8 — Lagbrudd og områdefiltrering

Eneste commits som endrer observerbar atferd. Hold dem identifiserbare for enkel revert.

**Commit 6.** `SaksbehandlerRepository` tar `skjermet: Boolean` og `område: Områder` som parametre. Tilgangsbeslutningen flyttes opp til `SaksbehandlerAdminTjeneste` og api-laget. Importen av `IPepClient` fjernes fra repository-laget.

**Commit 7.** `hentAlleSaksbehandlere(område, skjermet)` med `exists`-filter på ytre nivå:

```sql
$SAKSBEHANDLER_SELECT
where s.skjermet = :skjermet
  and exists (select 1 from saksbehandler_omrade so2
              join omrade o2 on o2.id = so2.omrade_id
              where so2.saksbehandler_id = s.id and o2.ekstern_id = :område)
```

Bruk `exists`, ikke filtrering i joinen. Filtrerer du i joinen, trunkeres `omrade_ekstern_ider`-aggregatet i `SAKSBEHANDLER_SELECT` (`:432-446`) og `Saksbehandler.områder` blir feil.

Treffer `ko/OppgaveKoAvdelingslederpanelApisNy.kt:131`, `ko/OppgaveKoApis.kt:66`, `reservasjon/ReservasjonApis.kt:167`, `reservasjon/ReservasjonApisNy.kt:261` og `saksbehandleradmin/SaksbehandlerAdminTjeneste.kt:106`. `sokSaksbehandler` (`:360`) arver filteret — den brukes til å flytte reservasjoner og skal ikke kunne tilby en UNG-saksbehandler for en K9-reservasjon.

`Saksbehandler.områder` i responsen viser fortsatt alle områder personen tilhører. Det er allerede eksponert for egen bruker via `brukersområder` og `InnloggetBrukerDto`.

**Commit 8.** Fjern `= Områder.K9` fra `oppgaveuthenting/query/QueryRequest.kt:10`. Feil hardt. Kompilatoren finner kallstedene, og hvert enkelt vurderes manuelt — ikke sett inn `Områder.K9` refleksivt.

Hardkodinger som skal hente område fra `Systemkontekst`:

- `domeneadaptere/k9/eventtiloppgave/OppgaveOppdatertHandler.kt:59` — `EksternOppgaveId("K9", …)` → `Områder.fraFagsystem(eventLagret.fagsystem)`
- `nøkkeltall/avdelingsleder/dagenstall/DagensTallService.kt:31-84`
- `nøkkeltall/.../NyeOgFerdigstilteService.kt:81-170`
- `domeneadaptere/k9/statistikk/OppgavestatistikkTjeneste.kt:36`
- `ko/OppgaveKoAvdelingslederpanelApisNy.kt:159-165` og `ko/OppgaveKoApis.kt:82-88`

### Commit 9-10 — Fjern token fra coroutine-konteksten

Disse tar `InnloggetBruker` eksplisitt:

- `infrastruktur/abac/SifAbacPdpKlientK9.kt:111,192`
- `infrastruktur/pdl/PdlService.kt:93,208`
- `infrastruktur/azuregraph/AzureGraphService.kt:36,54,133-134`
- `infrastruktur/abac/PepClient.kt:48,62`

Deretter:

- Slett `infrastruktur/rest/RequestContextService.kt` og Koin-registreringen i `KoinProfiles.kt:117`
- Forenkle `infrastruktur/db/TransactionalManager.kt:22-33`. `runBlocking(context)`-trikset fantes bare for å bevare `CoroutineRequestContext` inn i transaksjonen
- De 11 legacy-filene får mekanisk erstatning `withRequestContext(call)` → `medBrukerkontekst`. Hele legacy-treet kjører fast `Områder.K9` (`K9Los.kt:243,256`), så ingen atferdsendring. Ingen andre endringer i legacy
- `K9Los.kt:586` evaluerer `Span.current().asContextElement()` én gang ved oppstart og pinner den for alle jobbkjøringer i applikasjonens levetid. Flytt til per kjøring

### Commit 11 — Arkitekturtest

JUnit 5 med `kotest-assertions-core-jvm`, som allerede er på plass. JUnit dominerer i prosjektet (75 testfiler mot 11 med Kotest). Bruk `@TestFactory` som genererer én test per regel, les kildetreet med `java.nio.file`, og feil med filsti og linjenummer.

1. `call.område` og `områdeOrNull` bare i `infrastruktur/kontekst/` og `OmrådeRoute.kt`
2. Ingen `*Repository.kt` importerer `IPepClient` eller `IdToken`
3. `Områder.K9` og `Områder.UNG` hardkodet bare i `K9Los.kt`, `Gruppeoppsett.kt` og tester
4. Ingen `runBlocking` i `suspend`-funksjoner
5. Ingen `AttributeKey` utenfor `OmrådeRoute.kt`
6. Ingen `CoroutineContext`-parameter i tjenestesignaturer

Regex over kildefiler er mindre presist enn AST, men alle seks reglene er importer og token-mønstre som lar seg uttrykke pålitelig. Konsist ble vurdert og valgt bort: nyeste versjon 0.17.3 er fra desember 2024, bygget mot eldre Kotlin, og embedder kompilatoren.

## Sluttilstand

| Mekanisme | Status |
|---|---|
| `CoroutineRequestContext` | slettet |
| `call.attributes` + route-plugin | beholdt som edge-mekanisme. Leses ett sted og konverteres straks til `Brukerkontekst` |
| Eksplisitt `Områder` | `Kallkontekst` i tjeneste- og klientlag, `Områder` i repository-signaturer |
| Manuell `coroutineContext`-videreføring | slettet |
| Systemtoken-klienter | uendret, men typemessig knyttet til `Systemkontekst` |

`Span.current().asContextElement()` består som eneste coroutine-context-element. Det er riktig bruk — en OTel-span er ambient tverrgående tilstand, i motsetning til område og token.

## Risiko

| Commit | Diff | Risiko |
|---|---|---|
| 1 | liten | lav |
| 2 | liten, bare tillegg | ingen |
| 3-5 | stor, ~200 kallsteder | middels. Kompilatordrevet, men stor konfliktflate mot pågående arbeid |
| 6-8 | middels | høyest. Endrer SQL-filtrering og avdelingslederpanelets saksbehandlerliste |
| 9-10 | stor | middels |
| 11 | liten | lav |

Testradius er moderat: 13 av 104 testfiler berører `withRequestContext` eller `IPepClient`. `PepClientLocal` finnes allerede som fake.

## Før du starter

Linjenumrene gjelder branch-tilstanden ved `b1c23139`. Filstier og symbolnavn er stabile. Verifiser disse tre før du begynner:

- `innloggetbruker/InnloggetBrukerApi.kt:29`
- `infrastruktur/rest/RequestContextService.kt:53`
- `K9Los.kt:301-302`
