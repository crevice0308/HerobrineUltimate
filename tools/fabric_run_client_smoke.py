#!/usr/bin/env python3
"""Launch the production Fabric JAR and a separate real-client UI test mod."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import zipfile

from fabric_manual_build import ROOT, jar_directory, run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb-root', required=True, type=Path, help='Root containing usr/bin/Xvfb and its local libraries')
    parser.add_argument('--display', type=int, default=107)
    parser.add_argument('--test-world', default='HerobrineUltimate-Fabric-' + str(int(time.time())))
    args = parser.parse_args()
    cache = Path.home() / '.gradle/caches'
    toolchain = ROOT.parent / 'toolchain'
    build = ROOT / 'fabric/build/manual'
    output = ROOT / 'fabric/build/clienttest'
    output.mkdir(parents=True, exist_ok=True)
    vanilla = build / 'minecraft-mojmap.jar'
    mappings = build / 'minecraft-1.20.1.tiny'
    modules = cache / 'modules-2/files-2.1'
    remapper_deps = []
    for pattern in ('net.fabricmc/tiny-remapper/0.10.3/*/*.jar', 'net.fabricmc/mapping-io/0.5.1/*/*.jar', 'org.ow2.asm/*/9.6/*/*.jar'):
        remapper_deps.extend(sorted(modules.glob(pattern)))
    remapper_cp = os.pathsep.join(map(str, remapper_deps))
    deps = sorted((toolchain / 'deps').glob('*.jar'))
    source = output / 'src/com/yutadigital/herobrine/client/ClientSmokeTest.java'
    source.parent.mkdir(parents=True, exist_ok=True)
    text = (ROOT / 'tests/client/ClientSmokeTest.java').read_text()
    # Reflection strings refer to runtime field names. Get these two vanilla
    # fields from the same genuine joined mapping table used for production.
    fields = {}
    owner = None
    for line in mappings.read_text().splitlines():
        parts = line.split('\t')
        if parts[0] == 'c':
            owner = parts[3]
        elif parts[:2] == ['', 'f']:
            fields[(owner, parts[5])] = parts[4]
    for owner, name in [('net/minecraft/client/renderer/entity/LivingEntityRenderer', 'layers'), ('net/minecraft/client/gui/components/AbstractSliderButton', 'canChangeValue')]:
        text = text.replace('"' + name + '"', '"' + fields[(owner, name)] + '"')
    source.write_text(text)
    classes = output / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    cp = os.pathsep.join(map(str, [vanilla, build / 'classes', build / 'fabric-api-mojmap.jar', toolchain / 'fabric-deps/fabric-loader.jar', *deps]))
    refmap = classes / 'herobrineultimate-clienttest.refmap.json'
    sources = [source, *sorted((ROOT / 'fabric/src/clientTest/java').rglob('*.java'))]
    run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-encoding', 'UTF-8', '-classpath', cp,
         '-processorpath', cp + os.pathsep + remapper_cp, '-AreobfSrgFile=' + str(mappings.with_suffix('.srg')),
         '-AdefaultObfuscationEnv=searge', '-AoutRefMapFile=' + str(refmap), '-d', classes, *sources])
    data = json.loads(refmap.read_text())
    data['data'] = {'named:intermediary': data['mappings']}
    refmap.write_text(json.dumps(data, indent=2) + '\n')
    metadata = {'schemaVersion': 1, 'id': 'herobrineultimate_clienttest', 'version': '1.0.0', 'name': 'Herobrine Ultimate Client Test Fixtures', 'environment': 'client', 'entrypoints': {'client': ['com.yutadigital.herobrine.fabric.ClientSmokeBootstrap']}, 'mixins': ['herobrineultimate.clienttest.mixins.json'], 'depends': {'herobrineultimate': '*', 'fabric-api': '*'}}
    mixins = {'required': True, 'package': 'com.yutadigital.herobrine.fabric.mixin', 'compatibilityLevel': 'JAVA_17', 'refmap': refmap.name, 'client': ['ClientRenderTestMixin'], 'injectors': {'defaultRequire': 1}}
    (classes / 'fabric.mod.json').write_text(json.dumps(metadata, indent=2) + '\n')
    (classes / 'herobrineultimate.clienttest.mixins.json').write_text(json.dumps(mixins, indent=2) + '\n')
    named = output / 'clienttest-mojmap.jar'
    jar_directory(classes, named)
    remapped = output / 'herobrineultimate-clienttest.jar'
    run(['java', '-Xmx1G', '-cp', remapper_cp, 'net.fabricmc.tinyremapper.Main', named, remapped, mappings, 'mojmap', 'intermediary', vanilla, build / 'herobrineultimate-mojmap.jar', build / 'fabric-api-mojmap.jar', *deps])
    runtime = output / 'run'
    mods = runtime / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    for item in mods.glob('*.jar'):
        item.unlink()
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)
    for item in (ROOT / 'fabric/build/libs' / ('herobrineultimate-fabric-' + properties['mod_version'] + '.jar'), remapped, toolchain / 'fabric-deps/fabric-api.jar'):
        shutil.copy2(item, mods)
    (runtime / 'options.txt').write_text('version:3465\nlang:en_us\nonboardAccessibility:false\nfullscreen:false\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nguiScale:2\n')
    libsfile = cache / 'forge_gradle/mcp_repo/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/joined/listLibraries/libraries.txt'
    all_libraries = [Path(line[3:]) for line in libsfile.read_text().splitlines() if line.startswith('-e=')]
    libraries = [item for item in all_libraries if 'natives-' not in str(item)]
    natives = output / 'natives'
    natives.mkdir(exist_ok=True)
    for item in all_libraries:
        if item.name.endswith('-natives-linux.jar'):
            with zipfile.ZipFile(item) as archive:
                for name in archive.namelist():
                    if name.endswith('.so'):
                        (natives / Path(name).name).write_bytes(archive.read(name))
    asm = sorted(modules.glob('org.ow2.asm/*/9.6/*/*.jar'))
    game = cache / 'forge_gradle/minecraft_repo/versions/1.20.1/client.jar'
    cp = os.pathsep.join(map(str, [toolchain / 'fabric-deps/fabric-loader.jar', toolchain / 'fabric-deps/intermediary.jar', toolchain / 'deps/sponge-mixin-0.15.5+mixin.0.8.7.jar', game, *asm, *libraries]))
    reportdir = ROOT / 'docs/runtime-client-fabric'
    reportdir.mkdir(parents=True, exist_ok=True)
    for screenshot in reportdir.glob('*.png'):
        screenshot.unlink()
    resultfile = reportdir / 'client-smoke-results.json'
    if resultfile.exists():
        resultfile.unlink()
    env = dict(os.environ)
    xvfb_root = args.xvfb_root.resolve()
    env.update({'DISPLAY': '127.0.0.1:' + str(args.display), 'LIBGL_ALWAYS_SOFTWARE': '1', 'ALSOFT_DRIVERS': 'null',
                'LD_LIBRARY_PATH': str(xvfb_root / 'usr/lib/x86_64-linux-gnu') + os.pathsep + env.get('LD_LIBRARY_PATH', ''),
                'XKB_BIN_DIRECTORY': str(xvfb_root / 'usr/bin')})
    with (reportdir / 'xvfb.log').open('w') as log:
        xvfb = subprocess.Popen([str(xvfb_root / 'usr/bin/Xvfb'), ':' + str(args.display), '-screen', '0', '1920x1080x24', '-nolisten', 'unix', '-listen', 'tcp', '-ac', '-fp', str(xvfb_root / 'usr/share/fonts/X11/misc')], stdout=log, stderr=subprocess.STDOUT, env=env)
    try:
        for _ in range(30):
            ready = subprocess.run([str(xvfb_root / 'usr/bin/xdotool'), 'getdisplaygeometry'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
            if ready.returncode == 0:
                break
            time.sleep(.1)
        else:
            raise RuntimeError('Xvfb did not become ready; inspect ' + str(reportdir / 'xvfb.log'))
        command = ['java', '-Xmx3G', '-Dorg.lwjgl.librarypath=' + str(natives), '-Dfabric.gameVersion=1.20.1', '-Dfabric.log.disableAnsi=true', '-Dherobrineultimate.testOut=' + str(reportdir), '-Dherobrineultimate.testWorld=' + args.test_world, '-Dmixin.debug.export=true', '-cp', cp, 'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', 'FabricTest', '--uuid', '00000000000000000000000000000107', '--accessToken', '0', '--version', '1.20.1', '--gameDir', str(runtime), '--assetsDir', str(cache / 'forge_gradle/assets'), '--assetIndex', '5', '--width', '1280', '--height', '720']
        with (reportdir / 'client.log').open('w') as log:
            result = subprocess.run(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, timeout=240, env=env)
        print('Fabric real client exit:', result.returncode)
    finally:
        xvfb.terminate()
        xvfb.wait(timeout=10)
    if not resultfile.exists():
        raise RuntimeError('Real client did not write its result; inspect ' + str(reportdir / 'client.log'))
    report = json.loads(resultfile.read_text())
    print(json.dumps(report, indent=2))
    sys.exit(1 if report['failed'] else 0)


if __name__ == '__main__':
    main()
