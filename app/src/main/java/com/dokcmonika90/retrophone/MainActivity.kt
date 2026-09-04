package com.dokcmonika90.retrophone

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.media.*
import android.os.Bundle
import android.util.SparseIntArray
import android.view.*
import android.widget.*
import java.util.zip.ZipInputStream
import kotlin.math.max

class MainActivity : Activity() {
    private external fun nativeVersion(): String
    private external fun nativeLoad(bytes: ByteArray): Boolean
    private external fun nativeReset()
    private external fun nativeFrame(): IntArray
    private external fun nativeRunFrame()
    private external fun nativeButtons(mask: Int)
    private external fun nativeAudio(): ShortArray
    private external fun nativeIsLoaded(): Boolean
    companion object { init { System.loadLibrary("retro_recompiler") } }
    private lateinit var screen: GameView
    private lateinit var status: TextView
    private lateinit var toolbar: LinearLayout
    private var audio: AudioTrack? = null
    private var running = false
    private var audioRunning = false
    private var gameplayFullscreen = false
    private val frameMs = 16L
    private val activeTouches = SparseIntArray()
    private val libraryPrefs by lazy { getSharedPreferences("rom_library", MODE_PRIVATE) }
    private val romDirectory by lazy { java.io.File(filesDir, "roms").apply { mkdirs() } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enterImmersiveUi(); screen = GameView()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(screen, FrameLayout.LayoutParams(-1,-1).apply { topMargin=dp(62) })
        toolbar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(4),dp(8),dp(4));setBackgroundColor(0xCC101010.toInt());elevation=dp(4).toFloat()}
        val load=Button(this).apply{text="LOAD NES";setOnClickListener{pickRom()}}
        val library=Button(this).apply{text="LIBRARY";setOnClickListener{showLibrary()}}
        val reset=Button(this).apply{text="RESET";setOnClickListener{nativeReset();status.text="  Reset";screen.invalidate()}}
        val fullscreen=Button(this).apply{text="FULLSCREEN";setOnClickListener{setGameplayFullscreen(!gameplayFullscreen)}}
        status=TextView(this).apply{text="  ${nativeVersion()}";setTextColor(Color.WHITE);textSize=13f;gravity=Gravity.CENTER_VERTICAL;maxLines=2}
        toolbar.addView(load);toolbar.addView(library);toolbar.addView(reset);toolbar.addView(fullscreen);toolbar.addView(status,LinearLayout.LayoutParams(0,-1,1f))
        root.addView(toolbar,FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP));setContentView(root);startAudio();startLoop();autoLoadLastRom()
    }
    private fun dp(value:Int)= (value*resources.displayMetrics.density+0.5f).toInt()
    private fun enterImmersiveUi(){window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE}
    private fun setGameplayFullscreen(fullscreen:Boolean){gameplayFullscreen=fullscreen;toolbar.visibility=if(fullscreen)View.GONE else View.VISIBLE;val p=screen.layoutParams as FrameLayout.LayoutParams;p.topMargin=if(fullscreen)0 else dp(62);screen.layoutParams=p;status.text=if(nativeIsLoaded()){if(fullscreen)"  Fullscreen gameplay" else "  Gameplay controls visible"}else{if(fullscreen)"  Fullscreen — tap BACK to exit" else "  ${nativeVersion()}"};enterImmersiveUi();screen.invalidate()}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)enterImmersiveUi()}
    override fun onBackPressed(){if(gameplayFullscreen)setGameplayFullscreen(false)else super.onBackPressed()}
    private fun pickRom(){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)};startActivityForResult(intent,42)}
    private fun validHeader(b:ByteArray)=b.size>=16&&b[0].toInt()=='N'.code&&b[1].toInt()=='E'.code&&b[2].toInt()=='S'.code&&b[3].toInt()==0x1A
    private fun unwrapRom(data:ByteArray):Pair<ByteArray,String>?{if(validHeader(data))return data to "NES ROM";if(data.size>=4&&data[0].toInt()==0x50&&data[1].toInt()==0x4b){ZipInputStream(data.inputStream()).use{zip->var e=zip.nextEntry;while(e!=null){if(!e.isDirectory&&e.name.lowercase().endsWith(".nes")){val rom=zip.readBytes();if(validHeader(rom))return rom to e.name};e=zip.nextEntry}}};return null}
    private fun mapperOf(bytes:ByteArray)=if(bytes.size>=8)((bytes[6].toInt() and 0xF0) shr 4) or (bytes[7].toInt() and 0xF0) else -1
    private fun libraryFile(bytes:ByteArray)=java.io.File(romDirectory,"rom_${Integer.toHexString(bytes.contentHashCode())}.nes")
    private fun saveToLibrary(bytes:ByteArray,name:String){try{val f=libraryFile(bytes);f.writeBytes(bytes);val now=System.currentTimeMillis();libraryPrefs.edit().putString("name_${f.name}",name.substringAfterLast('/')).putString("last_rom",f.name).putLong("played_${f.name}",now).apply();f.setLastModified(now)}catch(_:Exception){}}
    private fun markPlayed(file:java.io.File){val now=System.currentTimeMillis();libraryPrefs.edit().putString("last_rom",file.name).putLong("played_${file.name}",now).apply();file.setLastModified(now)}
    private fun autoLoadLastRom(){val name=libraryPrefs.getString("last_rom",null)?:return;val file=java.io.File(romDirectory,name);if(!file.isFile)return;try{val bytes=file.readBytes();if(validHeader(bytes)){loadRomBytes(bytes,libraryPrefs.getString("name_${file.name}",file.name)?:file.name,false);markPlayed(file)}}catch(_:Exception){}}
    private fun showLibrary(){
        val all=romDirectory.listFiles{f->f.isFile&&f.extension.equals("nes",true)}?.toList()?:emptyList()
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(8))}
        val search=EditText(this).apply{hint="Search games...";singleLine=true}
        val sort=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Recently Played","Name","Size"))}
        val favoritesOnly=CheckBox(this).apply{text="Favorites only"}
        val last=libraryPrefs.getString("last_rom",null)
        val lastLabel=TextView(this).apply{text=if(last!=null)"Last played: ${libraryPrefs.getString("name_$last",last)}" else "No game played yet";textSize=14f;setPadding(0,dp(4),0,dp(8))}
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{addView(list)}
        box.addView(search);box.addView(sort);box.addView(favoritesOnly);box.addView(lastLabel);box.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun refresh(){
            list.removeAllViews();val q=search.text.toString().trim().lowercase();val mode=sort.selectedItemPosition
            val files=all.filter{f->val n=libraryPrefs.getString("name_${f.name}",f.name)?:f.name;(!favoritesOnly.isChecked||libraryPrefs.getBoolean("fav_${f.name}",false))&&(q.isEmpty()||n.lowercase().contains(q))}.sortedWith(when(mode){1->compareBy{libraryPrefs.getString("name_${it.name}",it.name)?.lowercase()?:it.name.lowercase()};2->compareByDescending<java.io.File>{it.length()};else->compareByDescending{libraryPrefs.getLong("played_${it.name}",it.lastModified())}})
            if(files.isEmpty()){list.addView(TextView(this).apply{text="No matching games.";textSize=17f;setPadding(0,dp(16),0,dp(16))});return}
            files.forEach{file->
                val name=libraryPrefs.getString("name_${file.name}",file.name)?:file.name;val fav=libraryPrefs.getBoolean("fav_${file.name}",false);val header=ByteArray(16);val count=try{file.inputStream().use{it.read(header)}}catch(_:Exception){0};val mapper=if(count>=8)mapperOf(header)else -1
                val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(6),0,dp(6))};val info=TextView(this).apply{text=(if(fav)"★ " else "☆ ")+name+"\nMapper $mapper • ${file.length()/1024} KB";textSize=16f}
                val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val play=Button(this).apply{text="PLAY"};val star=Button(this).apply{text=if(fav)"UNFAVORITE" else "FAVORITE"};val del=Button(this).apply{text="DELETE"}
                play.setOnClickListener{try{loadRomBytes(file.readBytes(),name,false);markPlayed(file);showLibrary()}catch(_:Exception){}}
                star.setOnClickListener{libraryPrefs.edit().putBoolean("fav_${file.name}",!libraryPrefs.getBoolean("fav_${file.name}",false)).apply();showLibrary()}
                del.setOnClickListener{AlertDialog.Builder(this).setTitle("Delete ROM?").setMessage("Delete $name from the ROM Library?").setNegativeButton("CANCEL",null).setPositiveButton("DELETE"){_,_->file.delete();val edit=libraryPrefs.edit().remove("name_${file.name}").remove("played_${file.name}").remove("fav_${file.name}");if(libraryPrefs.getString("last_rom",null)==file.name)edit.remove("last_rom");edit.apply();showLibrary()}.show()}
                buttons.addView(play);buttons.addView(star);buttons.addView(del);row.addView(info);row.addView(buttons);list.addView(row)
            }
        }
        search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()};override fun afterTextChanged(s:android.text.Editable?) {}})
        sort.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,pos:Int,id:Long){refresh()}}
        favoritesOnly.setOnCheckedChangeListener{_,_->refresh()};refresh()
        AlertDialog.Builder(this).setTitle("ROM Library").setView(box).setNegativeButton("CLOSE",null).show()
    }
    private fun loadRomBytes(bytes:ByteArray,name:String,save:Boolean){val mapper=mapperOf(bytes);try{if(save)saveToLibrary(bytes,name);val ok=nativeLoad(bytes);if(ok){nativeReset();status.text="  ROM loaded: $name (Mapper $mapper, ${bytes.size/1024} KB)";screen.invalidate()}else{status.text="  Mapper $mapper is not implemented by the current core";Toast.makeText(this,"LaiNES could not load Mapper $mapper",Toast.LENGTH_LONG).show()}}catch(e:Exception){status.text="  ROM load failed";Toast.makeText(this,"ROM load failed: ${e.message?:"unknown error"}",Toast.LENGTH_LONG).show()}}
    override fun onActivityResult(req:Int,result:Int,data:Intent?){super.onActivityResult(req,result,data);if(req!=42||result!=RESULT_OK||data?.data==null)return;try{val uri=data.data!!;try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};val raw=contentResolver.openInputStream(uri)?.use{it.readBytes()};if(raw==null){status.text="  ROM could not be read";return};val u=unwrapRom(raw);if(u==null){status.text="  Unsupported ROM wrapper";Toast.makeText(this,"No valid .nes ROM was found in that file",Toast.LENGTH_LONG).show();return};loadRomBytes(u.first,u.second,true)}catch(e:Exception){status.text="  ROM load failed";Toast.makeText(this,"ROM load failed: ${e.message?:"unknown error"}",Toast.LENGTH_LONG).show()}}
    private fun startAudio(){if(audioRunning)return;try{val sr=44100;val min=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw IllegalStateException("AudioTrack buffer size unavailable");val buffer=max(min,sr/4*2);audio=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(buffer).setTransferMode(AudioTrack.MODE_STREAM).build();audio?.play();audioRunning=true;Thread{while(audioRunning&&!isFinishing){try{val pcm=nativeAudio();if(pcm.isNotEmpty())audio?.write(pcm,0,pcm.size,AudioTrack.WRITE_BLOCKING)else Thread.sleep(2)}catch(_:InterruptedException){break}catch(e:Exception){runOnUiThread{status.text="  Audio error: ${e.message?:"unknown"}"};break}}}.start()}catch(e:Exception){audioRunning=false;status.text="  Audio unavailable: ${e.message?:"unknown"}"}}
    private fun startLoop(){running=true;Thread{while(running&&!isFinishing){val start=System.nanoTime();if(nativeIsLoaded())nativeRunFrame();runOnUiThread{screen.invalidate()};val sleep=frameMs-(System.nanoTime()-start)/1_000_000L;if(sleep>0)Thread.sleep(sleep)}}.start()}
    private fun controlMaskAt(x:Float,y:Float):Int{val w=screen.width.toFloat().coerceAtLeast(1f);val h=screen.height.toFloat().coerceAtLeast(1f);val r=minOf(w,h)*.105f;val dCx=w*.16f;val dCy=h*.77f;val aX=w*.84f;val aY=h*.77f;val bX=w*.72f;val bY=h*.86f;fun inside(px:Float,py:Float,rr:Float):Boolean{val dx=x-px;val dy=y-py;return dx*dx+dy*dy<=rr*rr};val dr=r*.72f;var m=0;if(inside(dCx,dCy-r*1.05f,dr))m=m or 8;if(inside(dCx,dCy+r*1.05f,dr))m=m or 4;if(inside(dCx-r*1.05f,dCy,dr))m=m or 2;if(inside(dCx+r*1.05f,dCy,dr))m=m or 1;if(m!=0)return m;if(inside(aX,aY,r*1.05f))return 16;if(inside(bX,bY,r*.95f))return 32;if(x>=w*.39f&&x<=w*.51f&&y>=h*.82f&&y<=h*.96f)return 64;if(x>=w*.51f&&x<=w*.63f&&y>=h*.82f&&y<=h*.96f)return 128;return 0}
    private fun refreshTouchMask(){var m=0;for(i in 0 until activeTouches.size())m=m or activeTouches.valueAt(i);nativeButtons(m);screen.invalidate()}
    override fun dispatchTouchEvent(e:MotionEvent):Boolean{val a=e.actionMasked;val th=if(gameplayFullscreen)0f else dp(62).toFloat();if(e.y<th&&activeTouches.size()==0)return super.dispatchTouchEvent(e);when(a){MotionEvent.ACTION_DOWN->{activeTouches.clear();activeTouches.put(e.getPointerId(0),controlMaskAt(e.getX(0),e.getY(0)));refreshTouchMask();return true};MotionEvent.ACTION_POINTER_DOWN->{val i=e.actionIndex;activeTouches.put(e.getPointerId(i),controlMaskAt(e.getX(i),e.getY(i)));refreshTouchMask();return true};MotionEvent.ACTION_MOVE->{for(i in 0 until e.pointerCount)activeTouches.put(e.getPointerId(i),controlMaskAt(e.getX(i),e.getY(i)));refreshTouchMask();return true};MotionEvent.ACTION_POINTER_UP->{val lifted=e.actionIndex;activeTouches.delete(e.getPointerId(lifted));for(i in 0 until e.pointerCount)if(i!=lifted)activeTouches.put(e.getPointerId(i),controlMaskAt(e.getX(i),e.getY(i)));refreshTouchMask();return true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{activeTouches.clear();nativeButtons(0);screen.invalidate();return true}};return true}
    inner class GameView:View(this){private val paint=Paint(Paint.FILTER_BITMAP_FLAG);private val bitmap=Bitmap.createBitmap(256,240,Bitmap.Config.ARGB_8888);init{isClickable=true;isFocusable=true;isFocusableInTouchMode=true};override fun onDraw(c:Canvas){val pixels=nativeFrame();if(pixels.size==256*240)bitmap.setPixels(pixels,0,256,0,0,256,240);c.drawColor(Color.BLACK);val integerScale=minOf(width/256,height/240);val scale=if(integerScale>=1)integerScale.toFloat() else minOf(width/256f,height/240f);val dw=256f*scale;val dh=240f*scale;val left=(width-dw)/2f;val top=(height-dh)/2f;c.drawBitmap(bitmap,null,RectF(left,top,left+dw,top+dh),paint);drawControls(c)};private fun drawControls(c:Canvas){val w=width.toFloat();val h=height.toFloat();val r=minOf(w,h)*.105f;val dCx=w*.16f;val dCy=h*.77f;val aX=w*.84f;val aY=h*.77f;val bX=w*.72f;val bY=h*.86f;paint.style=Paint.Style.FILL;paint.alpha=145;paint.color=Color.WHITE;c.drawCircle(dCx,dCy,r,paint);c.drawCircle(dCx-r*1.05f,dCy,r*.55f,paint);c.drawCircle(dCx+r*1.05f,dCy,r*.55f,paint);c.drawCircle(dCx,dCy-r*1.05f,r*.55f,paint);c.drawCircle(dCx,dCy+r*1.05f,r*.55f,paint);c.drawCircle(aX,aY,r,paint);c.drawCircle(bX,bY,r*.9f,paint);paint.textAlign=Paint.Align.CENTER;paint.textSize=r*.55f;paint.color=Color.DKGRAY;paint.alpha=230;c.drawText("A",aX,aY+r*.2f,paint);c.drawText("B",bX,bY+r*.2f,paint);paint.alpha=145;paint.color=Color.WHITE;c.drawRoundRect(RectF(w*.39f,h*.82f,w*.51f,h*.96f),16f,16f,paint);c.drawRoundRect(RectF(w*.51f,h*.82f,w*.63f,h*.96f),16f,16f,paint);paint.color=Color.DKGRAY;paint.alpha=230;paint.textSize=r*.38f;c.drawText("SELECT",w*.45f,h*.90f,paint);c.drawText("START",w*.57f,h*.90f,paint);var held=0;for(i in 0 until activeTouches.size())held=held or activeTouches.valueAt(i);paint.color=Color.YELLOW;paint.alpha=210;if((held and 8)!=0)c.drawCircle(dCx,dCy-r*1.05f,r*.56f,paint);if((held and 4)!=0)c.drawCircle(dCx,dCy+r*1.05f,r*.56f,paint);if((held and 2)!=0)c.drawCircle(dCx-r*1.05f,dCy,r*.56f,paint);if((held and 1)!=0)c.drawCircle(dCx+r*1.05f,dCy,r*.56f,paint);if((held and 16)!=0)c.drawCircle(aX,aY,r*.88f,paint);if((held and 32)!=0)c.drawCircle(bX,bY,r*.78f,paint);if((held and 64)!=0)c.drawRoundRect(RectF(w*.39f,h*.82f,w*.51f,h*.96f),16f,16f,paint);if((held and 128)!=0)c.drawRoundRect(RectF(w*.51f,h*.82f,w*.63f,h*.96f),16f,16f,paint)}}
    override fun onDestroy(){running=false;audioRunning=false;activeTouches.clear();nativeButtons(0);try{audio?.pause()}catch(_:Exception){};try{audio?.flush()}catch(_:Exception){};try{audio?.release()}catch(_:Exception){};super.onDestroy()}
}