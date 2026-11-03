#!/usr/bin/env python3
"""One-time build computer: bundle Sanskrit Veda collections into local SQLite.
No runtime network access. Does NOT claim complete reviewed Hindi translations.
Sanskrit source editions: Rig Śākala, Shukla Yajur Madhyandina, Sama Kauthuma,
Atharva Śaunaka. Other śākhās are not included.
"""
import json
import re
import sqlite3
import time
import urllib.request
import html
from html.parser import HTMLParser
from pathlib import Path
from collections import Counter

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'app/src/main/assets/corpus.db'
CACHE=ROOT/'source-cache'
BASE='https://raw.githubusercontent.com/bhavykhatri/DharmicData/main/'
DEV_TRANS=str.maketrans('0123456789','०१२३४५६७८९')
ASCII_TRANS=str.maketrans('०१२३४५६७८९','0123456789')
END=re.compile(r'(?:॥|।।)\s*([०-९0-9]{1,4})\s*(?:॥|।।)')
SAMA_END=re.compile(r'॥\s*([०-९0-9]{1,4})\s*$')
SAMA_LINE=re.compile(r'^.*?[०-९0-9]{2,}[a-z]\s+(.+)$')

def devan(num): return str(num).translate(DEV_TRANS)
def digits(s): return int(s.translate(ASCII_TRANS))
def download(url, cache):
    cache.parent.mkdir(parents=True,exist_ok=True)
    if cache.exists() and cache.stat().st_size>1000:
        return cache.read_bytes()
    errors=[]
    for retry in range(5):
        try:
            req=urllib.request.Request(url,headers={'User-Agent':'GK-Veda-Gyan-Research/1.0'})
            with urllib.request.urlopen(req, timeout=55) as r: data=r.read()
            if len(data)<1000: raise RuntimeError('Suspiciously small content')
            cache.write_bytes(data)
            print('Fetched',url,len(data),flush=True)
            return data
        except Exception as e:
            errors.append(str(e))
            time.sleep(retry+1)
    raise RuntimeError('Failed '+url+': '+'; '.join(errors))

def split_unit(raw):
    raw=raw.replace('\r\n','\n').strip()
    first, sep, rest=raw.partition('\n\n')
    if sep and len(first)<350 and END.search(rest):
        raw=rest
    matches=list(END.finditer(raw))
    if not matches: return [(None,raw)] if raw else []
    result=[]; last=0
    for m in matches:
        item=raw[last:m.end()].strip()
        if item: result.append((digits(m.group(1)),item))
        last=m.end()
    if raw[last:].strip():
        result.append((None,raw[last:].strip()))
    return result

class GetPre(HTMLParser):
    def __init__(self):
        super().__init__();self.depth=0;self.data=[]
    def handle_starttag(self,tag,attrs):
        if tag=='pre': self.depth+=1
    def handle_endtag(self,tag):
        if tag=='pre': self.depth=max(self.depth-1,0)
    def handle_data(self,data):
        if self.depth:self.data.append(data)

def samaveda_rows():
    url='https://sanskritdocuments.org/doc_veda/sv-kauthuma.html'
    content=download(url,CACHE/'sv-kauthuma.html').decode('utf-8','replace')
    parser=GetPre();parser.feed(content)
    data='\n'.join(parser.data) if parser.data else re.sub(r'<[^>]*>','',content)
    seq={};parts=[];misnumbered=[]
    for line in html.unescape(data).splitlines():
        match=SAMA_LINE.match(line)
        # Some source lines have stray characters before their verse label.
        # Keep any line with an actual ending number, even if that label is malformed.
        done=SAMA_END.search(line)
        if not match and not done: continue
        fragment=(match.group(1) if match else line.strip()).strip()
        parts.append(fragment)
        done=SAMA_END.search(fragment)
        if done:
            source_label=digits(done.group(1))
            n=len(seq)+1
            if source_label!=n: misnumbered.append((n,source_label))
            seq[n]='\n'.join(parts).strip();parts=[]
    print('Samaveda source numbering discrepancies:',misnumbered[:20],flush=True)
    missing=sorted(set(range(1,1876))-set(seq))
    if len(seq)!=1875 or missing:
        raise ValueError('Samaveda verse count mismatch: '+str(len(seq))+
                         ' missing: '+str(missing[:25]))
    for n in range(1,1876):
        name=('पूर्वार्चिक' if n<=585 else 'आरण्य आर्चिक' if n<=640 else 'महानाम्नी' if n<=650 else 'उत्तरार्चिक')
        yield ('sama',f'SV-{n}',f'सामवेद {devan(n)}',name,
               1 if n<=585 else 2 if n<=640 else 3 if n<=650 else 4,
               seq[n],url)

