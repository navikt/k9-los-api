create index if not exists event_dirty_event_nokkel_id_idx
    on event (event_nokkel_id)
    where dirty = true;

