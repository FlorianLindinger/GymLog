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

The current exercise is the one with the latest recorded set in this training. All distinct exercises from the current training, starting at 06:00 local time, supply context in newest-first order. Repeated sets do not create extra context entries. Hidden exercises also contribute.

For each region, estimated recent load is:

```text
recent load = min(1, sum(exercise intensity * 0.5^recency index))
overlap % = round(100 * sum(candidate intensity * recent load) / sum(candidate intensity))
```

The fixed weights are 100%, 50%, 25%, 12.5%, then 10% for every earlier exercise. Core/abs percentages in exercise presets are scaled by 1.5 (High = 150%, Medium = 75%, Low = 37.5%). There is no hidden Core multiplier, so existing profiles keep their saved values until edited. Region percentages can be edited directly using compact L/M/H shortcuts (25/50/100%) and a custom percentage field; 0 removes a region. They are never normalized by the number of exercises performed, so additional overlapping work can add load. Exercises from yesterday contribute 40% each, two training days ago 20% and three training days ago 10% by default (editable in Exercise Order), once per exercise per training day and without order weighting. Training days use the 06:00 boundary. This is a planning estimate. Missing profiles count as zero overlap.

Ranking and outlines share seven bins: 0-14% dark green, 15-28% green, 29-42% yellow, 43-57% orange, 58-71% red-orange, 72-85% red, and 86-100% dark red. The current exercise keeps its blue outline; completed exercises have no overlap outline. The overview has no overlap text header.

Ordering follows these rules:

1. Keep the current exercise first.
2. Keep unfinished Manual top exercises in their chosen arrow order.
3. Rank unfinished Auto exercises by overlap bin, from least to most overlap.
4. Within each bin, prefer higher Auto priority: + moves an exercise earlier and - moves it later, before comparing training frequency. With equal priority, prefer fewer distinct training days in the last two calendar months, then saved order and stable name/ID ordering. A training day starts at 06:00 local time; the window includes the entire training day at its start. Multiple sets count once. The Auto sorted section in Exercise Order previews this ranking. Priority applies to unfinished Auto exercises within the same overlap bin; it does not override the current exercise, manual groups, completed ordering or a different overlap bin.
5. Keep unfinished Manual bottom exercises in their chosen arrow order.
6. Put all remaining completed exercises last, ordered by their last set from first to last done.

Manual top/bottom override automatic ranking for unfinished exercises.

## Progress warnings

Weight-tracked exercises show warnings on one line: `60 days no kg increase` and/or `Strength score trend +2 %`, separated by a middle dot only when both apply. Trend warnings always use the all-set strength score, regardless of the chart display choice or legacy per-exercise metric overrides.

**Progress Warnings** settings let you choose days without a kg increase (default 60 complete 24-hour days), the minimum strength-score increase percentage, and the trend window in days (default 60, requiring at least two training days). Blank or zero disables the corresponding warning. Either a blank/zero window or minimum percentage disables trend warnings. A least-squares line is fitted through every training day's score within that window and its change from the first to the last day is compared (with two days this equals the plain change), so one unusual day cannot decide the warning alone; training days start at 06:00 local time. Saved week thresholds convert to equivalent days in local settings, exercise overrides, backups and plan files; zero stays disabled. Newly exported settings use day fields.

Every exercise has **Progress Warnings** in its details. It follows global thresholds by default. Turn off **Follow global warning settings** to customize thresholds or disable warnings for that exercise. There is no per-exercise Volume/1RM choice. Custom thresholds survive plan duplication, deletion undo, and JSON backup/restore. Legacy metric fields remain readable for compatibility but do not affect warnings or exercise display overrides.

### Chart metric and formula

The **Use strength score instead of volume** switch in **Menu** controls weight-exercise charts and entry summaries only. Its outlined label and the exercise-chart metric button open an explanation. Volume is total weight times reps. Level/watts use estimated intensity for a 10-minute effort: `intensity * (minutes / 10)^0.06`. This fixed Riegel-style heuristic uses the best individual entry, with **Est. 10-min [Lv.]** and **Est. 10-min [W]** labels. Level comparisons assume a linear scale on the same machine.

Strength score is the average estimated one-rep maximum over every valid set of a training day, using Epley, `weight * (1 + reps / 30)` (a single rep is its own load). Every set counts, extra reps are rewarded, and one set more or fewer at the same performance leaves it unchanged; see [strength-score.md](strength-score.md). Entry summaries use their own sets; charts, warnings, and the strength explorer use all sets from the training day.

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
