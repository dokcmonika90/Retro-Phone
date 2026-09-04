#include <jni.h>
#include <cstdint>
#include <vector>
#include <string>
#include <algorithm>

namespace nes {
static std::vector<uint8_t> prg;
static uint8_t ram[2048]{};
static uint8_t a=0,x=0,y=0,p=0x24,sp=0xfd;
static uint16_t pc=0x8000;
static bool loaded=false;
static uint8_t read(uint16_t addr){ if(addr<0x2000) return ram[addr&0x7ff]; if(addr>=0x8000 && !prg.empty()){ size_t i=addr-0x8000; if(prg.size()==16384)i%=16384; else i%=prg.size(); return prg[i]; } return 0; }
static void setZN(uint8_t v){ p=(p&~0x82)|(v?0:2)|(v&0x80); }
static void step(){ uint8_t op=read(pc++); switch(op){
case 0xEA: break;
case 0xA9: a=read(pc++); setZN(a); break;
case 0xA2: x=read(pc++); setZN(x); break;
case 0xA0: y=read(pc++); setZN(y); break;
case 0xAA: x=a; setZN(x); break;
case 0xA8: y=a; setZN(y); break;
case 0x8A: a=x; setZN(a); break;
case 0x98: a=y; setZN(a); break;
case 0xE8: ++x; setZN(x); break;
case 0xCA: --x; setZN(x); break;
case 0xC8: ++y; setZN(y); break;
case 0x88: --y; setZN(y); break;
case 0x4C: { uint8_t lo=read(pc++),hi=read(pc++); pc=(uint16_t(hi)<<8)|lo; break; }
default: break; }}
static bool load(const uint8_t* data,size_t n){ if(n<16 || data[0]!='N'||data[1]!='E'||data[2]!='S'||data[3]!=0x1A) return false; int prgBanks=data[4]; if(prgBanks<1) return false; size_t off=16+(data[6]&4?512:0); size_t bytes=size_t(prgBanks)*16384; if(off+bytes>n) return false; prg.assign(data+off,data+off+bytes); pc=0x8000; loaded=true; return true; }
}
extern "C" JNIEXPORT jstring JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeStatus(JNIEnv* e,jobject){ std::string s=nes::loaded?"NES ROM loaded • Mapper 0 • ARM64 native core":"Ready • Select an .nes ROM"; return e->NewStringUTF(s.c_str()); }
extern "C" JNIEXPORT jboolean JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeLoad(JNIEnv* e,jobject,jbyteArray arr){ if(!arr)return JNI_FALSE; jsize n=e->GetArrayLength(arr); std::vector<uint8_t> b(n); e->GetByteArrayRegion(arr,0,n,reinterpret_cast<jbyte*>(b.data())); return nes::load(b.data(),b.size())?JNI_TRUE:JNI_FALSE; }
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeReset(JNIEnv*,jobject){ nes::a=nes::x=nes::y=0; nes::p=0x24; nes::sp=0xfd; nes::pc=0x8000; }
extern "C" JNIEXPORT void JNICALL Java_com_dokcmonika90_retrophone_MainActivity_nativeRunFrame(JNIEnv*,jobject){ if(!nes::loaded)return; for(int i=0;i<5000;i++) nes::step(); }
