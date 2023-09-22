alter table feltdefinisjon add column if not exists visningsnavn varchar(100) NOT NULL DEFAULT '';
alter table feltdefinisjon add column if not exists kokriterie boolean NOT NULL DEFAULT FALSE;