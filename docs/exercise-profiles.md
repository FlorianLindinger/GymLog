# Exercise profiles and automatic ordering

In the overview, tap an exercise card to open its details, then choose **Body Regions & Intensity**. The metric and **+ Sets** controls remain separate tap targets. Overview titles, progress warnings, and history stay on one line and shrink to fit when space runs out. The current metric stays at the top right; level is abbreviated to **Lv.**. Set each body region to **Off**, **Low**, **Medium**, or **High**, then tap **Save**. The region guide and area colors remain available in the editor.

The selector opens with a front/back body-region guide. Its numbered color key names all 15 regions and uses the same current colors as the intensity controls, including custom colors. **Hide** collapses the guide when you want more room for editing. Show/Hide is remembered globally across all exercises and app restarts, including when you cancel profile edits. The default is shown. The old bottom **Show muscle reference** control has been removed.

The region editor immediately offers up to three matching profiles from the exercise name. **Browse exercise list** opens the full offline catalogue of machine, cable, free-weight, bodyweight, and cardio exercises. Search English or German names, including common aliases, umlaut spellings, prefixes, joined or separated words, swapped letters, and small spelling mistakes (up to two edits in long words). Station numbers and known gym/manufacturer prefixes are ignored: **Bauchmaschine 2**, **Bauchmaschine2**, and **FitX Technogym Bauchmaschine 2** all find **Bauchmaschine**. Equipment filters help distinguish similarly named exercises. Selecting an entry fills the draft profile; review or adjust it before saving. Saved regions are retained until you choose a suggestion or edit them. Cancel leaves the exercise's saved profile intact. A custom exercise can have any name and a fully manual profile.

The Add Exercise dialog also suggests matches while typing, offers the complete catalogue, and lets you edit the profile before adding the exercise. The exercise and its initial profile are saved together. Choosing a suggestion preserves a name you already entered. Cardio templates default to level/minute tracking; other templates default to weight tracking, including zero additional weight for bodyweight work. Once you explicitly choose a tracking mode (such as Watts), selecting another template preserves that choice.

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

Thus the previous exercise has half the influence of the current one, and the overlap percentage accounts for all assigned regions and their intensities. This is a planning estimate. The outline blends smoothly from green at zero overlap through yellow and orange to red at full overlap. Missing profiles count as zero overlap and use green when unfinished and not current. The current exercise keeps its blue outline; completed exercises have no overlap outline. Overlap percentages and lifetime training counts are not displayed on the cards.

Ordering follows these rules:

1. Keep the current exercise first.
2. Keep Manual top exercises in their chosen arrow order.
3. Auto sorted exercises put unfinished exercises before those already done. Within each group, lower overlap comes first; unknown overlap is treated as zero, alongside known zero-overlap exercises.
4. Equal overlap prefers higher tie priority, then fewer recorded training days across the active plan's full history. Multiple sets on one training day count once. Saved order and stable name/ID ordering resolve remaining ties. The Auto sorted section in Exercise Order previews this ranking; + raises priority and - lowers it.
5. Keep Manual bottom exercises in their chosen arrow order.

Manual top/bottom provide an explicit override of automatic ranking. The overview shows the comparison exercises and uses outline color to indicate overlap.

## Progress warnings

Weight-tracked exercises show warnings on one line: `4 weeks no kg increase` and/or `volume trend 2 %`, separated by a middle dot only when both apply. The warning text shrinks to fit the card.

**Progress Warnings** settings let you choose the weeks without a kg increase, minimum volume increase percentage, and volume window in trainings (default 5, minimum 2). Blank or zero disables the corresponding warning. For volume warnings, either a blank/zero window or blank/zero minimum percentage disables the warning; an enabled window requires at least two trainings. The training-count warning has been removed.

Volume is total kg times reps per training day (06:00 local boundary). Once a full window is available, the first and last training's volumes are compared. Every exercise has a **Progress Warnings** button in its details. It follows global warnings by default. Turn off **Follow global warning settings** to save custom thresholds or disable warnings for that exercise. Turn it back on to inherit future global changes. Custom settings survive plan duplication, deletion undo, and JSON backup/restore.

The selected window persists and is included in JSON backup/restore; older backups use the five-training default.

### Optional 1RM metric

Enable **Use 1RM instead of volume** directly in **Menu**. The adjacent **?** explains the metric. Exercise **Progress Warnings** still allows a custom metric when **Follow global warning settings** is off. Both global and per-exercise warning dialogs explicitly name the currently used trend metric. It is on by default for unconfigured settings and follows the existing global/custom setting choice. Explicitly saved choices are preserved. For weight exercises, the exercise chart, entry summary and trend warning use **1RM** instead of volume. Tap the chart's **1RM [kg] ?** legend to read the explanation. Level/watts entries have intensity and minutes, so their workload is intensity times minutes rather than a 1RM estimate; their chart labels are **Load [Lv. min]** and **Work [W min]**. Labels become **1RM [kg]** and **1RM trend x %**, and the window/percentage field labels change with the choice. Blank/zero still disables warnings, independently of displaying 1RM.

1RM means estimated one-repetition maximum: the load you might lift once. GymLog uses the Wathan equation, `100 * weight / (48.8 + 53.8 * exp(-0.075 * reps))`, taking the highest estimate among sets in each training day. A recorded single uses its actual load. Additional sets do not add to 1RM. Exercise entry summaries show the best estimate in that entry; chart and warning windows use the best set for the whole training day.

