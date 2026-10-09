"""Sanitize a local restored integration; never commit its private input APK."""
from pathlib import Path
import re, struct, sys
root = Path(sys.argv[1])
package = root / 'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
for name in ['MainActivity.smali', 'ImageGenerationHelper.smali']:
    path = package / name
    text = path.read_text(encoding='utf-8')
    text, count = re.subn(r'(const-string(?:/jumbo)?\s+\w+, )"(?:sk-[^"]+|[0-9a-f]{32})"', r'\1""', text)
    assert count == (2 if name == 'MainActivity.smali' else 1), 'Unexpected credential layout'
    path.write_text(text, encoding='utf-8')
path = package / 'BuildConfig.smali'
text = path.read_text(encoding='utf-8')
text, count = re.subn(r'(?m)(^\.field[^\n]*? = )"(?:sk-[^"]+|[0-9a-f]{32})"', r'\1""', text)
assert count == 2
text = text.replace('.field public static final DEBUG:Z = true', '.field public static final DEBUG:Z = false')
text = text.replace('const-string v0, "true"', 'const-string v0, "false"')
text = re.sub(r'(?m)(^\.field public static final VERSION_CODE:I = ).*$', r'\g<1>0x5a', text)
text = re.sub(r'(?m)(^\.field public static final VERSION_NAME:Ljava/lang/String; = ).*$', r'\1"2.7.1"', text)
text = re.sub(r'(?m)(^\.field public static final BUILD_TYPE:Ljava/lang/String; = ).*$', r'\1"release"', text)
path.write_text(text, encoding='utf-8')
path = package / 'RobotManager.smali'
text = path.read_text(encoding='utf-8')
for name in ['pepperIp', 'sshUser', 'sshPassword']:
    text = text.replace('.field private final ' + name + ':', '.field private ' + name + ':')
    pattern = r'const-string v0, "[^"\n]*"(\s+iput-object v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/RobotManager;->' + name + r':Ljava/lang/String;)'
    text, count = re.subn(pattern, r'const-string v0, ""\1', text)
    assert count == 1, name
anchor = '.method private final connectSsh()V\n    .locals 9\n'
assert anchor in text
text = text.replace(anchor, anchor + '''
    invoke-static {p0}, Lcom/softbankrobotics/pepper/pepperGPT/ReleaseSettings;->configure(Ljava/lang/Object;)Z
    move-result v0
    if-nez v0, :release_ssh_configured
    return-void
    :release_ssh_configured
''', 1)
path.write_text(text, encoding='utf-8')
path = package / 'RobotManager$connectSsh$1.smali'
text = path.read_text(encoding='utf-8')
pattern = r'new-instance v2, Lcom/jcraft/jsch/JSch;\s+invoke-direct \{v2\}, Lcom/jcraft/jsch/JSch;-><init>\(\)V'
text, count = re.subn(pattern, 'invoke-static/range {p0 .. p0}, Lcom/softbankrobotics/pepper/pepperGPT/ReleaseSettings;->newJSch(Ljava/lang/Object;)Lcom/jcraft/jsch/JSch;\n    move-result-object v2', text)
assert count == 1
pattern = r'(const-string v5, "StrictHostKeyChecking"\s+const-string v6, )"no"'
text, count = re.subn(pattern, r'\1"yes"', text)
assert count == 1
path.write_text(text, encoding='utf-8')
path = package / 'LegacyVoiceAdapter.smali'
text = path.read_text(encoding='utf-8')
anchor = '.method public static attachSettings(Landroid/app/Activity;)V\n'
assert anchor in text
start = text.index(anchor); position = text.index('\n', re.search(r'\.(?:locals|registers) \d+', text[start:]).start() + start) + 1
text = text[:position] + '\n    invoke-static/range {p0 .. p0}, Lcom/softbankrobotics/pepper/pepperGPT/ReleaseSettings;->attach(Landroid/app/Activity;)V\n' + text[position:]
path.write_text(text, encoding='utf-8')
path = root / 'AndroidManifest.xml'
data = bytearray(path.read_bytes()); strings = []; offsets = []; utf8 = False
pos = 8
while pos < len(data):
    kind, header, size = struct.unpack_from('<HHI', data, pos)
    if kind == 1:
        count, styles, flags, offset, styleoffset = struct.unpack_from('<IIIII', data, pos + 8)
        utf8 = bool(flags & 0x100)
        for i in range(count):
            p = pos + offset + struct.unpack_from('<I', data, pos + header + 4*i)[0]
            if utf8:
                n = data[p]; p += 1
                if n & 0x80: p += 1
                n = data[p]; p += 1
                if n & 0x80: n = ((n & 0x7f) << 8) | data[p]; p += 1
                strings.append(data[p:p+n].decode('utf-8')); offsets.append((p, n))
            else:
                n = struct.unpack_from('<H', data, p)[0]; p += 2
                if n & 0x8000: n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', data, p)[0]; p += 2
                strings.append(data[p:p+n*2].decode('utf-16le')); offsets.append((p, n*2))
    elif kind == 0x102:
        ns, name, start, attrsize, count = struct.unpack_from('<IIHHH', data, pos+16)
        for i in range(count):
            p = pos + 16 + start + i*attrsize
            ns, name, raw = struct.unpack_from('<III', data, p)
            datatype = data[p+15]; value = struct.unpack_from('<I', data, p+16)[0]
            if strings[name] == 'versionCode':
                assert value == 89; struct.pack_into('<I', data, p+16, 90)
            if strings[name] == 'versionName':
                assert strings[value] == '2.7.0'
                offset, length = offsets[value]
                new = '2.7.1'.encode('utf-8' if utf8 else 'utf-16le'); assert len(new) == length
                data[offset:offset+length] = new
            if strings[name] in ['debuggable', 'allowBackup']:
                assert datatype == 0x12; struct.pack_into('<I', data, p+16, 0)
    pos += size
path.write_bytes(data)
print('Removed embedded API/weather/robot credentials; runtime SSH requires a verified host key; backup and debugging disabled.')
