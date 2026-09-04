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
 uint8_t mmc3Select=0,mmc3Regs[8]{},mmc3IrqLatch=0,mmc3IrqCounter=0;bool mmc3IrqReload=false,mmc3IrqEnable=false;
 uint8_t ppuctrl=0,ppustatus=0,oam[256]{},vram[4096]{},pal[32]{},buttons=0;bool strobe=false;std::array<int32_t,65536>ji{};std::vector<Block>jb;std::array<uint32_t,W*H>fb{};double phase=0,freq=0;uint8_t vol=0;std::mutex m;
 Emu(){ji.fill(-1);}~Emu(){for(auto&b:jb)munmap(b.mem,b.size);}
 size_t prg8(uint8_t bank)const{size_t n=prg.size()/8192;return n?((size_t)bank%n)*8192:0;}
 size_t chr1(uint8_t bank)const{size_t n=chr.size()/1024;return n?((size_t)bank%n)*1024:0;}
 uint8_t prgRead(uint16_t a){if(prg.empty())return 0;if(mapper!=4)return prg[(a-0x8000)%prg.size()];size_t n=prg.size()/8192;if(!n)return 0;size_t slot=(a-0x8000)/8192;size_t bank=0;bool mode=mmc3Select&0x40;switch(slot){case 0:bank=mode?(n-2):mmc3Regs[6];break;case 1:bank=mmc3Regs[7];break;case 2:bank=mode?mmc3Regs[6]:(n-2);break;default:bank=n-1;break;}return prg[(bank%n)*8192+(a&0x1fff)];}
 uint8_t chrRead(uint16_t a){if(chr.empty())return 0;if(mapper!=4)return chr[(a%chr.size())];uint8_t bank;bool inv=mmc3Select&0x80;if(!inv){if(a<0x0800)bank=(a<0x0400)?(mmc3Regs[0]&0xfe):(mmc3Regs[0]|1);else if(a<0x1000)bank=(a<0x0800)?(mmc3Regs[1]&0xfe):(mmc3Regs[1]|1);else if(a<0x1400)bank=mmc3Regs[2];else if(a<0x1800)bank=mmc3Regs[3];else if(a<0x1c00)bank=mmc3Regs[4];else bank=mmc3Regs[5];}
 else{if(a<0x0400)bank=mmc3Regs[2];else if(a<0x0800)bank=mmc3Regs[3];else if(a<0x0c00)bank=mmc3Regs[4];else if(a<0x1000)bank=mmc3Regs[5];else if(a<0x1800)bank=(a<0x1400)?(mmc3Regs[0]&0xfe):(mmc3Regs[0]|1);else bank=(a<0x1c00)?(mmc3Regs[1]&0xfe):(mmc3Regs[1]|1);}
 return chr[chr1(bank)+(a&0x3ff)];}
 void mmc3IrqClock(){if(!loaded||mapper!=4)return;if(mmc3IrqCounter==0||mmc3IrqReload)mmc3IrqCounter=mmc3IrqLatch;else mmc3IrqCounter--;mmc3IrqReload=false;if(mmc3IrqCounter==0&&mmc3IrqEnable)c.p|=0x04;}
 uint8_t rd(uint16_t a){if(a<0x2000)return c.ram[a&2047];if(a<0x4000)return ppuRd(0x2000|(a&7));if(a==0x4016)return pad();if(a>=0x8000)return prgRead(a);return 0;}
 void wr(uint16_t a,uint8_t v){if(a<0x2000){c.ram[a&2047]=v;return;}if(a<0x4000){ppuWr(0x2000|(a&7),v);return;}if(a==0x4014){for(int i=0;i<256;i++)oam[i]=rd((uint16_t(v)<<8)+i);return;}if(a==0x4016){strobe=v&1;return;}if(a==0x4000)vol=v&15;if(a==0x4002)freq=1789773.0/(16.0*(uint16_t(v)+1));if(a<0x8000||mapper!=4)return;
   if(a<0xa000){if(!(a&1))mmc3Select=v;else mmc3Regs[mmc3Select&7]=v;}
   else if(a<0xc000){if(!(a&1)){} else {}}
   else if(a<0xe000){if(!(a&1))mmc3IrqLatch=v;else {mmc3IrqCounter=0;mmc3IrqReload=true;}}
   else {if(!(a&1))mmc3IrqEnable=false;else mmc3IrqEnable=true;}
 }
 uint8_t ppuRd(uint16_t r){if(r==0x2002){uint8_t v=ppustatus;ppustatus&=127;return v;}if(r==0x2004)return oam[0];return 0;}
 void ppuWr(uint16_t r,uint8_t v){if(r==0x2000)ppuctrl=v;if(r==0x2007)vram[0]=v;}
 uint8_t pad(){static int i=0;if(strobe)i=0;uint8_t v=i<8?(buttons>>i)&1:1;if(!strobe&&i<8)i++;return v|64;}
 void zn(uint8_t v){if(v)c.p&=~2;else c.p|=2;if(v&128)c.p|=128;else c.p&=~128;}
 uint8_t imm(){return rd(c.pc++);}uint16_t abs(){uint16_t l=rd(c.pc++),h=rd(c.pc++);return l|(h<<8);}void push(uint8_t v){c.ram[256+c.sp--]=v;}uint8_t pop(){return c.ram[256+ ++c.sp];}
 void step(){uint8_t o=rd(c.pc++);switch(o){case 0xea:break;case 0xa9:c.a=imm();zn(c.a);break;case 0xa2:c.x=imm();zn(c.x);break;case 0xa0:c.y=imm();zn(c.y);break;case 0xaa:c.x=c.a;zn(c.x);break;case 0x8a:c.a=c.x;zn(c.a);break;case 0xa8:c.y=c.a;zn(c.y);break;case 0x98:c.a=c.y;zn(c.a);break;case 0xe8:c.x++;zn(c.x);break;case 0xca:c.x--;zn(c.x);break;case 0xc8:c.y++;zn(c.y);break;case 0x88:c.y--;zn(c.y);break;case 0x85:wr(rd(c.pc++),c.a);break;case 0x86:wr(rd(c.pc++),c.x);break;case 0x84:wr(rd(c.pc++),c.y);break;case 0x8d:wr(abs(),c.a);break;case 0x8e:wr(abs(),c.x);break;case 0x8c:wr(abs(),c.y);break;case 0xa5:c.a=rd(rd(c.pc++));zn(c.a);break;case 0xad:c.a=rd(abs());zn(c.a);break;case 0x69:{uint16_t s=c.a+imm()+(c.p&1);c.p=(c.p&~1)|(s>255);c.a=s;zn(c.a);break;}case 0xe9:{uint16_t s=c.a-imm()-((c.p&1)?0:1);c.p=(c.p&~1)|(s<256);c.a=s;zn(c.a);break;}case 0x29:c.a&=imm();zn(c.a);break;case 0x09:c.a|=imm();zn(c.a);break;case 0x49:c.a^=imm();zn(c.a);break;case 0x4c:c.pc=abs();break;case 0x20:{uint16_t t=abs(),r=c.pc-1;push(r>>8);push(r);c.pc=t;break;}case 0x60:c.pc=pop()|(pop()<<8);c.pc++;break;case 0x48:push(c.a);break;case 0x68:c.a=pop();zn(c.a);break;case 0xd0:{int8_t n=imm();if(!(c.p&2))c.pc+=n;break;}case 0xf0:{int8_t n=imm();if(c.p&2)c.pc+=n;break;}case 0x18:c.p&=~1;break;case 0x38:c.p|=1;break;default:break;}}
