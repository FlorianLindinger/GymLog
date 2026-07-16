# Exercise Ordering

GymLog offers three ordering modes under **Menu → Exercise Ordering**. The active exercise is always shown first, regardless of the selected mode.

## Automatic

Automatic ordering ranks the remaining exercises by the following priorities, in order:

1. Exercises not yet completed in the current training
2. Exercises with the least conflict with the active exercise's body parts
3. Exercises that were not performed in the previous training
4. Exercises performed the fewest times overall
5. Exercise name, as a stable tie-breaker

Body-part conflict uses the strongest shared-area overlap score, so lower-conflict alternatives come first. This keeps useful alternatives near the top without hiding exercises that have received less attention.

## Mixed

Mixed ordering places exercises marked **Keep at Top** before the automatically ranked exercises. Pinned exercises keep their manual order; every unpinned exercise follows the Automatic rules.

To pin or unpin an exercise, open that exercise's editor and change **Keep at Top**. Long-press and drag a pinned exercise in the overview to reorder the pinned group.

## Fixed

Fixed ordering keeps the complete manual exercise order. Long-press and drag exercises in the overview; GymLog persists that order.

## Active Exercise

The active exercise is the exercise currently being logged in the live training. It remains first in Automatic, Mixed, and Fixed modes so the next set is always easy to reach.
