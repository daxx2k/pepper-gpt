"""Wire RealtimeLab into the restored PepperGPT APK after merging its compiled smali."""
from pathlib import Path
import re, sys

d=Path(sys.argv[1])
app=d/'smali_classes3/com/softbankrobotics/pepper/pepperGPT';pkg='Lcom/softbankrobotics/pepper/pepperGPT/RealtimeLab;'
def insert(s,method,code):
 start=s.index(method);m=re.search(r'(?m)^    \.(?:registers|locals) \d+\n',s[start:]);assert m
 end=start+m.end()
 return s[:end]+'\n    '+code+'\n'+s[end:]
p=app/'MainActivity.smali';s=p.read_text(encoding='utf-8')
s=insert(s,'.method public onCreateOptionsMenu(Landroid/view/Menu;)Z',f'invoke-static/range {{p0 .. p1}}, {pkg}->add(Landroid/app/Activity;Landroid/view/Menu;)V')
assert not re.search(r'(?m)^\.method .* onPause\(\)V',s)
s+='\n.method protected onPause()V\n    .locals 0\n    invoke-static {p0}, '+pkg+'->stop(Landroid/app/Activity;)V\n    invoke-super {p0}, Lcom/aldebaran/qi/sdk/design/activity/RobotActivity;->onPause()V\n    return-void\n.end method\n'
p.write_text(s,encoding='utf-8')
p=app/'LegacyVoiceAdapter.smali';s=p.read_text(encoding='utf-8')
for anchor,args,code in [('onFocusGained','p0 .. p1','focus(Landroid/app/Activity;Lcom/aldebaran/qi/sdk/QiContext;)V'),('onFocusLost','p0 .. p0','lose(Landroid/app/Activity;)V'),('stopSpeech','p0 .. p0','stop(Landroid/app/Activity;)V')]:
 access='private' if anchor=='stopSpeech' else 'public'
 s=insert(s,f'.method {access} static {anchor}(',f'invoke-static/range {{{args}}}, {pkg}->{code}')
p.write_text(s,encoding='utf-8')
