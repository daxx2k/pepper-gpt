from pathlib import Path
import re,sys

app=Path(sys.argv[1])/'smali_classes3/com/softbankrobotics/pepper/pepperGPT'
helper='Lcom/softbankrobotics/pepper/pepperGPT/RobotSsh;'
p=app/'RobotManager.smali'
s=p.read_text(encoding='utf-8')
marker='->context:Landroid/content/Context;\n\n    .line 36'
assert s.count(marker)==1
s=s.replace(marker,marker.replace('\n\n','\n\n    invoke-static {p1}, '+helper+'->init(Landroid/content/Context;)V\n\n'))
for field,key in [('pepperIp','ssh_host'),('sshUser','ssh_user'),('sshPassword','ssh_password')]:
 pattern=r'(?m)^    const-string v0, "[^"\n]*"(\n\n    iput-object v0, p0, [^\n]*->'+field+r':Ljava/lang/String;)'
 replacement=f'    const-string v0, "{key}"\n    invoke-static {{v0}}, {helper}->setting(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v0'
 s,count=re.subn(pattern,lambda m: replacement+m[1],s)
 assert count==1,field
marker='.method private final connectSsh()V\n    .locals 9\n'
assert s.count(marker)==1
s=s.replace(marker,marker+f'\n    invoke-static {{}}, {helper}->configured()Z\n    move-result v0\n    if-nez v0, :pepper_ssh_configured\n    return-void\n    :pepper_ssh_configured\n')
p.write_text(s,encoding='utf-8')
p=app/'RobotManager$connectSsh$1.smali'
s=p.read_text(encoding='utf-8')
marker='    invoke-direct {v2}, Lcom/jcraft/jsch/JSch;-><init>()V'
assert s.count(marker)==1
s=s.replace(marker,marker+f'\n\n    invoke-static {{v2}}, {helper}->trust(Lcom/jcraft/jsch/JSch;)V')
for old,new in [('diffie-hellman-group14-sha256','ecdh-sha2-nistp256,diffie-hellman-group14-sha256'),('no','yes')]:
 marker=f'const-string v6, "{old}"'
 assert s.count(marker)==1,old
 s=s.replace(marker,f'const-string v6, "{new}"')
p.write_text(s,encoding='utf-8')
