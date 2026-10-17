#!/usr/bin/env python3
"""Run actual Minecraft NBT checks with a caller-supplied real mapped classpath.

HEROBRINE_TEST_CLASSPATH must include the Mojang-mapped Minecraft 1.20.1 jar
and its normal dependency jars. Wildcards such as /path/dependencies/* work.
No Minecraft game classes or dependencies are copied into the project.
"""
from pathlib import Path
import argparse
import glob
import os
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--classpath", default=os.environ.get("HEROBRINE_TEST_CLASSPATH"))
parser.add_argument("--java", default="java")
args = parser.parse_args()
if not args.classpath:
    parser.error("Set HEROBRINE_TEST_CLASSPATH or pass --classpath with the real mapped Minecraft jar and dependencies.")
paths = []
for item in args.classpath.split(os.pathsep):
    if any(char in item for char in "*?["):
        paths.extend(sorted(glob.glob(item)))
    else:
        paths.append(item)
if not paths or any(not Path(item).exists() for item in paths):
    parser.error("Classpath contains a missing path or has no matching real jars.")
classpath = os.pathsep.join(paths)
project = Path(__file__).resolve().parents[1]
sources = [
    project / "common/src/main/java/com/yutadigital/herobrine/core/Ability.java",
    project / "common/src/main/java/com/yutadigital/herobrine/core/PlayerSettings.java",
    project / "common/src/main/java/com/yutadigital/herobrine/server/HerobrineWorldData.java",
    project / "tests/HerobrineWorldDataTest.java",
]
with tempfile.TemporaryDirectory(prefix="herobrine-nbt-tests-") as output:
    subprocess.run([args.java, "com.sun.tools.javac.Main", "-encoding", "UTF-8", "-proc:none", "-d", output, "-cp", classpath, *map(str, sources)], check=True)
    subprocess.run([args.java, "-ea", "-cp", output + os.pathsep + classpath, "com.yutadigital.herobrine.server.HerobrineWorldDataTest"], check=True)
