# 逐页截取桌面版界面（用于与网页版 docs/screenshots 比对）
# 用法: powershell -File tools\screens.ps1 [-OutDir DIR]
param(
    [string]$OutDir = "$env:TEMP\shots"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$gradlew = "e:\ke\cook\multiplatform\gradlew.bat"
$project = "e:\ke\cook\multiplatform"

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShotApi {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@

function Capture([string]$file) {
    $deadline = (Get-Date).AddSeconds(90)
    $proc = $null
    while ((Get-Date) -lt $deadline) {
        foreach ($p in (Get-Process -Name java, javaw -ErrorAction SilentlyContinue)) {
            if ($p.MainWindowHandle -ne 0 -and $p.MainWindowTitle -ne "") { $proc = $p; break }
        }
        if ($proc) { break }
        Start-Sleep -Milliseconds 700
    }
    if (-not $proc) { Write-Output "NO_WINDOW for $file"; return $null }
    [void][ShotApi]::ShowWindow($proc.MainWindowHandle, 9)
    [void][ShotApi]::SetForegroundWindow($proc.MainWindowHandle)
    Start-Sleep -Milliseconds 2500
    $r = New-Object ShotApi+RECT
    [void][ShotApi]::GetWindowRect($proc.MainWindowHandle, [ref]$r)
    $w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Output ("OK {0} {1}x{2}" -f $file, $w, $h)
    return $proc
}

$cases = @(
    @{ name = "timer-dark"; page = "timer"; theme = "dark"; about = "0" },
    @{ name = "timer-light"; page = "timer"; theme = "light"; about = "0" },
    @{ name = "stats-dark"; page = "stats"; theme = "dark"; about = "0" },
    @{ name = "todos-dark"; page = "todos"; theme = "dark"; about = "0" },
    @{ name = "settings-light"; page = "settings"; theme = "light"; about = "0" },
    @{ name = "about-light"; page = "settings"; theme = "light"; about = "1" }
)

foreach ($c in $cases) {
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
    $env:POMODORO_PAGE = $c.page
    $env:POMODORO_THEME = $c.theme
    $env:POMODORO_ABOUT = $c.about

    $starter = Start-Process -FilePath $gradlew -ArgumentList @("-p", $project, ":composeApp:run", "--console=plain", "-q") -PassThru -WindowStyle Hidden
    $proc = Capture (Join-Path $OutDir ($c.name + ".png"))
    if ($proc) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    Start-Process -FilePath "taskkill" -ArgumentList @("/F", "/T", "/PID", $starter.Id) -Wait -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 2
}
Write-Output "DONE -> $OutDir"
