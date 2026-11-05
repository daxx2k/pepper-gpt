from pathlib import Path
import re,sys

app = Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
owner = 'Lcom/softbankrobotics/pepper/pepperGPT/ImmersiveModeActivity;'
follow = 'Lcom/softbankrobotics/pepper/pepperGPT/StoryFollow;'
p=app/'ImmersiveModeActivity.smali'
s=p.read_text(encoding='utf-8')
old=f'''    iget-boolean v1, p0, {owner}->imageLoaded:Z

    if-eqz v1, :cond_6'''
new=f'''    invoke-static {{p0}}, {follow}->story(Landroid/app/Activity;)Z
    move-result v1
    if-nez v1, :pepper_story_voice_ready

{old}

    :pepper_story_voice_ready'''
assert s.count(old)==1
p.write_text(s.replace(old,new,1),encoding='utf-8')

# Save a late cover for replay, while the active scene keeps its own illustration.
p=app/'ImmersiveModeActivity$generateSceneImage$1$1.smali'
s=p.read_text(encoding='utf-8')
old='    .line 258\n'
new=f'''    iget-object v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/ImmersiveModeActivity$generateSceneImage$1$1;->this$0:Lcom/softbankrobotics/pepper/pepperGPT/ImmersiveModeActivity$generateSceneImage$1;
    iget v1, v0, Lcom/softbankrobotics/pepper/pepperGPT/ImmersiveModeActivity$generateSceneImage$1;->$index:I
    iget-object v0, v0, Lcom/softbankrobotics/pepper/pepperGPT/ImmersiveModeActivity$generateSceneImage$1;->this$0:{owner}
    invoke-static {{v0}}, {owner}->access$getCurrentSceneIndex$p({owner})I
    move-result v2
    if-ne v1, v2, :goto_0

{old}'''
assert s.count(old)==1
assert 'access$getCurrentSceneIndex$p(' in (app/'ImmersiveModeActivity.smali').read_text(encoding='utf-8')
p.write_text(s.replace(old,new,1),encoding='utf-8')
for p in app.glob('*.smali'):
    if p.name.startswith('SpeechGesture') or p.name.startswith('-$$Lambda$SpeechGesture$'):
        p.unlink()
