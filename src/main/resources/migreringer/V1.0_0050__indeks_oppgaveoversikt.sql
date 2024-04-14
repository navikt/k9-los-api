--for raskere uthening av oppgaver med autopunkter
create index oppgave_expr_idx9
    on oppgave (
        (data -> 'system'),
        (data -> 'fagsakYtelseType' ->> 'kode'),
        (data -> 'behandlingType' ->> 'kode'),
        (data -> 'aksjonspunkter' -> 'apTilstander')
    )
    where ((data -> 'aksjonspunkter' -> 'apTilstander' @? '$[*] ? (@."status"=="OPPR" && @."frist" != null)'));

--for å få raskere spørring for å få oversikt over oppgaver
create index oppgave_expr_idx10
    on oppgave (
        (data -> 'behandlingType' ->> 'kode'),
        (data -> 'fagsakYtelseType' ->> 'kode')
    )
    where (data -> 'aktiv') ::boolean;


--for å få raskere spørring for dagens tall og noen flere spørringer
create index oppgave_expr_idx11
    on oppgave (
        (data -> 'kode6'),
        (data -> 'behandlingType' ->> 'kode')
    )
    where (data -> 'aktiv') ::boolean;

create index nye_og_ferdigstilte_idx1 on nye_og_ferdigstilte (dato);
