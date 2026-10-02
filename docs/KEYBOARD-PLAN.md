# TileShell keyboard — phases

Metro-style input method (`:feature:keyboard`), built from the "Metro Keyboard for
Android" design canvas (Build spec + `Kb` component). Optional: the
`tileshell.keyboard` switch in `gradle.properties` decides per release whether the
module is in the build at all (off = no keyboard code, no IME service, no
personalize row).

Shared with TileShell: the accent (`accentId`) and the theme (follow system /
dark / light). The canvas's own 20-colour Metro grid is not used.

1. **Basic keyboard** — spec geometry (unit = (w − 8 − 9×6) ÷ 10, 50dp keys, 6/8dp
   gaps, row 2 inset ½ unit), accent press fill 140 ms, shift once / caps lock,
   auto-capital per the field's flags, &123 with two symbol pages, backspace repeat,
   double-space ". ", enter follows the field's action, number fields open on
   digits, basic emoji grid, keyboard haptic, personalize → system row. ✅ built
2. **Suggestions & autocorrect** — strip states, bundled English word list
   (AOSP en_GB, Apache 2.0, + a short Indian English list), on-device learning,
   autocorrect with backspace undo, levels, password fields hide the strip;
   keyboard settings page (Lumia switches). ✅ built
   *Phase 3 note:* AOSP has no Marathi/Hindi lists — another licensed source needed.
3. **Marathi / Hindi** — transliteration (rule table → candidates, ranked by
   Marathi/Hindi word lists from Tatoeba, CC-BY 2.0 FR; learns picks; offline only);
   a globe key (beside emoji) switches languages. ✅ built (transliteration only)
   Devanagari (InScript) key layout added later at the user's request, switchable.
   The space bar's sideways drag is left free for phase 4's cursor control.
4. **Gestures** — long-press accents + top-row digit hints, space-bar cursor drag,
   swipe typing with accent trail, haptic strength. ✅ built
5. **Panels** — full emoji (categories, recents, search), clipboard (1 h, pin),
   number/phone keypad, one-handed mode. ✅ built
6. **Voice** — SpeechRecognizer, microphone permission, privacy policy. ✅ built
   (brought back by the user the same day; privacy policy / Data safety still to do).
7. **Release** — privacy policy / Data safety (no keystrokes leave the phone),
   TalkBack, about/guide, performance, on-device pass, release decision on the switch.
