#!/usr/bin/env python3
"""Builds the Marathi / Hindi word lists for transliteration ranking and swipe
typing, counting words across sentence collections (see the assets' NOTICE):
  - Tatoeba per-language exports (CC-BY 2.0 FR), tab-separated: id, lang, text
    https://downloads.tatoeba.org/exports/per_language/mar/mar_sentences.tsv.bz2
  - Mozilla Common Voice sentence collector (CC0), one sentence per line
    https://raw.githubusercontent.com/common-voice/common-voice/main/server/data/mr/sentence-collector.txt
and, with --wiki, the language's Wikipedia (CC BY-SA 4.0) for the many words
everyday sentences never use (दिनचर्या, क्षेत्रफळ), ranked below the everyday ones:
    https://dumps.wikimedia.org/mrwiki/latest/mrwiki-latest-pages-articles.xml.bz2

Usage: build_indic_wordlist.py <out.txt> <source> [<source> ...] [--wiki <dump.xml.bz2>]
Output: word<TAB>freq lines, freq 1..230 on a log scale of how often the word
appears, so it ranks alongside the English list's 0..255.

Marathi's eyelash ra is written two ways: र + ् + zero-width joiner (Tatoeba's
दुसर्‍या) and ऱ + ् (दुसऱ्या). Both become the second; the joiner used to split
the word in two.
"""
import bz2, collections, math, re, sys

args = sys.argv[1:]
wiki = None
if '--wiki' in args:
    i = args.index('--wiki')
    wiki = args[i + 1]
    del args[i:i + 2]
out, sources = args[0], args[1:]

ZWJ, ZWNJ = '‍', '‌'
WORD = re.compile('[ऀ-ॣॱ-ॿ‌‍]+')
# Everyday sentences rank up to 230; a word only Wikipedia has, up to this.
WIKI_MAX = 150
# A Wikipedia word must appear this often (fewer is mostly typos and names).
WIKI_MIN_COUNT = 5
# Wikipedia adds at most this many words.
WIKI_MAX_WORDS = 120_000


def normalize(w):
    w = w.replace('र्' + ZWJ, 'ऱ्')
    return w.replace(ZWJ, '').replace(ZWNJ, '').strip('्')


def words(text):
    for raw in WORD.findall(text):
        w = normalize(raw)
        if len(w) >= 1:
            yield w


counts = collections.Counter()
for src in sources:
    for line in open(src, encoding='utf-8'):
        parts = line.rstrip('\n').split('\t')
        text = parts[2] if len(parts) >= 3 else parts[-1]
        for w in words(text):
            # Tatoeba's stock character "Tom" is in thousands of sentences.
            if w.startswith('टॉम'):
                continue
            counts[w] += 1


def scale(n, top, ceiling):
    return max(1, round(ceiling * math.log(n + 1) / math.log(top + 1)))


top = max(counts.values())
freq = {w: scale(n, top, 230) for w, n in counts.items()}

if wiki:
    wcounts = collections.Counter()
    in_text = False
    with bz2.open(wiki, 'rt', encoding='utf-8') as f:
        for line in f:
            # Only article text, not titles, ids or other metadata.
            if '<text' in line:
                in_text = True
            if in_text:
                wcounts.update(w for w in words(line) if len(w) >= 2)
            if '</text>' in line:
                in_text = False
    wtop = max(wcounts.values())
    added = 0
    for w, n in wcounts.most_common():
        if n < WIKI_MIN_COUNT or added >= WIKI_MAX_WORDS:
            break
        f_wiki = scale(n, wtop, WIKI_MAX)
        if w not in freq:
            added += 1
            freq[w] = f_wiki
        else:
            freq[w] = max(freq[w], f_wiki)
    print(added, 'words from Wikipedia')

with open(out, 'w', encoding='utf-8') as f:
    for w, n in sorted(freq.items(), key=lambda kv: (-kv[1], kv[0])):
        f.write(f"{w}\t{n}\n")
print(len(freq), 'words')
