"""Style the listening toggle in the decoded private 2.7.0 APK."""
from pathlib import Path
import re, sys
root=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
owner='Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;'
main=root/'MainActivity.smali'
text=main.read_text(encoding='utf-8')
assert 'styleListeningButton' not in text, 'Already patched'
text+='''
.method public static styleListeningButton(Landroid/widget/Button;)V
    .locals 2
    invoke-virtual {p0}, Landroid/widget/Button;->getText()Ljava/lang/CharSequence;
    move-result-object v0
    invoke-interface {v0}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;
    move-result-object v0
    const-string v1, "Stop Listening"
    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :start_color
    const-string v0, "#B3261E"
    goto :color_ready
    :start_color
    const-string v0, "#107C5A"
    :color_ready
    invoke-static {v0}, Landroid/graphics/Color;->parseColor(Ljava/lang/String;)I
    move-result v0
    invoke-static {v0}, Landroid/content/res/ColorStateList;->valueOf(I)Landroid/content/res/ColorStateList;
    move-result-object v0
    invoke-virtual {p0, v0}, Landroid/widget/Button;->setBackgroundTintList(Landroid/content/res/ColorStateList;)V
    const/4 v0, -0x1
    invoke-virtual {p0, v0}, Landroid/widget/Button;->setTextColor(I)V
    return-void
.end method
'''
main.write_text(text,encoding='utf-8')
for name in ['MainActivity$startListening$1','MainActivity$stopListening$1']:
 p=root/(name+'.smali');s=p.read_text(encoding='utf-8')
 s=re.sub(r'    invoke-virtual \{v0, v1\}, Landroid/widget/Button;->setBackgroundColor\(I\)V', '',s)
 needle='    invoke-virtual {v0, v1}, Landroid/widget/Button;->setText(Ljava/lang/CharSequence;)V'
 assert s.count(needle)==1
 s=s.replace(needle,needle+'\n\n    invoke-static {v0}, '+owner+'->styleListeningButton(Landroid/widget/Button;)V')
 p.write_text(s,encoding='utf-8')
p=root/'MainActivity$applyTheme$1.smali';s=p.read_text(encoding='utf-8')
needle='    invoke-virtual {v0, v1}, Landroid/widget/Button;->setBackgroundColor(I)V'
assert s.count(needle)==3
s=s.replace(needle,'    invoke-static {v0}, '+owner+'->styleListeningButton(Landroid/widget/Button;)V')
p.write_text(s,encoding='utf-8')
p=root/'MainActivity$recordAudio$1$2.smali';s=p.read_text(encoding='utf-8')
pattern=r'(    invoke-virtual \{(v\d+), v\d+\}, Landroid/widget/Button;->setBackgroundTintList\(Landroid/content/res/ColorStateList;\)V)'
def style_toggle(m):
 previous=re.findall(r'->(listenToggleButton|stopButton):Landroid/widget/Button;',s[:m.start()])
 assert previous
 if previous[-1]!='listenToggleButton':return m[1]
 return m[1]+'\n\n    invoke-static {'+m[2]+'}, '+owner+'->styleListeningButton(Landroid/widget/Button;)V'
s,n=re.subn(pattern,style_toggle,s)
assert n==6
assert s.count('->styleListeningButton(Landroid/widget/Button;)V')==3
p.write_text(s,encoding='utf-8')
print('Start: green. Stop: red. White text; colors persist through theme updates.')
