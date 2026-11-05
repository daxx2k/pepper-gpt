from pathlib import Path
import sys

app = Path(sys.argv[1])
prefix = 'Lcom/softbankrobotics/pepper/pepperGPT/'
theme = prefix + 'AppThemeViews;'
activity = 'Landroid/app/Activity;'

def once(s, old, new):
    assert s.count(old) == 1, old
    return s.replace(old, new)

def method(s, signature, transform):
    head, rest = s.split(signature, 1)
    body, tail = rest.split('.end method', 1)
    return head + signature + transform(body) + '.end method' + tail

def edit(file, transform):
    p = app / (file + '.smali')
    p.write_text(transform(p.read_text(encoding='utf-8')), encoding='utf-8')

def form(s):
    return method(s, ' onCreate(Landroid/os/Bundle;)V', lambda body:
        once(body, '    return-void',
             f'    invoke-static/range {{p0 .. p0}}, {theme}->form({activity})V\n\n    return-void'))

for screen in ('SettingsActivity', 'FullscreenEditorActivity'):
    edit(screen, form)

def radio(s):
    s = method(s, ' applyTheme()V', lambda _:
        f'\n    .registers 1\n    invoke-static {{p0}}, {theme}->radio({activity})V\n    return-void\n')
    return method(s, ' updateDanceButton(Landroid/widget/Button;)V', lambda m: once(m, '    return-void',
        f'    iget-boolean v0, p0, {prefix}RadioActivity;->danceEnabled:Z\n'
        f'    invoke-static {{p1, v0}}, {theme}->dance(Landroid/widget/Button;Z)V\n    return-void'))

edit('RadioActivity', radio)

edit('ImmersiveModeActivity', lambda s: method(s, ' applyTheme(Ljava/lang/String;)V', lambda _:
    f'\n    .registers 2\n    invoke-static {{p0, p1}}, {theme}->immersive({activity}Ljava/lang/String;)V\n    return-void\n'))

builder = 'Landroid/app/AlertDialog$Builder;'
dialog = 'Landroid/app/AlertDialog;'
for file in ('LanguageSwitch', 'LegacyVoiceAdapter'):
    edit(file, lambda s: once(s,
        f'invoke-virtual {{p0}}, {builder}->show(){dialog}',
        f'invoke-static {{p0}}, {theme}->show({builder}){dialog}'))

edit('MainActivity', lambda s: once(s, '    invoke-virtual {v0}, Landroid/app/Dialog;->show()V',
    '    invoke-virtual {v0}, Landroid/app/Dialog;->show()V\n'
    f'    invoke-static {{v0}}, {theme}->photo(Landroid/app/Dialog;)V'))

def theme_menu(s):
    anchor = f'    invoke-static {{p1}}, {prefix}UiItalian;->menu(Landroid/view/Menu;)V'
    return once(s, anchor, anchor +
        f'\n    invoke-static {{p0, p1}}, {prefix}ChatHeader;->bindMenu({activity}Landroid/view/Menu;)V')

edit('MainActivity', theme_menu)
