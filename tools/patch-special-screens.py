from pathlib import Path
import sys

app = Path(sys.argv[1])
prefix = 'Lcom/softbankrobotics/pepper/pepperGPT/'
activity = 'Landroid/app/Activity;'
qi = 'Lcom/aldebaran/qi/sdk/QiContext;'
future = 'Lcom/aldebaran/qi/Future;'

def edit(file, transform):
    p = app / (file + '.smali')
    p.write_text(transform(p.read_text(encoding='utf-8')), encoding='utf-8')

def once(s, old, new):
    assert s.count(old) == 1, old
    return s.replace(old, new)

def method(s, signature, edit_method):
    head, body = s.split(signature, 1)
    body, tail = body.split('.end method', 1)
    return head + signature + edit_method(body) + '.end method' + tail

def close(register):
    return f'    invoke-static {{{register}}}, {prefix}SpecialScreen;->closeAfterSpeech({activity})V\n'

def cancel(register):
    return f'    invoke-static {{{register}}}, {prefix}SpecialScreen;->cancel({activity})V\n'

weather = prefix + 'WeatherModeActivity;'
main = prefix + 'MainActivity;'
immersive = prefix + 'ImmersiveModeActivity;'

def weather_create(s):
    anchor = f'    invoke-static {{v1, v2}}, Lcom/aldebaran/qi/sdk/QiSDK;->register({activity}Lcom/aldebaran/qi/sdk/RobotLifecycleCallbacks;)V\n'
    head, rest = s.split(anchor, 1)
    return head + anchor + f'''
    sget-object v1, Lcom/aldebaran/qi/sdk/design/activity/conversationstatus/SpeechBarDisplayStrategy;->OVERLAY:Lcom/aldebaran/qi/sdk/design/activity/conversationstatus/SpeechBarDisplayStrategy;
    invoke-virtual {{v0, v1}}, {weather}->setSpeechBarDisplayStrategy(Lcom/aldebaran/qi/sdk/design/activity/conversationstatus/SpeechBarDisplayStrategy;)V
    invoke-static {{v0}}, {prefix}WeatherPanel;->render({activity})V

    iget-object v1, v0, {weather}->uiHandler:Landroid/os/Handler;
    iget-object v4, v0, {weather}->closeRunnable:Ljava/lang/Runnable;
    const-wide/16 v2, 0x7530
    invoke-virtual {{v1, v4, v2, v3}}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
    return-void
'''

def weather_focus(_):
    return f'''
    .registers 6
    invoke-static {{p0, p1}}, {prefix}LegacyVoiceAdapter;->onFocusGained({activity}{qi})V
{cancel('p0')}
    iget-object v0, p0, {weather}->uiHandler:Landroid/os/Handler;
    iget-object v1, p0, {weather}->closeRunnable:Ljava/lang/Runnable;
    invoke-virtual {{v0, v1}}, Landroid/os/Handler;->removeCallbacks(Ljava/lang/Runnable;)V

    iget-object v0, p0, {weather}->animationManager:{prefix}AnimationManager;
    invoke-virtual {{v0, p1}}, {prefix}AnimationManager;->onFocusGained({qi})V
    invoke-virtual {{v0}}, {prefix}AnimationManager;->playShowTablet()V

    invoke-static {{p0}}, {prefix}WeatherPanel;->speech({activity})Ljava/lang/String;
    move-result-object v1
    invoke-static {{p1}}, Lcom/aldebaran/qi/sdk/builder/SayBuilder;->with({qi})Lcom/aldebaran/qi/sdk/builder/SayBuilder;
    move-result-object v0
    invoke-static {{v0, v1}}, {prefix}LegacyVoiceAdapter;->withText(Lcom/aldebaran/qi/sdk/builder/SayBuilder;Ljava/lang/String;)Lcom/aldebaran/qi/sdk/builder/SayBuilder;
    move-result-object v0
    invoke-static {{v0}}, {prefix}LegacyVoiceAdapter;->buildAsync(Lcom/aldebaran/qi/sdk/builder/SayBuilder;){future}
    move-result-object v0
    invoke-static {{p0, v0}}, {prefix}WeatherNarration;->speak({activity}{future})V
    return-void
'''

def on_destroy(s):
    return method(s, ' onDestroy()V', lambda m: once(m, '    return-void', cancel('p0') + '    return-void'))

def weather_activity(s):
    s = method(s, ' onCreate(Landroid/os/Bundle;)V', weather_create)
    s = method(s, f' onRobotFocusGained({qi})V', weather_focus)
    return on_destroy(s)

edit('WeatherModeActivity', weather_activity)

def local_query(s):
    return once(s, '    const-string v5, "&units=metric"', f'''
    iget-object v5, p0, {prefix}MainActivity$getWeatherSuspending$2;->this$0:{main}
    invoke-static {{v5}}, {prefix}WeatherPanel;->queryUnits(Landroid/content/Context;)Ljava/lang/String;
    move-result-object v5''')

edit('MainActivity$getWeatherSuspending$2', local_query)

