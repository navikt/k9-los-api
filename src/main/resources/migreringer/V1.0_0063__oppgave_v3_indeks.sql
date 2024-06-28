
CREATE INDEX idx_oppgave_v3_reservasjonsnokkel_status_where_aktiv ON oppgave_v3(reservasjonsnokkel, status) where aktiv = true;