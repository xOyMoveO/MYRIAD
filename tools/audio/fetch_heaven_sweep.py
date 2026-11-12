"""Download an explicitly CC0 public Foley pack; no credentials or executable content."""
from pathlib import Path
import io
import urllib.request
import zipfile

root = Path('E:/mod/role_assets/audio/lingyuan-heaven-sweep-20261105/source')
root.mkdir(parents=True, exist_ok=True)
url = 'https://opengameart.org/sites/default/files/swishes.zip'
request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
data = urllib.request.urlopen(request, timeout=30).read()
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    assert not archive.testzip()
    for entry in archive.infolist():
        print(entry.filename, entry.file_size)
    recordings=[n for n in archive.namelist() if n.lower().endswith('.wav') and not n.startswith('__MACOSX/') and not Path(n).name.startswith('._')]
    assert len(recordings) == 13
    (root / 'swishes.zip').write_bytes(data)
