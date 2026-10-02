#!/usr/bin/env python3
"""Builds feature/keyboard/src/main/assets/keyboard/en_words.txt from AOSP
LatinIME's en_GB_wordlist.combined.gz (Apache 2.0; see the asset's NOTICE).

Usage: build_wordlist.py <en_GB_wordlist.combined.gz> <out.txt>
(Plain text: the APK compresses assets, and AGP unpacks a .gz asset anyway.)

Output lines:  word<TAB>freq[<TAB>x]   — x = valid but never suggested (offensive)
               >from<TAB>to             — correction shortcut ("dont" -> "don't")
"""
import gzip, sys

src, out = sys.argv[1], sys.argv[2]
words, shortcuts, last = [], [], None
last_real, last_freq = True, 0
for raw in gzip.open(src, 'rt', encoding='utf-8'):
    line = raw.rstrip('\n')
    if line.startswith(' word='):
        attrs = dict(p.split('=', 1) for p in line.strip().split(',') if '=' in p)
        last = attrs['word']
        flags = attrs.get('flags', '')
        last_real = not (attrs.get('not_a_word') == 'true' or 'nonword' in flags)
        last_freq = int(attrs.get('f', '0'))
        if not last_real:
            continue
        bad = 'offensive' in flags or attrs.get('possibly_offensive') == 'true'
        words.append((last, int(attrs.get('f', '0')), bad))
    elif line.startswith('  shortcut=') and last:
        target = line.strip()[len('shortcut='):].rsplit(',f=', 1)[0]
        if ' ' not in target:
            shortcuts.append((last, target, last_real, last_freq))

# A shortcut from a real word ("hid" -> "his") would replace correct typing;
# keep those only for contractions far more common than the word ("ill" -> "I'll").
freq = {}
for w, f, _ in words:
    freq[w.lower()] = max(f, freq.get(w.lower(), 0))
shortcuts = [(a, b) for a, b, real, f in shortcuts
             if not real or ("'" in b and freq.get(b.lower(), 0) >= f + 20)]

with open(out, 'w', encoding='utf-8') as f:
    for w, freq, bad in words:
        f.write(f"{w}\t{freq}\tx\n" if bad else f"{w}\t{freq}\n")
    for a, b in shortcuts:
        f.write(f">{a}\t{b}\n")
print(len(words), 'words,', len(shortcuts), 'shortcuts')
