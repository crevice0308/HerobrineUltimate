#!/usr/bin/env python3
"""Exercise the Fabric packet decoder with actual vanilla NBT and Netty."""
import os
from pathlib import Path
from fabric_manual_build import ROOT, run

cache = Path.home() / '.gradle/caches'
build = ROOT / 'fabric/build/manual'
output = ROOT / 'fabric/build/packettest'
output.mkdir(parents=True, exist_ok=True)
libraries_file = cache / 'forge_gradle/mcp_repo/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/joined/listLibraries/libraries.txt'
libraries = [Path(line[3:]) for line in libraries_file.read_text().splitlines() if line.startswith('-e=') and 'natives-' not in line]
cp = os.pathsep.join(map(str, [build / 'minecraft-mojmap.jar', build / 'classes', ROOT.parent / 'toolchain/fabric-deps/fabric-loader.jar', *libraries]))
run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-proc:none', '-encoding', 'UTF-8', '-classpath', cp, '-d', output, ROOT / 'tests/fabric/FabricPacketDecodeTest.java'])
run(['java', '-ea', '-classpath', str(output) + os.pathsep + cp, 'com.yutadigital.herobrine.fabric.FabricPacketDecodeTest'])
