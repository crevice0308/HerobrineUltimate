#!/usr/bin/env python3
"""Offline Fabric production build with the upstream Mixin AP and TinyRemapper.

This alternative to Loom uses genuine cached 1.20.1 game artifacts and official
Mojang/Fabric mappings. It never ships Minecraft classes or dependency classes.
The normal Gradle/Loom build remains the preferred developer workflow.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from io import BytesIO

ROOT = Path(__file__).resolve().parents[1]


def run(args):
    print('+', ' '.join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), check=True)


def descriptor(java_type, classes):
    depth = 0
    while java_type.endswith('[]'):
        depth += 1
        java_type = java_type[:-2]
    primitives = dict(void='V', boolean='Z', byte='B', char='C', short='S',
                      int='I', long='J', float='F', double='D')
    typ = primitives.get(java_type)
    if typ is None:
        typ = 'L' + classes.get(java_type, java_type.replace('.', '/')) + ';'
    return '[' * depth + typ


def build_mappings(client_map, server_map, intermediary_jar, output):
    lines = client_map.read_text().splitlines() + server_map.read_text().splitlines()
    classes = {}
    for line in lines:
        if line and not line.startswith((' ', '#')) and line.endswith(':'):
            named, official = line[:-1].split(' -> ')
            classes[named] = official.replace('.', '/')
    members = {}
    owner = None
    for line in lines:
        if not line or line.startswith('#'):
            continue
        if not line.startswith(' '):
            owner = line[:-1].split(' -> ')[1].replace('.', '/')
            continue
        signature, official = line.strip().split(' -> ')
        signature = re.sub(r'^\d+:\d+:', '', signature)
        signature = re.sub(r':\d+(?::\d+)?$', '', signature)
        typ, name = signature.split(' ', 1)
        if '(' in name:
            name, arguments = name[:-1].split('(', 1)
            desc = '(' + ''.join(descriptor(arg, classes) for arg in arguments.split(',') if arg) + ')' + descriptor(typ, classes)
            kind = 'm'
        else:
            kind, desc = 'f', descriptor(typ, classes)
        members[(owner, kind, official, desc)] = name
    named_classes = {v: k.replace('.', '/') for k, v in classes.items()}
    with zipfile.ZipFile(intermediary_jar) as archive:
        tiny = archive.read('mappings/mappings.tiny').decode().splitlines()
    if tiny[0] != 'tiny\t2\t0\tofficial\tintermediary':
        raise ValueError('Unexpected intermediary namespaces')
    output_lines = ['tiny\t2\t0\tofficial\tintermediary\tmojmap']
    records = []
    official = None
    totals = {'classes': 0, 'fields': 0, 'methods': 0, 'missing_member_mojmap': 0}
    for line in tiny[1:]:
        pieces = line.split('\t')
        if pieces[0] == 'c':
            official, inter = pieces[1:3]
            named = named_classes.get(official, official)
            output_lines.append(line + '\t' + named)
            records.append(('c', official, inter, named))
            totals['classes'] += 1
        elif pieces[:2] in (['', 'f'], ['', 'm']):
            kind, desc, obf, inter = pieces[1:5]
            named = members.get((official, kind, obf, desc), obf)
            if (official, kind, obf, desc) not in members:
                totals['missing_member_mojmap'] += 1
            output_lines.append(line + '\t' + named)
            records.append((kind, official, desc, obf, inter, named))
            totals['methods' if kind == 'm' else 'fields'] += 1
    output.write_text('\n'.join(output_lines) + '\n')
    inter_classes = {r[1]: r[2] for r in records if r[0] == 'c'}

    def remap_desc(desc, table):
        return re.sub(r'L([^;]+);', lambda match: 'L' + table.get(match[1], match[1]) + ';', desc)

    srg = []
    for rec in records:
        if rec[0] == 'c':
            _, _, inter, named = rec
            srg.append(f'CL: {named} {inter}')
        else:
            kind, owner, desc, _, inter, named = rec
            left = named_classes.get(owner, owner) + '/' + named
            right = inter_classes.get(owner, owner) + '/' + inter
            if kind == 'f':
                srg.append(f'FD: {left} {right}')
            else:
                srg.append(f'MD: {left} {remap_desc(desc, named_classes)} {right} {remap_desc(desc, inter_classes)}')
    output.with_suffix('.srg').write_text('\n'.join(srg) + '\n')
    print('Mapping counts:', json.dumps(totals), flush=True)
    return totals


def jar_directory(folder, target):
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for item in sorted(folder.rglob('*')):
            if item.is_file():
                info = zipfile.ZipInfo(item.relative_to(folder).as_posix(), (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                archive.writestr(info, item.read_bytes())


def fabric_api_compile_archive(original, target):
    """Flatten genuine nested API class files into a build-only classpath JAR."""
    entries = {}
    with zipfile.ZipFile(original) as umbrella:
        for module in sorted(name for name in umbrella.namelist() if name.endswith('.jar')):
            with zipfile.ZipFile(BytesIO(umbrella.read(module))) as nested:
                for name in nested.namelist():
                    if name.endswith('.class'):
                        data = nested.read(name)
                        if name in entries and entries[name] != data:
                            raise ValueError('Conflicting Fabric API class: ' + name)
                        entries[name] = data
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            archive.writestr(name, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=Path.home() / '.gradle/caches')
    parser.add_argument('--toolchain', type=Path, default=ROOT.parent / 'toolchain')
    parser.add_argument('--output', type=Path, default=ROOT / 'fabric/build/manual')
    args = parser.parse_args()
    cache, toolchain, work = args.cache.resolve(), args.toolchain.resolve(), args.output.resolve()
    work.mkdir(parents=True, exist_ok=True)
    deps, fabric = toolchain / 'deps', toolchain / 'fabric-deps'
    libraries = sorted(deps.glob('*.jar'))
    modules = cache / 'modules-2/files-2.1'
    remapper_deps = []
    for pattern in ('net.fabricmc/tiny-remapper/0.10.3/*/*.jar', 'net.fabricmc/mapping-io/0.5.1/*/*.jar', 'org.ow2.asm/*/9.6/*/*.jar'):
        found = sorted(modules.glob(pattern))
        if not found:
            raise FileNotFoundError('Required genuine cached dependency: ' + pattern)
        remapper_deps.extend(found)
    remapper_cp = os.pathsep.join(map(str, remapper_deps))
    mc_repo = cache / 'forge_gradle/minecraft_repo/versions/1.20.1'
    game = mc_repo / 'client.jar'
    mappings = work / 'minecraft-1.20.1.tiny'
    totals = build_mappings(mc_repo / 'client_mappings.txt', mc_repo / 'server_mappings.txt', fabric / 'intermediary.jar', mappings)
    vanilla = work / 'minecraft-mojmap.jar'
    run(['java', '-Xmx2G', '-cp', remapper_cp, 'net.fabricmc.tinyremapper.Main', game, vanilla, mappings, 'official', 'mojmap', *libraries])
    api = work / 'fabric-api-mojmap.jar'
    intermediary_api = work / 'fabric-api-intermediary-compile.jar'
    fabric_api_compile_archive(fabric / 'fabric-api.jar', intermediary_api)
    run(['java', '-Xmx1G', '-cp', remapper_cp, 'net.fabricmc.tinyremapper.Main', intermediary_api, api, mappings, 'intermediary', 'mojmap', vanilla, *libraries])
    classes = work / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    refmap = classes / 'herobrineultimate.refmap.json'
    cp = os.pathsep.join(map(str, [vanilla, api, fabric / 'fabric-loader.jar', *libraries]))
    sources = sorted((ROOT / 'common/src/main/java').rglob('*.java')) + sorted((ROOT / 'fabric/src/main/java').rglob('*.java'))
    run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-encoding', 'UTF-8', '-classpath', cp,
         '-processorpath', cp + os.pathsep + remapper_cp, '-AreobfSrgFile=' + str(mappings.with_suffix('.srg')),
         '-AdefaultObfuscationEnv=searge', '-AoutRefMapFile=' + str(refmap), '-d', classes, *sources])
    generated = json.loads(refmap.read_text())
    if not generated.get('mappings'):
        raise ValueError('Mixin AP produced an empty refmap')
    # The MCP AP accepts full SRG member tables; the resulting target symbols are
    # intermediary, so publish Fabric's standard namespace label for the context.
    generated['data'] = {'named:intermediary': generated['mappings']}
    refmap.write_text(json.dumps(generated, indent=2, sort_keys=True) + '\n')
    for resources in (ROOT / 'common/src/main/resources', ROOT / 'fabric/src/main/resources'):
        shutil.copytree(resources, classes, dirs_exist_ok=True)
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)
    metadata = classes / 'fabric.mod.json'
    metadata.write_text(metadata.read_text().replace('${version}', properties['mod_version']))
    named = work / 'herobrineultimate-mojmap.jar'
    jar_directory(classes, named)
    remapped = work / 'herobrineultimate-intermediary.jar'
    run(['java', '-Xmx2G', '-cp', remapper_cp, 'net.fabricmc.tinyremapper.Main', named, remapped, mappings, 'mojmap', 'intermediary', vanilla, api, fabric / 'fabric-loader.jar', *libraries])
    # Repack deterministically; TinyRemapper preserves resources including the AP
    # refmap and leaves only this mod's classes in the output archive.
    target = ROOT / 'fabric/build/libs' / ('herobrineultimate-fabric-' + properties['mod_version'] + '.jar')
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(remapped) as archive, zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as result:
        for name in sorted(archive.namelist()):
            if name.endswith('/'):
                continue
            if name.startswith(('net/minecraft/', 'net/fabricmc/', 'org/spongepowered/')):
                raise ValueError('Unexpected embedded dependency: ' + name)
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            result.writestr(info, archive.read(name))
    (work / 'build-evidence.json').write_text(json.dumps({'minecraft': '1.20.1', 'mappings': totals, 'refmap_mixins': len(generated['mappings']), 'artifact': str(target), 'runtime_test': 'separate, see docs'}, indent=2) + '\n')
    print('Production Fabric artifact:', target, flush=True)


if __name__ == '__main__':
    main()
