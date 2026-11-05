from pathlib import Path
import re
import sys

app = Path(sys.argv[1])
prefix = 'Lcom/softbankrobotics/pepper/pepperGPT/'
catalog = prefix + 'RadioStations;'
station = prefix + 'RadioStations$Station;'
selection = prefix + 'RadioStations$Selection;'

def once(s, old, new):
    assert s.count(old) == 1, old
    return s.replace(old, new)

def patch(file, transform):
    p = app / (file + '.smali')
    p.write_text(transform(p.read_text(encoding='utf-8')), encoding='utf-8')

def launch_radio(s):
    pattern = r'(?ms)(    :cond_b8\n).*?(    :goto_f0\n    nop\n\n    \.line 1899\n)'
    code = f'''    invoke-static {{v3}}, {catalog}->resolve(Ljava/lang/String;){station}
    move-result-object v5
    iget-object v2, v5, {station}->url:Ljava/lang/String;
    iget-object v4, v5, {station}->name:Ljava/lang/String;

'''
    s, count = re.subn(pattern, lambda m: m[1] + code + m[2], s)
    assert count == 1
    return s

def radio_activity(s):
    s = once(s, f'.implements Lcom/aldebaran/qi/sdk/RobotLifecycleCallbacks;',
             f'.implements Lcom/aldebaran/qi/sdk/RobotLifecycleCallbacks;\n.implements {selection}')
    anchor = f'    invoke-static/range {{p0 .. p0}}, {prefix}UiItalian;->screen(Landroid/app/Activity;)V'
    s = once(s, anchor, anchor + f'\n    invoke-static {{p0, p0}}, {catalog}->install(Landroid/app/Activity;{selection})V')
    s = once(s, 'invoke-static {p3}, Landroid/graphics/Color;->parseColor(Ljava/lang/String;)I',
             f'invoke-static {{p0}}, {catalog}->visualizerColor(Landroid/content/Context;)I')
    return s + f'''
.method public select({station})V
    .registers 5
    iget-object v0, p1, {station}->name:Ljava/lang/String;
    iget-object v1, p1, {station}->url:Ljava/lang/String;
    const/4 v2, 0x0
    invoke-direct {{p0, v0, v1, v2}}, {prefix}RadioActivity;->changeStation(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    return-void
.end method
'''

patch('MainActivity', launch_radio)
patch('RadioActivity', radio_activity)

def playing_test(s):
    pattern = r'(?ms)    const-string v[12], "Playing"\n.*?    invoke-static \{v0, v[12], v[12], v3, v4\}, Lkotlin/text/StringsKt;->contains\$default\([^\n]+\n'
    s, count = re.subn(pattern, f'    invoke-static {{v0}}, {catalog}->playingLabel(Ljava/lang/CharSequence;)Z\n', s)
    assert count == 1
    return s

def status_panel(s):
    s = once(s, '    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V',
             f'    invoke-static {{v0, v1}}, {catalog}->showStatus(Landroid/widget/TextView;Ljava/lang/String;)V')
    s = once(s, '    check-cast v1, Ljava/lang/CharSequence;\n\n    invoke-static {v0, v1}',
             '    invoke-static {v0, v1}')
    for old in ('const v2, -0xff0100', 'const/16 v2, -0x100'):
        s = once(s, '    '+old,
                 f'    invoke-virtual {{v0}}, {prefix}MusicVisualizerView;->getContext()Landroid/content/Context;\n'
                 f'    move-result-object v2\n    invoke-static {{v2}}, {catalog}->visualizerColor(Landroid/content/Context;)I\n'
                 '    move-result v2')
    return playing_test(s)

def initial_visualizer(s):
    anchor = f'    invoke-static {{p0, p0}}, {catalog}->install(Landroid/app/Activity;{selection})V'
    return once(s, anchor, anchor + f"""
    iget-object v0, p0, {prefix}RadioActivity;->visualizerView:{prefix}MusicVisualizerView;
    invoke-static {{p0}}, {catalog}->visualizerColor(Landroid/content/Context;)I
    move-result v1
    invoke-virtual {{v0, v1}}, {prefix}MusicVisualizerView;->setColor(I)V""")

patch('RadioActivity$onCreate$11', playing_test)
patch('RadioActivity$onCreate$1$onStatusChange$1', status_panel)
patch('RadioActivity', initial_visualizer)
