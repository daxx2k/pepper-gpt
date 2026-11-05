"""Patch the restored integration to use Realtime for the existing listening flow."""
from pathlib import Path
import re, sys

app = Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
root = 'Lcom/softbankrobotics/pepper/pepperGPT/'
main = root+'MainActivity;'
lab = root+'RealtimeLab;'
coro = root+'MainActivity$sendToOpenAI$1;'
activity = 'Landroid/app/Activity;'
def method(s, name, fn):
    pattern=r'(?ms)^\.method [^\n]* '+re.escape(name)+r'[^\n]*\n.*?^\.end method'
    s,n=re.subn(pattern,lambda m:fn(m[0]),s)
    assert n==1,name
    return s
def exact(s,a,b):
    assert s.count(a)==1,a
    return s.replace(a,b)
def prepend(s,code):
    m=re.search(r'(?m)^    \.(locals|registers) \d+\n',s)
    assert m
    return s[:m.end()]+'\n    '+code+'\n'+s[m.end():]
p=app/'MainActivity.smali'; s=p.read_text(encoding='utf-8')
s=exact(s,'    invoke-direct {p0}, '+main+'->listenAndRespond()V\n\n    .line 483',
          '    invoke-static {p0}, '+lab+'->start('+activity+')V\n\n    .line 483')
s=method(s,'resumeListeningIfActive()V',lambda m:exact(m,'invoke-direct {p0}, '+main+'->listenAndRespond()V','invoke-static {p0}, '+lab+'->start('+activity+')V'))
s=method(s,'stopListening()V',lambda m:prepend(m,'invoke-static {p0}, '+lab+'->stop('+activity+')V'))
call='    invoke-direct {p0}, '+main+'->stopListening()V'
s=method(s,'sendToOpenAI(Ljava/lang/String;)V',lambda m:exact(m,call,
  '    invoke-static {p0, p1}, '+lab+'->preserveForRoute('+activity+'Ljava/lang/String;)Z\n'
  '    move-result v0\n'
  '    if-nez v0, :rt_keep_listening\n'+call+'\n    :rt_keep_listening'))
assert not re.search(r'(?m)^\.method .* onPause\(\)V',s)
s+='\n.method protected onPause()V\n    .locals 0\n    invoke-static {p0}, '+lab+'->pause('+activity+')V\n    invoke-super {p0}, Lcom/aldebaran/qi/sdk/design/activity/RobotActivity;->onPause()V\n    return-void\n.end method\n'
p.write_text(s,encoding='utf-8')

p=app/'LegacyVoiceAdapter.smali';s=p.read_text(encoding='utf-8')
for name,args,target in [
 ('onFocusGained','p0 .. p1','focus('+activity+'Lcom/aldebaran/qi/sdk/QiContext;)V'),
 ('onFocusLost','p0 .. p0','lose('+activity+')V'),
 ('stopSpeech','p0 .. p0','interrupt('+activity+')V')]:
 s=method(s,name+'(',lambda m:prepend(m,'invoke-static/range {'+args+'}, '+lab+'->'+target))
p.write_text(s,encoding='utf-8')

p=app/'MainActivity$sendToOpenAI$1.smali';s=p.read_text(encoding='utf-8')
s=exact(s,'# direct methods','.field private realtimeRoute:'+lab+'\n\n# direct methods')
def ctor(m):
 m=exact(m,'    .locals 0','    .locals 1')
 return exact(m,'    return-void',
      '    iget-object v0, p0, '+coro+'->this$0:'+main+'\n'
      '    invoke-static {v0, p2}, '+lab+'->routeFor('+activity+'Ljava/lang/String;)'+lab+'\n'
      '    move-result-object v0\n'
      '    iput-object v0, p0, '+coro+'->realtimeRoute:'+lab+'\n'
      '    return-void')
s=method(s,'<init>(',ctor)
s=method(s,'create(',lambda m:exact(m,'    return-object v0',
      '    iget-object v1, p0, '+coro+'->realtimeRoute:'+lab+'\n'
      '    iput-object v1, v0, '+coro+'->realtimeRoute:'+lab+'\n'
      '    return-object v0'))
def gates(m):
 for label in ['3','5','7','9','b']:
  marker='    if-eqz v3, :cond_'+label+'\n'
  m=exact(m,marker,marker+
        '    iget-object v3, v8, '+coro+'->realtimeRoute:'+lab+'\n'
        '    invoke-static {v3}, '+lab+'->feature('+lab+')Z\n'
        '    move-result v3\n'
        '    if-nez v3, :rt_feature_'+label+'\n'
        '    sget-object v3, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;\n'
        '    return-object v3\n'
        '    :rt_feature_'+label+'\n')
 marker='    const-string v13, "none"'
 return exact(m,marker,
        '    iget-object v13, v8, '+coro+'->realtimeRoute:'+lab+'\n'
        '    invoke-static {v13, v11}, '+lab+'->normalRoute('+lab+'Ljava/lang/String;)Z\n'
        '    move-result v13\n'
        '    if-nez v13, :rt_continue_original\n'
        '    sget-object v13, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;\n'
        '    return-object v13\n'
        '    :rt_continue_original\n'+marker)
s=method(s,'invokeSuspend(',gates)
p.write_text(s,encoding='utf-8')

# The fallback recorder still sends completed WAV files through the Audio API.
p = app / 'MainActivity$sendAudioToWhisper$1.smali'
s = p.read_text(encoding='utf-8')
p.write_text(exact(s, '"whisper-1"', '"gpt-transcribe"'), encoding='utf-8')
