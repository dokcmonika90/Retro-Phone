from pathlib import Path

native = Path('app/src/main/cpp/native.cpp')
s = native.read_text()

# Correct NES palette mirroring.
s = s.replace('int p=(a-0x3f00)&31;if((p&3)==0)p=0;return pal[p]&0x3f;', 'int p=(a-0x3f00)&31;if(p==0x10)p=0;else if(p==0x14)p=4;else if(p==0x18)p=8;else if(p==0x1c)p=12;return pal[p]&0x3f;')
s = s.replace('int p=(a-0x3f00)&31;if((p&3)==0)p=0;pal[p]=v&0x3f;', 'int p=(a-0x3f00)&31;if(p==0x10)p=0;else if(p==0x14)p=4;else if(p==0x18)p=8;else if(p==0x1c)p=12;pal[p]=v&0x3f;')

# MMC3 A000 uses 0=vertical and 1=horizontal.  Horizontal maps
# NT0/NT1 to CIRAM page 0 and NT2/NT3 to page 1; vertical maps
# NT0/NT2 to page 0 and NT1/NT3 to page 1.
s = s.replace('mmc3Mirroring=(b[6]&1)==0;', 'mmc3Mirroring=(b[6]&1)!=0;')
s = s.replace('if(mmc3Mirroring)n&=1;else n=(n==1||n==2)?0:1;', 'if(mmc3Mirroring)n>>=1;else n&=1;')

# Proper PPU $2007 read buffer.
if 'uint8_t ppuReadBuffer=0;' not in s:
    s = s.replace('bool ppuLatch=false;', 'bool ppuLatch=false;uint8_t ppuReadBuffer=0;')
s = s.replace('case 0x2007:{uint8_t v=ppuReadMem(vaddr);uint8_t out=(vaddr&0x3fff)<0x3f00?0:v;vaddr=(vaddr+(ppuctrl&4?32:1))&0x7fff;return out;}', 'case 0x2007:{uint16_t a=vaddr&0x3fff;uint8_t v=ppuReadMem(a);uint8_t out=(a<0x3f00)?ppuReadBuffer:v;if(a<0x3f00)ppuReadBuffer=v;vaddr=(vaddr+(ppuctrl&4?32:1))&0x7fff;return out;}')
s = s.replace('vaddr=tempAddr;}ppuLatch=!ppuLatch;', 'vaddr=tempAddr;ppuReadBuffer=0;}ppuLatch=!ppuLatch;')
s = s.replace('vaddr=0;ppuLatch=false;', 'vaddr=0;tempAddr=0;ppuLatch=false;ppuReadBuffer=0;')

# Never execute the incomplete ARM64 JIT while correctness is being established.
s = s.replace('uint16_t p=c.pc;if(jit(p)&&ji[p]>=0)jb[ji[p]].fn(&c);else step();', 'step();')

# A 60 Hz NES frame is about 29,780 CPU cycles. The compact interpreter
# averages roughly three cycles per instruction, so use about 10,000 steps.
s = s.replace('run(29780);render();', 'ppustatus&=0x7f;nmiPending=false;run(10000);render();')
s = s.replace('run(12000);render();', 'ppustatus&=0x7f;nmiPending=false;run(10000);render();')

# Add RTI and BIT if the base source does not already contain them.
if 'case 0x40:c.p=(pop()&0xef)|0x20;' not in s:
    s = s.replace('case 0x00:c.pc++;push(c.pc>>8);', 'case 0x40:c.p=(pop()&0xef)|0x20;c.pc=uint16_t(pop())|(uint16_t(pop())<<8);break;case 0x24:{uint8_t v=rd(zp());if(v&0x40)c.p|=64;else c.p&=~64;if((c.a&v)==0)c.p|=2;else c.p&=~2;if(v&0x80)c.p|=128;else c.p&=~128;}break;case 0x2c:{uint8_t v=rd(abs());if(v&0x40)c.p|=64;else c.p&=~64;if((c.a&v)==0)c.p|=2;else c.p&=~2;if(v&0x80)c.p|=128;else c.p&=~128;}break;case 0x00:c.pc++;push(c.pc>>8);')

native.write_text(s)

# Keep the existing working multi-touch controller patch.
activity = Path('app/src/main/java/com/dokcmonika90/retrophone/MainActivity.kt')
k = activity.read_text()
old_start = '        override fun onTouchEvent(e: MotionEvent): Boolean {'
if old_start in k:
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