Wathan is a practical default, not a universally best predictor. [LeSuer et al. (1997)](https://paulogentil.com/pdf/The%20Accuracy%20of%20Prediction%20Equations%20for%20Estimating%201-RM%20Performance%20in%20the%20Bench%20Press%2C%20Squat%2C%20and%20Deadlift.pdf) found good bench/squat prediction in novice men and women using sets of ten or fewer repetitions to fatigue, while all tested equations underestimated deadlifts. Estimates are most useful for hard, lower-rep sets. The selected method is included in global and per-exercise backups; older backups without a metric choice use the current 1RM default.

## Catalogue reference notes

Names, translations, aliases, and simplified profiles are independently curated. The numerical intensity weights are GymLog estimates. Manufacturer model names and source URLs are retained as factual references. No third-party exercise descriptions, images, or videos are embedded in the catalogue.

Representative movement mappings were checked against:

- [ExRx: barbell bench press](https://exrx.net/WeightExercises/PectoralSternal/BBBenchPressPowerLift), which identifies the chest as a target and anterior shoulders and triceps among supporting muscles.
- [ExRx: cable pulldown](https://exrx.net/WeightExercises/LatissimusDorsi/CBFrontPulldown), which identifies the latissimus and lists elbow flexors and other upper-back/shoulder muscles as synergists.
- [ExRx: bodyweight squat](https://exrx.net/WeightExercises/Quadriceps/BWSquat), which identifies quadriceps as a target and glutes and adductors among supporting muscles.

The full catalogue includes additional independently estimated exercise variants. A selected profile is always reviewable and editable; exact involvement varies between variants and equipment.

### FitX and manufacturer equipment labels

Checked against official sources on 25 October 2026:

- [FitX training areas](https://hilfe.fitx.de/hc/de/articles/13123408339613-Welche-Trainingsangebote-gibt-es-bei-FitX) names Technogym and Hammer Strength as suppliers; [FitX machine training](https://www.fitx.de/trainingsbereiche/geraete) identifies Technogym as its machine partner.
- [Technogym Selection 900](https://www.technogym.com/en-US/category/selection-900/) supplies searchable equipment labels such as Pectoral, Arm Curl, Arm Extension, Delts Machine, Lower Back, Pulley, and Vertical Traction.
- [Abdominal Crunch](https://www.technogym.com/en-US/product/selection-700-abdominal-crunch_MNBC.html), [Total Abdominal](https://www.technogym.com/en-US/product/selection-900-total-abdominal_MNZP.html), and [Rotary Torso](https://www.technogym.com/en-US/product/selection-900-rotary-torso_MNYP.html) support the abdominal/core movement mappings. [Vertical Traction](https://www.technogym.com/en-US/product/selection-900-vertical-traction_MNGP.html) supports a pulldown-style starting profile.

These sources establish equipment labels and broad movement targets, not numerical intensity weights. No official source was found assigning **Bauchmaschine 2** to a specific model at every FitX studio. Numbers therefore fall back to the base exercise instead of inventing a model-specific assignment. Review the offered profile for your actual machine. All matching and suggestions work offline.

### Manufacturer inventory

The offline catalogue now includes 184 general exercises and 421 machine/movement choices for **271 published machine models**: **170 Technogym** and **101 Hammer Strength**. The [machine inventory](manufacturer-equipment.tsv) records every included model, its official product URL, and the suggested movement profiles. Manufacturer-only searches browse the matching brand; searches such as **Technogym Selection 900** also restrict the product line. **Stepmaster**, **Step Master**, **Stairmaster**, and numbered variants find the stair climber profile.

The inventory was collected from the official US catalogue/sitemap and available German regional results on 25 October 2026. It includes currently listed and archived exercise machines. Racks, ordinary benches, storage, accessories, clothing, bundles, and non-exercise devices are excluded. This is a snapshot of the published catalogues checked, not a claim about every discontinued model ever manufactured or availability at an individual FitX studio.

Covered ranges include Technogym Artis, Biostrength, Selection 700/900/Med, Pure Strength, Excite, Skill, Personal, cable stations and additional cardio models; and Hammer Strength Plate Loaded/Ground Base, Select, MTS, cable stations, and HD performance trainers. Important catalogue sources:

- [Technogym Selection 700](https://www.technogym.com/en-US/category/selection-700/), [Selection 900](https://www.technogym.com/en-US/category/selection-900/), [Artis](https://www.technogym.com/en-US/category/artis/), [Biostrength](https://www.technogym.com/en-US/category/biostrength/), [Pure Strength](https://www.technogym.com/en-US/category/pure-strength/), [cable machines](https://www.technogym.com/en-US/category/cable-machines/), [Excite](https://www.technogym.com/en-US/category/excite/), and [Skill](https://www.technogym.com/en-US/category/skill/).
- [Hammer Strength Plate Loaded](https://www.lifefitness.com/en-us/catalog/strength-training/plate-loaded/hammer-strength), [Select](https://www.lifefitness.com/en-us/catalog/strength-training/selectorized/hammer-strength-select), [MTS](https://www.lifefitness.com/en-us/catalog/strength-training/selectorized/motion-technology-selectorised), and [performance trainers](https://www.lifefitness.com/en-us/catalog/hammer-strength-performance-trainers).

Multi-purpose machines offer separate movements with separate profiles. For example, a dual pulley offers chest press and rowing choices instead of assigning the same regions to both. Flexability stretching machines, Reform, and the tibia dorsiflexion machine request manual regions rather than inventing a strength profile for regions or movements not adequately represented by the current body map. Stretching machines default to duration tracking. Manufacturer names do not imply manufacturer endorsement of GymLog's estimated intensity weights.
