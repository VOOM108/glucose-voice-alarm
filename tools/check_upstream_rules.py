#!/usr/bin/env python3
"""SPDX-License-Identifier: GPL-3.0-or-later
Verify the inventoried xDrip method bodies without a private/upstream checkout.
"""
import hashlib,json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
inventory=json.loads((root/'companionProbe/UPSTREAM_ALARM_IMPORT.json').read_text(encoding='utf-8'))
base=root/'companionProbe/src/main/java/de/giesen/camaps/companion/prototype/alarm/xdrip'

def body(source,signature):
    start=source.index(signature)
    if source.find(signature,start+1)>=0:raise ValueError('Ambiguous method: '+signature)
    opening=source.index('{',start)
    depth=1;index=opening+1;state='code'
    while depth and index<len(source):
        char=source[index];following=source[index:index+2]
        if state=='line':
            if char=='\n':state='code'
        elif state=='comment':
            if following=='*/':state='code';index+=1
        elif state in ('string','character'):
            if char=='\\':index+=1
            elif char==('"' if state=='string' else "'"):state='code'
        elif following=='//':state='line';index+=1
        elif following=='/*':state='comment';index+=1
        elif char=='"':state='string'
        elif char=="'":state='character'
        elif char=='{':depth+=1
        elif char=='}':depth-=1
        index+=1
    if depth:raise ValueError('Unclosed method: '+signature)
    return source[start:index]

count=0
for entry in inventory['files']:
    source=(base/(entry['class']+'.java')).read_text(encoding='utf-8')
    for method in entry['methods']:
        value=body(source,method['signature'])
        digest=hashlib.sha256(value.encode('utf-8')).hexdigest()
        if digest!=method['sha256']:raise SystemExit('Imported method changed: '+entry['class']+' / '+method['signature'])
        count+=1
if count!=23:raise SystemExit('Unexpected imported-method count')
print('Verified 23 verbatim xDrip method bodies from '+inventory['upstream_commit'])
