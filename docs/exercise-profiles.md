# Exercise profiles and automatic ordering

In the overview, tap **Regions** (or **Set regions**) on an exercise. The same editor is available on its detail page. Set each body region to **Off**, **Low**, **Medium**, or **High**, then tap **Save**. The muscle reference and area colors remain available in the editor.

**Suggest from exercise list** opens an offline catalogue of 177 machine, cable, free-weight, bodyweight, and cardio exercises. Search English or German names, including common aliases, umlaut spellings, prefixes, and small spelling mistakes. Equipment filters help distinguish similarly named exercises. Selecting an entry fills the draft profile; review or adjust it before saving. Cancel leaves the exercise's saved profile intact. A custom exercise can have any name and a fully manual profile.

The Add Exercise dialog also suggests matches while typing, offers the complete catalogue, and lets you edit the profile before adding the exercise. The exercise and its initial profile are saved together. Choosing a suggestion preserves a name you already entered. Cardio templates default to level/minute tracking; other templates default to weight tracking, including zero additional weight for bodyweight work.

## Intensity values

These are editable estimates of involvement, using broad body regions. They are relative scoring weights, not measurements of physiological activation or recovery.

| Setting | Meaning | Internal weight |
| --- | --- | --- |
| Off | Region is not part of this exercise's profile | No assignment |
| Low | Light involvement or stabilization | 0.25 |
| Medium | Supporting involvement | 0.5 |
| High | Primary involvement | 1 |

Adjust a suggested profile to reflect your machine, grip, technique, and experience. Existing half/full assignments retain their stored weights and appear as Medium/High. The existing database and JSON backup format already store numeric weights, including the new Low value. Body-area statistics now show Low separately and include its quarter weight in totals. Statistics use the exercise's current profile for recorded history.

## Overlap and ranking

The current exercise is the one with the latest recorded set in this training. The previous exercise is the latest **distinct** exercise before it, so several consecutive sets on one machine do not occupy both comparison slots. Only the current training, starting at 06:00 local time, supplies this context. Hidden exercises still contribute context when they were recently trained.

For each region, estimated recent load is:

```text
recent load = min(1, current intensity + 0.5 × previous intensity)
overlap % = round(100 × sum(candidate intensity × recent load) / sum(candidate intensity))
```

Thus the previous exercise has half the influence of the current one, and the displayed percentage accounts for all assigned regions and their intensities. This is a planning estimate. A missing profile shows unknown overlap; a partially configured context is labelled partial.

Ordering follows these rules:

1. Keep the current exercise first.
2. Keep Manual top exercises in their chosen arrow order.
3. Auto sorted exercises put unfinished exercises before those already done. Within each group, known overlap comes before unknown overlap, and lower overlap comes first.
4. Equal displayed overlap prefers the exercise with fewer recorded training days across the active plan's full history. Multiple sets on one training day count once. Tie priority, saved order, and stable name/ID ordering resolve remaining ties.
5. Keep Manual bottom exercises in their chosen arrow order.

Manual top/bottom provide an explicit override of automatic ranking. The overview shows the comparison exercises, estimated overlap, and the lifetime training count used for the tie-break.

## Catalogue reference notes

Names, translations, aliases, and simplified profiles are independently curated. The numerical intensity weights are GymLog estimates. No exercise descriptions, images, videos, or third-party datasets are embedded in the catalogue.

Representative movement mappings were checked against:

- [ExRx: barbell bench press](https://exrx.net/WeightExercises/PectoralSternal/BBBenchPressPowerLift), which identifies the chest as a target and anterior shoulders and triceps among supporting muscles.
- [ExRx: cable pulldown](https://exrx.net/WeightExercises/LatissimusDorsi/CBFrontPulldown), which identifies the latissimus and lists elbow flexors and other upper-back/shoulder muscles as synergists.
- [ExRx: bodyweight squat](https://exrx.net/WeightExercises/Quadriceps/BWSquat), which identifies quadriceps as a target and glutes and adductors among supporting muscles.

The full catalogue includes additional independently estimated exercise variants. A selected profile is always reviewable and editable; exact involvement varies between variants and equipment.
