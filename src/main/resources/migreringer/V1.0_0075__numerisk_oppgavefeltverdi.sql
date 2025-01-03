create function try_cast_int(p_in text, p_default int default null)
    returns int
as
    $$
begin
begin
return $1::int;
exception
    when others then
       return p_default;
end;
end;
$$
language plpgsql;

alter table oppgavefelt_verdi add column verdi_int int;
alter table oppgavefelt_verdi_aktiv add column verdi_int int;