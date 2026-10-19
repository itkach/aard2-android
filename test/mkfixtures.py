# Writes the test dictionaries in test/resources. Needs the slob package
# (https://github.com/itkach/slob); run from this directory.

import unicodedata

import slob

HTML = 'text/html; charset=utf-8'


def nfc(s):
    return unicodedata.normalize('NFC', s)


def nfd(s):
    return unicodedata.normalize('NFD', s)


# Three versions of one dictionary (same uri), a split one, and unrelated
# ones. Keys differing only in Unicode normalization are equal at every
# collation strength below identical, so they match each other on lookup.
DICTIONARIES = {
    'movetest-v1.slob': ('test:movetest', [
        ('apple', 'Apple A (v1)'),
        ('apple', 'Apple B (v1)'),
        ('banana', 'Banana (v1)'),
        ('cherry', 'Cherry (v1)'),
        (nfc('café'), 'Café (v1)'),
        (nfc('naïve'), 'Naïve (v1)'),
    ]),
    'movetest-v2.slob': ('test:movetest', [
        ('apple', 'Apple (v2)'),
        ('banana', 'Banana (v2)'),
        (nfd('café'), 'Café, decomposed (v2)'),
        (nfd('naïve'), 'Naïve, decomposed (v2)'),
        (nfc('naïve'), 'Naïve (v2)'),
    ]),
    'movetest-v3.slob': ('test:movetest', [
        ('apple', 'Apple (v3)'),
        ('banana', 'Banana (v3)'),
    ]),
    'other.slob': ('test:other', [
        ('apple', 'Apple (other)'),
    ]),
    # A dictionary that's one file in its first version and two volumes in
    # its second, which an article can end up in either of.
    'split-v1.slob': ('test:split', [
        ('apple', 'Apple (split v1)'),
        (nfc('café'), 'Café (split v1)'),
        ('cherry', 'Cherry (split v1)'),
        ('date', 'Date (split v1)'),
    ]),
    'split-v2-1.slob': ('test:split', [
        ('apple', 'Apple (split v2, volume 1)'),
        ('banana', 'Banana (split v2, volume 1)'),
        (nfd('café'), 'Café, decomposed (split v2, volume 1)'),
    ]),
    'split-v2-2.slob': ('test:split', [
        (nfc('café'), 'Café (split v2, volume 2)'),
        ('cherry', 'Cherry (split v2, volume 2)'),
    ]),
    'blank-label.slob': ('test:blank-label', [
        ('apple', 'Apple (blank label)'),
    ]),
}

# Each dictionary's label is its file name, except for these.
LABELS = {
    'blank-label.slob': ' ',
}

for name, (uri, articles) in DICTIONARIES.items():
    with slob.create(f'resources/{name}') as w:
        w.tag('label', LABELS.get(name, name))
        w.tag('uri', uri)
        for key, text in articles:
            w.add(f'<h1>{text}</h1>'.encode('utf-8'), key, content_type=HTML)
