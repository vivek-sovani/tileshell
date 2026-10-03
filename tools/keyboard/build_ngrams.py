#!/usr/bin/env python3
"""Builds the keyboard's next-word tables: which words most often follow one
word, or two, counted across sentence collections (see the assets' NOTICE):
  - English: Tatoeba's English sentences (CC-BY 2.0 FR)
    https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2
  - Marathi / Hindi: Tatoeba (CC-BY 2.0 FR) and Common Voice's sentence collector (CC0),
    the same sources as build_indic_wordlist.py.

Usage: build_ngrams.py <en|mr|hi> <words.txt> <out.txt> <source> [<source> ...]
Sources are Tatoeba TSV (id, lang, text) or one sentence per line. Only words in
<words.txt> (the language's word list, never-suggest words left out) are offered.
Output lines: context<TAB>next next next…, best first. A context is one word, two
words with a space, or "^" for a sentence's start; a two-word context is kept
only where it predicts differently from its last word alone.
"""
import collections, re, sys

lang, words_path, out_path, sources = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
EN = lang == 'en'
WORD = re.compile(r"[a-z]+(?:'[a-z]+)?") if EN else re.compile(r'[ऀ-ॣॱ-ॿ]+')
SENTENCE_SPLIT = re.compile(r'[.!?।॥;:]+')
START = '^'

# Tatoeba's stock characters fill thousands of sentences; they'd be every
# "after said" guess. Never offered as a next word.
STOCK_NAMES = {'tom', 'mary', 'john', 'alice', 'bob', 'ken', 'jack', 'jim', 'sami', 'layla',
               'टॉम', 'मेरी', 'मॅरी', 'टॉमने', 'टॉमला'}

BIGRAM_FOLLOWERS, TRIGRAM_FOLLOWERS = 5, 4
MIN_CONTEXT = 5 if EN else 3
MIN_PAIR = 3 if EN else 2
MIN_TRI_CONTEXT = 12 if EN else 6
MIN_TRIPLE = 4 if EN else 3

known = set()
for line in open(words_path, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if line.startswith('>') or len(p) < 2 or (len(p) >= 3 and p[2] == 'x'):
        continue
    known.add(p[0].lower())

bi = collections.defaultdict(collections.Counter)
tri = collections.defaultdict(collections.Counter)
for src in sources:
    for line in open(src, encoding='utf-8'):
        parts = line.rstrip('\n').split('\t')
        text = parts[2] if len(parts) >= 3 else parts[-1]
        if EN:
            text = text.lower().replace('’', "'")
        for sentence in SENTENCE_SPLIT.split(text):
            # A comma breaks a phrase: no pairs across it.
            for n, phrase in enumerate(sentence.split(',')):
                tokens = WORD.findall(phrase)
                if not tokens:
                    continue
                seq = ([START] if n == 0 else []) + tokens
                for i in range(1, len(seq)):
                    bi[seq[i - 1]][seq[i]] += 1
                    if i >= 2:
                        tri[seq[i - 2] + ' ' + seq[i - 1]][seq[i]] += 1


def best(counter, n, minimum):
    out = []
    for w, c in counter.most_common():
        if c < minimum or len(out) >= n:
            break
        if w in known and w not in STOCK_NAMES and len(w) > (0 if EN and w in ('a', 'i') else 1):
            out.append(w)
    return out


lines = []
bigram_lists = {}
for ctx, counter in bi.items():
    if (ctx != START and ctx not in known) or ctx in STOCK_NAMES or sum(counter.values()) < MIN_CONTEXT:
        continue
    nxt = best(counter, BIGRAM_FOLLOWERS, MIN_PAIR)
    if nxt:
        bigram_lists[ctx] = nxt
        lines.append((sum(counter.values()), ctx, nxt))
for ctx, counter in tri.items():
    a, b = ctx.split(' ')
    if a in STOCK_NAMES or b in STOCK_NAMES or b not in bigram_lists or sum(counter.values()) < MIN_TRI_CONTEXT:
        continue
    nxt = best(counter, TRIGRAM_FOLLOWERS, MIN_TRIPLE)
    if nxt and nxt[:2] != bigram_lists[b][:2]:
        lines.append((sum(counter.values()), ctx, nxt))
lines.sort(key=lambda t: t[1])
with open(out_path, 'w', encoding='utf-8') as f:
    for _, ctx, nxt in lines:
        f.write(ctx + '\t' + ' '.join(nxt) + '\n')
print(len(bigram_lists), 'one-word contexts,', len(lines) - len(bigram_lists), 'two-word')
