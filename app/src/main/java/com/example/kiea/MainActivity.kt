package com.example.kiea

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import org.kaldi.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

class MainActivity : AppCompatActivity(), RecognitionListener {

    //Define variables

    //Define TextToSpeech
    lateinit var tts:TextToSpeech
    //Define msg String in which the result of Vosk api gets saved
    var msg:String = ""
    //Create variable for current request Code so that we can get the current action
    var currentRequestCode =  100

    /*Requestcodes:
        1 = Request Audio
        100 = Normal
        101 = New Entry
        102 = Add Face, RecognitionListener
        103 = Self destruction
        104 = Delete Entry
     */

    //Define UI states
    private val STATE_START = 0
    private val STATE_READY = 1
    private val STATE_DONE = 2
    private val STATE_MIC = 4


    init {
        //For Vosk Api error states
        System.loadLibrary("kaldi_jni")
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Enable/Disable debug mode
        switch1.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) result_text.visibility = View.VISIBLE
            else result_text.visibility = View.INVISIBLE
        }
        //Set UI state to start
        setUiState(STATE_START)
        //Does what it says
        initiateTTS()
        //Make ImageView invisible because there is no image to show
        imageView.visibility = View.INVISIBLE

        //Create Instance of SetupTask AsyncTask for Vosk Api
        SetupTask(this).execute()

        //onClick for Microphone recognition
        buttonTrigger.setOnClickListener {
            textViewKIEAStatus.text = "K.I.E.A hört zu..."

            imageView.visibility = View.INVISIBLE
            recognizeMicrophone()
        }
    }
    //Check for Permissions
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Get permission form Microphone and Internet
            } else {
                finish()
            }
        }
    }



    //Set various UI states
    fun setUiState(state: Int){
        //Set the different Messages according to ui states
        when (state) {
            STATE_START -> {
                result_text.setText(R.string.preparing)
                textViewKIEAStatus.text = "K.I.E.A Inizialisiert"
                result_text.setMovementMethod(ScrollingMovementMethod())
                buttonTrigger.isEnabled = false
            }
            STATE_READY -> {
                result_text.setText(R.string.ready)
                textViewKIEAStatus.text = "K.I.E.A Bereit"
                buttonTrigger.setText(R.string.recognize_microphone)
                buttonTrigger.isEnabled = true
            }
            STATE_DONE -> {
                buttonTrigger.setText(R.string.recognize_microphone)
                buttonTrigger.isEnabled = true
            }
            STATE_MIC -> {
                buttonTrigger.setText(R.string.stop_microphone)
                result_text.setText(getString(R.string.say_something))
                buttonTrigger.isEnabled = true
            }
        }
    }



    //Get the image from  ImagePickerActivity back to pass it to convertToBase64 method
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val imageUri = data.data
                //Create bitmap from ImageUri
                val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                //Convert it to base64 string to store it in the Sqlite database
                convertToBase64(bitmap)
            }
        }

    }

    fun initiateTTS(){
        tts = TextToSpeech(this, TextToSpeech.OnInitListener {

            if (it == TextToSpeech.SUCCESS) {
                //Set the TTS language
                val ttsLanguage = tts.setLanguage(Locale.getDefault())
                //Check is language is supported
                if (ttsLanguage == TextToSpeech.LANG_MISSING_DATA || ttsLanguage == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                    makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
                } else {
                    //If everything is fine, start the tts and output a greeting message
                    Log.i("TTS", "Initialized successfully")

                    tts.setSpeechRate(1.8f)
                    tts.speak("K\nI\nE\nA\n gestartet", TextToSpeech.QUEUE_ADD, null)

                    tts.setSpeechRate(2f)
                    tts.speak("Wie kann ich behilflich sein?", TextToSpeech.QUEUE_ADD, null)

                    while (tts.isSpeaking) {}
                    //createIntent(100)
                    //recognizeMicrophone()

                }
            }

        })
    }

    fun convertToBase64(bitmap: Bitmap){
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val b = baos.toByteArray()
        //Encode the ByteArray into an base64 string
        val encodedString = Base64.encodeToString(b, Base64.DEFAULT)
        //Insert Facedata into the Sqlite database

        //Check if insert succeeded
        if (success) speak("Gesicht erfolgreich hinzugefügt")
    }



    fun decodeBase64(cursor: Cursor){
        //Get base64 string from cursor
        val stream = ByteArrayInputStream(Base64.decode(cursor.getString(3), Base64.DEFAULT))
        val bm = BitmapFactory.decodeStream(stream)
        //Send bitmap to recognizeFace method to set Landmarks on face
        val tempBitmap = BackgroundTasks().recognizeFace(bm, applicationContext)
        //Set imageView Visible to show face with Landmarks
        imageView.visibility = View.VISIBLE
        imageView.setImageDrawable(BitmapDrawable(resources, tempBitmap))
    }

    //Receive result from Vosk Api and process it
    fun receive(msg: String){
        //Output all entries from Sqlite database
        if (msg == "datenbank anzeigen"){
            //Get data from database

            if (cursor.count == 0){
                Log.e("ERROR", "Database is empty")
            }

            val stringBuffer = StringBuffer()

            //Use Cursor to get properties from current ID and store them in a stringBuffer
            while (cursor.moveToNext()){
                stringBuffer.append("\nKennung: ${cursor.getString(0)}")
                stringBuffer.append("\nName: ${cursor.getString(1)}")
                stringBuffer.append("\nGeboren: ${cursor.getString(2)}")
            }

        }
        //Add a facedata to ID in database
        else if (msg.contains("gesicht hinzufügen")){
            //Start ImagePickerActivity to pick image from gallery
            val open = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(open, 102)
        }
        //Delete facedata from database
        else if (msg.contains("gesicht löschen")){
            //Split msg to get ID
            val split = msg.split(" ")
            //Delete Face Data here

        }
        //Output all data from requested ID
        else if (msg.contains("info kennung")){
            //Split msg to get ID
            val split = msg.split(" ")
            //convert ID from word into Integer
            val id = BackgroundTasks().getNumbersFromWords(split[2]).toString()
            //Select entry from DB
            
            val stringBuffer = StringBuffer()
            //Use Cursor to get properties from current ID and store them in a stringBuffer
            while (cursor.moveToNext()){
                stringBuffer.append("\nKennung: ${cursor.getString(0)}")
                stringBuffer.append("\nName: " + cursor.getString(1))
                stringBuffer.append("\nGeboren: " + cursor.getString(2))

                //Check if facedata is available for this ID
                if (cursor.getString(3) != "nicht vorhanden") {
                    //If so, decode the facedata and display it
                    decodeBase64(cursor)
                    stringBuffer.append("\nGesichtsdaten vorhanden")
                }else{
                    stringBuffer.append("\nGesichtsdaten nicht vorhanden")
                }
            }

        }

        else if (msg == "herunterfahren"){
            finish()
        }
    }
}
/*
INDEX LIST OF ID'S IN SQLITE DATABASE
    1 Marlon
    7 TestUser
*/