from pathlib import Path
import json,re,sys

app=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
helper='Lcom/softbankrobotics/pepper/pepperGPT/UiItalian;'
labels=set(json.loads((Path(__file__).parent/'stable-voice/ui-italian-labels.json').read_text(encoding='utf-8')))
screens={'MainActivity','SettingsActivity','HelpActivity','FullscreenEditorActivity','ImmersiveModeActivity','RadioActivity','WeatherModeActivity'}
classes=screens|{'VoiceVolume','LanguageSwitch','ModelSettings','LegacyVoiceAdapter','RealtimeLab'}
literal=re.compile(r'(?m)^(\s*const-string(?:/jumbo)? ([vp]\d+), ("(?:\\.|[^"\\])*"))$')
def translate(m):
    try: text=json.loads(m[3].replace(r"\'","'"))
    except ValueError: return m[0]
    if text not in labels or text in {'No','OK'}: return m[0]
    return (f'{m[1]}\n    invoke-static/range {{{m[2]} .. {m[2]}}}, {helper}->text(Ljava/lang/String;)Ljava/lang/String;'
            f'\n    move-result-object {m[2]}')
def on_create(m):
    s=m[0]
    start=f'\n    invoke-static/range {{p0 .. p0}}, {helper}->attach(Landroid/app/Activity;)V\n'
    end=f'    invoke-static/range {{p0 .. p0}}, {helper}->screen(Landroid/app/Activity;)V\n'
    s=re.sub(r'(\.locals \d+\n)',lambda match:match[0]+start,s,count=1)
    return s.replace('    return-void',end+'    return-void')
for f in app.glob('*.smali'):
    cls=f.stem.split('$')[0]
    if cls not in classes: continue
    s=literal.sub(translate,f.read_text(encoding='utf-8'))
    if f.stem in screens:
        s=re.sub(r'(?ms)^\.method [^\n]* onCreate\(Landroid/os/Bundle;\)V\n.*?^\.end method',on_create,s)
    if f.stem=='MainActivity':
        marker='->inflate(ILandroid/view/Menu;)V'
        assert s.count(marker)==1
        s=s.replace(marker,marker+f'\n\n    invoke-static {{p1}}, {helper}->menu(Landroid/view/Menu;)V')
    if f.stem=='WeatherModeActivity':
        s=s.replace('Ljava/util/Locale;->getDefault()Ljava/util/Locale;',helper+'->dateLocale()Ljava/util/Locale;')
    f.write_text(s,encoding='utf-8')
