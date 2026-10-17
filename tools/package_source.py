#!/usr/bin/env python3
"""Create a reproducible source archive without build/cache/runtime game bytes."""
from pathlib import Path
import zipfile,hashlib,re
root=Path(__file__).resolve().parents[1]
version=re.search(r'^mod_version=(.+)$',(root/'gradle.properties').read_text(),re.M).group(1)
out=root.parent/f'HerobrineUltimate-{version}-source.zip'
exclude={'.gradle','build','run','toolchain','.git','__pycache__'}
paths=sorted(p for p in root.rglob('*') if p.is_file() and not exclude.intersection(p.relative_to(root).parts) and p.suffix not in {'.class'})
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in paths:
        info=zipfile.ZipInfo('HerobrineUltimate/'+p.relative_to(root).as_posix(),(2026,10,17,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        info.external_attr=(0o100755 if p.name=='gradlew' or p.suffix=='.sh' else 0o100644)<<16
        z.writestr(info,p.read_bytes())
print(out)
print(hashlib.sha256(out.read_bytes()).hexdigest())
print(len(paths),'files',out.stat().st_size,'bytes')
