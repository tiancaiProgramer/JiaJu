#!/usr/bin/env python3
"""Validate real camera rendering with the separately installed signed checker.

Only run for cameras whose live viewing the user has authorized. No screenshot,
recording, device setting write, package install or retry is performed here.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import re
import shlex
import subprocess
import threading

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED = (
    'route_correct', 'continuous_rendering', 'status_overlay_hidden',
    'same_session_during_watch', 'fullscreen_landscape', 'fullscreen_same_session',
    'fullscreen_keeps_playing', 'back_exits_fullscreen', 'exit_fullscreen_same_session',
    'settings_same_session', 'flip_degree_choices', 'disconnect_on_exit', 'layout_unchanged',
    'portrait_toolbar_below_video', 'action_buttons',
    'storage_card_visible', 'nas_entry_visible', 'storage_same_session',
)


def decode(data):
    try:
        return data.decode('utf-8')
    except UnicodeDecodeError:
        return data.decode('gb18030', errors='replace')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--model', required=True)
    parser.add_argument('--index', type=int, default=0)
    parser.add_argument('--seconds', type=int, default=30)
    parser.add_argument('--label', required=True, help='Unique file label under the output directory')
    parser.add_argument('--output', required=True, help='New/existing project testing/device-* directory')
    parser.add_argument('--apk', required=True, help='Local installed app APK, for provenance')
    args = parser.parse_args()
    if not 4 <= args.seconds <= 180 or args.index < 0:
        parser.error('Use 4-180 seconds and a nonnegative camera index.')
    if not re.fullmatch(r'[a-zA-Z0-9._-]+', args.label):
        parser.error('Invalid output label.')
    output = (ROOT / args.output).resolve()
    if output.parent != ROOT / 'testing' or not output.name.startswith('device-'):
        parser.error('Output must be a project testing/device-* directory.')
    output.mkdir(parents=True, exist_ok=True)
    log_file = output / (args.label + '.log')
    result_file = output / (args.label + '.json')
    if log_file.exists() or result_file.exists():
        parser.error('Output label already exists; preserve previous evidence.')
    apk = pathlib.Path(args.apk).resolve()
    adb = [args.adb, '-s', args.serial]
    command = shlex.join([
        'am', 'instrument', '-w', '-e', 'mode', 'camera_ui',
        '-e', 'camera_model', args.model, '-e', 'camera_index', str(args.index),
        '-e', 'seconds', str(args.seconds), '-e', 'fullscreen', 'true',
        'dev.homecontrol.tests/dev.homecontrol.app.MijiaDeviceCheckInstrumentation',
    ])
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    process = subprocess.Popen(adb + ['shell', command], stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT)
    expired = threading.Event()

    def timeout():
        expired.set()
        process.kill()

    timer = threading.Timer(args.seconds + 110, timeout)
    timer.start()
    lines = []
    launches = []
    try:
        with log_file.open('x', encoding='utf-8', newline='\n') as log:
            for raw in process.stdout:
                line = decode(raw).rstrip('\r\n')
                lines.append(line)
                log.write(line + '\n')
                log.flush()
                if 'camera_ui_waiting_for_main=true' in line:
                    launch = subprocess.run(adb + ['shell', 'am', 'start', '-W', '-n',
                                                  'dev.homecontrol.app/.MainActivity'],
                                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                            timeout=20)
                    launches.append(launch.returncode)
                if re.search(r'camera_ui_(watch_ms|rendered)=', line):
                    print(line, flush=True)
        process.wait(timeout=15)
    finally:
        timer.cancel()
        if process.poll() is None:
            process.kill()
            process.wait()
    text = '\n'.join(lines)
    fields = {}
    for key, value in re.findall(r'^INSTRUMENTATION_RESULT: ([^=]+)=(.*)$', text, re.M):
        try:
            fields[key] = json.loads(value)
        except ValueError:
            fields[key] = value
    # Do not put household names, device names or account-derived IDs in the JSON.
    fields = {k: v for k, v in fields.items()
              if k.startswith(('camera_ui_', 'screen_render_')) or k in (
                  'error', 'camera_error', 'camera_model', 'logged_in', 'mijia_devices',
                  'haier_devices', 'physical_commands_sent', 'video_saved_to_storage')}
    absent = [key for key in REQUIRED if fields.get('camera_ui_' + key) is not True]
    progress = [dict(rendered=int(frames), watch_ms=int(ms)) for frames, ms in re.findall(
        r'camera_ui_rendered=(\d+)\s+INSTRUMENTATION_STATUS: camera_ui_watch_ms=(\d+)', text)]
    advancing = all(b['rendered'] > a['rendered'] for a, b in zip(progress, progress[1:]))
    watch_complete = (fields.get('screen_render_duration_ms', 0) >= (args.seconds - 2) * 1000
                      and (args.seconds < 15 or bool(progress)))
    errors = any(key in fields for key in ('error', 'camera_error', 'camera_ui_error'))
    code_ok = bool(re.search(r'^INSTRUMENTATION_CODE: -1$', text, re.M))
    passed = (not expired.is_set() and process.returncode == 0 and code_ok
              and not absent and not errors and advancing and watch_complete
              and fields.get('physical_commands_sent') is False
              and fields.get('video_saved_to_storage') is False)
    report = dict(model=args.model, model_index=args.index, watch_seconds=args.seconds,
                  started_utc=started, apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                  apk_path=str(apk.relative_to(ROOT)), passed=passed, missing_checks=absent,
                  timed_out=expired.is_set(), adb_launch_codes=launches, progress=progress,
                  progress_advancing=advancing, watch_complete=watch_complete, results=fields)
    result_file.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=True), flush=True)
    raise SystemExit(0 if passed else 1)


if __name__ == '__main__':
    main()
