$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$tools = 'C:\Users\progerok\zhurnal\tools'
$jdk = Join-Path $tools 'jdk17'
$sdk = Join-Path $tools 'android-sdk'
New-Item -ItemType Directory -Force -Path $tools, $sdk | Out-Null

if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    Write-Host 'Downloading JDK 17 (Temurin)...'
    $jdkZip = Join-Path $tools 'jdk17.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile $jdkZip
    $tmp = Join-Path $tools 'jdk-tmp'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $jdkZip -DestinationPath $tmp -Force
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $jdk
    Remove-Item -Force $jdkZip
    Write-Host 'JDK installed.'
} else {
    Write-Host 'JDK already present.'
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"

$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
    Write-Host 'Downloading Android command-line tools...'
    $cmdZip = Join-Path $tools 'cmdline-tools.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $cmdZip
    $tmp = Join-Path $tools 'cmd-tmp'
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive -Path $cmdZip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
    Remove-Item -Force $cmdZip
    Remove-Item -Recurse -Force $tmp
    Write-Host 'Command-line tools installed.'
}

Write-Host 'Accepting Android SDK licenses...'
$yes = (1..30 | ForEach-Object { 'y' }) -join "`n"
$yes | & $sdkManager --sdk_root=$sdk --licenses | Out-Null

Write-Host 'Installing SDK packages (platform 35, build-tools, platform-tools)...'
& $sdkManager --sdk_root=$sdk 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed with exit code $LASTEXITCODE" }

Write-Host 'SDK READY'
$jdk | Set-Content (Join-Path $tools 'jdk-path.txt')
$sdk | Set-Content (Join-Path $tools 'sdk-path.txt')
