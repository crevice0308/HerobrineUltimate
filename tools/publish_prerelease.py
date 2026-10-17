#!/usr/bin/env python3
"""Publish immutable prereleases; run only in the approved release job."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


def gh(*args, check=True):
    result = subprocess.run(['gh', *args], capture_output=True, text=True)
    if check and result.returncode:
        raise RuntimeError('GitHub CLI failed: ' + result.stderr.strip())
    return result


def publish(tag, commit, directory, notes):
    # An existing tag/release is never moved, overwritten or promoted to stable.
    result = gh('release', 'view', tag, '--json', 'isDraft,isPrerelease', check=False)
    if result.returncode == 0:
        existing = json.loads(result.stdout)
        if existing['isDraft'] or not existing['isPrerelease']:
            raise RuntimeError('Existing release needs manual review: ' + tag)
        target = json.loads(gh('api', f'repos/{os.environ["GH_REPO"]}/git/ref/tags/{tag}').stdout)['object']
        if target['type'] != 'commit' or target['sha'] != commit:
            raise RuntimeError('Existing tag has a different source commit: ' + tag)
    else:
        gh('release', 'create', tag, *map(str, sorted(directory.iterdir())),
           '--target', commit, '--title', f'Herobrine Ultimate {tag}',
           '--notes-file', str(notes), '--draft', '--prerelease', '--latest=false')
        gh('release', 'edit', tag, '--draft=false', '--prerelease', '--latest=false')
    # Download the public assets again and compare their actual bytes.
    with tempfile.TemporaryDirectory() as temporary:
        gh('release', 'download', tag, '--dir', temporary)
        for path in directory.glob('*.jar'):
            remote = Path(temporary) / path.name
            if not remote.exists() or remote.read_bytes() != path.read_bytes():
                raise RuntimeError('Published asset mismatch: ' + path.name)
        sums = Path(temporary) / 'SHA256SUMS.txt'
        if not sums.is_file():
            raise RuntimeError('Missing published checksum manifest')
        for line in sums.read_text().splitlines():
            digest, filename = line.split(maxsplit=1)
            if hashlib.sha256((Path(temporary) / filename.strip()).read_bytes()).hexdigest() != digest:
                raise RuntimeError('Published checksum mismatch')
    print('Verified published assets:', tag, flush=True)


def main():
    root = Path(__file__).resolve().parents[1]
    original = root / 'release/original-alpha.2'
    for line in (original / 'SHA256SUMS.txt').read_text().splitlines():
        digest, filename = line.split(maxsplit=1)
        if hashlib.sha256((original / filename.strip()).read_bytes()).hexdigest() != digest:
            raise RuntimeError('Original supplied asset was modified')
    version = next(line.split('=', 1)[1] for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    if '-alpha.' not in version:
        raise RuntimeError('Only alpha prereleases are authorized')
    current = root / 'release/current'
    current.mkdir(parents=True, exist_ok=True)
    for loader in ('forge', 'fabric'):
        filename = f'herobrineultimate-{loader}-{version}.jar'
        found = list((root / 'release-artifacts').rglob(filename))
        if len(found) != 1:
            raise RuntimeError('Expected one baseline artifact: ' + filename)
        with zipfile.ZipFile(found[0]) as jar:
            names = jar.namelist()
            if not any('refmap' in name for name in names):
                raise RuntimeError('Missing mixin refmap')
            for name in names:
                if name.startswith(('net/minecraft/', 'net/fabricmc/')) or 'gametest' in name.lower() or 'clienttest' in name.lower():
                    raise RuntimeError('Forbidden bundled dependency/test class: ' + name)
                if name.endswith('.class') and int.from_bytes(jar.read(name)[6:8], 'big') != 61:
                    raise RuntimeError('Unexpected Java class target: ' + name)
            if loader == 'fabric' and json.loads(jar.read('fabric.mod.json'))['version'] != version:
                raise RuntimeError('Fabric version mismatch')
            if loader == 'forge' and f'version="{version}"' not in jar.read('META-INF/mods.toml').decode():
                raise RuntimeError('Forge version mismatch')
        (current / filename).write_bytes(found[0].read_bytes())
    (current / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in sorted(current.glob('*.jar'))))
    (current / 'provenance.json').write_text(json.dumps({'source_commit': os.environ['GITHUB_SHA'], 'workflow_run': os.environ['GITHUB_RUN_ID'], 'version': version, 'minecraft': '1.20.1', 'java': 17, 'forge': '47.4.10', 'fabric_loader': '0.16.14', 'fabric_api': '0.92.2+1.20.1', 'runtime_tested': False}, indent=2) + '\n')
    # Historical alpha.2 has different workflows from main and cannot be
    # released by GITHUB_TOKEN. Keep its exact bytes archived, without retrying.
    head = json.loads(gh('api', f'repos/{os.environ["GH_REPO"]}/git/ref/heads/main').stdout)['object']['sha']
    if head != os.environ['GITHUB_SHA']:
        raise RuntimeError('Main advanced; do not publish an obsolete workflow target')
    publish('v' + version, os.environ['GITHUB_SHA'], current, root / 'release/alpha.3-notes.md')


if __name__ == '__main__':
    main()
