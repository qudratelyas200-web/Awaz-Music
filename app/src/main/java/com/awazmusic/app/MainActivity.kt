package com.awazmusic.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private data class Song(val title: String, val uri: android.net.Uri)
    private val songs = mutableListOf<Song>()
    private var player: MediaPlayer? = null
    private var currentIndex = -1
    private lateinit var status: TextView
    private lateinit var songList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val titles = mutableListOf<String>()
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestAudioPermission()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 16)
        }
        root.addView(TextView(this).apply {
            text = "🎵 Awaz Music"
            textSize = 28f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply {
            text = "آماده برای پخش موسیقی"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        root.addView(status)
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }
        controls.addView(makeButton("قبلی") { playRelative(-1) })
        controls.addView(makeButton("پخش / مکث") { togglePlayback() })
        controls.addView(makeButton("بعدی") { playRelative(1) })
        root.addView(controls)
        root.addView(makeButton("🎙 فرمان صوتی فارسی") { listenForCommand() })
        songList = ListView(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, titles)
        songList.adapter = adapter
        songList.setOnItemClickListener { _, _, position, _ -> playSong(position) }
        root.addView(songList, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun requestAudioPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            requestPermissions(arrayOf(permission), 10)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else if (requestCode == 10) {
            status.text = "برای دیدن آهنگ‌ها، مجوز موسیقی لازم است."
        }
    }

    private fun loadSongs() {
        songs.clear()
        titles.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "آهنگ بدون نام"
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                songs.add(Song(title, uri))
                titles.add(title)
            }
        }
        adapter.notifyDataSetChanged()
        status.text = if (songs.isEmpty()) "آهنگی پیدا نشد؛ فایل موسیقی را در گوشی ذخیره کن." else "${songs.size} آهنگ پیدا شد"
    }

    private fun playSong(index: Int) {
        if (songs.isEmpty()) {
            status.text = "آهنگی برای پخش پیدا نشد."
            return
        }
        currentIndex = (index + songs.size) % songs.size
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, songs[currentIndex].uri)
                setOnCompletionListener { playRelative(1) }
                prepare()
                start()
            }
            status.text = "در حال پخش: ${songs[currentIndex].title}"
            songList.setItemChecked(currentIndex, true)
        } catch (e: Exception) {
            status.text = "پخش این آهنگ ممکن نشد."
        }
    }

    private fun playRelative(step: Int) {
        if (songs.isEmpty()) {
            status.text = "ابتدا آهنگ‌ها را بارگذاری کن."
            return
        }
        val next = if (currentIndex == -1) 0 else currentIndex + step
        playSong((next + songs.size) % songs.size)
    }

    private fun togglePlayback() {
        val p = player
        if (p == null) {
            playRelative(1)
        } else if (p.isPlaying) {
            p.pause()
            status.text = "پخش متوقف شد"
        } else {
            p.start()
            status.text = "در حال پخش: ${songs.getOrNull(currentIndex)?.title ?: ""}"
        }
    }

    private fun listenForCommand() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 11)
            Toast.makeText(this, "مجوز میکروفون را بده و دوباره دکمه را بزن.", Toast.LENGTH_LONG).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "تشخیص صدا در این گوشی در دسترس نیست."
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "فرمان بده: آهنگ بعدی، قبلی، پخش یا مکث")
        }
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "گوش می‌دهم..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "در حال تشخیص فرمان..." }
            override fun onError(error: Int) { status.text = "صدا تشخیص داده نشد؛ دوباره تلاش کن." }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().lowercase(Locale.ROOT)
                status.text = "فرمان: $words"
                when {
                    words.contains("بعدی") || words.contains("بعد") || words.contains("next") -> playRelative(1)
                    words.contains("قبلی") || words.contains("قبل") || words.contains("previous") -> playRelative(-1)
                    words.contains("مکث") || words.contains("توقف") || words.contains("pause") -> if (player?.isPlaying == true) togglePlayback()
                    words.contains("از اول") || words.contains("شروع دوباره") || words.contains("اول") -> {
                        if (player != null && currentIndex >= 0) {
                            player?.seekTo(0)
                            if (player?.isPlaying != true) player?.start()
                            status.text = "آهنگ از اول پخش می‌شود"
                        } else playRelative(1)
                    }
                    words.contains("پخش") || words.contains("شروع") || words.contains("ادامه") || words.contains("play") -> togglePlayback()
                    else -> status.text = "فرمان شناخته نشد؛ بگو آهنگ بعدی یا مکث."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}