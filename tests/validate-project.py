#!/usr/bin/env python3
"""Structural checks for metadata, localisations, source separation and assets."""
from pathlib import Path
import json,re,struct
root=Path(__file__).resolve().parents[1]
checks=0
def check(name,value):
    global checks
    if not value: raise AssertionError(name)
    checks+=1
for base in ['common','fabric']:
    for p in (root/base/'src/main/resources').rglob('*.json'):
        json.loads(p.read_text(encoding='utf-8'));check('valid JSON '+p.name,True)
for module,name in [('common','herobrineultimate.mixins.json'),('fabric','herobrineultimate.fabric.mixins.json')]:
    cfg=json.loads((root/module/'src/main/resources'/name).read_text())
    prefix=cfg['package'].replace('.','/')
    for side in ['mixins','client','server']:
        for cls in cfg.get(side,[]):
            rel=prefix+'/'+cls.replace('.','/')+'.java'
            check('mixin '+cls,any((root/base/'src/main/java'/rel).is_file() for base in ['common','fabric']))
    if module=='common':
        check('server display mixin registered','PlayerDisplayNameMixin' in cfg.get('mixins',[]))
        check('client mixins scoped to client',not any('client.' in c for c in cfg.get('mixins',[])))
lang=root/'common/src/main/resources/assets/herobrineultimate/lang'
en=json.loads((lang/'en_us.json').read_text());ja=json.loads((lang/'ja_jp.json').read_text())
check('JP/EN translations same keys',set(en)==set(ja))
for p in (root/'common/src/main/java/com/yutadigital/herobrine').rglob('*.java'):
    if '/server/' in p.as_posix() or '/core/' in p.as_posix():
        check('dedicated server source has no client reference '+p.name,'net.minecraft.client' not in p.read_text())
for name in ['herobrine.png','herobrine_eyes.png']:
    data=(root/'common/src/main/resources/assets/herobrineultimate/textures/entity'/name).read_bytes()
    check('PNG signature '+name,data[:8]==b'\x89PNG\r\n\x1a\n')
    check('64x64 skin '+name,struct.unpack('>II',data[16:24])==(64,64))
props=(root/'gradle/wrapper/gradle-wrapper.properties').read_text()
check('pinned Gradle SHA256',bool(re.search(r'distributionSha256Sum=[a-f0-9]{64}\n',props)))
meta=json.loads((root/'fabric/src/main/resources/fabric.mod.json').read_text())
check('MIT metadata',meta['license']=='MIT')
check('exact MC target',meta['depends']['minecraft']=='1.20.1')
check('Fabric API dependency declared','fabric-api' in meta['depends'])
check('Java17 required',meta['depends']['java']=='>=17')
check('source has no vendored game binaries',not list((root/'common').rglob('*.jar')))
check('wrapper exists',(root/'gradle/wrapper/gradle-wrapper.jar').is_file())
check('README exists',(root/'README.md').is_file())
print(f'PASS: {checks} project structure checks')

forge=(root/'forge/src/main/java/com/yutadigital/herobrine/forge/HerobrineForge.java').read_text()
check('Forge reload listener registered','addListener(this::start)' in forge)
check('Forge configuration loads on each server start',bool(re.search(r'private void start\(ServerStartingEvent event\)\s*\{\s*HerobrineServer.init',forge)))
check('Forge constructor does not load server config','HerobrineServer.init' not in forge.split('private void tick',1)[0])
print('PASS: Forge lifecycle wiring checks (static; runtime reopen not exercised)')
