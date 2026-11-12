"""Download the author's public CC0 packs via the same link flow as their webpage.
No credentials, login, or third-party mirrors. Archives are inspected, never executed.
"""
from pathlib import Path
import http.cookiejar
import io
import json
import re
import urllib.request
import zipfile

root = Path('E:/mod/role_assets/audio/hit-feedback-20261105/source')
root.mkdir(parents=True, exist_ok=True)
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
opener.addheaders = [('User-Agent', 'Mozilla/5.0')]
for pack in ('impact-sounds', 'rpg-audio'):
    page = 'https://kenney.nl/assets/' + pack
    html = opener.open(page, timeout=30).read().decode()
    ident = re.search(r"data-download-id=['\"]([0-9a-f]+)", html).group(1)
    request = urllib.request.Request('https://kenney.nl/download-link/' + ident, headers={'Referer': page})
    link = json.load(opener.open(request, timeout=30))['url']
    data = opener.open(link, timeout=60).read()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        assert not archive.testzip(), 'Corrupt download'
        licenses = [n for n in archive.namelist() if 'license' in n.lower() and n.endswith('.txt')]
        assert licenses, 'Missing pack license'
        text = archive.read(licenses[0]).decode('utf-8-sig')
        assert 'CC0' in text, 'Unexpected pack license'
        dest = root / ('kenney_' + pack + '.zip')
        # Preserve the earlier webpage response that was incorrectly served as a zip.
        if dest.exists() and not zipfile.is_zipfile(dest):
            dest.rename(root / (pack + '-webpage.html'))
        dest.write_bytes(data)
        (root / (pack + '-license.txt')).write_text(text, encoding='utf-8')
        print(pack, len(data), 'bytes; license:', text, flush=True)
        for n in archive.namelist():
            if n.lower().endswith(('.ogg', '.wav', '.mp3')):
                print(n)
