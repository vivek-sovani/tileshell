#!/usr/bin/env python3
"""Builds the English-word spellings for Marathi / Hindi transliteration: typed
"energy" offers एनर्जी, "positive" पॉजिटिव (Hindi) / पॉझिटिव्ह (Marathi).

Each common English word's pronunciation (CMU Pronouncing Dictionary, BSD
licence, see the assets' NOTICE) is spelled in Devanagari several ways, each
with a cost. The spelling the language's own word list has (the way people
actually write it) wins, the most common one first; a word the list lacks gets
the cheapest spelling, so its rules follow Hindi and Marathi habits (ज़ → ज /
झ, final व → व्ह in Marathi, -ing → िंग, o-spelled vowels → ॉ).

Usage: build_loanwords.py <lang mr|hi> <cmudict.dict> <en_words.txt> <own words> <out.txt>
Output: english<TAB>devanagari[<TAB>second spelling] lines, most common English word first.
"""
import re, sys

lang, cmu_path, en_path, own_path, out_path = sys.argv[1:6]
MR = lang == 'mr'
MIN_EN_FREQ = 90
VIRAMA, ANUSVARA = '्', 'ं'
BEAM = 64
MAX_KNOWN_COST = 1.0
COST_WEIGHT = 60
SECOND_SHARE = 0.6

# Everyday words the pronouncing dictionary lacks.
EXTRA = {
    'whatsapp': ['व्हॉट्सॲप'] if MR else ['व्हाट्सएप'],
    'youtube': ['यूट्यूब'],
    'instagram': ['इंस्टाग्राम'],
    'congratulations': ['कॉंग्रॅच्युलेशन्स'] if MR else ['कॉन्ग्रैचुलेशन्स'],
    'congrats': ['कॉंग्रॅट्स'] if MR else ['कॉन्ग्रैट्स'],
    'selfie': ['सेल्फी'],
    'online': ['ऑनलाईन'] if MR else ['ऑनलाइन'],
    'offline': ['ऑफलाईन'] if MR else ['ऑफलाइन'],
    'smartphone': ['स्मार्टफोन'],
    'laptop': ['लॅपटॉप'] if MR else ['लैपटॉप'],
    'internet': ['इंटरनेट'],
    'email': ['ईमेल'],
    'okay': ['ओके'],
    'ok': ['ओके'],
}

own = {}
for line in open(own_path, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) >= 2 and p[1].isdigit():
        own[p[0]] = int(p[1])

