param(
    [Parameter(Mandatory=$true)][string]$PickSource,
    [Parameter(Mandatory=$true)][string]$TabSource,
    [string]$OutputDirectory='E:/mod/策划文件/UI素材_2026-09-26'
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$drawingRefs=@([System.Drawing.Bitmap].Assembly.Location,[System.Drawing.Color].Assembly.Location)
foreach($name in @('System.Private.Windows.GdiPlus.dll','System.Private.Windows.Core.dll')) {
    $ref=Join-Path $PSHOME $name
    if(Test-Path $ref){$drawingRefs+=$ref}
}
Add-Type -ReferencedAssemblies $drawingRefs -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
public static class MyriadUiPixels {
    // One shared hand-selected palette keeps both UI textures in the same visual family.
    static readonly string[] Hex = {
        "070f16","091820","0c2028","102832","15303a","1b3943","22434c","2b4e56",
        "365c63","426a70","527b80","649197","7ba9ae","93c0c1","b0d6ce","d2e6d5",
        "15191c","202526","2c3231","3c4340","50534a","686951","82805e","9a936a",
        "b1a678","c8bd8d","e3d2a0","f3e5bb","281b20","3b2527","513031","683b36",
        "82493f","9c5949","b96b51","d1825e","e5a16c","f5c082","273e3b","34524b",
        "42685d","548273","69a08a","83baa1","a2d2b7","181c2a","252b3d","39415a"
    };
    public static void Convert(string source,string dest,int width,int height) {
        Color[] palette=Array.ConvertAll(Hex,h=>ColorTranslator.FromHtml("#"+h));
        using(Bitmap input=new Bitmap(source))
        using(Bitmap output=new Bitmap(width,height,PixelFormat.Format32bppArgb)) {
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                Color c=input.GetPixel(Math.Min(input.Width-1,(int)((x+.5)*input.Width/width)),Math.Min(input.Height-1,(int)((y+.5)*input.Height/height)));
                int best=0; long score=long.MaxValue;
                for(int p=0;p<palette.Length;p++) {
                    long r=c.R-palette[p].R,g=c.G-palette[p].G,b=c.B-palette[p].B;
                    long d=2*r*r+3*g*g+b*b;
                    if(d<score){score=d;best=p;}
                }
                output.SetPixel(x,y,palette[best]);
            }
            output.Save(dest,ImageFormat.Png);
        }
    }
}
'@
$project=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $project 'src/main/resources/assets/myriad/textures/gui/sprites/xianxia'
$originals=Join-Path $OutputDirectory '高清原图'
$pixels=Join-Path $OutputDirectory '游戏用像素素材'
New-Item -ItemType Directory -Force -Path $runtime,$originals,$pixels | Out-Null
Copy-Item -LiteralPath $PickSource -Destination (Join-Path $originals '选角界面背景.png')
Copy-Item -LiteralPath $TabSource -Destination (Join-Path $originals 'Tab战绩面板.png')
[MyriadUiPixels]::Convert($PickSource,(Join-Path $pixels 'pick_background.png'),512,288)
[MyriadUiPixels]::Convert($TabSource,(Join-Path $pixels 'tab_panel.png'),512,256)
Copy-Item -LiteralPath (Join-Path $pixels 'pick_background.png') -Destination $runtime
Copy-Item -LiteralPath (Join-Path $pixels 'tab_panel.png') -Destination $runtime
Write-Output "Packaged UI artwork: $OutputDirectory"
