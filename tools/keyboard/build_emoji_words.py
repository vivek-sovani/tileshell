#!/usr/bin/env python3
"""Builds the keyboard's word → emoji table: typing "pizza" offers 🍕, "lol" 😂,
"love" ❤️ in the suggestion strip.

From Unicode CLDR's English emoji annotations (keywords and names,
https://github.com/unicode-org/cldr/blob/main/common/annotations/en.xml, Unicode
licence, see the assets' NOTICE), limited to the emoji in emoji.txt, plus a few
everyday chat words CLDR doesn't rank first.

Usage: build_emoji_words.py <cldr en.xml> <emoji.txt> <en_words.txt> <out.txt>
Output lines: word<TAB>emoji, sorted by word.
"""
import collections, re, sys

cldr_path, emoji_path, en_path, out_path = sys.argv[1:5]
VS16 = '️'
PER_WORD = 1
# A keyword shared by this many emoji says nothing about which one is meant.
MAX_SHARED = 12
SKIP = {'face', 'person', 'man', 'woman', 'the', 'with', 'and', 'of', 'on', 'in', 'button', 'sign',
        'symbol', 'mark', 'flag', 'hand', 'body', 'object', 'other', 'part', 'type', 'medium',
        'light', 'dark', 'skin', 'tone', 'emotion',
        # Small words: an emoji after every "this" or "for" is noise.
        'this', 'that', 'for', 'from', 'but', 'not', 'who', 'what', 'over', 'you', 'can', 'out',
        'where', 'under', 'well', 'fine', 'first', 'are', 'was', 'his', 'her', 'its', 'all',
        'any', 'some', 'into', 'than', 'then', 'them', 'they', 'there', 'these', 'those', 'will',
        'would', 'could', 'should', 'have', 'has', 'had', 'been', 'being', 'were', 'which',
        'when', 'why', 'how', 'just', 'also', 'only', 'very', 'more', 'most', 'much', 'such',
        'about', 'after', 'before', 'off', 'own', 'same', 'other', 'each', 'both', 'few'}
# A common word gets an emoji only when it's the emoji's whole name (pizza 🍕,
# school 🏫) or it's in CHAT: CLDR's keywords are loose ("small" is the shrimp's).
# A rarer word ("chestnut", "lmao") may use an exact keyword; only a rare one a
# word inside a longer name.
COMMON_FREQ, RARE_FREQ, WEAK = 120, 60, 20
# Never offered in place of a word.
NEVER = {'🖕'}

en_freq = {}
for line in open(en_path, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 2 and p[1].isdigit():
        en_freq[p[0].lower()] = max(en_freq.get(p[0].lower(), 0), int(p[1]))

# Everyday words, the emoji people mean (first) — ahead of whatever CLDR ranks.
CHAT = {
    'love': '❤️', 'happy': '😊', 'lol': '😂', 'haha': '😂', 'hahaha': '😂', 'laugh': '😂',
    'sad': '😢', 'cry': '😭', 'ok': '👌', 'okay': '👌', 'thanks': '🙏', 'thank': '🙏',
    'please': '🙏', 'namaste': '🙏', 'namaskar': '🙏', 'congrats': '🎉', 'congratulations': '🎉',
    'birthday': '🎂', 'party': '🎉', 'cool': '😎', 'yes': '👍', 'good': '👍', 'great': '👍',
    'sorry': '😔', 'angry': '😠', 'kiss': '😘', 'hug': '🤗', 'smile': '😊', 'fire': '🔥',
    'wow': '😮', 'sleep': '😴', 'sleepy': '😴', 'tea': '☕', 'chai': '☕', 'coffee': '☕',
    'hi': '👋', 'hello': '👋', 'bye': '👋', 'night': '🌙', 'morning': '🌅', 'sun': '☀️',
    'rain': '🌧️', 'cake': '🎂', 'gift': '🎁', 'music': '🎵', 'car': '🚗', 'home': '🏠',
    'money': '💰', 'cricket': '🏏', 'diwali': '🪔', 'rakhi': '🧵', 'holi': '🎨', 'flower': '🌸',
    'heart': '❤️', 'football': '⚽', 'ball': '⚽', 'bike': '🚲', 'tree': '🌳', 'book': '📖', 'moon': '🌙', 'water': '💧', 'think': '🤔', 'thinking': '🤔', 'clap': '👏', 'strong': '💪', 'done': '✅',
}

order = {}
test_names = {}
for line in open(emoji_path, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 3:
        cp = p[1].replace(VS16, '')
        order.setdefault(cp, (len(order), p[1]))
        # Flags ("flag: India") aren't in CLDR's annotations file: their own name.
        test_names.setdefault(cp, p[2].lower().replace('flag: ', ''))

pattern = re.compile(r'<annotation cp="([^"]+)"( type="tts")?>([^<]*)</annotation>')
keywords, names = {}, {}
for m in pattern.finditer(open(cldr_path, encoding='utf-8').read()):
    cp = m.group(1).replace(VS16, '')
    if cp not in order:
        continue
    if m.group(2):
        names[cp] = m.group(3).lower()
    else:
        keywords[cp] = [k.strip().lower() for k in m.group(3).split('|')]

def words(text):
    return [w for w in re.split(r"[^a-z0-9']+", text.lower().replace('’', "'")) if w]

shared = collections.Counter()
for cp, kws in keywords.items():
    for k in set(w for kw in kws for w in words(kw)):
        shared[k] += 1

scores = collections.defaultdict(dict)
for cp in order:
    name = names.get(cp) or test_names.get(cp, '')
    name_words = words(name)
    for w in set(name_words):
        # The whole name (pizza 🍕) wins; a word of a longer name (water buffalo) is weak.
        score = 100 if name == w else WEAK - 2 * len(name_words)
        scores[w][cp] = max(scores[w].get(cp, -1e9), score)
    for kw in keywords.get(cp, []):
        for w in words(kw):
            if shared[w] > MAX_SHARED:
                continue
            score = (40 if kw == w else WEAK - 5) - 0.5 * len(keywords[cp])
            scores[w][cp] = max(scores[w].get(cp, -1e9), score)

table = {}
for w, by_cp in scores.items():
    if w in SKIP or len(w) < 3 or w.isdigit():
        continue
    by_cp = {cp: sc for cp, sc in by_cp.items() if cp not in NEVER}
    freq = en_freq.get(w, 0)
    if freq >= COMMON_FREQ:
        by_cp = {cp: sc for cp, sc in by_cp.items() if sc >= 100}
    elif freq >= RARE_FREQ:
        by_cp = {cp: sc for cp, sc in by_cp.items() if sc > WEAK}
    if not by_cp:
        continue
    best = sorted(by_cp.items(), key=lambda kv: (-kv[1], order[kv[0]][0]))[:PER_WORD]
    table[w] = [order[cp][1] for cp, _ in best]
for w, e in CHAT.items():
    rest = [x for x in table.get(w, []) if x.replace(VS16, '') != e.replace(VS16, '')]
    table[w] = ([e] + rest)[:PER_WORD]

with open(out_path, 'w', encoding='utf-8') as f:
    for w in sorted(table):
        f.write(w + '\t' + ' '.join(table[w]) + '\n')
print(len(table), 'words')
