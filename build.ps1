param(
  [string]$AndroidSdk = $env:ANDROID_SDK_ROOT,
  [string]$JavaRoot = $env:JAVA_HOME,
  [string]$Python = 'python'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk  = $AndroidSdk
$jdk  = $JavaRoot
if (-not $sdk) { throw 'ANDROID_SDK_ROOT is not set (or pass -AndroidSdk)' }
if (-not $jdk -or !(Test-Path (Join-Path $jdk 'bin\javac.exe'))) { throw 'JDK not found: set JAVA_HOME or pass -JavaRoot (JDK 17 required)' }
$bt   = Join-Path $sdk 'build-tools\36.0.0'
$platform = Join-Path $sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$bt;$env:PATH"

function Step([string]$name, [scriptblock]$block) {
  Write-Host "== $name"
  & $block
  if ($LASTEXITCODE -ne 0) { throw "failed: $name ($LASTEXITCODE)" }
}

foreach ($d in 'build\classes','build\dex','out') {
  New-Item -ItemType Directory -Path (Join-Path $root $d) -Force | Out-Null
}
$res = Join-Path $root 'res'
if (!(Test-Path $res)) { New-Item -ItemType Directory -Path $res | Out-Null }

$cp = "$platform;$root\libs\shizuku-api.jar;$root\libs\shizuku-provider.jar;$root\libs\shizuku-aidl.jar;$root\libs\shizuku-shared.jar;$root\libs\annotation.jar"
Step 'javac' { & "$jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $cp -d "$root\build\classes" "$root\src\com\smr\rootapp\MainActivity.java" "$root\src\com\smr\rootapp\App.java" }

$classFiles = @(Get-ChildItem "$root\build\classes" -Recurse -Filter '*.class' | ForEach-Object { $_.FullName })
Write-Host "class files: $($classFiles.Count)"
Step 'd8' { & "$bt\d8.bat" --min-api 26 --lib $platform --output "$root\build\dex" @classFiles "$root\libs\shizuku-api.jar" "$root\libs\shizuku-provider.jar" "$root\libs\shizuku-aidl.jar" "$root\libs\shizuku-shared.jar" }

Step 'aapt package' { & "$bt\aapt.exe" package -f -M "$root\AndroidManifest.xml" -S $res -A "$root\assets" -I $platform -0 zip -F "$root\build\unsigned.apk" }

Push-Location "$root\build\dex"
try {
  Step 'aapt add' { & "$bt\aapt.exe" add "$root\build\unsigned.apk" 'classes.dex' }
} finally { Pop-Location }

Step 'zipalign' { & "$bt\zipalign.exe" -f 4 "$root\build\unsigned.apk" "$root\build\aligned.apk" }

$signing = Join-Path $root '.signing'
New-Item -ItemType Directory -Path $signing -Force | Out-Null
$key = Join-Path $signing 'root.jks'
$passFile = Join-Path $signing 'password.txt'
$aapt2 = Join-Path $bt 'apksigner.bat'
if (!(Test-Path $key)) {
  [IO.File]::WriteAllText($passFile, [Guid]::NewGuid().ToString('N'), [Text.UTF8Encoding]::new($false))
  Step 'keytool' { & "$jdk\bin\keytool.exe" -genkeypair -keystore $key -storepass:file $passFile -keypass:file $passFile -alias root -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Shizuku Temp Root' }
}
$apk = Join-Path $root 'out\ShizukuRoot.apk'
Step 'apksigner' { & $aapt2 sign --ks $key --ks-key-alias root --ks-pass "file:$passFile" --out $apk "$root\build\aligned.apk" }
Step 'verify' { & $aapt2 verify --verbose $apk }

Get-Item $apk | Select-Object FullName, Length
