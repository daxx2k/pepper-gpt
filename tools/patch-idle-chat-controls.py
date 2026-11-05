"""Connect MainActivity touch dispatch to the hands-free chat controls."""
from pathlib import Path
import sys

path = Path(sys.argv[1]) / 'MainActivity.smali'
source = path.read_text(encoding='utf-8')
signature = 'dispatchTouchEvent(Landroid/view/MotionEvent;)Z'
assert '.method public ' + signature not in source
source += f'''
.method public {signature}
    .registers 3
    invoke-static {{p0, p1}}, Lcom/softbankrobotics/pepper/pepperGPT/MainControlBar;->touch(Landroid/app/Activity;Landroid/view/MotionEvent;)V
    invoke-super {{p0, p1}}, Lcom/aldebaran/qi/sdk/design/activity/RobotActivity;->{signature}
    move-result v0
    return v0
.end method
'''
path.write_text(source, encoding='utf-8')