#ifdef __aarch64__
 static uint32_t ldb(int t,int n,int i){return 0x39400000u|((uint32_t)i<<10)|(n<<5)|t;}static uint32_t stb(int t,int n,int i){return 0x39000000u|((uint32_t)i<<10)|(n<<5)|t;}static uint32_t ldh(int t,int n,int i){return 0x79400000u|((uint32_t)i<<10)|(n<<5)|t;}static uint32_t sth(int t,int n,int i){return 0x79000000u|((uint32_t)i<<10)|(n<<5)|t;}
 bool jit(uint16_t pc){if(ji[pc]>=0)return true;std::vector<uint32_t>v;uint16_t q=pc;for(int k=0;k<16;k++){uint8_t o=rd(q);if(o==0xea){v.push_back(ldh(3,0,offsetof(CPU,pc)/2));v.push_back(0x11000463);v.push_back(sth(3,0,offsetof(CPU,pc)/2));q++;}else if(o==0xe8){v.push_back(ldb(1,0,offsetof(CPU,x)));v.push_back(0x11000421);v.push_back(stb(1,0,offsetof(CPU,x)));v.push_back(ldh(3,0,offsetof(CPU,pc)/2));v.push_back(0x11000463);v.push_back(sth(3,0,offsetof(CPU,pc)/2));q++;}else break;}if(v.empty())return false;v.push_back(0xd65f03c0);size_t bytes=v.size()*4,page=(bytes+4095)&~size_t(4095);void*p=mmap(nullptr,page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);if(p==MAP_FAILED)return false;memcpy(p,v.data(),bytes);__builtin___clear_cache((char*)p,(char*)p+bytes);if(mprotect(p,page,PROT_READ|PROT_EXEC)){munmap(p,page);return false;}jb.push_back({p,page,(BlockFn)p});ji[pc]=jb.size()-1;return true;}
