"""Apply the narrow listening fix to an apktool-decoded private 2.7.0 APK.

Keep the original APK private: it contains the legacy build configuration.
Reassemble, then replace only classes3.dex in the original APK and re-sign.
"""
from pathlib import Path
import sys
import re

root = Path(sys.argv[1]) / 'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
owner = 'Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;'
whisper = root / 'MainActivity$sendAudioToWhisper$1.smali'
text = whisper.read_text(encoding='utf-8')
assert 'resumeAfterIgnoredAudio' not in text, 'Already patched'
for marker in ('Audio file too small, likely silent. Ignoring.', 'Ignored false detection:'):
    start = text.index(marker)
    end = start + re.search(r'    sget-object v\d+, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;', text[start:]).start()
    assert 'return-object' not in text[start:end]
    insertion = ('    iget-object v0, v7, '
                 'Lcom/softbankrobotics/pepper/pepperGPT/MainActivity$sendAudioToWhisper$1;'
                 '->this$0:' + owner + '\n\n'
                 '    invoke-virtual {v0}, ' + owner + '->resumeAfterIgnoredAudio()V\n\n')
    text = text[:end] + insertion + text[end:]
whisper.write_text(text, encoding='utf-8')
main = root / 'MainActivity.smali'
text = main.read_text(encoding='utf-8')
text += '''
.method public final resumeAfterIgnoredAudio()V
    .locals 1
    new-instance v0, Lcom/softbankrobotics/pepper/pepperGPT/ListeningRecovery;
    invoke-direct {v0, p0}, Lcom/softbankrobotics/pepper/pepperGPT/ListeningRecovery;-><init>(Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;)V
    invoke-virtual {p0, v0}, Landroid/app/Activity;->runOnUiThread(Ljava/lang/Runnable;)V
    return-void
.end method

.method public final resumeListeningIfActive()V
    .locals 2
    iget-boolean v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->isListening:Z
    if-eqz v0, :done
    iget-boolean v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->isSpeaking:Z
    if-nez v0, :done
    invoke-virtual {p0}, Landroid/app/Activity;->isFinishing()Z
    move-result v0
    if-nez v0, :done
    invoke-virtual {p0}, Landroid/app/Activity;->isDestroyed()Z
    move-result v0
    if-nez v0, :done
    invoke-direct {p0}, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->hideSpinner()V
    const-string v0, "ListeningRecovery"
    const-string v1, "Ignored audio completed; scheduling the next recording"
    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    invoke-direct {p0}, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->listenAndRespond()V
    :done
    return-void
.end method
'''
# A pending 300ms callback must obey a user pressing Stop.
start = text.index('.method private final recordAudio()V')
end = text.index('    iget-boolean v0, v8, '+owner+'->isSpeaking:Z', start)
text = text[:end] + ('    iget-boolean v0, v8, '+owner+'->isListening:Z\n'
                     '    if-nez v0, :recovery_record_active\n'
                     '    return-void\n'
                     '    :recovery_record_active\n\n') + text[end:]
main.write_text(text, encoding='utf-8')
(root / 'ListeningRecovery.smali').write_text('''
.class public final Lcom/softbankrobotics/pepper/pepperGPT/ListeningRecovery;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.field private final activity:Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;
.method public constructor <init>(Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/softbankrobotics/pepper/pepperGPT/ListeningRecovery;->activity:Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;
    return-void
.end method
.method public run()V
    .locals 1
    iget-object v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/ListeningRecovery;->activity:Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;
    invoke-virtual {v0}, Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;->resumeListeningIfActive()V
    return-void
.end method
''', encoding='utf-8')
print('Patched two ignored-audio returns and guarded the delayed recording.')