def immersive_activity(s):
    s = method(s, ' startStorySpeech()V', lambda m: once(m, '    if-eqz v3, :cond_35\n\n    return-void',
        '    if-eqz v3, :cond_35\n\n' + close('p0') + '    return-void'))
    return on_destroy(s)

edit('ImmersiveModeActivity', immersive_activity)

def scenes(s):
    owner = f'    iget-object v0, v8, {prefix}ImmersiveModeActivity$speakStoryByScenes$1;->this$0:{immersive}\n'
    s = once(s, '    :cond_13e\n    nop', '    :cond_13e\n' + owner + close('v0') + '    nop')
    return once(s, '    :cond_159\n    throw v0',
                '    :cond_159\n' + owner.replace('v0, v8', 'v4, v8') + close('v4') + '    throw v0')

edit('ImmersiveModeActivity$speakStoryByScenes$1', scenes)

def single_pass(s):
    owner = f'    iget-object v0, v0, {prefix}ImmersiveModeActivity$startStorySpeech$2;->this$0:{immersive}\n'
    return once(s, owner + '\n    const/4 v1, 0x0',
                owner + close('v0') + '\n    const/4 v1, 0x0')

edit('ImmersiveModeActivity$startStorySpeech$2$2$1', single_pass)

def resume_immersive(s):
    launch = f'    invoke-virtual {{v6, v12, v0}}, {main}->startActivityForResult(Landroid/content/Intent;I)V'
    return once(s, launch, f'''    iget-boolean v1, v6, {main}->wasListeningBeforeOpenAI:Z
    if-nez v1, :resume_after_immersive
    iget-boolean v1, v6, {main}->isListening:Z
    :resume_after_immersive
    iput-boolean v1, v6, {main}->wasListeningBeforeSpeaking:Z

''' + launch)

def resume_weather(s):
    launch = f'    invoke-virtual {{v12, v11, v13}}, {main}->startActivityForResult(Landroid/content/Intent;I)V'
    return once(s, launch, f'''    invoke-static {{v12}}, {main}->access$getWasListeningBeforeOpenAI$p({main})Z
    move-result v14
    invoke-static {{v12, v14}}, {main}->access$setWasListeningBeforeSpeaking$p({main}Z)V

''' + launch)

def main_activity(s):
    s = resume_immersive(s)
    return method(s, f' onRobotFocusGained({qi})V', lambda m: once(m,
        '    if-eqz v0, :cond_3e\n', '''    if-eqz v0, :cond_3e

    invoke-virtual {p0}, Landroid/app/Activity;->hasWindowFocus()Z
    move-result v0
    if-eqz v0, :cond_3e
'''))

edit('MainActivity', main_activity)
edit('MainActivity$sendToOpenAI$1$2', resume_weather)

def chat_header(s):
    owner = prefix + 'MainActivity$applyTheme$1;'
    return once(s, '    :cond_cb\n    return-void', f"""    :cond_cb
    iget-object v0, p0, {owner}->this$0:{main}
    invoke-static {{v0}}, {main}->access$getCurrentTheme$p({main})Ljava/lang/String;
    move-result-object v1
    invoke-static {{v0, v1}}, {prefix}ChatHeader;->apply({activity}Ljava/lang/String;)V
    invoke-static {{v0, v1}}, {prefix}MainControlBar;->theme({activity}Ljava/lang/String;)V
    return-void""")

edit('MainActivity$applyTheme$1', chat_header)

def reopen_with_theme(s):
    anchor = f"    :cond_70\n    iget-boolean v3, p0, {main}->isListening:Z"
    return once(s, anchor, f"""    :cond_70
    const-string v3, "EXTRA_THEME"
    iget-object v4, p0, {main}->currentTheme:Ljava/lang/String;
    invoke-virtual {{v2, v3, v4}}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    iget-boolean v3, p0, {main}->isListening:Z""")

def soft_bubble(s):
    old = """    const v2, 0x7f0500ab

    invoke-static {v0, v2}, Landroidx/core/content/ContextCompat;->getColor(Landroid/content/Context;I)I

    move-result v2"""
    return once(s, old, "    const v2, 0xff42688e")

def help_screen(s):
    support = "Landroidx/appcompat/app/"
    return method(s, " onCreate(Landroid/os/Bundle;)V", lambda _:
        f"""
    .registers 3
    invoke-super {{p0, p1}}, {support}AppCompatActivity;->onCreate(Landroid/os/Bundle;)V
    invoke-virtual {{p0}}, {prefix}HelpActivity;->getSupportActionBar(){support}ActionBar;
    move-result-object v0
    if-eqz v0, :show_help
    invoke-virtual {{v0}}, {support}ActionBar;->hide()V
    :show_help
    invoke-static {{p0}}, {prefix}HelpPanel;->render({activity})V
    return-void
""")

edit('MainActivity', lambda s: method(s, f' relaunchActivity({prefix}ChatMessage;)V', reopen_with_theme))
edit('ChatAdapter$UserViewHolder', soft_bubble)
edit('HelpActivity', help_screen)
