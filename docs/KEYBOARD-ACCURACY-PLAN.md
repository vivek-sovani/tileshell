# Keyboard accuracy plan — Windows Phone typing feel

Goal: on TileShell's own keyboard, match the Windows Phone keyboard's typing
accuracy (key placement, size, gaps, and above all the *touch handling*), and
close what is practical of the gap to Gboard. Not a goal: matching Gboard's
neural prediction or transliteration quality.

## What the module does today (read 2026-11-02)

- **Geometry** (`KeyGeometry.kt`, `KeyArea.kt`, `KeyboardScreen.kt`): keys are
  50dp high (40dp landscape), 6dp key gap, 8dp row gap, 4dp side padding, from
  the design canvas. `KeyGeometry.layout` computes every key box as a pure
  function, so hit-testing does not depend on laid-out positions.
- **Touch → key** (`KeyArea.kt` `hit`): the key whose box is *nearest* to the
  touch-down point, shifted down by a fixed `TOUCH_BIAS` = 4dp. That is all:
  pure geometry, no knowledge of what is being typed. A tap between keys goes to
  the nearest box, so there are no dead gaps, but the boundary between "r" and
  "t" is always halfway, whatever the word is.
- **Typing**: the key is chosen on touch-down, the letter is typed on lift
  (so long-press accents and swipe can take over). A new touch commits a still-held
  letter first, so two-thumb order is kept.
- **Correction** (`Suggester.kt`): edit-distance candidates scored by word
  frequency minus a penalty. Neighbouring-key slips cost less, but `KeyProximity`
  is only a yes/no "adjacent on qwerty" test; the actual tap positions are thrown
  away after the key is chosen.
- **Lexicon** (`WordList.kt`, `Lexicon`: `lookup`, `completions`), next words
  (`NextWords`), swipe (`SwipeDecoder`), transliteration (`Transliterator`,
  `Romanizer`), learned words: all exist. `AccuracyEvalTest` already measures
  typed and swiped accuracy over common words with floors.

## Why Windows Phone felt accurate

Beyond key size, the WP keyboard resized each key's *invisible* touch target as
you typed, based on which letter was likely next. After "th" the "e" target grew
and the targets of unlikely neighbours shrank. The keys drawn never moved. The
module has everything needed for this except the touch model itself.

## Plan (one session each; each ends with build, tests, commit)

### K1 — Measure first (no behaviour change)
- Debug-only **typing test** screen inside the keyboard settings activity: shows
  a fixed set of sentences, records every tap (position, the key it hit, the key
  that was intended) and reports error rate, per-key offset (mean) and spread.
  Done by hand on the phone (no adb gestures there), so the user can type the
  same sentences on Gboard and on TileShell and compare errors and speed.
- Offline harness in `AccuracyEvalTest` style: generate noisy taps (Gaussian
  around key centres with a downward/inward bias) for the common words, run the
  key chooser, report the wrong-key rate. This is the number every later session
  must improve and never regress.
- Output: baseline numbers for nearest-box, written into this file.

**K1 done (2026-11-02).** `TapLog` / `TapReplay` (taps recorded only while the typing
test is open; intended key worked out by replaying the taps against the known
sentence), `TypingTestActivity` (keyboard settings → "typing test": eight
sentences, no suggestions, reports speed, deletions, and for this keyboard the
landing offset, spread and mixed-up keys), `NoisyTapEvalTest`.
Baseline, nearest-box chooser, 3000 common English words, 1080px layout:

| scenario | wrong keys | words with a wrong key |
|---|---|---|
| careful (sigma 0.15 key widths, centred) | 0.00% | 0.0% |
| normal (sigma 0.22, a little high) | 0.56% | 3.7% |
| fast (sigma 0.30, high and to the side) | 4.35% | 25.3% |

K2 has to lower the normal and fast rows and never raise the careful one. The
typing test field uses "no suggestions", which makes the keyboard treat it as a
plain field: K2's smart touch must therefore stay on in plain text fields and
be off only for password, URL, email, number and phone fields.

### K2 — Touch model: likely-next-letter hit targets (the main win)
- New pure `TouchModel`: for a tap, score each candidate key within ~1.5 key
  widths as `log N(tap | key centre + bias, sigma)` + `w * log P(letter | typed prefix)`.
- New `PrefixLetterModel`: next-letter distribution for a prefix from the active
  lexicon (English; Latin letters of the romanised Marathi / Hindi in
  transliteration mode), cached per prefix so a touch-down costs microseconds.
  Needs a prefix range query on `WordList` (check its layout; add a sum over a
  prefix range if it is a sorted array).
- Guard rails (these decide whether it feels good or infuriating):
  - a tap well inside a key's drawn box always wins (the prior only moves the
    boundaries between neighbours, within a limited band, as WP did);
  - prior weight capped, plus a smoothing floor so any letter stays typable;
  - off for password, URL, email, number and phone fields and for the symbols
    layer; on/off switch in keyboard settings ("smart touch").
