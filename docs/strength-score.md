# All-set strength score

For weight exercises, the primary strength comparison is:

`score = sum(weight_kg * repetitions^0.1)` over every valid set in a training day.

Each set contributes fully. Zero/negative or nonfinite loads and nonpositive rep
counts contribute zero. Order does not matter. Two identical sets give twice the
score of one; additional sets are no longer restricted to a small best-set bonus.

The 0.1 exponent gives reps a diminishing influence, so normal heavier-weight,
lower-rep progression is less penalized than by volume. For example:

| Training | Volume (kg x reps) | Strength score (points) |
| --- | --- | --- |
| 3 sets of 10 at 100 kg | 3000 | 377.7 |
| 3 sets of 8 at 105 kg | 2520 | 387.8 |

Much less work can still reduce the score. It does not force all load increases
to appear as progress, and does not smooth away recorded declines.

The per-set expression is the Lombardi form listed in Table 1 of
[Ribeiro et al., Accuracy of 1RM Prediction Equations Before and After Resistance
Training in Three Different Lifts](https://journal.iusca.org/index.php/Journal/article/view/327)
([equation table](https://journal.iusca.org/index.php/Journal/article/download/327/417)).
**Summing those expressions is an app-specific comparison heuristic**, not a
validated measurement of strength, physical work, or a literal 1RM. The cited
study does not validate this session sum. Technique, proximity to failure,
warm-up sets, and exercise selection can all affect comparisons.

The shared calculation is used by exercise charts, entry summaries, strength
statistics and progression warnings. Volume remains separately available.
Stored workout records are unchanged; historical scores are recalculated.
The legacy `useOneRepMax` preference/backup field now selects this score rather
than the former session-adjusted 1RM, preserving existing display choices and
backup compatibility.

For weight-exercise charts, strength score (or volume when selected) is on the
left Y axis, recorded weight remains on the right, and reps use an independent
unlabelled scale. Reps have no fit. Level/watt charts also put the estimated
10-minute measure on the left and recorded level/watts on the right. Minutes
use an independent, unlabelled scale across the plot height with no fit.
Metric definitions are unchanged; duration-only charts show recorded minutes.
