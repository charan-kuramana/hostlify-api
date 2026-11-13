-- Keep every concern a resident raised on a checklist, even after they later confirm it.
ALTER TABLE room_checklists ADD COLUMN concern_history TEXT NULL;

-- Backfill: checklists currently disputed already hold their concern in resident_comment.
UPDATE room_checklists
SET concern_history = to_char(responded_at, 'DD Mon YYYY, HH12:MI AM') || ': ' || resident_comment
WHERE resident_response = 'DISPUTED' AND resident_comment IS NOT NULL AND responded_at IS NOT NULL;
