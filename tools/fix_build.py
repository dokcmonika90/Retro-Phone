from pathlib import Path

native = Path('app/src/main/cpp/native.cpp')
s = native.read_text()
s = s.replace('std::array<int32_t,65536>ji{};std::vector<Block>jb;std::array<uint32_t,W*H>fb{};double phase=0,freq=0;uint8_t vol=0;', 'std::array<int32_t,65536>ji{};std::vector<Block>jb;std::array<uint32_t,W*H>fb{};double phase=0,freq=0;uint8_t vol=0;uint16_t vaddr=0;bool ppuLatch=false;')
s = s.replace('if(r==0x2002){uint8_t v=ppustatus;ppustatus&=127;return v;}if(r==0x2004)return oam[0];return 0;', 'if(r==0x2002){uint8_t v=ppustatus;ppustatus&=127;ppuLatch=false;return v;}if(r==0x2004)return oam[0];if(r==0x2007){uint8_t v=vram[vaddr&0x0fff];vaddr+=(ppuctrl&4)?32:1;return v;}return 0;')
s = s.replace('if(r==0x2000)ppuctrl=v;if(r==0x2007)vram[0]=v;', 'if(r==0x2000)ppuctrl=v;if(r==0x2005||r==0x2006){ppuLatch=!ppuLatch;if(r==0x2006){if(!ppuLatch)vaddr=(vaddr&0x00ff)|((uint16_t(v)&0x3f)<<8);else vaddr=(vaddr&0x3f00)|v;}}if(r==0x2007){vram[vaddr&0x0fff]=v;vaddr+=(ppuctrl&4)?32:1;}')
s = s.replace('bool jit(uint16_t pc){if(ji[pc]>=0)return true;std::vector<uint32_t>v;uint16_t q=pc;for(int k=0;k<16;k++){', 'bool jit(uint16_t pc){if(ji[pc]>=0)return true;std::vector<uint32_t>v;uint16_t q=pc;int translated=0;for(int k=0;k<16;k++){')
s = s.replace('q++;}else if(o==0xe8){', 'q++;translated++;}else if(o==0xe8){')
s = s.replace('q++;}else break;}v.push_back(0xd65f03c0);', 'q++;translated++;}else break;}if(!translated)return false;v.push_back(0xd65f03c0);')
s = s.replace('std::fill(std::begin(pal),std::end(pal),0);std::fill(fb.begin(),fb.end(),0xff000000);', 'std::fill(std::begin(pal),std::end(pal),0);pal[0]=0x0f;pal[1]=0x01;pal[2]=0x21;pal[3]=0x31;vaddr=0;ppuLatch=false;std::fill(fb.begin(),fb.end(),0xff000000);')
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
