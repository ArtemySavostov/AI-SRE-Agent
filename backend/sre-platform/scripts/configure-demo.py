#!/usr/bin/env python3

import argparse
import getpass
import http.cookiejar
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://localhost:8080')
    parser.add_argument('--email')
    parser.add_argument('--project-id')
    parser.add_argument('--server-id')
    parser.add_argument('--connection', type=Path, default=Path(__file__).resolve().parent.parent / '.local/demo-connection.json')
    parser.add_argument('--host', help='Override SSH host: localhost for local Java, host.docker.internal for Docker Desktop')
    args = parser.parse_args()
    settings = json.loads(args.connection.read_text())
    if args.host:
        settings['host'] = args.host
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(path, method='GET', data=None, form=False):
        headers = {}
        if method != 'GET':
            csrf = request('/api/auth/csrf')
            headers[csrf['headerName']] = csrf['token']
        body = None
        if data is not None:
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
            body = (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        req = urllib.request.Request(args.url.rstrip('/') + path, data=body, headers=headers, method=method)
        try:
            with opener.open(req, timeout=75) as response:
                payload = response.read()
                return json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            try:
                message = json.loads(error.read()).get('message', str(error.code))
            except (ValueError, AttributeError):
                message = str(error.code)
            raise RuntimeError(f'{method} {path}: {message}') from None

    email = args.email or input('Email пользователя SRE-платформы: ').strip()
    request('/api/auth/login', 'POST', {'email': email, 'password': getpass.getpass('Пароль SRE-платформы: ')}, form=True)

    def choose(items, label):
        for index, item in enumerate(items, 1):
            print(f'{index}. {item["name"]} ({item["id"]})')
        print('0. Создать demo-' + label)
        value = int(input('Выбор: '))
        if value == 0:
            return None
        if value < 1 or value > len(items):
            raise ValueError('Неверный номер')
        return items[value - 1]['id']

    project_id = args.project_id or choose(request('/api/project'), 'проект')
    if project_id is None:
        project_id = request('/api/project', 'POST', {'name': 'Demo infrastructure', 'description': 'PetClinic demo server'})['id']
    base = '/api/project/' + project_id + '/server'
    server_id = args.server_id or choose(request(base), 'сервер')
    if server_id is None:
        server_id = request(base, 'POST', {'name': 'PetClinic demo', 'hostname': 'git', 'ipAddress': '127.0.0.1', 'os': 'Linux'})['id']
    base += '/' + server_id
    request(base + '/ssh', 'PUT', settings)
    print('SSH:', request(base + '/ssh/test', 'POST'))
    job = request(base + '/discovery', 'POST')
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        state = request(base + '/discovery')
        if state['jobId'] != job['jobId']:
            raise RuntimeError('Другой пользователь запустил новую задачу discovery')
        if state['status'] == 'FAILED':
            raise RuntimeError(f'{state["errorCode"]}: {state["error"]}')
        if state['status'] == 'SUCCEEDED':
            snapshot = state['snapshot']
            print(json.dumps(snapshot['host'], ensure_ascii=False, indent=2))
            print('Docker:', snapshot['docker']['version'])
            for container in snapshot['containers']:
                print(container['name'], container['state'], container.get('health') or 'no healthcheck')
            print('Результат:', args.url.rstrip('/') + base + '/discovery')
            return
        time.sleep(2)
    raise RuntimeError('Истекло время ожидания. Задача может продолжать работать; проверьте GET ' + base + '/discovery')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
