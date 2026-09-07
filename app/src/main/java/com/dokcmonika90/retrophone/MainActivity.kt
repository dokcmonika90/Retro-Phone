package com.dokcmonika90.retrophone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.media.*
import android.os.Bundle
import android.util.SparseIntArray
import android.view.*
import android.widget.*
import java.io.File
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
    private val romDirectory by lazy { File(getExternalFilesDir(null), "ROMs").apply { mkdirs() } }
    private val oldRomDirectory by lazy { File(filesDir, "roms") }

    private val supportedExtensions = setOf("nes", "fds", "sfc", "smc", "fig", "gb", "gbc", "gba", "md", "gen", "smd", "sms", "gg", "n64", "z64", "v64", "nds", "a26", "cue", "iso", "img", "pbp", "chd", "cdi", "gdi", "zip", "7z")

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        migrateOldRoms()
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enterImmersiveUi(); screen = GameView()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(screen, FrameLayout.LayoutParams(-1,-1).apply { topMargin=dp(62) })
        toolbar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(4),dp(8),dp(4));setBackgroundColor(0xCC101010.toInt());elevation=dp(4).toFloat()}
        val load=Button(this).apply{text="LOAD ROM";setOnClickListener{pickRom()}}
        val library=Button(this).apply{text="LIBRARY";setOnClickListener{showLibrary()}}
        val web=Button(this).apply{text="WEB ROMS";setOnClickListener{startActivity(Intent(this@MainActivity,RomWebImporterActivity::class.java))}}
        val reset=Button(this).apply{text="RESET";setOnClickListener{nativeReset();status.text="  Reset";screen.invalidate()}}
        val fullscreen=Button(this).apply{text="FULLSCREEN";setOnClickListener{setGameplayFullscreen(!gameplayFullscreen)}}
        status=TextView(this).apply{text="  ${nativeVersion()}";setTextColor(Color.WHITE);textSize=13f;gravity=Gravity.CENTER_VERTICAL;maxLines=2}
        toolbar.addView(load);toolbar.addView(library);toolbar.addView(web);toolbar.addView(reset);toolbar.addView(fullscreen);toolbar.addView(status,LinearLayout.LayoutParams(0,-1,1f))
        root.addView(toolbar,FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP));setContentView(root);startAudio();startLoop();autoLoadLastRom()
    }

    private fun migrateOldRoms(){try{if(!oldRomDirectory.isDirectory)return;oldRomDirectory.listFiles()?.filter{it.isFile}?.forEach{old->val target=File(romDirectory,old.name);if(!target.exists())old.copyTo(target,false)}}catch(_:Exception){}}
    private fun dp(value:Int)= (value*resources.displayMetrics.density+0.5f).toInt()
    private fun enterImmersiveUi(){window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE}
    private fun setGameplayFullscreen(fullscreen:Boolean){gameplayFullscreen=fullscreen;toolbar.visibility=if(fullscreen)View.GONE else View.VISIBLE;val p=screen.layoutParams as FrameLayout.LayoutParams;p.topMargin=if(fullscreen)0 else dp(62);screen.layoutParams=p;status.text=if(nativeIsLoaded()){if(fullscreen)"  Fullscreen gameplay" else "  Gameplay controls visible"}else{if(fullscreen)"  Fullscreen — tap BACK to exit" else "  ${nativeVersion()}"};enterImmersiveUi();screen.invalidate()}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)enterImmersiveUi()}
    override fun onBackPressed(){if(gameplayFullscreen)setGameplayFullscreen(false)else super.onBackPressed()}

    private fun pickRom(){val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)};startActivityForResult(intent,42)}
    private fun validHeader(b:ByteArray)=b.size>=16&&b[0].toInt()=='N'.code&&b[1].toInt()=='E'.code&&b[2].toInt()=='S'.code&&b[3].toInt()==0x1A
    private fun unwrapRom(data:ByteArray):Pair<ByteArray,String>?{if(validHeader(data))return data to "NES ROM";if(data.size>=4&&data[0].toInt()==0x50&&data[1].toInt()==0x4b){ZipInputStream(data.inputStream()).use{zip->var e=zip.nextEntry;while(e!=null){if(!e.isDirectory&&e.name.lowercase().endsWith(".nes")){val rom=zip.readBytes();if(validHeader(rom))return rom to e.name};e=zip.nextEntry}}};return null}
    private fun mapperOf(bytes:ByteArray)=if(bytes.size>=8)(((bytes[6].toInt() and 0xF0) shr 4) or (bytes[7].toInt() and 0xF0)) else -1
    private fun libraryFile(bytes:ByteArray)=File(romDirectory,"rom_${Integer.toHexString(bytes.contentHashCode())}.nes")
    private fun saveToLibrary(bytes:ByteArray,name:String){try{val f=libraryFile(bytes);f.writeBytes(bytes);val now=System.currentTimeMillis();libraryPrefs.edit().putString("name_${f.name}",name.substringAfterLast('/')).putString("last_rom",f.name).putLong("played_${f.name}",now).apply();f.setLastModified(now)}catch(_:Exception){}}
    private fun markPlayed(file:File){val now=System.currentTimeMillis();libraryPrefs.edit().putString("last_rom",file.name).putLong("played_${file.name}",now).apply();file.setLastModified(now)}
    private fun autoLoadLastRom(){val name=libraryPrefs.getString("last_rom",null)?:return;val file=File(romDirectory,name);if(!file.isFile)return;try{val bytes=file.readBytes();if(validHeader(bytes)){loadRomBytes(bytes,libraryPrefs.getString("name_${file.name}",file.name)?:file.name,false);markPlayed(file)}}catch(_:Exception){}}

    private fun showLibrary(){
        val all=romDirectory.listFiles{f->f.isFile&&f.extension.lowercase() in supportedExtensions}?.toList()?:emptyList()
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(8))}
        val search=EditText(this).apply{hint="Search games...";singleLine=true}
        val systems=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("All Systems","NES","SNES","Game Boy / Color","Game Boy Advance","Genesis / Mega Drive","Master System / Game Gear","Nintendo 64","Nintendo DS","PlayStation","PSP","Dreamcast / Naomi","Atari 2600","Arcade"))}
        val sort=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Recently Played","Name","Size"))}
        val favoritesOnly=CheckBox(this).apply{text="Favorites only"}
        val last=libraryPrefs.getString("last_rom",null)
        val lastLabel=TextView(this).apply{text=if(last!=null)"Last played: ${libraryPrefs.getString("name_$last",last)}" else "No game played yet";textSize=14f;setPadding(0,dp(4),0,dp(8))}
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val scroll=ScrollView(this).apply{addView(list)}
        box.addView(search);box.addView(systems);box.addView(sort);box.addView(favoritesOnly);box.addView(lastLabel);box.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun systemFor(ext:String):String=when(ext.lowercase()){"nes","fds"->"NES";"sfc","smc","fig"->"SNES";"gb","gbc"->"Game Boy / Color";"gba"->"Game Boy Advance";"md","gen","smd"->"Genesis / Mega Drive";"sms","gg"->"Master System / Game Gear";"n64","z64","v64"->"Nintendo 64";"nds"->"Nintendo DS";"cue","iso","img","pbp","chd"->"PlayStation";"cso"->"PSP";"cdi","gdi"->"Dreamcast / Naomi";"a26"->"Atari 2600";"zip","7z"->"Arcade";else->"Other"}
        fun playableFor(ext:String)=EmulatorCoreRegistry.findForExtension(ext).any{it.installed}
        fun refresh(){
            list.removeAllViews();val q=search.text.toString().trim().lowercase();val selected=systems.selectedItem?.toString()?:("All Systems");val mode=sort.selectedItemPosition
            val files=all.filter{f->val n=libraryPrefs.getString("name_${f.name}",f.name)?:f.name;val sys=systemFor(f.extension);(!favoritesOnly.isChecked||libraryPrefs.getBoolean("fav_${f.name}",false))&&(q.isEmpty()||n.lowercase().contains(q))&&(selected=="All Systems"||selected==sys)}.sortedWith(when(mode){1->compareBy{libraryPrefs.getString("name_${it.name}",it.name)?.lowercase()?:it.name.lowercase()};2->compareByDescending<File>{it.length()};else->compareByDescending{libraryPrefs.getLong("played_${it.name}",it.lastModified())}})
            if(files.isEmpty()){list.addView(TextView(this).apply{text="No matching games.";textSize=17f;setPadding(0,dp(16),0,dp(16))});return}
            files.forEach{file->val name=libraryPrefs.getString("name_${file.name}",file.name)?:file.name;val fav=libraryPrefs.getBoolean("fav_${file.name}",false);val sys=systemFor(file.extension);val playable=playableFor(file.extension);val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(6),0,dp(6))};val info=TextView(this).apply{text=(if(fav)"★ " else "☆ ")+name+"\n$sys • ${file.extension.uppercase()} • ${file.length()/1024} KB\n${if(playable)"READY TO PLAY" else "CORE NOT INSTALLED"}";textSize=16f};val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val play=Button(this).apply{text=if(playable)"PLAY" else "INFO"};val star=Button(this).apply{text=if(fav)"UNFAVORITE" else "FAVORITE"};val del=Button(this).apply{text="DELETE"}
                if(playable)play.setOnClickListener{try{if(file.extension.equals("nes",true)){loadRomBytes(file.readBytes(),name,false);markPlayed(file);showLibrary()}else Toast.makeText(this@MainActivity,"${systemFor(file.extension)} is registered but its native core is not wired into gameplay yet.",Toast.LENGTH_LONG).show()}catch(_:Exception){}} else play.setOnClickListener{Toast.makeText(this@MainActivity,"${systemFor(file.extension)} core is not installed yet.",Toast.LENGTH_SHORT).show()}
                star.setOnClickListener{libraryPrefs.edit().putBoolean("fav_${file.name}",!libraryPrefs.getBoolean("fav_${file.name}",false)).apply();showLibrary()}
                del.setOnClickListener{AlertDialog.Builder(this).setTitle("Delete ROM?").setMessage("Delete $name from the ROM Library?").setNegativeButton("CANCEL",null).setPositiveButton("DELETE"){_,_->file.delete();val edit=libraryPrefs.edit().remove("name_${file.name}").remove("played_${file.name}").remove("fav_${file.name}");if(libraryPrefs.getString("last_rom",null)==file.name)edit.remove("last_rom");edit.apply();showLibrary()}.show()}
                buttons.addView(play);buttons.addView(star);buttons.addView(del);row.addView(info);row.addView(buttons);list.addView(row)}
        }
        search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()};override fun afterTextChanged(s:android.text.Editable?){}})
        systems.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){refresh()}}
        sort.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){refresh()}}
        favoritesOnly.setOnCheckedChangeListener{_,_->refresh()};refresh();AlertDialog.Builder(this).setTitle("ROM Library").setView(box).setNegativeButton("CLOSE",null).show()
    }

    private fun loadRomBytes(bytes:ByteArray,name:String,save:Boolean){val mapper=mapperOf(bytes);try{if(save)saveToLibrary(bytes,name);val ok=nativeLoad(bytes);if(ok){nativeReset();status.text="  ROM loaded: $name (Mapper $mapper, ${bytes.size/1024} KB)";screen.invalidate()}else{status.text="  Mapper $mapper is not implemented by the current core";Toast.makeText(this,"LaiNES could not load Mapper $mapper",Toast.LENGTH_LONG).show()}}catch(e:Exception){status.text="  ROM load failed";Toast.makeText(this,"ROM load failed: ${e.message?:"unknown error"}",Toast.LENGTH_LONG).show()}}
    override fun onActivityResult(req:Int,result:Int,data:Intent?){super.onActivityResult(req,result,data);if(req!=42||result!=RESULT_OK||data?.data==null)return;try{val uri=data.data!!;try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};val raw=contentResolver.openInputStream(uri)?.use{it.readBytes()};if(raw==null){status.text="  ROM could not be read";return};val u=unwrapRom(raw);if(u==null){status.text="  Unsupported ROM wrapper";Toast.makeText(this,"Only NES ROMs can currently be loaded into the emulator. Other formats can be stored in the library.",Toast.LENGTH_LONG).show();return};loadRomBytes(u.first,u.second,true)}catch(e:Exception){status.text="  ROM load failed";Toast.makeText(this,"ROM load failed: ${e.message?:"unknown error"}",Toast.LENGTH_LONG).show()}}
    private fun startAudio(){if(audioRunning)return;try{val sr=44100;val min=AudioTrack.getMinBufferSize(sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw IllegalStateException("AudioTrack buffer size unavailable");val buffer=max(min,sr/4*2);audio=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(buffer).setTransferMode(AudioTrack.MODE_STREAM).build();audio?.play();audioRunning=true;Thread{while(audioRunning&&!isFinishing){try{val pcm=nativeAudio();if(pcm.isNotEmpty())audio?.write(pcm,0,pcm.size,AudioTrack.WRITE_BLOCKING)else Thread.sleep(2)}catch(_:InterruptedException){break}catch(e:Exception){runOnUiThread{status.text="  Audio error: ${e.message?:"unknown"}"};break}}}.start()}catch(e:Exception){audioRunning=false;status.text="  Audio unavailable: ${e.message?:"unknown"}"}}
    private fun startLoop(){running=true;Thread{while(running&&!isFinishing){val start=System.nanoTime();if(nativeIsLoaded())nativeRunFrame();runOnUiThread{screen.invalidate()};val sleep=frameMs-(System.nanoTime()-start)/1_000_000L;if(sleep>0)Thread.sleep(sleep)}}.start()}
    private fun controlMaskAt(x:Float,y:Float):Int{val w=screen.width.toFloat().coerceAtLeast(1f);val h=screen.height.toFloat().coerceAtLeast(1f);val r=minOf(w,h)*.105f;val dCx=w*.16f;val dCy=h*.77f;val aX=w*.84f;val aY=h*.77f;val bX=w*.72f;val bY=h*.86f;fun inside(px:Float,py:Float,rr:Float):Boolean{val dx=x-px;val dy=y-py;return dx*dx+dy*dy<=rr*rr};val dr=r*.72f;var m=0;if(inside(dCx,dCy-r*1.05f,dr))m=m or 8;if(inside(dCx,dCy+r*1.05f,dr))m=m or 4;if(inside(dCx-r*1.05f,dCy,dr))m=m or 2;if(inside(dCx+r*1.05f,dCy,dr))m=m or 1;if(m!=0)return m;if(inside(aX,aY,r*1.05f))return 16;if(inside(bX,bY,r*.95f))return 32;if(x>=w*.39f&&x<=w*.51f&&y>=h*.82f&&y<=h*.96f)return 64;if(x>=w*.51f&&x<=w*.63f&&y>=h*.82f&&y<=h*.96f)return 128;return 0}
    private fun refreshTouchMask(){var m=0;for(i in 0 until activeTouches.size())m=m or activeTouches.valueAt(i);nativeButtons(m);screen.invalidate()}
    override fun dispatchTouchEvent(e:MotionEvent):Boolean{val a=e.actionMasked;val th=if(gameplayFullscreen)0f else dp(62).toFloat();if(e.y<th&&activeTouches.size()==0)return super.dispatchTouchEvent(e);when(a){MotionEvent.ACTION_DOWN->{activeTouches.clear();activeTouches.put(e.getPointerId(0),controlMaskAt(e.getX(0),e.getY(0)));refreshTouchMask();return true};MotionEvent.ACTION_POINTER_DOWN->{val i=e.actionIndex;activeTouches.put(e.getPointerId(i),controlMaskAt(e.getX(i),e.getY(i)));refreshTouchMask();return true};MotionEvent.ACTION_MOVE->{for(i in 0 until e.pointerCount)activeTouches.put(e.getPointerId(i),controlMaskAt(e.getX(i),e.getY(i)));refreshTouchMask();return true};MotionEvent.ACTION_POINTER_UP->{activeTouches.delete(e.getPointerId(e.actionIndex));refreshTouchMask();return true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{activeTouches.clear();refreshTouchMask();return true}};return super.dispatchTouchEvent(e)}
    inner class GameView:View(this){private val paint=Paint().apply{isFilterBitmap=false};private val src=Rect(0,0,256,240);private var pixels=IntArray(256*240);override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.BLACK);if(nativeIsLoaded()){val p=nativeFrame();if(p.size==pixels.size)pixels=p;val dst=RectF(0f,0f,width.toFloat(),height.toFloat());c.drawBitmap(pixels,0,256,src,dst,paint)}else{paint.color=Color.WHITE;paint.textSize=dp(20).toFloat();paint.textAlign=Paint.Align.CENTER;c.drawText("Retro Phone",width/2f,height/2f,paint);paint.textSize=dp(14).toFloat();c.drawText("Load a legally obtained ROM to start",width/2f,height/2f+dp(30),paint)}}}
}
