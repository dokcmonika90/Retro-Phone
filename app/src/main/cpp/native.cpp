#include <jni.h>
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>
#include <cmath>
#include <sys/mman.h>
#include <cstddef>

namespace nes {
constexpr int W=256,H=240;
struct CPU{uint8_t a=0,x=0,y=0,p=0x24,sp=0xfd;uint16_t pc=0;uint8_t ram[2048]{};};
using BlockFn=void(*)(CPU*);
struct Block{void*mem;size_t size;BlockFn fn;};
struct Emu{
 CPU c{};std::vector<uint8_t>prg,chr;bool loaded=false;uint8_t mapper=0;
 uint8_t mmc3Select=0,mmc3Regs[8]{},mmc3IrqLatch=0,mmc3IrqCounter=0;bool mmc3IrqReload=false,mmc3IrqEnable=false,mmc3Mirroring=false;
 uint8_t ppuctrl=0,ppumask=0,ppustatus=0,oam[256]{},oamAddr=0;uint16_t vaddr=0,tempAddr=0;uint8_t fineX=0;bool ppuLatch=false;
 std::array<uint8_t,2048>vram{};std::array<uint8_t,32>pal{};uint8_t buttons=0;bool strobe=false;int padIndex=0;
 std::array<int32_t,65536>ji{};std::vector<Block>jb;std::array<uint32_t,W*H>fb{};double phase=0,freq=0;uint8_t vol=0;bool nmiPending=false;std::mutex m;
 Emu(){ji.fill(-1);}~Emu(){for(auto&b:jb)munmap(b.mem,b.size);}
 size_t chr1(uint8_t bank)const{size_t n=chr.size()/1024;return n?((size_t)bank%n)*1024:0;}
 uint8_t prgRead(uint16_t a){if(prg.empty())return 0;if(mapper!=4)return prg[(a-0x8000)%prg.size()];size_t n=prg.size()/8192;if(!n)return 0;size_t s=(a-0x8000)/8192,b=0;bool m=mmc3Select&0x40;switch(s){case 0:b=m?(n-2):mmc3Regs[6];break;case 1:b=mmc3Regs[7];break;case 2:b=m?mmc3Regs[6]:(n-2);break;default:b=n-1;}return prg[(b%n)*8192+(a&8191)];}
 uint8_t chrRead(uint16_t a){if(chr.empty())return 0;if(mapper!=4)return chr[a%chr.size()];a&=0x1fff;uint8_t b=0;bool inv=mmc3Select&0x80;if(!inv){if(a<0x800)b=(a<0x400)?(mmc3Regs[0]&0xfe):(mmc3Regs[0]|1);else if(a<0x1000)b=(a<0xc00)?(mmc3Regs[1]&0xfe):(mmc3Regs[1]|1);else if(a<0x1400)b=mmc3Regs[2];else if(a<0x1800)b=mmc3Regs[3];else if(a<0x1c00)b=mmc3Regs[4];else b=mmc3Regs[5];}else{if(a<0x400)b=mmc3Regs[2];else if(a<0x800)b=mmc3Regs[3];else if(a<0xc00)b=mmc3Regs[4];else if(a<0x1000)b=mmc3Regs[5];else if(a<0x1400)b=mmc3Regs[0]&0xfe;else if(a<0x1800)b=mmc3Regs[0]|1;else if(a<0x1c00)b=mmc3Regs[1]&0xfe;else b=mmc3Regs[1]|1;}return chr[chr1(b)+(a&1023)];}
 void mmc3IrqClock(){if(mapper!=4)return;if(mmc3IrqCounter==0||mmc3IrqReload)mmc3IrqCounter=mmc3IrqLatch;else --mmc3IrqCounter;mmc3IrqReload=false;}
 int ntIndex(uint16_t a)const{a=(a-0x2000)&0x0fff;int n=a/0x400;if(mmc3Mirroring)n&=1;else n=(n==1||n==2)?0:1;return n*0x400+(a&0x3ff);}
 uint8_t ppuReadMem(uint16_t a){a&=0x3fff;if(a<0x2000)return chrRead(a);if(a<0x3f00)return vram[ntIndex(a)];int p=(a-0x3f00)&31;if((p&3)==0)p=0;return pal[p]&0x3f;}
 void ppuWriteMem(uint16_t a,uint8_t v){a&=0x3fff;if(a<0x2000)return;if(a<0x3f00){vram[ntIndex(a)]=v;return;}int p=(a-0x3f00)&31;if((p&3)==0)p=0;pal[p]=v&0x3f;}
 uint8_t ppuRd(uint16_t r){switch(r){case 0x2002:{uint8_t v=ppustatus;ppustatus&=0x7f;ppuLatch=false;return v;}case 0x2004:return oam[oamAddr];case 0x2007:{uint8_t v=ppuReadMem(vaddr);uint8_t out=(vaddr&0x3fff)<0x3f00?0:v;vaddr=(vaddr+(ppuctrl&4?32:1))&0x7fff;return out;}default:return 0;}}
 void ppuWr(uint16_t r,uint8_t v){switch(r){case 0x2000:ppuctrl=v;tempAddr=(tempAddr&0xf3ff)|((uint16_t(v)&3)<<10);break;case 0x2001:ppumask=v;break;case 0x2003:oamAddr=v;break;case 0x2004:oam[oamAddr++]=v;break;case 0x2005:if(!ppuLatch){fineX=v&7;tempAddr=(tempAddr&0xffe0)|(v>>3);}else tempAddr=(tempAddr&0x8fff)|((uint16_t(v)&7)<<12)|((uint16_t(v)&0xf8)<<2);ppuLatch=!ppuLatch;break;case 0x2006:if(!ppuLatch)tempAddr=(tempAddr&0x00ff)|((uint16_t(v)&0x3f)<<8);else{tempAddr=(tempAddr&0x7f00)|v;vaddr=tempAddr;}ppuLatch=!ppuLatch;break;case 0x2007:ppuWriteMem(vaddr,v);vaddr=(vaddr+(ppuctrl&4?32:1))&0x7fff;break;}}
 uint8_t pad(){if(strobe)padIndex=0;uint8_t v=padIndex<8?((buttons>>padIndex)&1):1;if(!strobe&&padIndex<8)padIndex++;return v|0x40;}
 uint8_t rd(uint16_t a){if(a<0x2000)return c.ram[a&2047];if(a<0x4000)return ppuRd(0x2000|(a&7));if(a==0x4016)return pad();if(a>=0x8000)return prgRead(a);return 0;}
 void wr(uint16_t a,uint8_t v){if(a<0x2000){c.ram[a&2047]=v;return;}if(a<0x4000){ppuWr(0x2000|(a&7),v);return;}if(a==0x4014){for(int i=0;i<256;i++)oam[(oamAddr+i)&255]=rd(uint16_t(v)<<8|i);return;}if(a==0x4016){strobe=v&1;if(strobe)padIndex=0;return;}if(a==0x4000){vol=v&15;return;}if(a==0x4002){freq=1789773.0/(16.0*(uint16_t(v)+1));return;}if(a<0x8000||mapper!=4)return;if(a<0xa000){if(!(a&1))mmc3Select=v;else mmc3Regs[mmc3Select&7]=v;}else if(a<0xc000){if(!(a&1))mmc3Mirroring=v&1;}else if(a<0xe000){if(!(a&1))mmc3IrqLatch=v;else{mmc3IrqCounter=0;mmc3IrqReload=true;}}else{if(!(a&1))mmc3IrqEnable=false;else mmc3IrqEnable=true;}}
 void zn(uint8_t v){if(v)c.p&=~2;else c.p|=2;if(v&128)c.p|=128;else c.p&=~128;}
 void setC(bool v){if(v)c.p|=1;else c.p&=~1;}void setV(bool v){if(v)c.p|=64;else c.p&=~64;}
 uint8_t imm(){return rd(c.pc++);}uint16_t abs(){uint16_t l=rd(c.pc++),h=rd(c.pc++);return l|(h<<8);}uint16_t zp(){return rd(c.pc++);}uint16_t zpx(){return uint8_t(rd(c.pc++)+c.x);}uint16_t zpy(){return uint8_t(rd(c.pc++)+c.y);}uint16_t abx(){return abs()+c.x;}uint16_t aby(){return abs()+c.y;}uint16_t izx(){uint8_t z=uint8_t(rd(c.pc++)+c.x);return uint16_t(rd(z))|(uint16_t(rd(uint8_t(z+1)))<<8);}uint16_t izy(){uint8_t z=rd(c.pc++);return (uint16_t(rd(z))|(uint16_t(rd(uint8_t(z+1)))<<8))+c.y;}uint16_t ind(){uint16_t p=abs();return uint16_t(rd(p))|(uint16_t(rd((p&0xff00)|uint8_t(p+1)))<<8);}
 void push(uint8_t v){c.ram[0x100+c.sp--]=v;}uint8_t pop(){return c.ram[0x100+ ++c.sp];}
 void adc(uint8_t v){uint16_t s=c.a+v+(c.p&1);setC(s>255);setV((~(c.a^v)&(c.a^s)&128)!=0);c.a=uint8_t(s);zn(c.a);}void sbc(uint8_t v){adc(uint8_t(~v));}
 void cmp(uint8_t a,uint8_t v){uint16_t d=a-v;setC(a>=v);zn(uint8_t(d));}
 void branch(bool yes){int8_t n=int8_t(imm());if(yes)c.pc=uint16_t(c.pc+n);}
 void step(){uint8_t o=rd(c.pc++);switch(o){
 case 0x00:c.pc++;push(c.pc>>8);push(c.pc);push(c.p|0x10);c.p|=4;c.pc=uint16_t(rd(0xfffe))|(uint16_t(rd(0xffff))<<8);break;
 case 0xea:break;case 0xa9:c.a=imm();zn(c.a);break;case 0xa5:c.a=rd(zp());zn(c.a);break;case 0xb5:c.a=rd(zpx());zn(c.a);break;case 0xad:c.a=rd(abs());zn(c.a);break;case 0xbd:c.a=rd(abx());zn(c.a);break;case 0xb9:c.a=rd(aby());zn(c.a);break;case 0xa1:c.a=rd(izx());zn(c.a);break;case 0xb1:c.a=rd(izy());zn(c.a);break;
 case 0xa2:c.x=imm();zn(c.x);break;case 0xa6:c.x=rd(zp());zn(c.x);break;case 0xb6:c.x=rd(zpy());zn(c.x);break;case 0xae:c.x=rd(abs());zn(c.x);break;case 0xbe:c.x=rd(aby());zn(c.x);break;
 case 0xa0:c.y=imm();zn(c.y);break;case 0xa4:c.y=rd(zp());zn(c.y);break;case 0xb4:c.y=rd(zpx());zn(c.y);break;case 0xac:c.y=rd(abs());zn(c.y);break;case 0xbc:c.y=rd(abx());zn(c.y);break;
 case 0x85:wr(zp(),c.a);break;case 0x95:wr(zpx(),c.a);break;case 0x8d:wr(abs(),c.a);break;case 0x9d:wr(abx(),c.a);break;case 0x99:wr(aby(),c.a);break;case 0x81:wr(izx(),c.a);break;case 0x91:wr(izy(),c.a);break;
 case 0x86:wr(zp(),c.x);break;case 0x96:wr(zpy(),c.x);break;case 0x8e:wr(abs(),c.x);break;case 0x84:wr(zp(),c.y);break;case 0x94:wr(zpx(),c.y);break;case 0x8c:wr(abs(),c.y);break;
 case 0xaa:c.x=c.a;zn(c.x);break;case 0x8a:c.a=c.x;zn(c.a);break;case 0xa8:c.y=c.a;zn(c.y);break;case 0x98:c.a=c.y;zn(c.a);break;case 0xba:c.x=c.sp;zn(c.x);break;case 0x9a:c.sp=c.x;break;
 case 0xe8:c.x++;zn(c.x);break;case 0xca:c.x--;zn(c.x);break;case 0xc8:c.y++;zn(c.y);break;case 0x88:c.y--;zn(c.y);break;
 case 0x69:adc(imm());break;case 0x65:adc(rd(zp()));break;case 0x75:adc(rd(zpx()));break;case 0x6d:adc(rd(abs()));break;case 0x7d:adc(rd(abx()));break;case 0x79:adc(rd(aby()));break;case 0x61:adc(rd(izx()));break;case 0x71:adc(rd(izy()));break;
 case 0xe9:case 0xeb:sbc(imm());break;case 0xe5:sbc(rd(zp()));break;case 0xf5:sbc(rd(zpx()));break;case 0xed:sbc(rd(abs()));break;case 0xfd:sbc(rd(abx()));break;case 0xf9:sbc(rd(aby()));break;case 0xe1:sbc(rd(izx()));break;case 0xf1:sbc(rd(izy()));break;
 case 0x29:c.a&=imm();zn(c.a);break;case 0x25:c.a&=rd(zp());zn(c.a);break;case 0x35:c.a&=rd(zpx());zn(c.a);break;case 0x2d:c.a&=rd(abs());zn(c.a);break;case 0x3d:c.a&=rd(abx());zn(c.a);break;case 0x39:c.a&=rd(aby());zn(c.a);break;case 0x21:c.a&=rd(izx());zn(c.a);break;case 0x31:c.a&=rd(izy());zn(c.a);break;
 case 0x09:c.a|=imm();zn(c.a);break;case 0x05:c.a|=rd(zp());zn(c.a);break;case 0x0d:c.a|=rd(abs());zn(c.a);break;case 0x49:c.a^=imm();zn(c.a);break;case 0x45:c.a^=rd(zp());zn(c.a);break;case 0x4d:c.a^=rd(abs());zn(c.a);break;
 case 0xc9:cmp(c.a,imm());break;case 0xc5:cmp(c.a,rd(zp()));break;case 0xcd:cmp(c.a,rd(abs()));break;case 0xe0:cmp(c.x,imm());break;case 0xe4:cmp(c.x,rd(zp()));break;case 0xec:cmp(c.x,rd(abs()));break;case 0xc0:cmp(c.y,imm());break;case 0xc4:cmp(c.y,rd(zp()));break;case 0xcc:cmp(c.y,rd(abs()));break;
 case 0x4c:c.pc=abs();break;case 0x6c:c.pc=ind();break;case 0x20:{uint16_t t=abs(),r=c.pc-1;push(r>>8);push(r);c.pc=t;}break;case 0x60:c.pc=uint16_t(pop())|(uint16_t(pop())<<8);c.pc++;break;
 case 0x48:push(c.a);break;case 0x68:c.a=pop();zn(c.a);break;case 0x08:push(c.p|0x30);break;case 0x28:c.p=(pop()&0xef)|0x20;break;case 0x18:c.p&=~1;break;case 0x38:c.p|=1;break;case 0x58:c.p&=~4;break;case 0x78:c.p|=4;break;case 0xb8:c.p&=~64;break;case 0xd8:c.p&=~8;break;case 0xf8:c.p|=8;break;
 case 0xd0:branch(!(c.p&2));break;case 0xf0:branch(c.p&2);break;case 0x10:branch(!(c.p&128));break;case 0x30:branch(c.p&128);break;case 0x90:branch(!(c.p&1));break;case 0xb0:branch(c.p&1);break;case 0x50:branch(!(c.p&64));break;case 0x70:branch(c.p&64);break;
 case 0xc6:{uint16_t a=zp();uint8_t v=rd(a)-1;wr(a,v);zn(v);}break;case 0xe6:{uint16_t a=zp();uint8_t v=rd(a)+1;wr(a,v);zn(v);}break;case 0xce:{uint16_t a=abs();uint8_t v=rd(a)-1;wr(a,v);zn(v);}break;case 0xee:{uint16_t a=abs();uint8_t v=rd(a)+1;wr(a,v);zn(v);}break;
 case 0x4a:setC(c.a&1);c.a>>=1;zn(c.a);break;case 0x0a:setC(c.a&128);c.a<<=1;zn(c.a);break;case 0x6a:{uint8_t old=c.a;bool cc=c.p&1;setC(old&1);c.a=(old>>1)|(cc?128:0);zn(c.a);}break;case 0x2a:{uint8_t old=c.a;bool cc=c.p&1;setC(old&128);c.a=(old<<1)|(cc?1:0);zn(c.a);}break;
 case 0x46:{uint16_t a=zp();uint8_t v=rd(a);setC(v&1);v>>=1;wr(a,v);zn(v);}break;case 0x06:{uint16_t a=zp();uint8_t v=rd(a);setC(v&128);v<<=1;wr(a,v);zn(v);}break;case 0x66:{uint16_t a=zp();uint8_t v=rd(a);bool cc=c.p&1;setC(v&1);v=(v>>1)|(cc?128:0);wr(a,v);zn(v);}break;case 0x26:{uint16_t a=zp();uint8_t v=rd(a);bool cc=c.p&1;setC(v&128);v=(v<<1)|(cc?1:0);wr(a,v);zn(v);}break;
 default:break;}}
#ifdef __aarch64__
 static uint32_t ldb(int t,int n,int i){return 0x39400000u|((uint32_t)i<<10)|(n<<5)|t;}static uint32_t stb(int t,int n,int i){return 0x39000000u|((uint32_t)i<<10)|(n<<5)|t;}static uint32_t ldh(int t,int n,int i){return 0x79400000u|((uint32_t)i<<10)|(n<<5)|t;}static uint32_t sth(int t,int n,int i){return 0x79000000u|((uint32_t)i<<10)|(n<<5)|t;}
 bool jit(uint16_t pc){if(ji[pc]>=0)return true;std::vector<uint32_t>v;uint16_t q=pc;int n=0;for(int k=0;k<16;k++){uint8_t o=rd(q);if(o==0xea){v.push_back(ldh(3,0,offsetof(CPU,pc)/2));v.push_back(0x11000463);v.push_back(sth(3,0,offsetof(CPU,pc)/2));q++;n++;}else if(o==0xe8){v.push_back(ldb(1,0,offsetof(CPU,x)));v.push_back(0x11000421);v.push_back(stb(1,0,offsetof(CPU,x)));v.push_back(ldh(3,0,offsetof(CPU,pc)/2));v.push_back(0x11000463);v.push_back(sth(3,0,offsetof(CPU,pc)/2));q++;n++;}else break;}if(!n)return false;v.push_back(0xd65f03c0);size_t bytes=v.size()*4,page=(bytes+4095)&~size_t(4095);void*p=mmap(nullptr,page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);if(p==MAP_FAILED)return false;memcpy(p,v.data(),bytes);__builtin___clear_cache((char*)p,(char*)p+bytes);if(mprotect(p,page,PROT_READ|PROT_EXEC)){munmap(p,page);return false;}jb.push_back({p,page,(BlockFn)p});ji[pc]=jb.size()-1;return true;}
#endif
 void run(int n){
   for(int i=0;i<n;i++){
     if(nmiPending){push(c.pc>>8);push(c.pc);push((c.p&~4)|0x20);c.p|=4;c.pc=uint16_t(rd(0xfffa))|(uint16_t(rd(0xfffb))<<8);nmiPending=false;}
#ifdef __aarch64__
     uint16_t p=c.pc;if(jit(p)&&ji[p]>=0)jb[ji[p]].fn(&c);else step();
#else
     step();
#endif
   }
 }
 void load(const std::vector<uint8_t>&b){loaded=false;if(b.size()<16||memcmp(b.data(),"NES\x1a",4))return;uint8_t np=b[4],nc=b[5];mapper=(b[6]>>4)|(b[7]&0xf0);if(mapper!=0&&mapper!=4)return;size_t off=16+((b[6]&4)?512:0),ps=np*16384,cs=nc*8192;if(!ps||b.size()<off+ps)return;prg.assign(b.begin()+off,b.begin()+off+ps);off+=ps;chr.assign(cs?cs:8192,0);if(cs&&b.size()>=off+cs)std::copy(b.begin()+off,b.begin()+off+cs,chr.begin());if(mapper==4){std::fill(std::begin(mmc3Regs),std::end(mmc3Regs),0);mmc3Regs[6]=0;mmc3Regs[7]=1;mmc3Select=0;mmc3Mirroring=(b[6]&1)==0;mmc3IrqLatch=mmc3IrqCounter=0;mmc3IrqReload=false;mmc3IrqEnable=false;}loaded=true;reset();}
 void reset(){c=CPU{};std::fill(std::begin(c.ram),std::end(c.ram),0);vram.fill(0);pal.fill(0);std::fill(std::begin(oam),std::end(oam),0);ppuctrl=ppumask=ppustatus=oamAddr=0;vaddr=tempAddr=0;fineX=0;ppuLatch=false;nmiPending=false;padIndex=0;pal[0]=0x0f;pal[1]=0x01;pal[2]=0x21;pal[3]=0x31;std::fill(fb.begin(),fb.end(),0xff000000);ji.fill(-1);if(!prg.empty())c.pc=uint16_t(rd(0xfffc))|(uint16_t(rd(0xfffd))<<8);}
 uint32_t col(uint8_t i){static const uint32_t p[64]={0xff666666,0xff002a88,0xff1412a7,0xff3b00a4,0xff5f006b,0xff7e003c,0xff7b0800,0xff5c1d00,0xff344000,0xff0b4f00,0xff005500,0xff00504a,0xff003c63,0,0,0,0xffadadad,0xff155fd9,0xff4240ff,0xff7527fe,0xffa01acc,0xffb71e5b,0xffb52f16,0xff994800,0xff6b6d00,0xff388700,0xff0d9300,0xff008b7a,0xff00749e,0,0,0,0xffffffff,0xff64b0ff,0xff9290ff,0xffc76fff,0xfff36aff,0xfffe6e92,0xffff9b5b,0xfffdbd43,0xffcfe04a,0xff9af57a,0xff5cf0b0,0xff5de1d4,0xff60cfff,0xff808080,0,0,0,0xffffffff,0xffc0dfff,0xffffb8ff,0xffffe0a0,0xffffe0a0,0xffe8f090,0xffc8ff80,0xffa0ffc0,0xffa0fff0,0xffa0e8ff,0xffc0c0c0,0,0};return p[i&63];}
 void render(){int tableBase=(ppuctrl&0x10)?0x1000:0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int tx=x>>3,ty=y>>3;int ni=ntIndex(0x2000+((ty*32+tx)&0x3ff));uint8_t t=vram[ni];int px=x&7,py=y&7;uint16_t o=tableBase+t*16+py;uint8_t lo=chrRead(o),hi=chrRead(o+8);uint8_t q=((lo>>(7-px))&1)|(((hi>>(7-px))&1)<<1);int ai=ntIndex(0x23c0+((ty>>2)*8)+(tx>>2));uint8_t av=vram[ai];int sh=((ty&2)?4:0)+((tx&2)?2:0);uint8_t pn=(av>>sh)&3;uint8_t idx=q?uint8_t(pn*4+q):pal[0];fb[y*W+x]=col(idx&63);}ppustatus|=0x80;if(ppuctrl&0x80)nmiPending=true;for(int i=0;i<8;i++)mmc3IrqClock();}
 void frameRun(){if(!loaded){for(int y=0;y<H;y++)for(int x=0;x<W;x++){uint8_t v=((x>>4)^(y>>4))&15;fb[y*W+x]=0xff000000|(v*17<<16)|(v*9<<8)|(v*5);}return;}run(12000);render();}
 std::vector<int16_t>audio(){std::vector<int16_t>o(735);if(!loaded||freq<=1)return o;double d=freq/44100.;for(auto&s:o){phase+=d;if(phase>=1)phase-=1;s=(int16_t)((phase<.5?1:-1)*vol*1500);}return o;}
};static Emu e;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeVersion(JNIEnv*env,jobject){return env->NewStringUTF("NES renderer + audio + ARM64 dynamic recompiler 0.7 (MMC3 + PPU + 6502)");}
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeLoad(JNIEnv*env,jobject,jbyteArray a){if(!a)return JNI_FALSE;jsize n=env->GetArrayLength(a);std::vector<uint8_t>b(n);env->GetByteArrayRegion(a,0,n,(jbyte*)b.data());std::lock_guard<std::mutex>g(nes::e.m);nes::e.load(b);return nes::e.loaded;}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeReset(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::e.m);nes::e.reset();}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeRunFrame(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::e.m);nes::e.frameRun();}
extern "C" JNIEXPORT jintArray JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeFrame(JNIEnv*env,jobject){std::lock_guard<std::mutex>g(nes::e.m);jintArray a=env->NewIntArray(nes::W*nes::H);env->SetIntArrayRegion(a,0,nes::W*nes::H,(jint*)nes::e.fb.data());return a;}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeButtons(JNIEnv*,jobject,jint m){std::lock_guard<std::mutex>g(nes::e.m);nes::e.buttons=(uint8_t)m;}
extern "C" JNIEXPORT jshortArray JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeAudio(JNIEnv*env,jobject){std::lock_guard<std::mutex>g(nes::e.m);auto b=nes::e.audio();jshortArray a=env->NewShortArray(b.size());env->SetShortArrayRegion(a,0,b.size(),b.data());return a;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeIsLoaded(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::e.m);return nes::e.loaded;}
