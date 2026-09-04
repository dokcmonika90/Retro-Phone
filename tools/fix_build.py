from pathlib import Path

native = Path('app/src/main/cpp/native.cpp')
s = native.read_text()
# Correct NES palette mirroring.
s = s.replace('int p=(a-0x3f00)&31;if((p&3)==0)p=0;return pal[p]&0x3f;', 'int p=(a-0x3f00)&31;if(p==0x10)p=0;else if(p==0x14)p=4;else if(p==0x18)p=8;else if(p==0x1c)p=12;return pal[p]&0x3f;')
s = s.replace('int p=(a-0x3f00)&31;if((p&3)==0)p=0;pal[p]=v&0x3f;', 'int p=(a-0x3f00)&31;if(p==0x10)p=0;else if(p==0x14)p=4;else if(p==0x18)p=8;else if(p==0x1c)p=12;pal[p]=v&0x3f;')
# Correct MMC3 mirroring bit semantics.
s = s.replace('mmc3Mirroring=(b[6]&1)==0;', 'mmc3Mirroring=(b[6]&1)!=0;')
# Add a proper PPU read buffer field if the source does not have one.
if 'uint8_t ppuReadBuffer=0;' not in s:
    s = s.replace('bool ppuLatch=false;', 'bool ppuLatch=false;uint8_t ppuReadBuffer=0;')
# $2007 must return the previous buffered nametable byte and immediately load the next one.
s = s.replace('case 0x2007:{uint8_t v=ppuReadMem(vaddr);uint8_t out=(vaddr&0x3fff)<0x3f00?0:v;vaddr=(vaddr+(ppuctrl&4?32:1))&0x7fff;return out;}', 'case 0x2007:{uint16_t a=vaddr&0x3fff;uint8_t v=ppuReadMem(a);uint8_t out=(a<0x3f00)?ppuReadBuffer:v;if(a<0x3f00)ppuReadBuffer=v;vaddr=(vaddr+(ppuctrl&4?32:1))&0x7fff;return out;}')
s = s.replace('vaddr=tempAddr;}ppuLatch=!ppuLatch;', 'vaddr=tempAddr;ppuReadBuffer=0;}ppuLatch=!ppuLatch;')
s = s.replace('vaddr=0;ppuLatch=false;', 'vaddr=0;tempAddr=0;ppuLatch=false;ppuReadBuffer=0;')
# The ARM64 JIT is incomplete; force the deterministic interpreter.
s = s.replace('uint16_t p=c.pc;if(jit(p)&&ji[p]>=0)jb[ji[p]].fn(&c);else step();', 'step();')
# Do not run 29,780 instructions. A 60 Hz NES frame is about 29,780 CPU cycles; the current interpreter averages ~3 cycles/instruction.
s = s.replace('run(29780);render();', 'ppustatus&=0x7f;run(10000);render();')
s = s.replace('run(12000);render();', 'ppustatus&=0x7f;run(10000);render();')
# Add the missing official 6502 addressing modes. Previously these opcodes fell through default and effectively became NOPs.
extra = ''' case 0x15:c.a|=rd(zpx());zn(c.a);break;case 0x1d:c.a|=rd(abx());zn(c.a);break;case 0x19:c.a|=rd(aby());zn(c.a);break;case 0x11:c.a|=rd(izy());zn(c.a);break;
 case 0x55:c.a^=rd(zpx());zn(c.a);break;case 0x5d:c.a^=rd(abx());zn(c.a);break;case 0x59:c.a^=rd(aby());zn(c.a);break;case 0x51:c.a^=rd(izy());zn(c.a);break;
 case 0x35:c.a&=rd(zpx());zn(c.a);break;case 0x3d:c.a&=rd(abx());zn(c.a);break;case 0x39:c.a&=rd(aby());zn(c.a);break;case 0x31:c.a&=rd(izy());zn(c.a);break;
 case 0xd5:cmp(c.a,rd(zpx()));break;case 0xdd:cmp(c.a,rd(abx()));break;case 0xd9:cmp(c.a,rd(aby()));break;case 0xd1:cmp(c.a,rd(izy()));break;
 case 0x16:{uint16_t a=zpx();uint8_t v=rd(a);setC(v&128);v<<=1;wr(a,v);zn(v);}break;case 0x0e:{uint16_t a=abs();uint8_t v=rd(a);setC(v&128);v<<=1;wr(a,v);zn(v);}break;case 0x1e:{uint16_t a=abx();uint8_t v=rd(a);setC(v&128);v<<=1;wr(a,v);zn(v);}break;
 case 0x56:{uint16_t a=zpx();uint8_t v=rd(a);setC(v&1);v>>=1;wr(a,v);zn(v);}break;case 0x4e:{uint16_t a=abs();uint8_t v=rd(a);setC(v&1);v>>=1;wr(a,v);zn(v);}break;case 0x5e:{uint16_t a=abx();uint8_t v=rd(a);setC(v&1);v>>=1;wr(a,v);zn(v);}break;
 case 0x36:{uint16_t a=zpx();uint8_t v=rd(a);bool cc=c.p&1;setC(v&128);v=(v<<1)|(cc?1:0);wr(a,v);zn(v);}break;case 0x2e:{uint16_t a=abs();uint8_t v=rd(a);bool cc=c.p&1;setC(v&128);v=(v<<1)|(cc?1:0);wr(a,v);zn(v);}break;case 0x3e:{uint16_t a=abx();uint8_t v=rd(a);bool cc=c.p&1;setC(v&128);v=(v<<1)|(cc?1:0);wr(a,v);zn(v);}break;
 case 0x76:{uint16_t a=zpx();uint8_t v=rd(a);bool cc=c.p&1;setC(v&1);v=(v>>1)|(cc?128:0);wr(a,v);zn(v);}break;case 0x6e:{uint16_t a=abs();uint8_t v=rd(a);bool cc=c.p&1;setC(v&1);v=(v>>1)|(cc?128:0);wr(a,v);zn(v);}break;case 0x7e:{uint16_t a=abx();uint8_t v=rd(a);bool cc=c.p&1;setC(v&1);v=(v>>1)|(cc?128:0);wr(a,v);zn(v);}break;
'''
s = s.replace(' default:break;}}\n#ifdef __aarch64__', extra + ' default:break;}}\n#ifdef __aarch64__')
# RTI and BIT are required by many NES interrupt handlers.
s = s.replace('case 0x00:c.pc++;push(c.pc>>8);', 'case 0x40:c.p=(pop()&0xef)|0x20;c.pc=uint16_t(pop())|(uint16_t(pop())<<8);break;case 0x24:{uint8_t v=rd(zp());if(v&0x40)c.p|=64;else c.p&=~64;if((c.a&v)==0)c.p|=2;else c.p&=~2;if(v&0x80)c.p|=128;else c.p&=~128;}break;case 0x2c:{uint8_t v=rd(abs());if(v&0x40)c.p|=64;else c.p&=~64;if((c.a&v)==0)c.p|=2;else c.p&=~2;if(v&0x80)c.p|=128;else c.p&=~128;}break;case 0x00:c.pc++;push(c.pc>>8);')
native.write_text(s)