#endif
 void run(int n){
#ifdef __aarch64__
 for(int i=0;i<n;i++){uint16_t p=c.pc;if(jit(p)&&ji[p]>=0)jb[ji[p]].fn(&c);else step();}
#else
 for(int i=0;i<n;i++)step();
#endif
 }
 void load(const std::vector<uint8_t>&b){loaded=false;if(b.size()<16||memcmp(b.data(),"NES\x1a",4))return;uint8_t np=b[4],nc=b[5];mapper=(b[6]>>4)|(b[7]&0xf0);if(mapper!=0&&mapper!=4)return;size_t off=16+((b[6]&4)?512:0),ps=np*16384,cs=nc*8192;if(!ps||b.size()<off+ps)return;prg.assign(b.begin()+off,b.begin()+off+ps);off+=ps;chr.assign(cs?cs:8192,0);if(cs&&b.size()>=off+cs)std::copy(b.begin()+off,b.begin()+off+cs,chr.begin());if(mapper==4){for(int i=0;i<8;i++)mmc3Regs[i]=0;mmc3Regs[6]=0;mmc3Regs[7]=1;mmc3Select=0;mmc3IrqLatch=mmc3IrqCounter=0;mmc3IrqReload=false;mmc3IrqEnable=false;}loaded=true;reset();}
 void reset(){c=CPU{};std::fill(std::begin(c.ram),std::end(c.ram),0);std::fill(std::begin(vram),std::end(vram),0);std::fill(std::begin(pal),std::end(pal),0);std::fill(fb.begin(),fb.end(),0xff000000);ji.fill(-1);if(!prg.empty())c.pc=uint16_t(rd(0xfffc))|(uint16_t(rd(0xfffd))<<8);}
 uint32_t col(uint8_t i){static const uint32_t p[64]={0xff666666,0xff002a88,0xff1412a7,0xff3b00a4,0xff5f006b,0xff7e003c,0xff7b0800,0xff5c1d00,0xff344000,0xff0b4f00,0xff005500,0xff00504a,0xff003c63,0,0,0,0xffadadad,0xff155fd9,0xff4240ff,0xff7527fe,0xffa01acc,0xffb71e5b,0xffb52f16,0xff994800,0xff6b6d00,0xff388700,0xff0d9300,0xff008b7a,0xff00749e,0,0,0,0xffffffff,0xff64b0ff,0xff9290ff,0xffc76fff,0xfff36aff,0xfffe6e92,0xffff9b5b,0xfffdbd43,0xffcfe04a,0xff9af57a,0xff5cf0b0,0xff5de1d4,0xff60cfff,0xff808080,0,0,0,0xffffffff,0xffc0dfff,0xe8bfff,0xffffb8ff,0xffffcfa0,0xffffe0a0,0xffe8f090,0xffc8ff80,0xffa0ffc0,0xffa0fff0,0xffa0e8ff,0xffc0c0c0,0,0};return p[i&63];}
 void render(){for(int y=0;y<H;y++)for(int x=0;x<W;x++){int tx=(x>>3)&31,ty=(y>>3)&29;uint8_t t=vram[ty*32+tx];int px=x&7,py=y&7;size_t o=size_t(t)*16+py;uint8_t lo=chrRead(uint16_t(o));uint8_t hi=chrRead(uint16_t(o+8));uint8_t b=((lo>>(7-px))&1)|(((hi>>(7-px))&1)<<1);fb[y*W+x]=col(pal[b&31]);}ppustatus|=128;}
 void frameRun(){if(!loaded){for(int y=0;y<H;y++)for(int x=0;x<W;x++){uint8_t v=((x>>4)^(y>>4))&15;fb[y*W+x]=0xff000000|(v*17<<16)|(v*9<<8)|(v*5);}return;}run(9932);render();}
 std::vector<int16_t>audio(){std::vector<int16_t>o(735);if(!loaded||freq<=1)return o;double d=freq/44100.;for(auto&s:o){phase+=d;if(phase>=1)phase-=1;s=(int16_t)((phase<.5?1:-1)*vol*1500);}return o;}
};static Emu e;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeVersion(JNIEnv*env,jobject){return env->NewStringUTF("NES renderer + audio + ARM64 dynamic recompiler 0.6 (MMC3 Mapper 4)");}
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeLoad(JNIEnv*env,jobject,jbyteArray a){if(!a)return JNI_FALSE;jsize n=env->GetArrayLength(a);std::vector<uint8_t>b(n);env->GetByteArrayRegion(a,0,n,(jbyte*)b.data());std::lock_guard<std::mutex>g(nes::e.m);nes::e.load(b);return nes::e.loaded;}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeReset(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::e.m);nes::e.reset();}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeRunFrame(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::e.m);nes::e.frameRun();}
extern "C" JNIEXPORT jintArray JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeFrame(JNIEnv*env,jobject){std::lock_guard<std::mutex>g(nes::e.m);jintArray a=env->NewIntArray(nes::W*nes::H);env->SetIntArrayRegion(a,0,nes::W*nes::H,(jint*)nes::e.fb.data());return a;}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeButtons(JNIEnv*,jobject,jint m){std::lock_guard<std::mutex>g(nes::e.m);nes::e.buttons=(uint8_t)m;}
extern "C" JNIEXPORT jshortArray JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeAudio(JNIEnv*env,jobject){std::lock_guard<std::mutex>g(nes::e.m);auto b=nes::e.audio();jshortArray a=env->NewShortArray(b.size());env->SetShortArrayRegion(a,0,b.size(),b.data());return a;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeIsLoaded(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::e.m);return nes::e.loaded;}
