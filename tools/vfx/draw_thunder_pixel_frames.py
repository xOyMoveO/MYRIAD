"""Authored code-native pixel contours and temporal curves, not AI output or edited prior art.

No service dependency. Shared 14-color palette, straight alpha, fixed pivot and eight real phases.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import math,random

ART=Path('E:/mod/role_assets/tianxing/20261107/layered_vfx_hd')
SIZE=256
PADDING=24
PALETTE=((20,65,160),(28,91,204),(35,118,235),(46,150,255),(50,184,255),
         (55,215,255),(73,236,255),(104,247,255),(144,252,255),(184,254,255),
         (211,252,255),(235,253,255),(250,254,255),(255,255,255))

def rgba(i,a=255):return (*PALETTE[i],round(max(0,min(255,a))))
class NativePixelDraw:
    """Rasterize float contours at the FINAL resolution, never enlarge a 128px bitmap."""
    def __init__(self,image):self.draw=ImageDraw.Draw(image);self.scale=(SIZE-2*PADDING)/128
    def coords(self,xy):
        if isinstance(xy[0],(int,float)):return [round(PADDING+x*self.scale) for x in xy]
        return [(round(PADDING+x*self.scale),round(PADDING+y*self.scale)) for x,y in xy]
    def line(self,xy,fill,width=1):self.draw.line(self.coords(xy),fill=fill,width=max(1,round(width*self.scale)),joint='curve')
    def polygon(self,xy,fill):self.draw.polygon(self.coords(xy),fill=fill)
    def ellipse(self,xy,fill):self.draw.ellipse(self.coords(xy),fill=fill)

def canvas():
    image=Image.new('RGBA',(SIZE,SIZE));return image,NativePixelDraw(image)

def point(cx,cy,r,angle):return (cx+math.cos(angle)*r,cy+math.sin(angle)*r)
def polyline(draw,points,width=2,alpha=255):
    if len(points)<2:return
    p=points
    draw.line(p,fill=rgba(0,alpha*.65),width=width+6)
    draw.line(p,fill=rgba(3,alpha*.88),width=width+3)
    draw.line(p,fill=rgba(6,alpha),width=width+1)
    draw.line(p,fill=rgba(12,alpha),width=max(1,width-1))
def sparks(draw,frame,count=16,radius=42,alpha=255,seed=1,expand=False):
    rng=random.Random(seed)
    for i in range(count):
        angle=rng.random()*math.tau;phase=rng.random();radius0=radius*(.43+.54*phase)
        r=radius0*(.58+frame*.075 if expand else 1+.04*math.sin(frame*.8+i))
        x,y=point(64,64,r,angle);brightness=.55+.45*math.sin(frame*.7+i*.9)**2
        if not 6<x<121 or not 6<y<121:continue
        color=rgba(8 if i%3 else 12,alpha*brightness)
        length=1+(i%3==0)
        draw.line((x-length,y,x+length,y),fill=color,width=1)
        if i%3==0:draw.line((x,y-length,x,y+length),fill=color,width=1)

def slash(frame):
    im,d=canvas()
    amount=(.38,.60,.86,1,1,.90,.70,.47)[frame]
    a=255 if frame<6 else (225 if frame==6 else 178)
    # One continuous curved cutting ridge on the RIGHT, with torn tips trailing left.
    begin=-1.60-.12*math.sin(frame*.4);finish=1.60+.07*math.sin(frame*.7)
    n=192;outer=[];inner=[]
    for i in range(n+1):
        t=i/n;angle=begin+(finish-begin)*t
        envelope=math.sin(math.pi*t)**.62
        jag=(1.2*math.sin(t*115.2+frame*.6)+1.1*math.sin(t*41.6))*envelope
        r=46*amount+jag;outer.append(point(46,64,r,angle))
        inner.append(point(46,64,r-(7+7*envelope)*amount,angle))
    d.polygon(outer+inner[::-1],fill=rgba(2,a*.9))
    for band,col in ((3,4),(6,6),(9,9)):
        edge=[]
        for i,(x,y) in enumerate(outer):
            t=i/n;angle=begin+(finish-begin)*t
            edge.append((x-math.cos(angle)*band*amount,y-math.sin(angle)*band*amount))
        d.polygon(outer+edge[::-1],fill=rgba(col,a))
    d.line(outer,fill=rgba(13,a),width=2)
    # Secondary ribbons have distinct curl/radius, not copies of the same silhouette.
    for k in range(3):
        pts=[]
        for i in range(80):
            t=i/79;angle=-1.4+(2.8-.22*k)*t
            r=(28+k*5)*amount+math.sin(t*8+frame*.5+k)*1.8
            pts.append(point(42-k*3,64,r,angle))
        d.line(pts,fill=rgba(3+k*2,a*(.56+.12*k)),width=2)
        # A native one-pixel electric filament could not survive the previous 104px downsample.
        fine=[point(42-k*3,64,(27+k*5)*amount+math.sin(t*10+frame*.5+k)*1.2,-1.4+(2.8-.22*k)*t) for t in (i/111 for i in range(112))]
        d.line(fine,fill=rgba(10,a*.86),width=.45)
    for sign in (-1,1):
        tip=outer[0 if sign<0 else -1]
        polyline(d,[tip,(tip[0]-9,tip[1]-sign*4),(tip[0]-15,tip[1]+sign*2),(tip[0]-24,tip[1])],1,a*.9)
    sparks(d,frame,17,48,a,seed=20,expand=frame>=5)
    return im

def bolt(frame):
    im,d=canvas();phase=frame*math.tau/8
    points=[]
    for i in range(14):
        x=10+i*8;y=64 if i in (0,13) else 64+math.sin(i*2.6+phase)*5+math.sin(i*.9-phase)*3
        points.append((x,y))
    polyline(d,points,7)
    d.line([(x,y+.65*math.sin(i*2+phase)) for i,(x,y) in enumerate(points)],fill=rgba(13),width=.45)
    for i in (2,4,7,9,11):
        x,y=points[i];sign=-1 if i%2 else 1
        polyline(d,[(x,y),(x+5,y+sign*9),(x-1,y+sign*16),(x+9,y+sign*(22+3*math.sin(phase+i)))],1,220)
    # Travelling white pulse and blue broken parallel filament.
    pulse=12+(frame/8)*100
    d.polygon([(pulse-6,64),(pulse,59),(pulse+8,64),(pulse,69)],fill=rgba(12))
    d.line([(12+i*6,77+math.sin(i*.7-phase)*3) for i in range(17)],fill=rgba(3,170),width=1)
    sparks(d,frame,12,49,230,seed=6)
    return im

def impact(frame):
    im,d=canvas()
    radius=(9,17,28,39,45,48,50,52)[frame];alpha=(245,255,255,255,244,223,195,170)[frame]
    rng=random.Random(41);points=[]
    for i in range(32):
        a=i*math.tau/32;r=radius*(1 if i%4==0 else .68 if i%2==0 else .23)+rng.random()*2
        points.append(point(64,64,r,a))
    d.polygon(points,fill=rgba(2,alpha*.82))
    for scale,col in ((.86,5),(.64,8),(.35,12)):
        d.polygon([(64+(x-64)*scale,64+(y-64)*scale) for x,y in points],fill=rgba(col,alpha))
    for ring in range(2):
        r=radius*(.7+.21*ring)
        for i in range(8):
            a=i*math.tau/8+frame*.045+ring*.15
            pts=[point(64,64,r,a+t*.30) for t in (0,.25,.50,.75,1)]
            d.line(pts,fill=rgba(5+ring*3,alpha*.86),width=2)
    if frame>=3:
        # Opening the core is dissipation, not a static star scaled larger.
        d.ellipse((64-frame,64-frame,64+frame,64+frame),fill=(0,0,0,0))
        for i in range(8):
            a=i*math.tau/8
            p=point(64,64,radius*.65,a);q=point(64,64,radius*.94,a+.06)
            d.line((p,q),fill=rgba(11,alpha),width=1)
    sparks(d,frame,24,47,alpha,seed=13,expand=False)
    return im

def seal(frame):
    im,d=canvas();spin=frame*math.tau/48
    for r,col,width in ((46,3,2),(44,8,1),(35,5,1),(25,8,1)):
        pts=[point(64,64,r,i*math.tau/288) for i in range(289)]
        d.line(pts,fill=rgba(col,245),width=width)
    for i in range(6):
        a=spin+i*math.tau/6
        # Stepped lightning rune, radial blade and isolated tip; open center remains transparent.
        local=[(23,-2),(31,-2),(29,-9),(40,0),(31,0),(34,8),(23,-2)]
        pts=[(64+x*math.cos(a)-y*math.sin(a),64+x*math.sin(a)+y*math.cos(a)) for x,y in local]
        d.polygon(pts,fill=rgba(9));d.line(pts,fill=rgba(12),width=1)
        d.line([point(64,64,r,a) for r in (47,52)],fill=rgba(11),width=2)
        for j in (-1,1):
            p=point(64,64,39,a+j*.23);q=point(64,64,42,a+j*.23)
            d.line((p,q),fill=rgba(6),width=2)
        # Fine paired ticks retain the same large sixfold silhouette, but add real native detail.
        for tick in range(4):
            angle=a+.30+tick*.06
            d.line((point(64,64,36,angle),point(64,64,38.5,angle)),fill=rgba(11,245),width=.45)
    for i in range(18):
        a=i*math.tau/18-spin*.5
        d.line((point(64,64,18,a),point(64,64,21,a)),fill=rgba(7,230),width=1)
    sparks(d,frame,10,51,230,seed=9)
    return im

def corona(frame):
    im,d=canvas();phase=frame*math.tau/8
    for k in range(4):
        base=phase+k*math.tau/4;points=[]
        for j in range(113):
            t=j/112;a=base+t*1.35
            r=38+7*math.sin(t*3+k*.7)
            x=64+math.cos(a)*r;y=64+math.sin(a)*r*.89
            points.append((x,y))
        for width,col,alpha in ((7,1,155),(4,4,225),(2,7,255)):
            d.line(points,fill=rgba(col,alpha),width=width)
        polyline(d,points[-17:],1)
        d.line([(x+math.cos(base)*.8,y+math.sin(base)*.8) for x,y in points],fill=rgba(10,240),width=.45)
        tip=points[-1];a=base+1.35
        polyline(d,[tip,(tip[0]+math.cos(a)*7,tip[1]+math.sin(a)*7),(tip[0]-math.sin(a)*3,tip[1]+math.cos(a)*3)],1,240)
    for k in range(3):
        a=-phase*.5+k*math.tau/3
        pts=[point(64,64,24+2*math.sin(j*.8),a+j*.13) for j in range(9)]
        d.line(pts,fill=rgba(5,225),width=1)
    sparks(d,frame,18,53,245,seed=7)
    return im

def pillar(frame):
    im,d=canvas()
    intensity=(.60,.86,1,1,.94,.8,.67,.55)[frame]
    pts=[]
    for i in range(14):
        y=9+i*8;x=64 if i in (0,13) else 64+math.sin(i*2.75+frame*.45)*6
        pts.append((x,y))
    polyline(d,pts,round(2+2*intensity),255*intensity)
    d.line([(x+.45*math.sin(i*2+frame*.4),y) for i,(x,y) in enumerate(pts)],fill=rgba(13,255*intensity),width=.45)
    for i in (2,4,6,9,11):
        x,y=pts[i];sign=-1 if i%2 else 1
        # Stable parent branches drift slightly; later frames break into detached fragments.
        branch=[(x,y),(x+sign*12,y+4),(x+sign*9,y+11),(x+sign*(23+3*math.sin(frame*.6+i)),y+18)]
        if frame>=5:branch=branch[1:]
        polyline(d,branch,1,235*intensity)
        x0,y0=branch[-2];x1,y1=branch[-1]
        for fork in range(2):
            a=.3+.25*fork;x=x0+(x1-x0)*a;y=y0+(y1-y0)*a
            d.line([(x,y),(x+sign*3.5,y+1),(x+sign*2.2,y+4.5)],fill=rgba(10,230*intensity),width=.45)
    for k in (-1,1):
        path=[(64+k*17+math.sin(i*1.3+frame*.6)*3,16+i*7) for i in range(13)]
        d.line(path,fill=rgba(3,190*intensity),width=1)
    sparks(d,frame,18,49,255*intensity,seed=16)
    return im

def main():
    ART.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGBA',(SIZE*8,SIZE*6))
    for row,draw in enumerate((slash,bolt,impact,seal,corona,pillar)):
        for frame in range(8):
            image=draw(frame)
            assert image.getchannel('A').getbbox(),(row,frame)
            sheet.paste(image,(frame*SIZE,row*SIZE))
    sheet.save(ART/'source_sheet.png')
    print('Authored native 256px frames saved directly, float contours/fine branches, no low-resolution intermediate or bitmap upscale.')

if __name__=='__main__':main()
