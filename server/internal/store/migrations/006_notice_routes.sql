UPDATE notices SET target_route = 'post/' || substring(target_route from 8)::int::text
WHERE target_route ~ '^arisu_[0-9]{6}$';

UPDATE notices SET target_route = 'user/' || substring(target_route from 6)::int::text
WHERE target_route ~ '^User_[0-9]{6}$';
