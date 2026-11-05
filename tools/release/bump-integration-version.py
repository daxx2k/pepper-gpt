"""Update the version fields in the private APK's binary Android manifest."""
from pathlib import Path
import struct
import sys

def bump(raw, old_version, new_version, old_code, new_code):
    data = bytearray(raw)
    strings, offsets = [], []
    changed = set()
    pos = 8
    while pos < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, pos)
        assert size >= header > 0
        if kind == 1:
            count, _, flags, start, _ = struct.unpack_from('<IIIII', data, pos + 8)
            utf8 = bool(flags & 0x100)
            encoding = 'utf-8' if utf8 else 'utf-16le'
            for i in range(count):
                index = pos + start + struct.unpack_from('<I', data, pos + header + 4 * i)[0]
                if utf8:
                    index += 2 if data[index] & 0x80 else 1
                    length = data[index]
                    index += 1
                    if length & 0x80:
                        length = ((length & 0x7f) << 8) | data[index]
                        index += 1
                else:
                    length = struct.unpack_from('<H', data, index)[0]
                    index += 2
                    if length & 0x8000:
                        length = ((length & 0x7fff) << 16) | struct.unpack_from('<H', data, index)[0]
                        index += 2
                    length *= 2
                strings.append(data[index:index+length].decode(encoding))
                offsets.append((index, length))
        elif kind == 0x102:
            _, tag, start, attrsize, count = struct.unpack_from('<IIHHH', data, pos + 16)
            if strings[tag] == 'manifest':
                for i in range(count):
                    index = pos + 16 + start + attrsize * i
                    _, name, _, _, _, dtype, value = struct.unpack_from('<IIIHBBI', data, index)
                    if strings[name] == 'versionCode':
                        assert dtype == 0x10 and value == old_code and new_code > value
                        struct.pack_into('<I', data, index + 16, new_code)
                        changed.add('code')
                    elif strings[name] == 'versionName':
                        assert dtype == 3 and strings[value] == old_version
                        offset, length = offsets[value]
                        encoded = new_version.encode(encoding)
                        assert len(encoded) == length
                        data[offset:offset+length] = encoded
                        changed.add('name')
        pos += size
    assert changed == {'code', 'name'}
    return bytes(data)

if __name__ == '__main__':
    manifest, old_name, new_name, old_code, new_code = sys.argv[1:]
    path = Path(manifest)
    path.write_bytes(bump(path.read_bytes(), old_name, new_name, int(old_code), int(new_code)))
