"""Generate the empty, self-contained GameTest structure, not a user's map."""
import gzip
import struct
from pathlib import Path
def name(s):
    b = s.encode(); return struct.pack('>H', len(b))+b
def tag(t, n, b): return bytes([t])+name(n)+b
def ints(values): return b''.join(struct.pack('>i', v) for v in values)
data = tag(9, 'size', bytes([3])+ints([3, 48, 8, 24]))
data += tag(9, 'palette', bytes([10])+ints([1])+tag(8,'Name',name('minecraft:air'))+bytes([0]))
data += tag(9, 'blocks', bytes([10])+ints([0]))
data += tag(9, 'entities', bytes([10])+ints([0]))
data += tag(3, 'DataVersion', ints([4900]))
root = Path(__file__).resolve().parents[1] / 'src/main/resources/data/myriad/structure/combat_test_empty.nbt'
root.parent.mkdir(parents=True, exist_ok=True)
root.write_bytes(gzip.compress(tag(10, '', data+bytes([0]))))
