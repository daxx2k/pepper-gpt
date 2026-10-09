"""Add the voice adapter to decoded 2.7.0 without replacing its feature flows.

Compile tools/stable-voice/LegacyVoiceAdapter.java with Android/QiSDK APIs,
desugar with D8 at API 23 and pass its disassembled classes as argument two.
"""
from pathlib import Path
import re, shutil, sys
decoded=Path(sys.argv[1]); adapter=Path(sys.argv[2])
root=decoded/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
bridge='Lcom/softbankrobotics/pepper/pepperGPT/LegacyVoiceAdapter;'
assert not (root/'LegacyVoiceAdapter.smali').exists(),'Already patched'
counts={}
replacements=[
 ('invoke-virtual','Lcom/aldebaran/qi/sdk/builder/SayBuilder;->withText(Ljava/lang/String;)Lcom/aldebaran/qi/sdk/builder/SayBuilder;', 'withText(Lcom/aldebaran/qi/sdk/builder/SayBuilder;Ljava/lang/String;)Lcom/aldebaran/qi/sdk/builder/SayBuilder;'),
 ('invoke-virtual','Lcom/aldebaran/qi/sdk/builder/SayBuilder;->build()Lcom/aldebaran/qi/sdk/object/conversation/Say;', 'build(Lcom/aldebaran/qi/sdk/builder/SayBuilder;)Lcom/aldebaran/qi/sdk/object/conversation/Say;'),
 ('invoke-virtual','Lcom/aldebaran/qi/sdk/builder/SayBuilder;->buildAsync()Lcom/aldebaran/qi/Future;', 'buildAsync(Lcom/aldebaran/qi/sdk/builder/SayBuilder;)Lcom/aldebaran/qi/Future;'),
 ('invoke-interface','Lcom/aldebaran/qi/sdk/object/conversation/Say;->async()Lcom/aldebaran/qi/sdk/object/conversation/Say$Async;', 'async(Lcom/aldebaran/qi/sdk/object/conversation/Say;)Lcom/aldebaran/qi/sdk/object/conversation/Say$Async;'),
 ('invoke-interface','Lcom/aldebaran/qi/sdk/object/conversation/Say$Async;->run()Lcom/aldebaran/qi/Future;', 'runAsync(Lcom/aldebaran/qi/sdk/object/conversation/Say$Async;)Lcom/aldebaran/qi/Future;'),
 ('invoke-interface','Lcom/aldebaran/qi/sdk/object/conversation/Say;->run()V', 'runSync(Lcom/aldebaran/qi/sdk/object/conversation/Say;)V'),
]
for file in root.glob('*.smali'):
 s=file.read_text(encoding='utf-8');original=s
 for instruction, target, replacement in replacements:
  pattern=re.escape(instruction)+r'(/range)? (\{[^}]+\}), '+re.escape(target)
  s,n=re.subn(pattern,lambda m:'invoke-static'+(m[1] or '')+' '+m[2]+', '+bridge+'->'+replacement,s)
  counts[replacement.split('(')[0]]=counts.get(replacement.split('(')[0],0)+n
 if s!=original:file.write_text(s,encoding='utf-8')
assert counts=={'withText':6,'build':2,'buildAsync':4,'async':4,'runAsync':4,'runSync':2},counts
def prepend(file, signature, call):
 s=file.read_text(encoding='utf-8')
 start=s.index(signature);end=s.index('.end method',start)
 segment=s[start:end]
 match=re.search(r'    \.locals \d+\n',segment);assert match
 index=start+match.end();s=s[:index]+'\n    '+call+'\n'+s[index:]
 file.write_text(s,encoding='utf-8')
for name in ['MainActivity','ImmersiveModeActivity','WeatherModeActivity','RadioActivity']:
 p=root/(name+'.smali')
 prepend(p,'.method public onRobotFocusGained(Lcom/aldebaran/qi/sdk/QiContext;)V',
          'invoke-static/range {p0 .. p1}, '+bridge+'->onFocusGained(Landroid/app/Activity;Lcom/aldebaran/qi/sdk/QiContext;)V')
 prepend(p,'.method public onRobotFocusLost()V',
          'invoke-static/range {p0 .. p0}, '+bridge+'->onFocusLost(Landroid/app/Activity;)V')
p=root/'SettingsActivity.smali';s=p.read_text(encoding='utf-8')
start=s.index('.method protected onCreate(Landroid/os/Bundle;)V');end=s.index('.end method',start)
segment=s[start:end];assert segment.count('    return-void')==1
segment=segment.replace('    return-void','    invoke-static/range {p0 .. p0}, '+bridge+'->attachSettings(Landroid/app/Activity;)V\n\n    return-void')
p.write_text(s[:start]+segment+s[end:],encoding='utf-8')
# A cancelled speech completion must not restart recording behind a different screen.
p=root/'MainActivity.smali'
prepend(p,'.method private final startListening()V',
         'invoke-virtual/range {p0 .. p0}, Landroid/app/Activity;->hasWindowFocus()Z\n'
         '    move-result v0\n    if-nez v0, :voice_window_active\n    return-void\n    :voice_window_active')
# Preserve actual pause escapes through Regex.replace; slow stories only.
p=root/'SpeechDurationHelper.smali';s=p.read_text(encoding='utf-8')
old=r'    const-string v2, "$1 \\pau=380\\ "'
new=r'    const-string v2, "$1 \\\\pau=500\\\\ "'
assert s.count(old)==1
s=s.replace(old,new)
start=s.index('.method public final speedForMode(Ljava/lang/String;)I');end=s.index('.end method',start)+len('.end method')
s=s[:start]+'''.method public final speedForMode(Ljava/lang/String;)I
    .locals 1
    const-string v0, "story"
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :normal_speed
    const/16 v0, 0x46
    return v0
    :normal_speed
    const/16 v0, 0x50
    return v0
.end method'''+s[end:]
# Duration estimates must use the actual new sentence pause duration.
s=s.replace('0x17c','0x1f4')
s=s.replace('.field public static final STORY_SPEED_PERCENT:I = 0x50','.field public static final STORY_SPEED_PERCENT:I = 0x46')
start=s.index('.method public static synthetic estimateStoryMs$default(');end=s.index('.end method',start)
s=s[:start]+s[start:end].replace('0x50','0x46')+s[end:]
p.write_text(s,encoding='utf-8')
for file in adapter.glob('**/*.smali'):
 target=decoded/'smali_classes3'/file.relative_to(adapter)
 assert not target.exists(),target
 target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target)
print('Added offline Cori selector, speech/touch adapter and story pauses. Original feature routines retained. Hooks:',counts)
