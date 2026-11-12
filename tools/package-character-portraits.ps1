param(
    [Parameter(Mandatory=$true)][string]$SourceDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$ProjectDirectory=(Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$roleSource=Get-Content -LiteralPath (Join-Path $ProjectDirectory 'src/main/java/com/myriad/character/CharacterType.java') -Raw
$ids=@([regex]::Matches($roleSource,'(?m)^    [A-Z]+\("([a-z]+)"') | ForEach-Object {$_.Groups[1].Value})
$lang=Get-Content -LiteralPath (Join-Path $ProjectDirectory 'src/main/resources/assets/myriad/lang/zh_cn.json') -Raw | ConvertFrom-Json -AsHashtable
$textures=Join-Path $ProjectDirectory 'src/main/resources/assets/myriad/textures/gui/sprites/portraits'
New-Item -ItemType Directory -Path $OutputDirectory,$textures -Force | Out-Null
$sheet=[System.Drawing.Bitmap]::new(1280,[int]([Math]::Ceiling($ids.Count/5.0)*286+52))
$canvas=[System.Drawing.Graphics]::FromImage($sheet)
$canvas.Clear([System.Drawing.Color]::FromArgb(16,23,33))
$canvas.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$font=[System.Drawing.Font]::new('Microsoft YaHei',13)
$small=[System.Drawing.Font]::new('Microsoft YaHei',9)
$brush=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(227,218,197))
$canvas.DrawString('仙凡劫 · 全角色头像   /   大图预览 + 32px 实际尺寸',$font,$brush,16,14)
try {
    for($i=0;$i -lt $ids.Count;$i++) {
        $id=$ids[$i]
        $name=$lang['character.myriad.'+$id]
        $source=Join-Path $SourceDirectory ($id+'.png')
        if(-not (Test-Path -LiteralPath $source)){$source=Join-Path $SourceDirectory ($name+'.png')}
        $img=[System.Drawing.Image]::FromFile($source)
        try {
            if($img.Width -ne $img.Height -or $img.Width -lt 512){throw "Invalid portrait dimensions: $id"}
            $thumb=[System.Drawing.Bitmap]::new(256,256)
            $g=[System.Drawing.Graphics]::FromImage($thumb)
            try {$g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic;$g.DrawImage($img,0,0,256,256)} finally {$g.Dispose()}
            try {
                $thumb.Save((Join-Path $textures ($id+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
                $thumb.Save((Join-Path $OutputDirectory ($name+'.png')),[System.Drawing.Imaging.ImageFormat]::Png)
                $x=16+($i%5)*256;$y=52+[int][Math]::Floor($i/5.0)*286
                $canvas.DrawImage($thumb,$x,$y,220,220)
                $canvas.DrawString($lang['character.myriad.'+$id],$font,$brush,$x,$y+224)
                $canvas.DrawImage($thumb,$x+188,$y+247,32,32)
                $canvas.DrawString($id,$small,$brush,$x,$y+251)
            } finally {$thumb.Dispose()}
            Write-Output "$id : $($img.Width)x$($img.Height) -> 256x256"
        } finally {$img.Dispose()}
    }
    $sheet.Save((Join-Path (Split-Path $OutputDirectory -Parent) '全角色头像总览.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally {$canvas.Dispose();$sheet.Dispose();$font.Dispose();$small.Dispose();$brush.Dispose()}
Write-Output "Packaged $($ids.Count) portraits."
