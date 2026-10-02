#!/usr/bin/env python3
"""Builds the Marathi / Hindi word lists for transliteration ranking and swipe
typing, counting words across sentence collections (see the assets' NOTICE):
  - Tatoeba per-language exports (CC-BY 2.0 FR), tab-separated: id, lang, text
    https://downloads.tatoeba.org/exports/per_language/mar/mar_sentences.tsv.bz2
  - Mozilla Common Voice sentence collector (CC0), one sentence per line
    https://raw.githubusercontent.com/common-voice/common-voice/main/server/data/mr/sentence-collector.txt

Usage: build_indic_wordlist.py <out.txt> <source> [<source> ...]
Output: word<TAB>freq lines, freq 1..230 on a log scale of how often the word
appears, so it ranks alongside the English list's 0..255.
"""
import collections, math, re, sys

out, sources = sys.argv[1], sys.argv[2:]
WORD = re.compile(r'[\u0900-\u0963\u0971-\u097F]+')
counts = collections.Counter()
for src in sources:
    for line in open(src, encoding='utf-8'):
        parts = line.rstrip('\n').split('\t')
        text = parts[2] if len(parts) >= 3 else parts[-1]
        for w in WORD.findall(text):
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
