from pathlib import Path
import re
import sys

app = Path(sys.argv[1]) / 'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
prefix = 'Lcom/softbankrobotics/pepper/pepperGPT/'

def edit(file, transform):
    path = app / file
    path.write_text(transform(path.read_text(encoding='utf-8')), encoding='utf-8')

def once(s, old, new):
    assert s.count(old) == 1, old
    return s.replace(old, new, 1)

for name in ['generateSceneImage', 'generateRemainingScenesInBackground']:
    edit(f'ImmersiveModeActivity${name}$1.smali', lambda s: once(
        once(s, '"gpt-image-1"', '"gpt-image-2.5-flare"'), '"medium"', '"low"'))

# The restored APK has separate literals for the cover and the generic image fallback.
edit('MainActivity$generateStory$firstSceneResult$1.smali', lambda s: once(
    s, '"gpt-image-1-mini"', '"gpt-image-2.5-flare"'))

def image_models(s):
    s = once(s, 'const-string v1, "gpt-image-1-mini"', 'const-string v1, "gpt-image-2.5-sunburst"')
    return s.replace('"gpt-image-1"', '"gpt-image-2.5-flare"').replace(
        '"gpt-image-1-mini"', '"gpt-image-2.5-flare"')

edit('ImageGenerationHelper.smali', image_models)

def fade_cover(s):
    head, success = s.split('.method public onSuccess()V', 1)
    call = f'''    iget-object v0, p0, {prefix}ImmersiveModeActivity$loadIllustration$1;->this$0:{prefix}ImmersiveModeActivity;
    invoke-static {{v0}}, {prefix}StoryFollow;->ready(Landroid/app/Activity;)V

'''
    return head + '.method public onSuccess()V' + once(success, '    return-void', call + '    return-void')

edit('ImmersiveModeActivity$loadIllustration$1.smali', fade_cover)

def schedule_waiting_pose(s):
    signature = '.method private final generateSceneImage(IZ)V'
    head, body = s.split(signature, 1)
    method, tail = body.split('.end method', 1)
    call = f'    invoke-static {{p0, p1}}, {prefix}StoryFollow;->illustrationRequested(Landroid/app/Activity;I)V\n\n'
    method = once(method, '    .line 246\n', call + '    .line 246\n')
    return head + signature + method + '.end method' + tail

def finish_waiting_pose(s):
    callback = prefix + 'ImmersiveModeActivity$generateSceneImage$1$1;'
    job = prefix + 'ImmersiveModeActivity$generateSceneImage$1;'
    screen = prefix + 'ImmersiveModeActivity;'
    call = (
        f'    iget-object v0, p0, {callback}->this$0:{job}\n'
        f'    iget-object v0, v0, {job}->this$0:{screen}\n'
        f'    invoke-static {{v0}}, {prefix}StoryFollow;->stopWaiting(Landroid/app/Activity;)V\n\n'
    )
    return once(s, '    .line 256\n', call + '    .line 256\n')

edit('ImmersiveModeActivity.smali', schedule_waiting_pose)
edit('ImmersiveModeActivity$generateSceneImage$1$1.smali', finish_waiting_pose)

def spinner_hook(s, kind):
    owner = prefix + f'MainActivity${kind}Spinner$1;'
    action = 'spinnerShown' if kind == 'show' else 'spinnerHidden'
    head, run = s.split('.method public final run()V', 1)
    call = (
        f'    iget-object v0, p0, {owner}->this$0:{prefix}MainActivity;\n'
        f'    invoke-static {{v0}}, {prefix}RealtimeLab;->{action}(Landroid/app/Activity;)V\n\n'
    )
    return head + '.method public final run()V' + once(run, '    return-void', call + '    return-void')

for kind in ('show', 'hide'):
    edit(f'MainActivity${kind}Spinner$1.smali', lambda s: spinner_hook(s, kind))

def story_handoff(s):
    a = s.index('->access$isStoryRequest(')
    b = s.index('->generateStory(', a)
    route = prefix + 'RealtimeLab;->'
    return s[:a] + once(s[a:b], route + 'feature(', route + 'storyFeature(') + s[b:]

edit('MainActivity$sendToOpenAI$1.smali', story_handoff)

def end_wait_before_speech(s):
    signature = '.method private final speak(Ljava/lang/String;)V'
    head, rest = s.split(signature, 1)
    method, tail = rest.split('.end method', 1)
    line = '    .line 1172'
    call = f'    invoke-static {{p0}}, {prefix}RealtimeLab;->spinnerHidden(Landroid/app/Activity;)V\n\n'
    return head + signature + once(method, line, call + line) + '.end method' + tail

edit('MainActivity.smali', end_wait_before_speech)

def main_control_bar(s):
    def method_patch(source, signature, transform):
        head, rest = source.split(signature, 1)
        method, tail = rest.split('.end method', 1)
        return head + signature + transform(method) + '.end method' + tail

    def attach(method):
        call = f'    invoke-static/range {{p0 .. p0}}, {prefix}MainControlBar;->attach(Landroid/app/Activity;)V\n\n'
        return once(method, '    return-void', call + '    return-void')

    def cancel(method):
        call = f'''    invoke-static {{p0}}, {prefix}MainControlBar;->cancelIfBusy(Landroid/app/Activity;)Z
    move-result v0
    if-eqz v0, :control_bar_normal
    return-void
    :control_bar_normal
'''
        return once(method, '    .line 463', call + '    .line 463')

    def illustration(method):
        anchor = '    if-eqz v11, :cond_705\n\n'
        call = f'    invoke-static {{v2}}, {prefix}RealtimeLab;->storyIllustrationPending(Landroid/app/Activity;)V\n\n'
        return once(method, anchor + '    .line 1609', anchor + call + '    .line 1609')

    s = method_patch(s, ' onCreate(Landroid/os/Bundle;)V', attach)
    s = method_patch(s, ' toggleListening()V', cancel)
    return method_patch(s, ' generateStory(Ljava/lang/String;Lokhttp3/OkHttpClient;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;', illustration)

edit('MainActivity.smali', main_control_bar)
