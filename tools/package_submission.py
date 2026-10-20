"""Create a source-only submission from Git's file inventory; never print secrets.

Run after the documented Gradle gates. Requires Python 3.10+ and Git.
"""
import argparse
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_DIRS = {'.git', '.gradle', '.kotlin', '.idea', '.vscode', '.claude',
                 'build', '.cxx', '.externalNativeBuild', '__pycache__', 'captures'}
EXCLUDED_NAMES = {'local.properties', 'signing.properties', 'secrets.properties',
                  'google-services.json', '.DS_Store', 'Thumbs.db'}
EXCLUDED_SUFFIXES = {'.apk', '.aab', '.aar', '.jks', '.keystore', '.key', '.pem',
                     '.p12', '.pfx', '.log', '.tmp', '.hprof', '.iml', '.zip', '.pyc'}
REQUIRED = {'gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar',
            'gradle/wrapper/gradle-wrapper.properties', 'gradle/libs.versions.toml',
            'settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts',
            'app/src/main/AndroidManifest.xml', '.gitignore', '.gitrules',
            'local.properties.example', 'README.md', 'docs/FINAL_INTEGRATION_REPORT.md',
            'docs/REQUIREMENTS_TRACEABILITY.md', 'docs/DEMO_AND_VIVA.md',
            'docs/FREE_MOVIE_CATALOGUE.md', 'docs/CONTRIBUTION_EVIDENCE.md',
            'docs/SAFE_SUBMISSION.md'}
TOKEN_PATTERN = re.compile(rb'AIza[0-9A-Za-z_-]{30,}|gh[pousr]_[A-Za-z0-9]{20,}|'
                           rb'github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|'
                           rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|'
                           rb'eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}')
ASSIGNMENT_PATTERN = re.compile(
    r'''(?i)(?:api[_-]?key|access[_-]?token|secret|password)\s*["']?\s*[:=]\s*["']?([\w/+=.-]{24,})''')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, stderr=subprocess.DEVNULL)


def names(*args):
    return set(git('ls-files', '-z', *args).decode('utf-8').strip('\0').split('\0')) - {''}


def excluded(name):
    p = PurePosixPath(name)
    return (p.is_absolute() or '..' in p.parts or bool(set(p.parts) & EXCLUDED_DIRS)
            or p.name in EXCLUDED_NAMES or p.suffix.lower() in EXCLUDED_SUFFIXES
            or p.name.startswith('.env'))


def placeholder(value):
    return (not value or value.lower().startswith(('your_', 'placeholder', '<', '${')))


def local_secrets():
    # Only compare in memory. Never write these values or send them to a service.
    values = []
    local = ROOT / 'local.properties'
    if local.exists():
        for line in local.read_text(encoding='utf-8').splitlines():
            if line.lstrip().startswith(('#', '!')) or '=' not in line:
                continue
            key, value = line.split('=', 1)
            if re.search(r'(?i)key|token|secret|password', key) and not placeholder(value.strip()):
                values.append(value.strip().encode())
    for key in ('TMDB_API_KEY', 'GEMINI_API_KEY'):
        value = os.environ.get(key, '')
        if not placeholder(value):
            values.append(value.encode())
    return values


def scan(name, data, secrets):
    if any(value in data for value in secrets) or TOKEN_PATTERN.search(data):
        raise ValueError(f'{name}: potential credential detected; value withheld')
    text = data.decode('utf-8', errors='replace')
    if any(not placeholder(m.group(1)) and m.group(1) not in {
            'BuildConfig.TMDB_API_KEY', 'BuildConfig.GEMINI_API_KEY'
    } for m in ASSIGNMENT_PATTERN.finditer(text)):
        raise ValueError(f'{name}: potential credential assignment; value withheld')
    if name == 'local.properties.example':
        for line in text.splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            key, sep, value = line.partition('=')
            if not sep or key not in {'TMDB_API_KEY', 'GEMINI_API_KEY'} or not placeholder(value):
                raise ValueError(f'{name}: only placeholder API-key assignments are permitted')


def source_files():
    tracked = names()
    if 'local.properties' in tracked:
        raise ValueError('local.properties: tracked configuration; stop before commit or push')
    if subprocess.run(['git', 'check-ignore', '-q', 'local.properties'], cwd=ROOT).returncode:
        raise ValueError('local.properties: ignore rule is missing')
    for name in sorted(tracked):
        if excluded(name):
            raise ValueError(f'{name}: forbidden tracked file; review before packaging')
    candidates = tracked | names('--others', '--exclude-standard')
    files = {name: ROOT / name for name in candidates if not excluded(name)}
    missing = REQUIRED - files.keys()
    if missing:
        raise ValueError('Missing required files: ' + ', '.join(sorted(missing)))
    for prefix in ('app/src/main/java/', 'app/src/main/res/', 'app/src/test/', 'app/src/androidTest/'):
        if not any(name.startswith(prefix) for name in files):
            raise ValueError('Missing required source tree: ' + prefix)
    for name, path in files.items():
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT) or not path.is_file():
            raise ValueError(f'{name}: unsupported or missing source file')
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scan-only', action='store_true')
    parser.add_argument('--output', type=Path, help='New ZIP path outside the repository')
    args = parser.parse_args()
    if not args.scan_only and args.output is None:
        parser.error('--output or --scan-only is required')
    files, secrets = source_files(), local_secrets()
    payload = {name: path.read_bytes() for name, path in sorted(files.items())}
    for name, data in payload.items():
        scan(name, data, secrets)
    print(f'Source scan PASS: {len(payload)} files; no detected credentials; local.properties ignored/untracked.')
    if args.scan_only:
        return
    output = args.output.resolve()
    if output.is_relative_to(ROOT) or output.suffix.lower() != '.zip':
        raise ValueError('Choose a ZIP path outside the repository')
    # Exclusive creation prevents overwriting an existing archive.
    with output.open('xb') as stream:
        with zipfile.ZipFile(stream, 'w', zipfile.ZIP_DEFLATED) as archive:
            for name, data in payload.items():
                archive.writestr('MADVerse/' + name, data)
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise ValueError('Archive CRC verification failed; do not distribute')
        members = archive.namelist()
        expected = {'MADVerse/' + name for name in payload}
        if len(members) != len(expected) or set(members) != expected:
            raise ValueError('Archive member verification failed; do not distribute')
        for member in members:
            name = member.removeprefix('MADVerse/')
            data = archive.read(member)
            if excluded(name) or data != payload[name]:
                raise ValueError(f'{name}: archive content mismatch; do not distribute')
            scan(name, data, secrets)
    print(f'Archive PASS: {output}')
    print(f'Members: {len(payload)}; source, tests, docs and wrapper JAR present; excluded files absent.')
    print('SHA256: ' + hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        # OSError/subprocess errors can include unexpected environment data; keep them generic.
        print(str(error) if isinstance(error, ValueError) else
              f'Packaging failed ({type(error).__name__}); archive must not be distributed.')
        raise SystemExit(1)
