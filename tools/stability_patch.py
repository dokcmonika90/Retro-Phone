from pathlib import Path

p = Path('app/src/main/cpp/native.cpp')
s = p.read_text()
# run() counts instructions, not 6502 clock cycles. The previous 29780 setting
# advanced multiple NES frames and made the displayed state alternate/flicker.
s = s.replace('run(29780);render();', 'run(10000);render();')
# Correct the four universal background-palette mirrors (3F10/14/18/1C).
s = s.replace('int p=(a-0x3f00)&31;if((p&3)==0)p=0;', 'int p=(a-0x3f00)&31;if(p==0x10)p=0;else if(p==0x14)p=4;else if(p==0x18)p=8;else if(p==0x1c)p=12;')
# MMC3 iNES mirroring bit: 1 = vertical, 0 = horizontal.
s = s.replace('mmc3Mirroring=(b[6]&1)==0;', 'mmc3Mirroring=(b[6]&1)!=0;')
p.write_text(s)
