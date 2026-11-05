#!/usr/bin/env python3
"""Restore a verified NAS continuation package to a new directory, offline by default."""
import argparse,hashlib,json,pathlib,shutil,subprocess

ROOT=pathlib.Path(__file__).resolve().parents[1]

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as file:
        for chunk in iter(lambda:file.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination',nargs='?',help='A new, nonexistent project directory')
    parser.add_argument('--from-github',action='store_true',help='Clone public GitHub source instead of the offline Git bundle')
    parser.add_argument('--verify-only',action='store_true',help='Verify package checksums without restoring files')
    args=parser.parse_args()
    file=ROOT/'_backup/checksums.json'
    if not file.is_file():parser.error('Run this script from the extracted private NAS continuation package.')
    for name,expected in json.loads(file.read_text(encoding='utf-8')).items():
        relative=pathlib.PurePosixPath(name)
        if relative.is_absolute() or '..' in relative.parts:raise SystemExit('Invalid backup path')
        path=ROOT.joinpath(*relative.parts)
        if not path.is_file() or path.is_symlink() or path.stat().st_size!=expected['size'] or digest(path)!=expected['sha256']:raise SystemExit('Backup checksum mismatch: '+name)
    print('NAS continuation checksums verified.',flush=True)
    if args.verify_only:return
    if not args.destination:parser.error('Specify a new project directory.')
    destination=pathlib.Path(args.destination).expanduser().resolve()
    if destination.exists():parser.error('Destination already exists; choose a new directory.')
    info=json.loads((ROOT/'_backup/repository.json').read_text(encoding='utf-8'))
    source=info['github'] if args.from_github else str(ROOT/'source/jiaju.git.bundle')
    destination.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run(['git','clone','--no-checkout',source,str(destination)],check=True)
    subprocess.run(['git','-C',str(destination),'checkout','-B','main',info['head']],check=True)
    subprocess.run(['git','-C',str(destination),'remote','set-url','origin',info['github']],check=True)
    for folder in ['private-signing','local','_backup']:shutil.copytree(ROOT/folder,destination/folder)
    shutil.copy2(ROOT/'CONTINUATION-NAS.txt',destination/'CONTINUATION-NAS.txt')
    exclude=destination/'.git/info/exclude'
    with exclude.open('a',encoding='utf-8') as file:file.write('\n# Private NAS continuation material\nprivate-signing/\nlocal/\n_backup/\nCONTINUATION-NAS.txt\n')
    private=destination/'private-signing';private.chmod(0o700)
    for path in private.iterdir():path.chmod(0o600)
    release=destination/'.local/saved-release';release.mkdir(parents=True,exist_ok=True)
    artifact=ROOT/info['apk']['path']
    shutil.copy2(artifact,release/artifact.name)
    if subprocess.check_output(['git','-C',str(destination),'rev-parse','HEAD'],text=True).strip()!=info['head']:raise SystemExit('Restored revision mismatch')
    print('Restored source and private signing material:',destination)
    print('Edit local/build-environment.json, then run python3 tools/Build-JiaJu.py --check')
    print('Build with python3 tools/Build-JiaJu.py; output is in dist/. GitHub authentication is not included.')

if __name__=='__main__':main()
