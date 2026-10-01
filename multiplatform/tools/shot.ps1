# 截取 Compose Desktop 应用窗口（ASCII-only，避免 PS 5.1 中文编码问题）
# 用法: powershell -File tools\shot.ps1 -Out "C:\path\shot.png" [-WaitSec 60]
param(
    [string]$Out = "$env:TEMP\desktop-app.png",
    [int]$WaitSec = 60
)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinApi {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@

function Find-AppWindow {
    $procs = Get-Process -Name java, javaw -ErrorAction SilentlyContinue
    foreach ($p in $procs) {
        if ($p.MainWindowHandle -ne 0 -and $p.MainWindowTitle -ne "") { return $p }
    }
    return $null
}

$deadline = (Get-Date).AddSeconds($WaitSec)
$proc = $null
while ((Get-Date) -lt $deadline) {
    $proc = Find-AppWindow
    if ($proc) { break }
    Start-Sleep -Milliseconds 800
}

if (-not $proc) { Write-Output "NO_WINDOW"; exit 1 }

[void][WinApi]::ShowWindow($proc.MainWindowHandle, 9)   # SW_RESTORE
[void][WinApi]::SetForegroundWindow($proc.MainWindowHandle)
Start-Sleep -Milliseconds 1200

$r = New-Object WinApi+RECT
[void][WinApi]::GetWindowRect($proc.MainWindowHandle, [ref]$r)
$w = $r.Right - $r.Left
$h = $r.Bottom - $r.Top
if ($w -le 0 -or $h -le 0) { Write-Output "BAD_RECT"; exit 1 }

$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Write-Output ("OK {0} {1}x{2} pid={3}" -f $Out, $w, $h, $proc.Id)
