# WIP stats

Open **WIP stats** beside the existing **Stats** button. It is an independent dashboard for the active training plan. The original Stats screen remains available.

## Scope and controls

- Select an interval, All, or Custom. All totals, charts, exercise histories, records and body-region workload use the same inclusive training-day range.
- Comparisons use the immediately preceding period with the same number of calendar days. Zero or absent baselines never produce an invented percentage.
- A training day runs from 06:00 to 05:59 local time. Future-dated entries are excluded.
- The date interval, custom dates, hidden-exercise inclusion and weekly goal are remembered. Hidden exercise inclusion applies to the whole dashboard.
- Current warning badges are explicitly marked **Now**: they follow saved warning settings and today's date, independently of the historical interval.

## At a glance

The summary shows training days, recorded weight sets, median estimated 1RM change, new exercise bests, workload in tonnes of kg times reps, cardio minutes, total/average recorded span, exercises trained and recorded weight repetitions.

Recorded span is the time between a training day's first and last entries. A day with only one distinct timestamp has unknown duration and is excluded from duration averages. This is distinct from the explicitly recorded minutes on cardio entries.

The strength summary takes the median percentage change across comparable weight exercises. It does not sum 1RM kilograms across unrelated exercises or mix weight progress with cardio intensity.

## Consistency and calendar

Set a weekly goal of 1-7 training days. Goal success counts complete Monday-Sunday weeks only. Partial boundary weeks and an ongoing week are visible but excluded from the success rate.

The calendar shows up to the last 12 weeks of the range. Shading reflects recorded entry count, not physiological intensity. Tap a training date to inspect its exercises and jump into an exercise's details.

## Trends and exercise explorer

Switch the main trend chart between weekly training sessions, total weight workload per training, recorded span and cardio minutes.

Search exercises and choose Progress, Warnings, Frequency or Name:

- Weight exercise progress uses the best Wathan 1RM estimate from each training day.
- Watts progress uses peak recorded power per day.
- Level progress uses peak recorded level and reports absolute level steps rather than percentages.
- Duration progress uses total recorded minutes per day.
- The baseline is the previous period's last training when available. Otherwise it is the first of at least two trainings in the selected range.
- Frequency includes exercises with no entries in the period. Average gaps use training dates in the range; last-trained age refers to today.
- Expand an exercise to inspect its chart and workload, read the 1RM explanation or open its details. Back returns to WIP stats.

The strength explorer deliberately exposes 1RM separately from the workload-volume chart. Current warning badges still use each exercise's selected global/custom Volume or 1RM rules.

## Records, regions and context

Personal records require the period's best to exceed the exercise's best from all earlier history. First-ever entries establish a baseline, rather than being announced as records.

Body-region workload counts recorded weight sets, weighted using current profile involvement: High 1, Medium 0.5 and Low 0.25. Multiple regions can receive credit from a set. It is an estimate of logged workload, not a fatigue or recovery score.

Data context shows missing profiles, weight sets above ten reps and unknown training spans. **How measured** explains these rules inside the dashboard.

## 1RM defaults and other modes

Unconfigured settings default to 1RM, while explicitly saved choices are preserved. The menu's switch and exercise overrides remain the controls for exercise charts, summaries and warnings. Tap the exercise plot's **1RM [kg] ?** legend for an explanation.

Level/watts entries record intensity and duration, not lifting repetitions. Applying a repetition-based 1RM equation would not represent a one-repetition maximum. Their exercise plots instead label intensity times minutes as Load (Lv. min) or Work (W min), and WIP stats tracks their peak intensity and recorded minutes separately.
