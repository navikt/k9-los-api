# Endring: Validering av utdaterte markdown-filer

### Oppsummering
Vi har lagt til en mekanisme for å håndtere midlertidige markdown-filer i `docs/`-mappen. Disse filene blir vurdert som utdaterte hvis de ikke er endret i løpet av 30 dager. Hvis utdaterte filer oppdages, vil bygget feile som en del av den automatiserte valideringen.

### Detaljer om implementasjonen
1. **Script**:
   - Fil: `find_outdated_docs.sh`
   - Oppgave: Sjekker `.md`-filer i `docs/`-mappen som ikke har blitt endret i løpet av de siste 30 dagene.
   - Endringer: Bruk av `30 dager` som konsistent tidsperiode.

2. **GitHub Actions Workflow**:
   - Fil: `.github/workflows/check_outdated_docs.yml`
   - Plan: Kjører scriptet daglig klokken 00:00 for å validere markdown-filer.
   - Bygget feiler hvis utdaterte filer oppdages.

### Hvordan det fungerer:
- Hver dag kjøres et cron-jobb.
- Scriptet finner `.md`-filer og sammenligner siste endringsdato med dagens dato minus 30 dager.
- Utdaterte filer listes opp, og bygget feiler dersom noen finnes.

### Anbefalinger
- Oppdatering eller sletting av utdaterte dokumenter bør integreres i utviklingsarbeidet for å unngå feil i byggprosessen.
