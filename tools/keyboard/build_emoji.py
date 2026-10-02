#!/usr/bin/env python3
"""Builds feature/keyboard/src/main/assets/keyboard/emoji.txt from Unicode's
emoji-test.txt (https://unicode.org/Public/emoji/latest/emoji-test.txt, Unicode
terms of use). Fully-qualified emoji, no skin-tone variants, grouped into the
canvas's emoji tabs.

Output lines: tab<TAB>emoji<TAB>name
"""
import sys

TABS = {
    'Smileys & Emotion': 'smileys', 'People & Body': 'smileys',
    'Animals & Nature': 'nature', 'Food & Drink': 'food',
    'Travel & Places': 'travel', 'Activities': 'travel',
    'Objects': 'symbols', 'Symbols': 'symbols', 'Flags': 'symbols',
}
src, out = sys.argv[1], sys.argv[2]
group, rows = None, []
for line in open(src, encoding='utf-8'):
    if line.startswith('# group:'):
        group = line.split(':', 1)[1].strip()
        continue
    if not line.strip() or line.startswith('#') or '; fully-qualified' not in line:
        continue
    tab = TABS.get(group)
    if tab is None:
        continue
    comment = line.split('#', 1)[1].strip()          # "😀 E1.0 grinning face"
    emoji, _, rest = comment.partition(' ')
    name = rest.partition(' ')[2]
    if 'skin tone' in name:
        continue
    rows.append(f"{tab}\t{emoji}\t{name}\n")
open(out, 'w', encoding='utf-8').writelines(rows)
print(len(rows), 'emoji')