- Replace `KeyGeometry.hit` use in `KeyArea.hit` for CHAR keys only; function
  keys keep plain geometry. Prefix = the word being typed (the controller
  already commits a held letter before the next touch, so the prefix is right
  for two-thumb typing).
- Unit tests: boundary shifts toward the likely letter, deep-inside taps never
  change, unlikely letters still typable, determinism. Re-run K1's harness.

**K2 done (2026-11-02).** `TouchModel` + `PrefixLetterModel` (`TouchModel.kt`),
`Lexicon.nextLetterWeights` (`WordList` scans the prefix range once, cached per
prefix), `KeyboardController.touchPriors()` (prefix from the text before the
cursor; in transliteration the English letters typed, from the romanised list),
`TypingRules.smartTouchAllowed`, setting "Smart touch" (default on). Guard rails as
planned: central 56% of a key always wins (`coreInset` 0.22), prior floored at
e^-4, function keys untouched. Parameters (sigma 0.25 key widths, weight 1) picked
by a sweep in `NoisyTapEvalTest` (`TOUCH_SWEEP=1`); results with simulated taps:

| set / scenario | nearest-box | touch model |
|---|---|---|
| common words, normal | 0.56% | 0.20% |
| common words, fast | 4.35% | 2.19% |
| rare words, normal | 0.58% | 0.22% |
| rare words, fast | 4.40% | 2.28% |
| not words (reversed), normal | 0.70% | 0.74% |
| not words (reversed), fast | 4.73% | 4.68% |
| careful, any set | 0.00% | 0.00% |

Simulated, not measured on a thumb: the real test is the typing test with the
setting on and off (keyboard settings → smart touch).

### K3 — Per-user touch calibration
- Replace the fixed 4dp bias with learned offsets, separate for the left and
  right half and per row (thumbs land differently). Learn from taps whose
  intent is known: letters kept after an accepted suggestion or autocorrect, and
  the K1 typing test. Stored on the phone only (like learned words), with a
  clear-learned option and a reset.
- Also decide, from K1 data, whether to resolve the key at lift instead of
  touch-down for taps that slide a little.

**K3 done (2026-11-02), with a change from the plan:** intent in normal typing is
unknowable, so nothing is inferred from text. `TouchCalibration` (`TouchCalibration.kt`)
keeps, for eight zones (left / right half × four rows), the running mean in dp of
where touches landed relative to the centre of the key they went to, shrunk
towards the old fixed offset (4 dp above) by 40 pseudo-touches, limited to 10 dp,
window of ~600 touches. `TapLearner` feeds it: a letter touch counts once the
next key shows it was not deleted at once; a letter deleted at once and followed
by a neighbouring key is firm evidence for that neighbour (weight 3). Applied in
`KeyArea.hit` before the touch model. Only numbers are stored (own prefs file
`keyboard_touch`, saved every 25 touches and on close), nothing in password
fields; "Touch calibration" in keyboard settings shows the learned offset, a
switch and a reset. Simulated grip the default does not suit (dx +0.12, dy -0.28
key widths): the learned offset came out 3.7 / -8.5 dp against the true 3.7 / -8.6,
and wrong keys fell from 1.07% to 0.59%; for the three earlier scenarios learning
is never worse (`NoisyTapEvalTest`, `TouchCalibrationTest`). Resolving the key at
lift instead of touch-down is left until the typing test shows whether it is needed.

### K4 — Spatial autocorrect (closes most of the Gboard gap we can close)
- Keep the tap points of the word being typed. Score correction candidates by
  the sum over letters of the touch likelihood plus word frequency, instead of
  the yes/no `KeyProximity` penalty. A word typed with a tap that was nearly on
  the neighbouring key becomes a strong candidate; a clean tap is trusted.
- Tune against K1's harness with the existing accuracy floors.

### K5 — Geometry and feel
- Settings for key height scale, key gap and side padding; try the WP-like
  values (tight gaps, full-width keys) and compare in the typing test. Because
  hit targets fill the gaps, gap size is cosmetic. Key height is what matters
  for accuracy.
- Check landscape, one-handed mode and the Devanagari 11-key rows (narrower keys
  make the touch model matter more there).

## Not in this plan
- A neural prediction model or Gboard-style transliteration ranking.
- Shipping: the keyboard is still excluded from release builds
  (`tileshell.keyboard=false`). Turning it back on is a separate decision after
  K1–K4 numbers are in.

## Decision (2026-11-02)
Stay on TileShell's own keyboard: its Marathi / Hindi transliteration, next-word
lists and learned words are not given up. Forking another keyboard is off the
table. If K2–K4 do not close the gap on the typing test, the answer is more
work on prediction in this code (word lists, ranking), not a different base.
