# Backup and recovery

A JSON backup includes exercise trend visibility and the complete five-slot
rep-wheel layout, including empty slots. On restore, wheel layouts follow their
exercises even when database IDs change. Older backups without these fields
use the default trend visibility and reconstruct reps from recorded history;
stale layouts belonging to old IDs are cleared.

The workout restore runs in a database transaction. Global warning thresholds,
fatigue boundaries, previous-day fatigue percentages and wheel preferences are saved after every workout row has
validated. A malformed workout or wheel layout therefore leaves existing
workouts and settings unchanged.

Undo is offered only after a successful backup import. Cancelling the file
picker or failing to restore a file does not arm Undo. Importing a training plan
cannot trigger a backup Undo. The Undo snapshot is captured immediately before
the selected backup is restored, and Undo refreshes both workout and Body data.

Database versions 9 and 10 retain their existing non-destructive upgrade path.
Versions older than 9 are preserved and open the recovery dialog instead of
deleting workout tables. Use a compatible app version to export a JSON backup
before replacing such a database. Creating a new database requires a separate
deletion confirmation; cancelling it keeps the existing database. External JSON
backup files are not deleted.
