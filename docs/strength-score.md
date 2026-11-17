# Strength score

For weight exercises, the strength score of a training day is the **average
estimated one-rep maximum (1RM) over every valid set**, in kg:

`set estimate = weight_kg * (1 + repetitions / 30)` ([Epley](https://journal.iusca.org/index.php/Journal/article/download/327/417)); a single rep is its own load.

`score = mean(set estimates)` over the day's valid sets.

Zero/negative or nonfinite loads and nonpositive rep counts are ignored. Order
does not matter.

| Training | Volume (kg x reps) | Strength score (kg) |
| --- | --- | --- |
| 3 sets of 10 at 100 kg | 3000 | 133.3 |
| 3 sets of 12 at 100 kg | 3600 | 140.0 |
| 2 sets of 10 at 100 kg | 2000 | 133.3 |
| 3 sets of 8 at 105 kg | 2520 | 133.0 |

Why an average: every set counts (a weak last set lowers the score), more reps
are rewarded (each rep adds about 3.3 % of the load), and doing one set more or
fewer at the same performance does not change it. How much work was done is what
volume measures, and volume remains available separately.

Limits: estimates are less reliable beyond roughly 12 reps. Light warm-up sets
logged together with work sets pull the average down, so log work sets only or
compare days with the same warm-up habit. Technique, proximity to failure and
exercise selection affect comparisons. This is an estimate for comparing the same
exercise over time, not a measured maximum.

The shared calculation is used by exercise charts, entry summaries, strength
statistics and progression warnings. Stored workout records are unchanged;
historical scores are recalculated. The legacy `useOneRepMax` preference/backup
field selects this score rather than volume, preserving existing choices.

For weight-exercise charts, strength score (or volume when selected) is on the
left Y axis, recorded weight remains on the right, and reps use an independent
unlabelled scale. Reps have no fit. Level/watt charts put the estimated
10-minute measure on the left and recorded level/watts on the right. Minutes
use an independent, unlabelled scale across the plot height with no fit.
Duration-only charts show recorded minutes.
