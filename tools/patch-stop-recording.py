"""Discard a stopped recording before showing Thinking or uploading it."""
from pathlib import Path
import sys
root=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
owner='Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;'
p=root/'MainActivity$recordAudio$1$1.smali';s=p.read_text(encoding='utf-8')
needle='    invoke-static {v0}, '+owner+'->access$showThinkingIndicator('+owner+')V'
assert s.count(needle)==1
s=s.replace(needle,'    invoke-static {v0}, '+owner+'->showThinkingIfListening('+owner+')V')
p.write_text(s,encoding='utf-8')
p=root/'MainActivity$recordAudio$1.smali';s=p.read_text(encoding='utf-8')
needle='    invoke-static {v11, v12}, '+owner+'->access$sendAudioToWhisper('+owner+'Ljava/lang/String;)Lkotlinx/coroutines/Job;'
assert s.count(needle)==1
s=s.replace(needle,'    invoke-static {v11, v12}, '+owner+'->sendRecordedAudioIfListening('+owner+'Ljava/lang/String;)V')
p.write_text(s,encoding='utf-8')
p=root/'MainActivity.smali';s=p.read_text(encoding='utf-8')
assert 'showThinkingIfListening' not in s
s=s.replace('.field private isListening:Z','.field private volatile isListening:Z')
s+='''
.method public static showThinkingIfListening(Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;)V
    .locals 1
    iget-boolean v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->isListening:Z
    if-eqz v0, :done
    invoke-direct {p0}, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->showThinkingIndicator()V
    :done
    return-void
.end method

.method public static sendRecordedAudioIfListening(Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;Ljava/lang/String;)V
    .locals 1
    iget-boolean v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->isListening:Z
    if-eqz v0, :done
    invoke-direct {p0, p1}, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->sendAudioToWhisper(Ljava/lang/String;)Lkotlinx/coroutines/Job;
    :done
    return-void
.end method
'''
p.write_text(s,encoding='utf-8')
print('Stop prevents the recording from displaying Thinking or launching transcription.')
