from pathlib import Path
import re,sys
p=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT/RobotManager.smali'
s=p.read_text(encoding='utf-8')
helper='Lcom/softbankrobotics/pepper/pepperGPT/RobotLights;'
marker='    iput-object p1, p0, Lcom/softbankrobotics/pepper/pepperGPT/RobotManager;->sshSession:Lcom/jcraft/jsch/Session;'
assert s.count(marker)==1
s=s.replace(marker,marker+f'\n    invoke-static {{p1}}, {helper}->connection(Lcom/jcraft/jsch/Session;)V')
for name,body in {
  'startSpeakingLedAnimation':'', 'stopSpeakingLedAnimation':'',
  'startThinkingLedAnimation':f'    const-string v0, "thinking"\n    invoke-static {{p0, v0}}, {helper}->show(Ljava/lang/Object;Ljava/lang/String;)V\n',
  'stopThinkingLedAnimation':f'    invoke-static {{p0}}, {helper}->clear(Ljava/lang/Object;)V\n',
}.items():
    pattern=r'(?ms)(^\.method public final '+name+r'\(\)V\n).*?^\.end method'
    s,n=re.subn(pattern,lambda m:m[1]+'    .locals 1\n'+body+'    return-void\n.end method',s)
    assert n==1,name
p.write_text(s,encoding='utf-8')
