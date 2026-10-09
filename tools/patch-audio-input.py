"""Correct WAV sizes and avoid rejecting sentences containing short fillers."""
from pathlib import Path
import sys
root=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
owner='Lcom/softbankrobotics/pepper/pepperGPT/MainActivity;'
p=root/'MainActivity$recordAudio$1.smali';s=p.read_text(encoding='utf-8')
needle='    invoke-static {v11, v12}, '+owner+'->access$sendAudioToWhisper('+owner+'Ljava/lang/String;)Lkotlinx/coroutines/Job;'
assert s.count(needle)==1 and 'finishRecordedWav' not in s
s=s.replace(needle,'    invoke-static {v12}, '+owner+'->finishRecordedWav(Ljava/lang/String;)V\n\n'+needle)
p.write_text(s,encoding='utf-8')
p=root/'MainActivity$sendAudioToWhisper$1.smali';s=p.read_text(encoding='utf-8')
needle='Lkotlin/text/StringsKt;->contains$default(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z'
assert s.count(needle)==1
s=s.replace(needle,owner+'->containsLongIgnoredPhrase(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z')
p.write_text(s,encoding='utf-8')
p=root/'MainActivity.smali';s=p.read_text(encoding='utf-8')
assert 'finishRecordedWav' not in s
s+='''
.method public static finishRecordedWav(Ljava/lang/String;)V
    .locals 5
    new-instance v0, Ljava/io/RandomAccessFile;
    const-string v1, "rw"
    invoke-direct {v0, p0, v1}, Ljava/io/RandomAccessFile;-><init>(Ljava/lang/String;Ljava/lang/String;)V
    :try_start
    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->length()J
    move-result-wide v2
    long-to-int v1, v2
    add-int/lit8 v4, v1, -0x8
    invoke-static {v4}, Ljava/lang/Integer;->reverseBytes(I)I
    move-result v4
    const-wide/16 v2, 0x4
    invoke-virtual {v0, v2, v3}, Ljava/io/RandomAccessFile;->seek(J)V
    invoke-virtual {v0, v4}, Ljava/io/RandomAccessFile;->writeInt(I)V
    add-int/lit8 v4, v1, -0x2c
    invoke-static {v4}, Ljava/lang/Integer;->reverseBytes(I)I
    move-result v4
    const-wide/16 v2, 0x28
    invoke-virtual {v0, v2, v3}, Ljava/io/RandomAccessFile;->seek(J)V
    invoke-virtual {v0, v4}, Ljava/io/RandomAccessFile;->writeInt(I)V
    :try_end
    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->close()V
    return-void
    .catchall {:try_start .. :try_end} :close_on_error
    :close_on_error
    move-exception v1
    invoke-virtual {v0}, Ljava/io/RandomAccessFile;->close()V
    throw v1
.end method

.method public static containsLongIgnoredPhrase(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z
    .locals 2
    invoke-interface {p1}, Ljava/lang/CharSequence;->length()I
    move-result v0
    const/16 v1, 0xc
    if-ge v0, v1, :long_phrase
    const/4 v0, 0x0
    return v0
    :long_phrase
    invoke-static {p0, p1, p2, p3, p4}, Lkotlin/text/StringsKt;->contains$default(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;)Z
    move-result v0
    return v0
.end method
'''
p.write_text(s,encoding='utf-8')
print('Fixed WAV lengths and restricted substring filtering to long phrases.')
