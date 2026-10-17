#!/usr/bin/env python3
"""Run the shared world scenarios through upstream Fabric's GameTest server.

Requires a production JAR from fabric_manual_build.py and the matching upstream
fabric-gametest-api-v1 1.2.13+1802ada577 test dependency in the toolchain cache.
All test classes/resources stay in a separate test-only mod JAR.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

from fabric_manual_build import ROOT, jar_directory, run


def main():
    cache = Path.home() / '.gradle/caches'
    toolchain = ROOT.parent / 'toolchain'
    build = ROOT / 'fabric/build/manual'
    output = ROOT / 'fabric/build/gametest'
    output.mkdir(parents=True, exist_ok=True)
    vanilla = build / 'minecraft-mojmap.jar'
    mappings = build / 'minecraft-1.20.1.tiny'
    modules = cache / 'modules-2/files-2.1'
    remapper_deps = []
    for pattern in ('net.fabricmc/tiny-remapper/0.10.3/*/*.jar', 'net.fabricmc/mapping-io/0.5.1/*/*.jar', 'org.ow2.asm/*/9.6/*/*.jar'):
        remapper_deps.extend(sorted(modules.glob(pattern)))
    remapper_cp = os.pathsep.join(map(str, remapper_deps))
    deps = sorted((toolchain / 'deps').glob('*.jar'))
    source = ROOT / 'forge/src/gametest/java/com/yutadigital/herobrine/server/HerobrineServerGameTests.java'
    text = source.read_text()
    text = '\n'.join(line for line in text.splitlines() if not line.startswith(('import net.minecraftforge.', '@GameTestHolder', '@PrefixGameTestTemplate'))) + '\n'
    text = text.replace('template = "empty", templateNamespace = "herobrineultimate"', 'template = "herobrineultimate:empty"')
    text = text.replace('Forge LivingAttackEvent', 'Fabric ServerPlayer damage mixin').replace("Forge's damage hook", 'Fabric damage hook').replace('Forge entity-load event', 'Fabric entity-load event').replace('Actual LivingDeathEvent', 'Actual Fabric LivingEntity death mixin')
    generated = output / 'src/com/yutadigital/herobrine/server/HerobrineServerGameTests.java'
    generated.parent.mkdir(parents=True, exist_ok=True)
    generated.write_text(text)
    classes = output / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    cp = os.pathsep.join(map(str, [vanilla, build / 'classes', *deps]))
    run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-classpath', cp, '-d', classes, generated])
    resource = ROOT / 'forge/src/gametest/resources'
    shutil.copytree(resource, classes, dirs_exist_ok=True)
    metadata = {'schemaVersion': 1, 'id': 'herobrineultimate_gametest', 'version': '1.0.0', 'name': 'Herobrine Ultimate GameTest Fixtures', 'environment': 'server', 'entrypoints': {'fabric-gametest': ['com.yutadigital.herobrine.server.HerobrineServerGameTests']}, 'depends': {'herobrineultimate': '*', 'fabric-gametest-api-v1': '*'}}
    (classes / 'fabric.mod.json').write_text(json.dumps(metadata, indent=2) + '\n')
    named = output / 'gametest-mojmap.jar'
    jar_directory(classes, named)
    remapped = output / 'herobrineultimate-gametest.jar'
    run(['java', '-Xmx1G', '-cp', remapper_cp, 'net.fabricmc.tinyremapper.Main', named, remapped, mappings, 'mojmap', 'intermediary', vanilla, build / 'herobrineultimate-mojmap.jar', *deps])
    runtime = output / 'run'
    mods = runtime / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    for item in mods.glob('*.jar'):
        item.unlink()
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)
    for item in (ROOT / 'fabric/build/libs' / ('herobrineultimate-fabric-' + properties['mod_version'] + '.jar'), remapped, toolchain / 'fabric-deps/fabric-api.jar', toolchain / 'fabric-deps/fabric-gametest-api-v1.jar'):
        shutil.copy2(item, mods)
    # Deliberately retain a false EULA file. The upstream GameTest module starts
    # its noninteractive automated test framework without accepting an agreement.
    (runtime / 'eula.txt').write_text('eula=false\n')
    (runtime / 'server.properties').write_text('online-mode=false\nlevel-seed=0\nmax-tick-time=60000\n')
    libsfile = cache / 'forge_gradle/mcp_repo/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/joined/listLibraries/libraries.txt'
    libraries = [Path(line[3:]) for line in libsfile.read_text().splitlines() if line.startswith('-e=') and 'natives-' not in line]
    asm = sorted(modules.glob('org.ow2.asm/*/9.6/*/*.jar'))
    game = cache / 'forge_gradle/mcp_repo/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/joined/extractServer/output.jar'
    cp = os.pathsep.join(map(str, [toolchain / 'fabric-deps/fabric-loader.jar', toolchain / 'fabric-deps/intermediary.jar', toolchain / 'deps/sponge-mixin-0.15.5+mixin.0.8.7.jar', game, *asm, *libraries]))
    report = ROOT / 'docs/gametest-fabric-results.xml'
    if report.exists():
        report.unlink()
    command = ['java', '-Xmx2G', '-Djava.awt.headless=true', '-Dfabric.gameVersion=1.20.1', '-Dfabric.log.disableAnsi=true', '-Dfabric-api.gametest', '-Dfabric-api.gametest.report-file=' + str(report), '-Dmixin.debug.export=true', '-cp', cp, 'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
    with (ROOT / 'docs/gametest-fabric.log').open('w') as log:
        result = subprocess.run(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, timeout=180)
    print('Fabric GameTest exit:', result.returncode)
    if report.exists():
        print(report.read_text())
    if (runtime / 'eula.txt').read_text().strip() != 'eula=false':
        raise RuntimeError('EULA file was unexpectedly changed')
    sys.exit(result.returncode)


if __name__ == '__main__':
    main()
