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
    Enterprise_Boundary(dvhBoundary, "k9") {
        System_Boundary(k9los, "K9-los") {
            System(k9los, "k9-los-api", "Oppgavekøer og -statistikk for k9")
            System(k9losweb, "k9-los-web", "Webapplikasjon for oppgavehåndtering og ledelsesstyring")
            SystemDb(postgresql, "PostgreSQL Database", "Lagrer oppgave- og ledelsesstyringsdata")
        }

        System_Ext(k9sak, "k9-sak", "Produserer meldinger om tilstand på behandlinger")
        System_Ext(k9klage, "k9-klage", "Produserer meldinger om tilstand på behandlinger")
        System_Ext(k9tilbake, "k9-tilbake", "Produserer meldinger om tilstand på behandlinger")
        System_Ext(k9punsj, "k9-punsj", "Produserer meldinger om tilstand på punsjoppgaver")

        Rel_U(k9sak, k9los, "Sender meldinger om tilstand på behandlinger")
        Rel_U(k9tilbake, k9los, "Sender meldinger om tilstand på behandlinger")
        Rel_U(k9klage, k9los, "Sender meldinger om tilstand på behandlinger")
        Rel_U(k9punsj, k9los, "Sender meldinger om tilstand på punsjoppgaver")
        Rel_U(k9los, dvh, "Sender oppgavestatistikk")
    }

    System_Ext(dvh, "Datavarehus", "Ansvar for oppgavestatistikk")

    Person(saksbehandler, "Saksbehandler", "Bruker som håndterer oppgaver i k9-los")
    Person(admin, "Avdelingsleder", "Administrerer oppgavekøer")
    Person(beslutter, "Beslutter", "Bruker som kontrollerer og godkjenner behandlinger før vedtak fattes")
    
    Rel_U(saksbehandler, k9los, "")
    Rel_U(admin, k9los, "")
    Rel_U(beslutter, k9los, "")

    
    
    
    UpdateLayoutConfig($c4ShapeInRow="5", $c4BoundaryInRow="1")
    
```


```mermaid
    C4Context
      title System Context diagram for Internet Banking System
      Enterprise_Boundary(b0, "BankBoundary0") {
        Person(customerA, "Banking Customer A", "A customer of the bank, with personal bank accounts.")
        Person(customerB, "Banking Customer B")
        Person_Ext(customerC, "Banking Customer C", "desc")

        Person(customerD, "Banking Customer D", "A customer of the bank, <br/> with personal bank accounts.")

        System(SystemAA, "Internet Banking System", "Allows customers to view information about their bank accounts, and make payments.")

        Enterprise_Boundary(b1, "BankBoundary") {

          SystemDb_Ext(SystemE, "Mainframe Banking System", "Stores all of the core banking information about customers, accounts, transactions, etc.")

          System_Boundary(b2, "BankBoundary2") {
            System(SystemA, "Banking System A")
            System(SystemB, "Banking System B", "A system of the bank, with personal bank accounts. next line.")
          }

          System_Ext(SystemC, "E-mail system", "The internal Microsoft Exchange e-mail system.")
          SystemDb(SystemD, "Banking System D Database", "A system of the bank, with personal bank accounts.")

          Boundary(b3, "BankBoundary3", "boundary") {
            SystemQueue(SystemF, "Banking System F Queue", "A system of the bank.")
            SystemQueue_Ext(SystemG, "Banking System G Queue", "A system of the bank, with personal bank accounts.")
          }
        }
      }

      BiRel(customerA, SystemAA, "Uses")
      BiRel(SystemAA, SystemE, "Uses")
      Rel(SystemAA, SystemC, "Sends e-mails", "SMTP")
      Rel(SystemC, customerA, "Sends e-mails to")

      UpdateElementStyle(customerA, $fontColor="red", $bgColor="grey", $borderColor="red")
      UpdateRelStyle(customerA, SystemAA, $textColor="blue", $lineColor="blue", $offsetX="5")
      UpdateRelStyle(SystemAA, SystemE, $textColor="blue", $lineColor="blue", $offsetY="-10")
      UpdateRelStyle(SystemAA, SystemC, $textColor="blue", $lineColor="blue", $offsetY="-40", $offsetX="-50")
      UpdateRelStyle(SystemC, customerA, $textColor="red", $lineColor="red", $offsetX="-50", $offsetY="20")

      UpdateLayoutConfig($c4ShapeInRow="5", $c4BoundaryInRow="1")

```