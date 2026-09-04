#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>
#include <cmath>
#include <sys/mman.h>
#include <unistd.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "RetroPhone", __VA_ARGS__)

namespace nes {
constexpr int W=256,H=240;
enum Button : uint8_t { RIGHT=1, LEFT=2, DOWN=4, UP=8, A=16, B=32, SELECT=64, START=128 };
struct CPU { uint8_t a=0,x=0,y=0,p=0x24,sp=0xfd; uint16_t pc=0; uint8_t ram[0x800]{}; };
using BlockFn=void(*)(CPU*);
struct JitBlock { void* mem=nullptr; size_t size=0; BlockFn fn=nullptr; };

struct Emulator {
 CPU cpu{}; std::vector<uint8_t> prg,chr; bool loaded=false,chrRam=false; uint8_t mapper=0;
 uint8_t ppuCtrl=0,ppuMask=0,ppuStatus=0,oamAddr=0,scrollX=0,scrollY=0; bool addrLatch=false; uint16_t ppuAddr=0;
 uint8_t oam[256]{},vram[0x1000]{},palette[32]{},controller=0; bool controllerStrobe=false;
 std::array<int32_t,65536> jitIndex{}; std::vector<JitBlock> blocks; std::array<uint32_t,W*H> frame{};
 double audioPhase=0,audioFreq=0; uint8_t audioVol=0; std::mutex mu;
 Emulator(){jitIndex.fill(-1);blocks.reserve(1024);} ~Emulator(){for(auto&b:blocks)if(b.mem)munmap(b.mem,b.size);}
 uint8_t read(uint16_t a){if(a<0x2000)return cpu.ram[a&0x7ff];if(a<0x4000)return ppuRead(0x2000|(a&7));if(a==0x4016)return readController();if(a>=0x8000&&!prg.empty()){size_t o=a-0x8000; if(prg.size()==0x4000)o%=0x4000;else o%=prg.size();return prg[o];}return 0;}
 void write(uint16_t a,uint8_t v){if(a<0x2000){cpu.ram[a&0x7ff]=v;return;}if(a<0x4000){ppuWrite(0x2000|(a&7),v);return;}if(a==0x4014){uint16_t base=v<<8;for(int i=0;i<256;i++)oam[(oamAddr+i)&255]=read(base+i);return;}if(a==0x4016){controllerStrobe=(v&1)!=0;return;}if(a>=0x4000&&a<=0x4017)updateAudio(a,v);}
 uint8_t ppuRead(uint16_t r){switch(r){case 0x2002:{uint8_t v=ppuStatus;ppuStatus&=0x7f;addrLatch=false;return v;}case 0x2004:return oam[oamAddr];case 0x2007:{uint8_t v=vramRead(ppuAddr);ppuAddr+=(ppuCtrl&4)?32:1;ppuAddr&=0x3fff;return v;}default:return 0;}}
 void ppuWrite(uint16_t r,uint8_t v){switch(r){case 0x2000:ppuCtrl=v;break;case 0x2001:ppuMask=v;break;case 0x2003:oamAddr=v;break;case 0x2004:oam[oamAddr++]=v;break;case 0x2005:if(!addrLatch){scrollX=v;addrLatch=true;}else{scrollY=v;addrLatch=false;}break;case 0x2006:if(!addrLatch){ppuAddr=(v&0x3f)<<8;addrLatch=true;}else{ppuAddr=(ppuAddr&0x3f00)|v;addrLatch=false;}break;case 0x2007:vramWrite(ppuAddr,v);ppuAddr+=(ppuCtrl&4)?32:1;ppuAddr&=0x3fff;break;}}
 uint8_t vramRead(uint16_t a){a&=0x3fff;if(a>=0x3f00)return palette[(a-0x3f00)&31];return vram[a&0xfff];}
 void vramWrite(uint16_t a,uint8_t v){a&=0x3fff;if(a>=0x3f00)palette[(a-0x3f00)&31]=v&0x3f;else vram[a&0xfff]=v;}
 uint8_t readController(){static uint8_t idx=0;if(controllerStrobe)idx=0;uint8_t bit=idx<8?((controller>>idx)&1):1;if(!controllerStrobe&&idx<8)idx++;return bit|0x40;}
 void updateAudio(uint16_t a,uint8_t v){static uint16_t period=0;if(a==0x4000)audioVol=v&15;if(a==0x4002)period=(period&0x700)|v;if(a==0x4003){period=(period&0xff)|((uint16_t)(v&7)<<8);audioFreq=period?1789773.0/(16.0*(period+1)):0;}}
 void setZN(uint8_t v){if(v==0)cpu.p|=2;else cpu.p&=~2;if(v&0x80)cpu.p|=0x80;else cpu.p&=~0x80;}
 uint16_t zp(){return read(cpu.pc++);} uint16_t abs(){uint16_t l=read(cpu.pc++),h=read(cpu.pc++);return l|(h<<8);} uint8_t imm(){return read(cpu.pc++);} void push(uint8_t v){cpu.ram[0x100+cpu.sp--]=v;}uint8_t pop(){return cpu.ram[0x100+(++cpu.sp)];}
 void step(){uint8_t op=read(cpu.pc++);switch(op){case 0xEA:break;case 0xA9:cpu.a=imm();setZN(cpu.a);break;case 0xA2:cpu.x=imm();setZN(cpu.x);break;case 0xA0:cpu.y=imm();setZN(cpu.y);break;case 0xAA:cpu.x=cpu.a;setZN(cpu.x);break;case 0x8A:cpu.a=cpu.x;setZN(cpu.a);break;case 0xA8:cpu.y=cpu.a;setZN(cpu.y);break;case 0x98:cpu.a=cpu.y;setZN(cpu.a);break;case 0xE8:cpu.x++;setZN(cpu.x);break;case 0xCA:cpu.x--;setZN(cpu.x);break;case 0xC8:cpu.y++;setZN(cpu.y);break;case 0x88:cpu.y--;setZN(cpu.y);break;case 0x85:write(zp(),cpu.a);break;case 0x86:write(zp(),cpu.x);break;case 0x84:write(zp(),cpu.y);break;case 0x8D:write(abs(),cpu.a);break;case 0x8E:write(abs(),cpu.x);break;case 0x8C:write(abs(),cpu.y);break;case 0xAD:cpu.a=read(abs());setZN(cpu.a);break;case 0xAE:cpu.x=read(abs());setZN(cpu.x);break;case 0xAC:cpu.y=read(abs());setZN(cpu.y);break;case 0xA5:cpu.a=read(zp());setZN(cpu.a);break;case 0xA6:cpu.x=read(zp());setZN(cpu.x);break;case 0xA4:cpu.y=read(zp());setZN(cpu.y);break;case 0x69:{uint16_t s=cpu.a+imm()+(cpu.p&1);cpu.p=(cpu.p&~1)|((s>255)?1:0);cpu.a=(uint8_t)s;setZN(cpu.a);break;}case 0xE9:{uint16_t s=cpu.a-imm()-((cpu.p&1)?0:1);cpu.p=(cpu.p&~1)|((s<256)?1:0);cpu.a=(uint8_t)s;setZN(cpu.a);break;}case 0x29:cpu.a&=imm();setZN(cpu.a);break;case 0x09:cpu.a|=imm();setZN(cpu.a);break;case 0x49:cpu.a^=imm();setZN(cpu.a);break;case 0xC9:{uint8_t v=imm(),r=cpu.a-v;if(cpu.a>=v)cpu.p|=1;else cpu.p&=~1;setZN(r);break;}case 0xE0:{uint8_t v=imm(),r=cpu.x-v;if(cpu.x>=v)cpu.p|=1;else cpu.p&=~1;setZN(r);break;}case 0xC0:{uint8_t v=imm(),r=cpu.y-v;if(cpu.y>=v)cpu.p|=1;else cpu.p&=~1;setZN(r);break;}case 0x4C:cpu.pc=abs();break;case 0x20:{uint16_t t=abs(),r=cpu.pc-1;push(r>>8);push(r);cpu.pc=t;break;}case 0x60:cpu.pc=pop()|(pop()<<8);cpu.pc++;break;case 0x40:cpu.p=pop();cpu.pc=pop()|(pop()<<8);break;case 0x48:push(cpu.a);break;case 0x68:cpu.a=pop();setZN(cpu.a);break;case 0x08:push(cpu.p|0x10);break;case 0x28:cpu.p=pop();break;case 0xD0:{int8_t o=(int8_t)imm();if(!(cpu.p&2))cpu.pc+=o;break;}case 0xF0:{int8_t o=(int8_t)imm();if(cpu.p&2)cpu.pc+=o;break;}case 0x10:{int8_t o=(int8_t)imm();if(!(cpu.p&0x80))cpu.pc+=o;break;}case 0x30:{int8_t o=(int8_t)imm();if(cpu.p&0x80)cpu.pc+=o;break;}case 0x90:{int8_t o=(int8_t)imm();if(!(cpu.p&1))cpu.pc+=o;break;}case 0xB0:{int8_t o=(int8_t)imm();if(cpu.p&1)cpu.pc+=o;break;}case 0x18:cpu.p&=~1;break;case 0x38:cpu.p|=1;break;case 0x58:cpu.p&=~4;break;case 0x78:cpu.p|=4;break;default:break;}}
#ifdef __aarch64__
 static uint32_t ldrb(int rt,int rn,int imm){return 0x39400000u|((uint32_t)imm<<10)|(rn<<5)|rt;}static uint32_t strb(int rt,int rn,int imm){return 0x39000000u|((uint32_t)imm<<10)|(rn<<5)|rt;}static uint32_t ldrh(int rt,int rn,int imm){return 0x79400000u|((uint32_t)imm<<10)|(rn<<5)|rt;}static uint32_t strh(int rt,int rn,int imm){return 0x79000000u|((uint32_t)imm<<10)|(rn<<5)|rt;}
 bool compile(uint16_t pc){if(jitIndex[pc]>=0)return true;std::vector<uint32_t>c;uint16_t cur=pc;for(int n=0;n<24;n++){uint8_t op=read(cur);if(op==0xEA){c.push_back(ldrh(3,0,offsetof(CPU,pc)/2));c.push_back(0x11000463u);c.push_back(strh(3,0,offsetof(CPU,pc)/2));cur++;}else if(op==0xE8){c.push_back(ldrb(1,0,offsetof(CPU,x)));c.push_back(0x11000421u);c.push_back(strb(1,0,offsetof(CPU,x)));c.push_back(ldrh(3,0,offsetof(CPU,pc)/2));c.push_back(0x11000463u);c.push_back(strh(3,0,offsetof(CPU,pc)/2));cur++;}else if(op==0xCA){c.push_back(ldrb(1,0,offsetof(CPU,x)));c.push_back(0x51000421u);c.push_back(strb(1,0,offsetof(CPU,x)));c.push_back(ldrh(3,0,offsetof(CPU,pc)/2));c.push_back(0x11000463u);c.push_back(strh(3,0,offsetof(CPU,pc)/2));cur++;}else break;}c.push_back(0xD65F03C0u);size_t bytes=c.size()*4,page=(bytes+4095)&~size_t(4095);void*m=mmap(nullptr,page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);if(m==MAP_FAILED)return false;memcpy(m,c.data(),bytes);__builtin___clear_cache((char*)m,(char*)m+bytes);if(mprotect(m,page,PROT_READ|PROT_EXEC)){munmap(m,page);return false;}blocks.push_back({m,page,(BlockFn)m});jitIndex[pc]=(int)blocks.size()-1;return true;}
#endif
 void runCPU(int n){for(int i=0;i<n;i++){#ifdef __aarch64__ uint16_t pc=cpu.pc;if(compile(pc))blocks[jitIndex[pc]].fn(&cpu);else step();#else step();#endif}}
 void loadRom(const std::vector<uint8_t>&b){if(b.size()<16||b[0]!='N'||b[1]!='E'||b[2]!='S'||b[3]!=0x1a){loaded=false;return;}uint8_t p=b[4],c=b[5];mapper=(b[6]>>4)|(b[7]&0xf0);if(mapper!=0){loaded=false;return;}size_t off=16+((b[6]&4)?512:0),ps=p*16384,cs=c*8192;if(b.size()<off+ps){loaded=false;return;}prg.assign(b.begin()+off,b.begin()+off+ps);off+=ps;chrRam=cs==0;chr.assign(chrRam?8192:cs,0);if(cs)std::copy(b.begin()+off,b.begin()+std::min(b.size(),off+cs),chr.begin());loaded=true;reset();}
 void reset(){cpu=CPU{};std::fill(std::begin(cpu.ram),std::end(cpu.ram),0);ppuStatus=0xA0;ppuCtrl=ppuMask=0;addrLatch=false;ppuAddr=0;std::fill(std::begin(vram),std::end(vram),0);std::fill(std::begin(palette),std::end(palette),0);std::fill(frame.begin(),frame.end(),0xff000000);audioPhase=audioFreq=0;audioVol=0;jitIndex.fill(-1);cpu.pc=(uint16_t)read(0xfffc)|((uint16_t)read(0xfffd)<<8);}
 uint32_t color(uint8_t i){static const uint32_t pal[64]={0xff666666,0xff002a88,0xff1412a7,0xff3b00a4,0xff5f006b,0xff7e003c,0xff7b0800,0xff5c1d00,0xff344000,0xff0b4f00,0xff005500,0xff00504a,0xff003c63,0,0,0,0xffadadad,0xff155fd9,0xff4240ff,0xff7527fe,0xffa01acc,0xffb71e5b,0xffb52f16,0xff994800,0xff6b6d00,0xff388700,0xff0d9300,0xff008b7a,0xff00749e,0,0,0,0xffffffff,0xff64b0ff,0xff9290ff,0xffc76fff,0xfff36aff,0xfffe6e92,0xffff9b5b,0xfffdbd43,0xffcfe04a,0xff9af57a,0xff5cf0b0,0xff5de1d4,0xff60cfff,0xff808080,0,0,0,0xffffffff,0xffc0dfff,0xffd0d0ff,0xffe8bfff,0xffffb8ff,0xffffb8ff,0xffffcfa0,0xffffe0a0,0xffe8f090,0xffc8ff80,0xffa0ffc0,0xffa0fff0,0xffa0e8ff,0xffc0c0c0,0,0};return pal[i&63];}
 void render(){for(int y=0;y<H;y++)for(int x=0;x<W;x++){int tx=((x+scrollX)>>3)&31,ty=((y+scrollY)>>3)&29;uint8_t tile=vram[(ty*32+tx)&0x3ff];int px=(x+scrollX)&7,py=(y+scrollY)&7;size_t off=(size_t)tile*16+py;uint8_t lo=chr.empty()?0:chr[off%chr.size()],hi=chr.empty()?0:chr[(off+8)%chr.size()];uint8_t bit=((lo>>(7-px))&1)|(((hi>>(7-px))&1)<<1);uint8_t pi=(bit==0)?0:(uint8_t)((((tile>>2)&3)*4+bit)&31);frame[y*W+x]=color(palette[pi]);}ppuStatus|=0x80;}
 void demo(){for(int y=0;y<H;y++)for(int x=0;x<W;x++){uint8_t v=((x>>4)^(y>>4))&15;frame[y*W+x]=0xff000000|(v*17<<16)|(v*9<<8)|(v*5);}}
 void runFrame(){if(!loaded){demo();return;}runCPU(9932);render();}
 void setButtons(uint8_t m){controller=m;}std::vector<int16_t>audio(){std::vector<int16_t>o(735);if(!loaded||audioFreq<=1)return o;double inc=audioFreq/44100.;for(auto&s:o){audioPhase+=inc;if(audioPhase>=1)audioPhase-=1;s=(int16_t)((audioPhase<.5?1:-1)*audioVol*1500);}return o;}
}; static Emulator emu;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeVersion(JNIEnv*e,jobject){return e->NewStringUTF("NES renderer + audio + ARM64 dynamic recompiler 0.3");}
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeLoad(JNIEnv*e,jobject,jbyteArray a){if(!a)return JNI_FALSE;jsize n=e->GetArrayLength(a);std::vector<uint8_t>b(n);e->GetByteArrayRegion(a,0,n,(jbyte*)b.data());std::lock_guard<std::mutex>g(nes::emu.mu);nes::emu.loadRom(b);return nes::emu.loaded?JNI_TRUE:JNI_FALSE;}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeReset(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::emu.mu);nes::emu.reset();}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeRunFrame(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::emu.mu);nes::emu.runFrame();}
extern "C" JNIEXPORT jintArray JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeFrame(JNIEnv*e,jobject){std::lock_guard<std::mutex>g(nes::emu.mu);jintArray a=e->NewIntArray(nes::W*nes::H);e->SetIntArrayRegion(a,0,nes::W*nes::H,(jint*)nes::emu.frame.data());return a;}
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeButtons(JNIEnv*,jobject,jint m){std::lock_guard<std::mutex>g(nes::emu.mu);nes::emu.setButtons((uint8_t)m);}
extern "C" JNIEXPORT jshortArray JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeAudio(JNIEnv*e,jobject){std::lock_guard<std::mutex>g(nes::emu.mu);auto b=nes::emu.audio();jshortArray a=e->NewShortArray((jsize)b.size());e->SetShortArrayRegion(a,0,(jsize)b.size(),b.data());return a;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeIsLoaded(JNIEnv*,jobject){std::lock_guard<std::mutex>g(nes::emu.mu);return nes::emu.loaded?JNI_TRUE:JNI_FALSE;}
