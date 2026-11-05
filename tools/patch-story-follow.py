from pathlib import Path
import re
import sys

app = Path(sys.argv[1]) / 'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
owner = 'Lcom/softbankrobotics/pepper/pepperGPT/'
follow = owner + 'StoryFollow;'
activity = owner + 'ImmersiveModeActivity;'
scene_callback = owner + 'ImmersiveModeActivity$showSceneImage$1;'
picasso = owner + 'ImmersiveModeActivity$showSceneImage$1$1;'

def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new, 1)

def patch(text, name, edit):
    pattern = r'(?ms)^\.method [^\n]* ' + re.escape(name) + r'\n.*?^\.end method'
    result, count = re.subn(pattern, lambda match: edit(match[0]), text)
    assert count == 1, name
    return result

def entry(body, lines):
    return re.sub(r'(    \.locals \d+\n)', lambda m: m[0] + '\n' + lines, body, count=1)

def call(name):
    return f'    invoke-static/range {{p0 .. p0}}, {follow}->{name}(Landroid/app/Activity;)V\n'

def bypass(body, scene=False):
    lines = f'    invoke-static {{p0}}, {follow}->story(Landroid/app/Activity;)Z\n'
    lines += '    move-result v0\n    if-eqz v0, :pepper_default_scroll\n'
    if scene:
        lines += f'    invoke-static {{p0, p1}}, {follow}->scene(Landroid/app/Activity;I)V\n'
    return entry(body, lines + '    return-void\n    :pepper_default_scroll\n')

file = app / 'ImmersiveModeActivity.smali'
text = file.read_text(encoding='utf-8')
text = patch(text, 'onCreate(Landroid/os/Bundle;)V', lambda b: replace_once(b, '    return-void', call('attach') + '    return-void'))
text = patch(text, 'onDestroy()V', lambda b: entry(b, call('detach')))
text = patch(text, 'startGentleScrolling()V', bypass)
text = patch(text, 'finishGentleScrolling()V', bypass)
text = patch(text, 'startSceneScroll(IJ)V', lambda b: bypass(b, scene=True))
text = patch(text, 'showSceneImage(I)V', lambda b: replace_once(b, '    if-nez v0, :cond_2\n', '    if-nez v0, :cond_2\n\n' + call('pending')))
file.write_text(text, encoding='utf-8')

file = app / 'ImmersiveModeActivity$showSceneImage$1$1.smali'
text = file.read_text(encoding='utf-8')
def finish(body):
    lines = f'    iget-object v0, p0, {picasso}->this$0:{scene_callback}\n'
    lines += f'    iget-object v0, v0, {scene_callback}->this$0:{activity}\n'
    lines += f'    invoke-static {{v0}}, {follow}->ready(Landroid/app/Activity;)V\n'
    return replace_once(body, '    return-void', lines + '    return-void')
text = patch(text, 'onSuccess()V', finish)
text = patch(text, 'onError(Ljava/lang/Exception;)V', finish)
file.write_text(text, encoding='utf-8')

file = app / 'MainActivity.smali'
text = file.read_text(encoding='utf-8')
def identity(body):
    lines = '    const-string v2, "\\n\\nPepper identity: You are Pepper, a female humanoid robot. Use feminine forms when referring to yourself in Italian and she/her in English when referring to yourself in the third person. Keep the selected personality and use the active conversation language."\n'
    lines += '    invoke-virtual {v1, v2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;\n'
    lines += '    move-result-object v1\n'
    return replace_once(body, '    return-object v1', lines + '    return-object v1')
text = patch(text, 'readSystemPrompt()Ljava/lang/String;', identity)
file.write_text(text, encoding='utf-8')
