UPDATE notices SET actor_id = substring(dedupe_key from '^up:[0-9]+:([0-9]+)$')::bigint
WHERE actor_id IS NULL AND dedupe_key ~ '^up:[0-9]+:[0-9]+$';

UPDATE notices SET actor_id = substring(dedupe_key from '^follow:[0-9]+:([0-9]+)$')::bigint
WHERE actor_id IS NULL AND dedupe_key ~ '^follow:[0-9]+:[0-9]+$';

UPDATE notices n SET actor_id = p.author_id
FROM posts p
WHERE n.actor_id IS NULL
  AND n.dedupe_key ~ '^tracked:[0-9]+:'
  AND p.id = substring(n.dedupe_key from '^tracked:([0-9]+):')::int;

UPDATE notices SET actor_id = substring(dedupe_key from '^profile:([0-9]+):')::bigint
WHERE actor_id IS NULL AND dedupe_key ~ '^profile:[0-9]+:';
