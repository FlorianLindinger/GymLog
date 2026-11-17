# Recovery weighting in exercise suggestions

The overview uses small **planning heuristics**, centered on 1.0, to retain
overlap from earlier exercises for slightly different amounts of exercise order:

| Body region | Factor |
| --- | --- |
| Biceps, triceps | 1.1 |
| Quads, hamstrings | 0.9 |
| Core / abs | 1.25 |
| Chest, upper back, lower back, traps, shoulders | 1.0 |
| Forearms, glutes, adductors, abductors, calves | 1.0 |

These exact numbers are product choices, **not published physiological ratios**.
Core/abs uses 80% of its previous decay rate (20% less recovery per exercise
step), represented by the reciprocal factor `1 / 0.8 = 1.25`. This is independent
of the editable core involvement percentage and its preset boost.
Other regions retain neutral defaults where the cited comparisons do not cover them.
The factors apply consistently to the shared overlap score used for automatic
ranking, percentage labels and outline colors. They do not change recorded sets,
body-area workload totals, manual ordering, or completion ordering.

For an earlier exercise at zero-based position `i` (newest = 0), each region's
contribution is `involvement * max(0.1, 0.5^(i / factor))`. Contributions are summed and
capped at 1 per region. Each candidate region is multiplied by its own involvement; the highest resulting contribution determines the score (capped at 100%). Adding a region can never reduce another region's contribution.
A higher factor retains more influence from earlier exercises. The latest
exercise's contribution is unchanged. Four green/yellow/orange/red bins are shared by colors and Auto sorting. In Menu > Exercise Order, choose the inclusive yellow/orange/red starts (defaults 25%, 50%, 75%).

This model uses exercise order within the training day, not elapsed recovery
time. It does not estimate readiness, fatigue, hours until recovery, or injury
risk. It does not account for load, proximity to failure, training experience,
sleep, or individual recovery. This checkout also retains its previous-day contributions: 30% per exercise yesterday, 15% two days ago, and 7.5% three days ago, without order weighting. Editable region intensities and the 10% floor for older current-day exercises are preserved.

## Evidence and limits

- [Chen et al., Comparison in eccentric exercise-induced muscle damage among
  four limb muscles (2011)](https://pubmed.ncbi.nlm.nih.gov/20852880/)
  compares elbow flexors/extensors with knee flexors/extensors after maximal
  eccentric exercise in sedentary men.
- [Jamurtas et al., Comparison between leg and arm eccentric exercises of the
  same relative intensity on indices of muscle damage (2005)](https://pubmed.ncbi.nlm.nih.gov/16007451/)
  found greater damage and slower functional recovery in elbow flexors than
  knee extensors in untrained men.

These studies support a cautious directional distinction in those protocols.
They do not validate 1.1/0.9, a universal muscle hierarchy, or decay by exercise
position in a trained person's normal workout. Keep adjustments modest and
treat the suggestions as ordering assistance.

## Small boosts within an overlap bin

Auto priority remains ahead of the following combined score. Each active
progress warning adds 0.25, up to two warnings; global settings and per-exercise
overrides determine which warnings are active. Fewer training days in the last
two calendar months add a peer-relative catch-up boost of 0.25 per training
difference, with each count capped at three. Higher combined scores come first.
A new exercise therefore stops receiving a frequency advantage after three
recent training days, rather than having to catch up with every older training.
These boosts do not change the overlap outline colors, manual groups or
completed-exercise order.

Color boundaries are global, saved locally and included in JSON backups. Older backups without boundaries use the defaults. Auto priority and warning/frequency boosts only reorder within a color bin; current-exercise and manual-group placement keep their existing rules.

Fatigue context includes recorded exercises across all training plans, including hidden exercises. Switching plans changes the candidate list, not the accumulated regional load. The read query retains only the current training day and the three preceding training days, using the local 06:00 boundary and excluding future entries. Automatic ordering and overview colors use this same context; frequency boosts and completed-exercise placement still refer to the active plan.
