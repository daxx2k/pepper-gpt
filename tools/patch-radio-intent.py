"""Add semantic radio recognition; all existing feature handlers remain intact."""
from pathlib import Path
import argparse
parser=argparse.ArgumentParser();parser.add_argument("decoded",type=Path);args=parser.parse_args()
p=args.decoded/"smali_classes3/com/softbankrobotics/pepper/pepperGPT/MainActivity.smali"
s=p.read_text(encoding="utf-8");start=s.index(".method private final isRadioRequest(Ljava/lang/String;)Z");end=s.index(".end method",start)+len(".end method")
assert "RadioIntent;->isRadioRequest" not in s[start:end], "Hook already applied"
replacement=""".method private final isRadioRequest(Ljava/lang/String;)Z
    .locals 1
    move-object v0, p1
    invoke-static {p1}, Lcom/softbankrobotics/pepper/pepperGPT/LanguageSwitch;->route(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p1
    invoke-static {p0, v0, p1}, Lcom/softbankrobotics/pepper/pepperGPT/RadioIntent;->isRadioRequest(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;)Z
    move-result v0
    return v0
.end method"""
p.write_text(s[:start]+replacement+s[end:],encoding="utf-8")
print("Semantic radio predicate installed; original handler and normal conversation unchanged.")
