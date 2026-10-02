#!/usr/bin/env python3
"""Builds the Marathi / Hindi word lists for transliteration ranking from
Tatoeba's per-language sentence exports (CC-BY 2.0 FR; see the assets' NOTICE).

Usage: build_indic_wordlist.py <xxx_sentences.tsv> <out.txt>
  (download: https://downloads.tatoeba.org/exports/per_language/mar/mar_sentences.tsv.bz2)

Output: word<TAB>freq lines, freq 1..230 on a log scale of how often the word
appears, so it ranks alongside the English list's 0..255.
"""
import collections, math, re, sys

src, out = sys.argv[1], sys.argv[2]
WORD = re.compile(r'[ऀ-ॣॱ-ॿ]+')
counts = collections.Counter()
for line in open(src, encoding='utf-8'):
    parts = line.rstrip('\n').split('\t')
    if len(parts) < 3:
        continue
    for w in WORD.findall(parts[2]):
        # Tatoeba's stock character "Tom" is in thousands of sentences.
        if w.startswith('टॉम'):
            continue
        counts[w] += 1
top = max(counts.values())
with open(out, 'w', encoding='utf-8') as f:
    for w, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        freq = max(1, round(230 * math.log(n + 1) / math.log(top + 1)))
        f.write(f"{w}\t{freq}\n")
print(len(counts), 'words')