english = []
seen = set()
for line in open(en_path, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if len(p) < 2 or not p[1].isdigit() or line.startswith('>'):
        continue
    w, f = p[0], int(p[1])
    if f >= MIN_EN_FREQ and len(w) >= 3 and re.fullmatch('[A-Za-z]+', w) and (len(p) < 3 or p[2] != 'x'):
        if w.lower() not in seen:  # India, London: typed in lower case
            seen.add(w.lower())
            english.append(w.lower())

pron = {}
for line in open(cmu_path, encoding='utf-8'):
    line = line.split('#')[0].strip()
    if not line:
        continue
    word, *phones = line.split()
    if '(' in word:  # alternative pronunciations: the first is the usual one
        continue
    pron[word] = phones

VOWELS = {'AA', 'AE', 'AH', 'AO', 'AW', 'AY', 'EH', 'ER', 'EY', 'IH', 'IY', 'OW', 'OY', 'UH', 'UW'}


def cons_options(ph, final, plural=False):
    if ph == 'Z' and plural:
        return [('स', 0), ('ज़', 0.5)]  # a plural s is written स (outcomes → आउटकम्स)
    if ph == 'Z' or ph == 'ZH':
        return [('झ', 0), ('ज', 0.4), ('ज़', 0.6)] if MR else [('ज', 0), ('ज़', 0.2)]
    if ph == 'V' and final and MR:
        return [('व्ह', 0), ('व', 0.3)]
    if ph == 'F':
        return [('फ', 0)] if MR else [('फ', 0), ('फ़', 0.3)]
    table = {
        'P': [('प', 0)], 'B': [('ब', 0)], 'T': [('ट', 0), ('त', 0.9)], 'D': [('ड', 0), ('द', 0.9)],
        'K': [('क', 0)], 'G': [('ग', 0)], 'CH': [('च', 0)], 'JH': [('ज', 0)], 'V': [('व', 0)],
        'TH': [('थ', 0)], 'DH': [('द', 0), ('ध', 0.5)], 'S': [('स', 0)], 'SH': [('श', 0)],
        'HH': [('ह', 0)], 'M': [('म', 0)], 'N': [('न', 0)], 'L': [('ल', 0)], 'R': [('र', 0)],
        'W': [('व', 0)], 'Y': [('य', 0)],
    }
    return table[ph]


def vowel_options(ph, stress, hint, final):
    """(independent, matra, cost) choices for one vowel sound."""
    if ph == 'AA':
        o = [('ऑ', 'ॉ', 0), ('आ', 'ा', 0.5)] if hint and 'o' in hint else [('आ', 'ा', 0), ('ऑ', 'ॉ', 0.6)]
    elif ph == 'AE':
        o = ([('ॲ', 'ॅ', 0), ('ऐ', 'ै', 0.5), ('ए', 'े', 0.6)] if MR
             else [('ऐ', 'ै', 0), ('ऍ', 'ॅ', 0.4), ('ए', 'े', 0.5)])
    elif ph == 'AH' and stress != '0':
        o = [('अ', '', 0), ('आ', 'ा', 0.7)]
    elif ph == 'AH':
        # Only a single spelled vowel says which (nation's "io" doesn't).
        h = hint if hint and len(hint) == 1 else 'a'
        o = {
            # Indian English says the spelled vowel: mobile → मोबाइल, police → पुलिस / पोलीस.
            'i': [('इ', 'ि', 0), ('अ', '', 0.4), ('आइ', 'ाइ', 0.4), ('आई', 'ाई', 0.4)],
            'o': [('अ', '', 0), ('ओ', 'ो', 0.3), ('उ', 'ु', 0.6)],
            'u': [('अ', '', 0), ('उ', 'ु', 0.5)],
            'e': [('अ', '', 0), ('ए', 'े', 0.4)],
        }.get(h, [('अ', '', 0), ('आ', 'ा', 0.6), ('ए', 'े', 0.4)])
        if final and h == 'a':
            o = [('आ', 'ा', 0), ('अ', '', 0.6)]  # camera → कॅमेरा, data → डेटा
        elif final:
            o = [x for x in o if x[1] != 'े']  # कैमरे is another word's form
        o = o + [('इ', 'ि', 0.8)]
    elif ph == 'AO':
        o = [('ऑ', 'ॉ', 0), ('ओ', 'ो', 0.4), ('आ', 'ा', 0.8)]
    elif ph == 'AW':
        o = [('आउ', 'ाउ', 0), ('आऊ', 'ाऊ', 0.3), ('औ', 'ौ', 0.6)]
    elif ph == 'AY':
        o = ([('आई', 'ाई', 0), ('आइ', 'ाइ', 0.2), ('ऐ', 'ै', 0.8)] if MR
             else [('आइ', 'ाइ', 0), ('आई', 'ाई', 0.2), ('ऐ', 'ै', 0.8)])
    elif ph == 'EH':
        o = [('ए', 'े', 0), ('इ', 'ि', 0.5), ('ऐ', 'ै', 0.8)]
    elif ph == 'EY':
        o = [('ए', 'े', 0)]
    elif ph == 'IH':
        o = [('इ', 'ि', 0), ('ई', 'ी', 0.5), ('ए', 'े', 0.8)]
    elif ph == 'IY':
        o = [('ई', 'ी', 0)] if final else [('ई', 'ी', 0), ('इ', 'ि', 0.4)]
    elif ph == 'OW':
        o = [('ओ', 'ो', 0), ('ऑ', 'ॉ', 0.6)]
    elif ph == 'OY':
        o = [('ऑय', 'ॉय', 0), ('ओय', 'ोय', 0.4)]
    elif ph == 'UH':
        o = [('उ', 'ु', 0), ('ऊ', 'ू', 0.5)]
    elif ph == 'UW':
        o = [('ऊ', 'ू', 0), ('उ', 'ु', 0.3)]
    else:
        raise KeyError(ph)
    return o


def vowel_hints(word, phones):
    """The spelled vowel letters behind each vowel sound, when they line up."""
    w = word
    if len(w) > 3 and w.endswith('e') and w[-2] not in 'aeiouy' and phones[-1].rstrip('012') not in VOWELS:
        w = w[:-1]  # silent final e (positive, mobile)
    groups = re.findall('(?:[aeiou]|(?<=.)y)+', w)  # y is a vowel except at the start (yes)
    count = sum(1 for p in phones if p.rstrip('012') in VOWELS)
    return groups if len(groups) == count else None


def spellings(word, phones):
    hints = vowel_hints(word, phones)
    beam = [('', 0.0, False)]  # text, cost, ends in a consonant still waiting for its vowel
    vi = 0
    n = len(phones)
    i = 0
    while i < n:
        raw = phones[i]
        ph, stress = raw.rstrip('012'), raw[len(raw.rstrip('012')):]
        nxt = phones[i + 1].rstrip('012') if i + 1 < n else None
        final = i == n - 1
        new = []
        if ph in VOWELS and ph != 'ER':
            hint = hints[vi] if hints else None
            vi += 1
            glide = ph in ('IY', 'IH') and nxt in VOWELS
            for text, cost, pending in beam:
                if glide:  # media → मीडिया, india → इंडिया
                    new.append((text + ('ि' if pending else 'इ') + 'य', cost, True))
                    continue
                for ind, mat, c in vowel_options(ph, stress, hint, final):
                    new.append((text + (mat if pending else ind), cost + c, False))
        elif ph == 'ER':
            hint = hints[vi] if hints else None
            vi += 1
            # A schwa then r: अर् / the consonant's own a, then र waiting. Before
            # another vowel the spelled one is often said (camera → कॅमेरा).
            spelled = {'e': 'े', 'i': 'ि', 'o': 'ो', 'u': 'ु', 'a': 'ा'}.get((hint or ' ')[0])
            for text, cost, pending in beam:
                new.append((text + ('' if pending else 'अ') + 'र', cost, True))
                if pending and spelled and nxt in VOWELS:
                    new.append((text + spelled + 'र', cost + 0.4, True))
        elif ph == 'NG':
            # -ing → िंग; before k / g just ं (bank → बँक/बैंक, think → थिंक).
            for text, cost, pending in beam:
                if nxt in ('K', 'G'):
                    new.append((text + ANUSVARA, cost, False))
                else:
                    new.append((text + ANUSVARA + 'ग', cost, True))
        else:
            for text, cost, pending in beam:
                for dev, c in cons_options(ph, final, plural=final and word.endswith('s') and i > 0
                                           and phones[i - 1].rstrip('012') not in VOWELS):
                    if pending:
                        prev = text[-1]
                        joined = (text + VIRAMA + dev, cost + c, True)
                        new.append(joined)
                        # n / m before another consonant is often ं (कंप्यूटर).
                        if prev in ('न', 'म') and ph not in ('Y', 'R', 'W'):
                            new.append((text[:-1] + ANUSVARA + dev, cost + c + 0.2, True))
                        new.append((text + dev, cost + c + 0.5, True))  # an unwritten a between them (ईवनिंग)
                    else:
                        new.append((text + dev, cost + c, True))
        best = {}
        for s in new:
            if s[0] not in best or s[1] < best[s[0]][1]:
                best[s[0]] = s
        beam = sorted(best.values(), key=lambda s: s[1])[:BEAM]
        i += 1
    return [(t, c) for t, c, _ in beam]


def choose(word, options, owner):
    """The usual spelling, plus a second one the word list also has (नेगेटिव / निगेटिव)."""
    # Only spellings close to the sound count, and none that is plainly another
    # English word's (data isn't देता "gives" or डेट "date"; disk isn't डेस्क).
    known = sorted(
        ((own[t] - COST_WEIGHT * c, t) for t, c in options
         if t in own and c <= MAX_KNOWN_COST and owner.get(t, word) == word),
        reverse=True,
    )
    if known:
        first = known[0][1]
        # A second spelling only when it's nearly as common (नेगेटिव / निगेटिव), not a stray one.
        second = [t for _, t in known[1:2] if own[t] >= SECOND_SHARE * own[first]]
        return [first] + second
    return [options[0][0]]


generated = {}
for w in english:
    phones = pron.get(w)
    if not phones:
        continue
    try:
        generated[w] = spellings(w, phones)
    except KeyError:
        continue
# Each word's most literal spelling belongs to it (the first, most common word wins).
owner = {}
for w, options in generated.items():
    owner.setdefault(options[0][0], w)
out = [(w, choose(w, options, owner)) for w, options in generated.items()]
out += [(w, d) for w, d in EXTRA.items() if w not in generated]
with open(out_path, 'w', encoding='utf-8') as f:
    for w, d in out:
        f.write(w + '\t' + '\t'.join(d) + '\n')
print(len(out), 'words')
