# Comparison repair — Shraddha source pass, 2026-10-22

Baseline: `6c3f11b`. Existing 2–3 movie comparison and independent jobs were retained.

## Findings and correction

- Search-result cards navigated to Details while the nearby button selected a movie. Cards now select the intended comparison column; selected posters still open Details. Columns are labelled Movie 1/2/3 and replacement identifies its target.
- Negative initial IDs were silently discarded, restored invalid IDs could start requests, and a completed empty/loading-only flow could leave a permanent spinner. Invalid IDs now show removable/replacable errors without network calls; missing terminal responses become retryable errors.
- Detail responses were accepted without checking their movie ID. They now must match the selected entry. Independent request versions and cancellation guard removed/reselected IDs; one column's error or retry does not clear another.
- Query and replacement target were not saved. SavedStateHandle now retains them alongside selected IDs; removing the initial movie persists an empty list so restoration does not re-add it.
- Duplicate selection reports why it is rejected. Invalid replacement targets cannot silently discard a new selection. Positive unique search results retain stable lazy-list keys.

## Navigation and display

The app shell menu opens `comparison?movieId=0` with no mandatory selection. Recommendations opens the same route with an initial ID. Navigation Compose uses an integer argument with default 0; initial positive IDs load into the first column. There was no existing Compare action in Movie Details to reconnect. The app-shell Back action and Android back stack remain intact. Returning from a selected poster's Details retains the comparison ViewModel.

Details loading remains independent per movie. Retry affects only that movie. Selection, removal and replacement preserve the other columns. The existing maximum of three movies is unchanged; swapping is not introduced. Matching detail metadata takes precedence over search summaries. Overview, budget and revenue rows now use available domain metadata; budget/revenue are labelled USD and nonpositive amounts display Not available. Existing ratings, votes, release date, runtime, genres, language and popularity remain associated with their column. Popularity is never taken from the synthetic zero in MovieDetails.toMovie().

## Validation and limitations

ComparisonViewModelTest retains all original assertions and adds empty/initial/invalid entry, concurrent/out-of-order completion, cancellation, same-ID reselection, mismatch/retry, empty-flow completion, search failure/retry, replacement, persistence and duplicate selection cases. JVM fakes require no live TMDB access. See LOGIC_DEBUG_PASS.md for final gate totals.

Compose interaction, narrow-screen layout, focus/keyboard, Android back gestures, rotation and OS process-death recovery are pending physical-device acceptance. SavedStateHandle unit tests do not establish actual process-death behavior. No device was used.
