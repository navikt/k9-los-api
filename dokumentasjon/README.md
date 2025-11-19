Søk:
 - kriterier
 - uttrekksnøkkel

Kø:
 - kriterier
 - rapportkriterier
 - saksbehandlere
 - antall (hentet dynamisk/cachet)
 - uttrekksnøkkel

Treig kjøringsresultat:
 - nøkkel
 - data
   - Oppgaveentitet
     - metadata (status, andre first class citizens) //under tvil
     - verdier (utplukk fra KV)

```mermaid
C4Context
    title k9-los-api kontekstdiagram
    Person(saksbehandler, "Saksbehandler", "Bruker som håndterer oppgaver i k9-los")
    Person(admin, "Avdelingsleder", "Administrerer oppgavekøer")
    Person(beslutter, "Beslutter", "Bruker som kontrollerer og godkjenner behandlinger før vedtak fattes")
    
    System_Boundary(k9los, "K9-los") {
        System(k9los, "k9-los-api", "Oppgavekøer og -statistikk for k9")
        System(k9losweb, "k9-los-web", "Webapplikasjon for oppgavehåndtering og ledelsesstyring")
        SystemDb(postgresql, "PostgreSQL Database", "Lagrer oppgave- og ledelsesstyringsdata")
    }
    
    Rel_U(saksbehandler, k9los, "test")
    Rel_U(admin, k9los, "test")
    Rel_U(beslutter, k9los, "test")

    System_Ext(k9sak, "k9-sak", "Produserer meldinger om tilstand på behandlinger")
    System_Ext(k9klage, "k9-klage", "Produserer meldinger om tilstand på behandlinger")
    System_Ext(k9tilbake, "k9-tilbake", "Produserer meldinger om tilstand på behandlinger")
    System_Ext(k9punsj, "k9-punsj", "Produserer meldinger om tilstand på punsjoppgaver")
    System_Ext(dvh, "Datavarehus", "Ansvar for oppgavestatistikk")

    Rel_R(k9sak, k9los, "Sender meldinger om tilstand på behandlinger")
    Rel_R(k9tilbake, k9los, "Sender meldinger om tilstand på behandlinger")
    Rel_R(k9klage, k9los, "Sender meldinger om tilstand på behandlinger")
    Rel_R(k9punsj, k9los, "Sender meldinger om tilstand på punsjoppgaver")
    Rel_D(k9los, dvh, "Sender oppgavestatistikk")
    
```