activity = Path('app/src/main/java/com/dokcmonika90/retrophone/MainActivity.kt')
k = activity.read_text()
old_start = '        override fun onTouchEvent(e: MotionEvent): Boolean {'
start = k.index(old_start)
end = k.index('        }\n    }\n    override fun onDestroy()', start) + len('        }')
new = '''        override fun onTouchEvent(e: MotionEvent): Boolean {
            var mask = 0
            val w = width.toFloat(); val h = height.toFloat()
            val d = minOf(w, h) * .16f
            val cx = w*.16f; val cy = h*.77f; val bx = w*.84f; val by = h*.77f
            for (i in 0 until e.pointerCount) {
                val x = e.getX(i); val y = e.getY(i)
                if ((x-cx)*(x-cx)+(y-(cy-d*.7f))*(y-(cy-d*.7f)) < d*d*.35f) mask = mask or 8
                if ((x-cx)*(x-cx)+(y-(cy+d*.7f))*(y-(cy+d*.7f)) < d*d*.35f) mask = mask or 4
                if ((x-(cx-d*.7f))*(x-(cx-d*.7f))+(y-cy)*(y-cy) < d*d*.35f) mask = mask or 2
                if ((x-(cx+d*.7f))*(x-(cx+d*.7f))+(y-cy)*(y-cy) < d*d*.35f) mask = mask or 1
                if ((x-bx)*(x-bx)+(y-by)*(y-by) < d*d*.35f) mask = mask or 16
                if ((x-(bx-d*.9f))*(x-(bx-d*.9f))+(y-(by+d*.55f))*(y-(by+d*.55f)) < d*d*.35f) mask = mask or 32
                if (x>w*.43f && x<w*.50f && y>h*.84f) mask = mask or 64
                if (x>w*.51f && x<w*.58f && y>h*.84f) mask = mask or 128
            }
            if (mask != lastMask) { lastMask = mask; nativeButtons(mask) }
            invalidate()
            return true
        }'''
k = k[:start] + new + k[end:]
activity.write_text(k)