def raw_rows():
    for mandala in range(1,11):
        p=f'Rigveda/rigveda_mandala_{mandala}.json'
        rows=json.loads(download(BASE+p,CACHE/p).decode('utf-8-sig'))
        for entry in rows:
            m=entry.get('mandala',mandala)
            s=entry.get('sukta',0)
            for idx,(number,text) in enumerate(split_unit(entry.get('text','')),1):
                num=number or idx
                yield ('rig',f'RV-{m}-{s}-{num}-{idx}',f'ऋग्वेद {devan(m)}.{devan(s)}.{devan(num)}',
                       f'मण्डल {devan(m)}',int(m),text,BASE+p)
    p='Yajurveda/vajasneyi_madhyadina_samhita.json'
    rows=json.loads(download(BASE+p,CACHE/p).decode('utf-8-sig'))
    for entry in rows:
        chapter=int(entry.get('adhyaya',0))
        for idx,(number,text) in enumerate(split_unit(entry.get('text','')),1):
            num=number or idx
            yield ('yajur',f'YV-{chapter}-{num}-{idx}',f'यजुर्वेद {devan(chapter)}.{devan(num)}',
                   f'अध्याय {devan(chapter)}',chapter,text,BASE+p)
    for k in range(1,21):
        p=f'AtharvaVeda/atharvaveda_kaanda_{k}.json'
        rows=json.loads(download(BASE+p,CACHE/p).decode('utf-8-sig'))
        for entry in rows:
            ka=entry.get('kaanda',k)
            su=entry.get('sukta',0)
            for idx,(number,text) in enumerate(split_unit(entry.get('text','')),1):
                num=number or idx
                yield ('atharva',f'AV-{ka}-{su}-{num}-{idx}',f'अथर्ववेद {devan(ka)}.{devan(su)}.{devan(num)}',
                       f'काण्ड {devan(ka)}',int(ka),text,BASE+p)

HINDI={
    'RV-1-1-1-1':(
      'मैं यज्ञ के पुरोहित, देवस्वरूप ऋत्विज तथा श्रेष्ठ रत्नों के दाता अग्नि की स्तुति करता हूँ।',
      'यह ऋग्वेद का आरम्भिक मन्त्र है। अग्नि को यज्ञ का माध्यम और दिव्य तेज का प्रतीक माना गया है। यह एक संक्षिप्त अध्ययनात्मक विवेचन है, सम्पूर्ण भाष्य नहीं।'),
    'YV-1-5-5':(
      'हे अग्नि, व्रतों के रक्षक! मैं अपना संकल्प निभाऊँ, मुझे सफलता मिले और मैं असत्य से सत्य की ओर बढ़ूँ।',
      'मन्त्र में सत्यपालन और व्रत के प्रति निष्ठा का संकल्प मिलता है। यह अध्ययन हेतु संक्षिप्त आशय है।'),
    'SV-1':(
      'हे अग्नि, स्तुति के साथ इस यज्ञ में पधारें और अर्पित हवि को स्वीकार करें।',
      'सामवेद का आरम्भ अग्नि के आह्वान से होता है। इस मन्त्र का गान वैदिक साम की परम्परा से जुड़ता है।'),
}
def main():
    DEST.parent.mkdir(parents=True,exist_ok=True)
    if DEST.exists():DEST.unlink()
    db=sqlite3.connect(DEST)
    db.executescript("""
      PRAGMA journal_mode=DELETE;
      CREATE TABLE verse (
        id INTEGER PRIMARY KEY,
        veda TEXT NOT NULL,
        reference TEXT NOT NULL,
        section TEXT NOT NULL,
        section_no INTEGER NOT NULL,
        sanskrit TEXT NOT NULL,
        meaning TEXT NOT NULL DEFAULT '',
        commentary TEXT NOT NULL DEFAULT '',
        source TEXT NOT NULL
      );
      CREATE INDEX by_veda ON verse(veda,section_no,id);
    """)
    stats=Counter(); bad=0
    for v, key, reference, section, sn, sanskrit, source in list(raw_rows())+list(samaveda_rows()):
        if not sanskrit or len(sanskrit)<6:
            bad+=1;continue
        meaning,explanation=HINDI.get(key,('',''))
        db.execute('INSERT INTO verse(veda,reference,section,section_no,sanskrit,meaning,commentary,source) VALUES(?,?,?,?,?,?,?,?)',
                   (v,reference,section,sn,sanskrit,meaning,explanation,source))
        stats[v]+=1
    db.commit()
    print('Final Sanskrit mantra record counts:',dict(stats),'short rejected=',bad,flush=True)
    expected={'rig':9900,'yajur':1750,'atharva':5500,'sama':1875}
    for v,minimum in expected.items():
        if stats[v]<minimum:
            raise ValueError(f'{v}: only {stats[v]} Sanskrit records (required at least {minimum})')
    db.execute('PRAGMA integrity_check')
    db.close()
    print('Created offline DB:',DEST,DEST.stat().st_size,'bytes',flush=True)

if __name__=='__main__':main()
