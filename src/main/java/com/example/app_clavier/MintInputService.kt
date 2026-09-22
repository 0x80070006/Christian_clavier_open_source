package com.example.app_clavier

import android.content.*
import android.inputmethodservice.InputMethodService
import android.os.*
import android.text.InputType
import android.view.*
import android.view.inputmethod.*
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class MintInputService:InputMethodService(){
    private var keyboard:MintKeyboard?=null
    private var secure=false;private var canCorrect=false
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val clipboard by lazy{getSystemService(CLIPBOARD_SERVICE) as ClipboardManager}
    private val clipboardListener=ClipboardManager.OnPrimaryClipChangedListener{if(!secure){ClipboardHistory.capture(this,clipboard);keyboard?.refreshClipboardPanel()}}
    private var clipboardListening=false
    private val suggestionRunnable=Runnable{calculateSuggestions()}
    @Volatile private var corrector:FrenchCorrector?=null
    @Volatile private var generation=0
    private var session=0
    private var currentCursor=-1
    private var suggestionWord=""
    @Volatile private var destroyed=false
    private val prefs by lazy{KeyboardPrefs.of(this)}
    private data class Undo(val before:String,val after:String,val session:Int)
    private data class QueuedKey(val session:Int,val key:String)
    private var undo:Undo?=null
    private val queuedKeys=ArrayDeque<QueuedKey>(96)
    private val prefsListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->main.post{keyboard?.refreshTheme();scheduleSuggestions()}}
    override fun onCreate(){super.onCreate();prefs.registerOnSharedPreferenceChangeListener(prefsListener);worker.execute{EmojiCatalog.all(this);corrector=FrenchCorrector(assets.open("fr_frequency.txt").reader());if(!destroyed)main.post{scheduleSuggestions()}}}
    override fun onDestroy(){destroyed=true;if(clipboardListening)clipboard.removePrimaryClipChangedListener(clipboardListener);prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy()}
    override fun onCreateInputView():View=MintKeyboard(this,::handle).also{keyboard=it;configure(currentInputEditorInfo)}
    override fun onEvaluateFullscreenMode()=false
    override fun onStartInput(info:EditorInfo?,restarting:Boolean){super.onStartInput(info,restarting);session++;generation++;undo=null;queuedKeys.clear();currentCursor=info?.initialSelEnd ?: -1;configure(info)}
    override fun onStartInputView(info:EditorInfo?,restarting:Boolean){super.onStartInputView(info,restarting);configure(info);flushQueuedKeys();applyPending()}
    override fun onWindowShown(){super.onWindowShown();keyboard?.refreshTheme();if(!clipboardListening){clipboard.addPrimaryClipChangedListener(clipboardListener);clipboardListening=true};if(!secure)ClipboardHistory.capture(this,clipboard);flushQueuedKeys();applyPending()}
    override fun onWindowHidden(){if(clipboardListening){clipboard.removePrimaryClipChangedListener(clipboardListener);clipboardListening=false};super.onWindowHidden()}
    override fun onFinishInput(){generation++;session++;undo=null;queuedKeys.clear();main.removeCallbacks(suggestionRunnable);super.onFinishInput()}
    override fun onUpdateSelection(oldSelStart:Int,oldSelEnd:Int,newSelStart:Int,newSelEnd:Int,candidatesStart:Int,candidatesEnd:Int){
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
        currentCursor=newSelEnd
        if(newSelStart!=newSelEnd)undo=null
        scheduleSuggestions()
    }
    private fun configure(info:EditorInfo?){
        val type=info?.inputType ?: 0;val variation=type and InputType.TYPE_MASK_VARIATION;val cls=type and InputType.TYPE_MASK_CLASS
        secure=(cls==InputType.TYPE_CLASS_TEXT && variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) || (cls==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        canCorrect=cls==InputType.TYPE_CLASS_TEXT && !secure && variation !in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,InputType.TYPE_TEXT_VARIATION_URI) && type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS==0
        keyboard?.setSearchAction((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION==EditorInfo.IME_ACTION_SEARCH)
        keyboard?.reset(cls==InputType.TYPE_CLASS_NUMBER || cls==InputType.TYPE_CLASS_PHONE || cls==InputType.TYPE_CLASS_DATETIME,secure)
    }
    private fun trailingWord(text:String):String=text.takeLastWhile{it.isLetter() || it=='\'' || it=='’' || it=='-'}
    private fun personal(word:String)=prefs.getString("personal","")!!.lineSequence().any{it.trim().equals(word,true)}
    private fun scheduleSuggestions(){
        if(destroyed)return
        generation++
        main.removeCallbacks(suggestionRunnable)
        // Avoid a cross-process cursor query and toolbar redraw between fast consecutive taps.
        main.postDelayed(suggestionRunnable,85)
    }
    private fun calculateSuggestions(){
        if(destroyed)return
        val id=generation
        if(!canCorrect){keyboard?.showSuggestions(emptyList());return}
        val text=currentInputConnection?.getTextBeforeCursor(80,0)?.toString() ?: ""
        val word=trailingWord(text)
        if(word.length<2){suggestionWord="";keyboard?.showSuggestions(emptyList());return}
        val engine=corrector ?: return
        val tolerance=prefs.getInt("tolerance",55)
        worker.execute {
            if(id!=generation)return@execute
            val learned=UserLexicon.suggestions(this,word).map{if(word.contains('’'))it.replace('\'','’') else it}
            val choices=(learned+engine.candidates(word,tolerance).map{it.word}).distinct().take(3)
            main.post{if(id==generation && !destroyed && keyboard?.showSuggestions(choices)==true){suggestionWord=word}}
        }
    }
    private fun delimiter(value:String){
        val ic=currentInputConnection ?: return
        val word=trailingWord(ic.getTextBeforeCursor(80,0)?.toString() ?: "")
        val selected=!ic.getSelectedText(0).isNullOrEmpty()
        ic.commitText(value,1) // Never wait for lexical search before displaying the typed character.
        val engine=corrector
        if(canCorrect && !secure && word.length>=2 && engine?.contains(word)!=true)UserLexicon.record(this,word)
        if(!canCorrect || selected || engine==null || !prefs.getBoolean("correction",true) || personal(word) || word.length<3)return
        val inputSession=session;val expected=word+value;val tolerance=prefs.getInt("tolerance",55)
        worker.execute {
            val replacement=engine.correction(word,tolerance) ?: return@execute
            main.post {
                val connection=currentInputConnection
                if(!destroyed && session==inputSession && connection!=null && connection.getSelectedText(0).isNullOrEmpty() && connection.getTextBeforeCursor(expected.length,0)?.toString()==expected){
                    connection.beginBatchEdit();connection.deleteSurroundingText(expected.length,0);connection.commitText(replacement+value,1);connection.endBatchEdit()
                    undo=Undo(expected,replacement+value,session)
                }
            }
        }
    }
    private fun undoCorrection():Boolean {
        val u=undo ?: return false;val ic=currentInputConnection ?: return false
        if(u.session!=session || ic.getTextBeforeCursor(u.after.length,0)?.toString()!=u.after){undo=null;return false}
        ic.beginBatchEdit();ic.deleteSurroundingText(u.after.length,0);ic.commitText(u.before,1);ic.endBatchEdit();undo=null;return true
    }
    private fun flushQueuedKeys(){
        if(currentInputConnection==null)return
        while(queuedKeys.isNotEmpty()){
            val next=queuedKeys.removeFirst()
            if(next.session==session)handle(next.key)
        }
    }
    private fun handle(key:String){
        val ic=currentInputConnection
        if(ic==null){if(queuedKeys.size>=96)queuedKeys.removeFirst();queuedKeys.addLast(QueuedKey(session,key));return}
        when {
            key=="delete" -> {if(!undoCorrection()){if(!ic.getSelectedText(0).isNullOrEmpty())ic.commitText("",1)else ic.deleteSurroundingTextInCodePoints(1,0)}}
            key.startsWith("replaceLong:") -> {
                val parts=key.split(':',limit=3)
                if(parts.size==3 && ic.getSelectedText(0).isNullOrEmpty() && ic.getTextBeforeCursor(1,0)?.toString()?.equals(parts[1],true)==true){
                    ic.beginBatchEdit();ic.deleteSurroundingTextInCodePoints(1,0);ic.commitText(parts[2],1);ic.endBatchEdit()
                }
            }
            key=="undo" -> if(!undoCorrection())Toast.makeText(this,"Aucune correction à annuler ici",Toast.LENGTH_SHORT).show()
            key=="enter" -> {
                val options=currentInputEditorInfo?.imeOptions ?: 0;val a=options and EditorInfo.IME_MASK_ACTION
                if(options and EditorInfo.IME_FLAG_NO_ENTER_ACTION==0 && a!=EditorInfo.IME_ACTION_NONE && a!=EditorInfo.IME_ACTION_UNSPECIFIED)ic.performEditorAction(a)else delimiter("\n")
            }
            key=="back" -> requestHideSelf(0)
            key=="picker" -> (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            key in listOf("settings","themes","correction") -> startActivity(Intent(this,SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("section",key))
            key=="voice" || key=="media" -> {
                if(secure){Toast.makeText(this,"Indisponible dans un champ privé",Toast.LENGTH_SHORT).show();return}
                startActivity(Intent(this,MediaInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind",key).putExtra("target",currentInputEditorInfo?.packageName))
            }
            key=="paste" -> if(!secure){val clip=(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip;if(clip!=null && clip.itemCount>0)ic.commitText(clip.getItemAt(0).coerceToText(this),1)}
            key.startsWith("clip:") -> if(!secure){val index=key.substringAfter(':').toIntOrNull() ?: -1;ClipboardHistory.items(this).getOrNull(index)?.let{ic.commitText(it,1)}}
            key=="clearclips" -> {ClipboardHistory.clear(this);Toast.makeText(this,"Historique effacé",Toast.LENGTH_SHORT).show()}
            key=="selectAll" -> ic.performContextMenuAction(android.R.id.selectAll)
            key=="copy" -> if(!secure)ic.performContextMenuAction(android.R.id.copy)
            key=="cut" -> if(!secure)ic.performContextMenuAction(android.R.id.cut)
            key=="left" || key=="right" -> {undo=null;val code=if(key=="left")KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT;ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code));ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))}
            key.startsWith("suggest:") -> {
                val word=trailingWord(ic.getTextBeforeCursor(80,0)?.toString() ?: "")
                if(word.isNotEmpty() && word==suggestionWord && canCorrect){var new=key.substringAfter(':');if(word.first().isUpperCase())new=new.replaceFirstChar{it.uppercase()};ic.beginBatchEdit();ic.deleteSurroundingText(word.length,0);ic.commitText("$new ",1);ic.endBatchEdit();UserLexicon.record(this,new)}
            }
            key in listOf(" ",".",",","!","?",";",":") -> delimiter(key)
            else -> {undo=null;ic.commitText(key,1)}
        }
        InputLatency.committed()
        scheduleSuggestions()
    }
    private fun applyPending(){
        val pending=PendingInput.result ?: return
        val info=currentInputEditorInfo ?: return
        if(info.packageName!=pending.target)return
        PendingInput.result=null
        if(secure)return
        pending.text?.let{currentInputConnection?.commitText(it,1)}
        pending.uri?.let{uri->
            val mime=contentResolver.getType(uri) ?: "image/*"
            if(Build.VERSION.SDK_INT>=25 && info.contentMimeTypes?.any{ClipDescription.compareMimeTypes(mime,it)}==true){
                val content=InputContentInfo(uri,ClipDescription("Image choisie",arrayOf(mime)),null)
                val ok=runCatching{currentInputConnection?.commitContent(content,InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null)==true}.getOrDefault(false)
                if(!ok)Toast.makeText(this,"Cette application a refusé l’image",Toast.LENGTH_LONG).show()
            }else Toast.makeText(this,"Ce champ n’accepte pas les images ou GIF du clavier",Toast.LENGTH_LONG).show()
        }
    }
}
