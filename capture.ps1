Start-Sleep -Seconds 3
Add-Type @"
    using System;
    using System.Runtime.InteropServices;
    public class Win32 {
        [DllImport("user32.dll")]
        public static extern bool SetForegroundWindow(IntPtr hWnd);
        [DllImport("user32.dll")]
        public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    }
"@

$procs = Get-Process | Where-Object { $_.ProcessName -match "java" }
foreach ($p in $procs) {
    if ($p.MainWindowHandle -ne [IntPtr]::Zero) {
        [Win32]::ShowWindow($p.MainWindowHandle, 9) # SW_RESTORE
        [Win32]::SetForegroundWindow($p.MainWindowHandle)
        Start-Sleep -Milliseconds 500
    }
}

Add-Type -AssemblyName System.Windows.Forms
[System.Windows.Forms.SendKeys]::SendWait("%{PRTSC}")
Start-Sleep -Milliseconds 500
$img = [System.Windows.Forms.Clipboard]::GetImage()
if ($img -ne $null) {
    $img.Save("C:\Users\andre\Documents\2026-04-28-Work-FastJava\FastGraphics3\comparison_shot.png", [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "Captured to comparison_shot.png"
} else {
    Write-Output "Failed to capture image from clipboard"
}
