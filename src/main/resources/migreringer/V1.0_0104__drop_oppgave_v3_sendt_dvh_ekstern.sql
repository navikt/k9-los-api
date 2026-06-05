-- Drop the deprecated oppgave_v3_sendt_dvh_ekstern table.
-- Tracking has been inverted to use oppgave_v3_dvh_pending (items to send)
-- instead of oppgave_v3_sendt_dvh_ekstern (items already sent).
DROP TABLE IF EXISTS OPPGAVE_V3_SENDT_DVH_EKSTERN;

