--indeks for raskere uthening av oppgaver med autopunkter
create index oppgave_expr_idx9
    on oppgave (
        (data -> 'system'),
        (data -> 'fagsakYtelseType' ->> 'kode'),
        (data -> 'behandlingType' ->> 'kode'),
        (data -> 'aksjonspunkter' -> 'apTilstander')
    )
    where ((data -> 'aksjonspunkter' -> 'apTilstander' @? '$[*] ? (@."status"=="OPPR" && @."frist" != null)'));

--indeks for å få raskere spørring for å få oversikt over oppgaver
create index oppgave_expr_idx10
    on oppgave (
        (data -> 'fagsakYtelseType' ->> 'kode'),
        (data -> 'behandlingType' ->> 'kode')
    )
    where (data -> 'aktiv') ::boolean;