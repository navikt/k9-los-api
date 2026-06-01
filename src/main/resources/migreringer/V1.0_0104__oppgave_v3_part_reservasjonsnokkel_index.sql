-- Index for effektivt oppslag av åpne/ventende/uavklarte oppgaver gitt reservasjonsnøkkel.
-- Legges direkte på leaf-partisjonen for AAPEN/VENTER/UAVKLART.
CREATE INDEX idx_oppgave_v3_part_avu_reservasjonsnokkel
    ON oppgave_v3_aapen_venter_uavklart_part (reservasjonsnokkel);
