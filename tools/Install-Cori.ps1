[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Serial,[string]$Adb='adb')
$ErrorActionPreference='Stop'
$taskVoiceDir=Join-Path $env:LOCALAPPDATA 'PepperGPT\voice'
New-Item -ItemType Directory -Force -Path $taskVoiceDir | Out-Null
$taskVoiceFile=Join-Path $taskVoiceDir 'Cori-armv7-1.10.1.apk'
$taskHash='44d4a547889f5df3d051c2b1a92e215dacd232b6f110c7f3426235db26a42c40'
$taskUrl='https://huggingface.co/csukuangfj/sherpa-onnx-apk/resolve/main/tts-engine-2/sherpa-onnx-1.10.1-armeabi-v7a-en-tts-engine-vits-piper-en_GB-cori-medium.apk'
& $Adb -s $Serial get-state
if($LASTEXITCODE -ne 0){throw 'Tablet unavailable; connect and authorize ADB first.'}
if(-not(Test-Path -LiteralPath $taskVoiceFile) -or (Get-FileHash -LiteralPath $taskVoiceFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskHash){
    Invoke-WebRequest -Uri $taskUrl -OutFile $taskVoiceFile
}
if((Get-FileHash -LiteralPath $taskVoiceFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskHash){throw 'Cori download checksum mismatch.'}
& $Adb -s $Serial install -r -d $taskVoiceFile
if($LASTEXITCODE -ne 0){throw 'Cori installation failed; no device data was cleared.'}
Write-Output 'Cori installed. Choose Piper / Cori in PepperGPT Settings and test the voice.'
