"""Add the recipe thumbnail style to a decoded private stable APK; no resources change."""
from pathlib import Path
import argparse

parser = argparse.ArgumentParser()
parser.add_argument("decoded", type=Path)
args = parser.parse_args()
file = args.decoded / "smali_classes3/com/softbankrobotics/pepper/pepperGPT/ChatAdapter$RobotViewHolder.smali"
source = file.read_text(encoding="utf-8")
signature = ".method public final bind(Lcom/softbankrobotics/pepper/pepperGPT/ChatMessage;Ljava/lang/String;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)V"
start = source.index(signature)
end = source.index(".end method", start)
method = source[start:end]
assert "ChatImageStyle;->apply" not in method, "Thumbnail hook already applied"
anchor = "    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V"
assert method.count(anchor) == 1
hook = """

    invoke-virtual {p1}, Lcom/softbankrobotics/pepper/pepperGPT/ChatMessage;->getActivityType()Ljava/lang/String;
    move-result-object v0
    iget-object v1, p0, Lcom/softbankrobotics/pepper/pepperGPT/ChatAdapter$RobotViewHolder;->imageView:Landroid/widget/ImageView;
    invoke-static {v1, v0}, Lcom/softbankrobotics/pepper/pepperGPT/ChatImageStyle;->apply(Landroid/view/View;Ljava/lang/String;)V
"""
method = method.replace(anchor, anchor + hook)
file.write_text(source[:start] + method + source[end:], encoding="utf-8")
print("Recipe thumbnail hook applied; original text, image loading and tap callbacks retained.")
