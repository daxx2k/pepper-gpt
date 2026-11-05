from pathlib import Path
import sys
base=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
path=base/'ChatAdapter$RobotViewHolder.smali'
source=path.read_text(encoding='utf-8')
start=source.index('.method public final bind(')
end=source.index('.end method',start)
s=source[start:end]
assert 'CameraChat;->bind' not in s
anchor='    invoke-static {v1, v0}, Lcom/softbankrobotics/pepper/pepperGPT/ChatImageStyle;->apply(Landroid/view/View;Ljava/lang/String;)V'
s=s.replace(anchor,anchor+'\n    invoke-static {v1}, Lcom/softbankrobotics/pepper/pepperGPT/CameraChat;->reset(Landroid/widget/ImageView;)V')
assert s.count('    return-void')==1
s=s.replace('    return-void','''    iget-object v0, p0, Lcom/softbankrobotics/pepper/pepperGPT/ChatAdapter$RobotViewHolder;->imageView:Landroid/widget/ImageView;
    invoke-static {v0, p1}, Lcom/softbankrobotics/pepper/pepperGPT/CameraChat;->bind(Landroid/widget/ImageView;Ljava/lang/Object;)V
    return-void''')
path.write_text(source[:start]+s+source[end:],encoding='utf-8')
