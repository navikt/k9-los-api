# Databasemigrering og deploy

```mermaid
flowchart TD
    A[Push eller workflow_dispatch] --> B[Workflow: Bygg og deploy]
    B --> C[Bygg og test applikasjonen]

    C --> D{Default branch?}
    D -- Nei --> E[Last opp image som workflow-artifact]
    D -- Ja --> F[Publiser image til GAR]

    F --> A1{Godkjenning for dev-miljø}
    F --> A2{Review pending deployments for prod}

    A1 --> G1[Start miljøbeskyttet dev-jobb]
    A2 --> G2[Start miljøbeskyttet prod-jobb]
    G1 --> H1[Naisjob kjører Flyway i dev]
    G2 --> H2[Naisjob kjører Flyway i prod]
    H1 --> I1{Migrering fullført?}
    H2 --> I2{Migrering fullført?}

    I1 -- Nei --> X1[Stopp dev-kjeden]
    I2 -- Nei --> X2[Stopp prod-kjeden]
    I1 -- Ja --> J1[Deploy applikasjonen i dev]
    I2 -- Ja --> J2[Deploy applikasjonen i prod]

    J1 --> K1[Ny dev-pod verifiserer Flyway-historikk]
    J2 --> K2[Ny prod-pod verifiserer Flyway-historikk]
    K1 --> L1{Databasen oppdatert?}
    K2 --> L2{Databasen oppdatert?}
    L1 -- Nei --> X3[Dev-poden stopper]
    L2 -- Nei --> X4[Prod-poden stopper]
    L1 -- Ja --> M1[Dev-rollout fullføres]
    L2 -- Ja --> M2[Prod-rollout fullføres]

    N[Lokal kjøring eller verdikjedetest] --> O[Applikasjonen kjører Flyway selv]
    O --> P[Applikasjonen starter]

    Q[Gammel applikasjon fortsetter å kjøre] -. under migreringen .-> H1
    Q -. under migreringen .-> H2
    R[Manuell skalering til 0 ved låsende eller<br/>ikke-bakoverkompatibel migrering] -. før migrering .-> G1
    R -. før migrering .-> G2

    classDef automatic fill:#d8f3dc,stroke:#2d6a4f,color:#000;
    classDef local fill:#fff3bf,stroke:#e67700,color:#000;
    classDef failure fill:#ffe3e3,stroke:#c92a2a,color:#000;
    classDef workload fill:#dbe4ff,stroke:#364fc7,color:#000;

    class A,B,C,D,E,F automatic;
    class G1,G2,H1,H2,J1,J2,K1,K2,M1,M2,Q workload;
    class N,O,P,R local;
    class I1,I2,L1,L2,X1,X2,X3,X4 failure;
```

## Egenskaper ved flyten

- Flyten tilsvarer master, men hver appdeploy avhenger av en migreringsjobb for samme miljø og image.
- Dev- og prodløpet starter uavhengig av hverandre etter build, som på master.
- Migreringsjobben bruker admin-rollen og kjører Flyway.
- Migrering og appdeploy ligger i samme miljøbeskyttede jobb. Én `Review pending deployments`-godkjenning åpner begge stegene, og ingen av dem kjører før godkjenningen.
- Applikasjonen kan ikke migrere i dev eller prod. Den validerer historikken og stopper ved ugyldige eller ventende migreringer.
- Lokalt og i verdikjedetester kjører applikasjonen selv Flyway før oppstart.
- Den gamle applikasjonen fortsetter å kjøre under en bakoverkompatibel migrering.
- Flyten kan ikke avgjøre om en migrering er bakoverkompatibel. Ved behov må gamle podder skaleres til 0 manuelt før migreringen starter.
