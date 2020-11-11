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
    //Define DatabaseHelper
    lateinit var myDb:DatabaseHelper
    //Define Model
    private var model: Model? = null
    //Define SpeechRecognizer
    private var recognizer: SpeechRecognizer? = null
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
        //Initiate DatabaseHelper Object
        myDb = DatabaseHelper(this)
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

    class SetupTask(activity: MainActivity) : AsyncTask<Void, Void, Exception>() {
        //Get an activity reference
        var activityReference: WeakReference<MainActivity>

        init {
            this.activityReference = WeakReference(activity)
        }

        //Load Vosk Api and model in AsyncTask
        override fun doInBackground(vararg p0: Void?): Exception? {
            try {

                //Set the assets for Vosk Api
                val assets = Assets(activityReference.get())
                val assetDir = assets.syncAssets()
                Log.d("KIEA", "Sync files in the folder $assetDir")
                //Set the Log level from Vosk Api to -1 to disable logging
                Vosk.SetLogLevel(-1)
                //Set the language model which is a german one
                activityReference.get()!!.model = Model("$assetDir/model-android")

            }catch (e: IOException){
                return e
            }
            return null
        }

        //If AsyncTask has finished set Ui states to Ready
        override fun onPostExecute(result: Exception?) {
            super.onPostExecute(result)
            activityReference.get()?.setUiState(1)
        }
    }
    //Check for Permissions
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                SetupTask(this).execute()
            } else {
                finish()
            }
        }
    }

    //Shutdown the recognizer
    override fun onDestroy() {
        super.onDestroy()
        if (recognizer != null) {
            recognizer!!.cancel()
            recognizer!!.shutdown()
        }
    }

    //Get result from Vosk Api
    override fun onResult(p0: String) {
        setUiState(STATE_DONE)
        textViewKIEAStatus.text = "K.I.E.A Bereit"
        recognizer!!.cancel()
        recognizer = null

        //Remove debugging info and extract pure result from JSON format
        val jres = JSONObject(p0)
        val text: String = jres["text"] as String

        //Set msg to have the value of text in global variable
        msg = text
        //Show result message to the user in TextView
        textViewInfo.text = text

        //Check current requestCode
        when (currentRequestCode){
            //When requestCode is 100, create a normal request
            100 -> {
                //Check if there is a follow-up request, and if so, change current request code according to action
                if (text == "eintrag hinzufügen") {
                    speak("Name und Jahr bitte")
                    currentRequestCode = 101
                    while (tts.isSpeaking) {}
                    recognizeMicrophone()
                } else if (text == "selbst zerstörung" || text == "selbst zerstören") {
                    speak("Sind sie sicher?")
                    currentRequestCode = 103
                    while (tts.isSpeaking) {}
                    recognizeMicrophone()
                } else if (text == "eintrag löschen") {
                    speak("Kennung bitte")
                    currentRequestCode = 104
                    while (tts.isSpeaking) {
                    }
                    recognizeMicrophone()
                }
                //Execute the receive method to process the result
                receive(text)
            }
            //When requestCode is 101, then create a new Entry in Sqlite database
            101 -> {
                //Split result into segments to get the ID
                val split = text.split(" ")
                //Convert ID from word to integer
                val id = BackgroundTasks().getNumbersFromWords(split[1])
                //Insert data into Sqlite database
                val isInserted = myDb.insertData(split[0], id, "nicht vorhanden")
                //Check if insert succeeded
                if (isInserted != null) speak("Erfolgreich eingetragen\n Neue Kennung: $isInserted")
                else speak("Fehler")
                //Reset the requestCode to 100
                currentRequestCode = 100
            }
            //When request is 103, initiate self-destruction
            103 -> {
                if (msg == "ja") {
                    Log.e("JAWOHL", "ALLA")
                    myDb.deleteAllData()
                    speak("Datenbank erfolgreich zerstört")
                } else if (msg == "nein") {
                    speak("Vorgang abgebrochen")
                }
                //Reset the requestCode to 100
                currentRequestCode = 100
            }
            //When requestCode is 104, delete an entry from Sqlite database
            104 -> {
                val id = BackgroundTasks().getNumbersFromWords(text).toString()
                val status = myDb.deleteEntry(id)
                if (status > 0) speak("Eintrag gelöscht")
                else speak("Fehler, Eintrag nicht gelöscht")
                //Reset the requestCode to 100
                currentRequestCode = 100
            }
        }
    }

    //Get a partial result from Vosk Api
    override fun onPartialResult(p0: String?) {
        //Remove debugging info and extract pure result from JSON format
        val jres = JSONObject(p0)
        val text: String = jres["partial"] as String
        //Show user the partial result
        textViewInfo.text = text

    }

    //Handle Timeout
    override fun onTimeout() {
        recognizer!!.cancel()
        recognizer = null
        setUiState(STATE_READY)
    }

    //Handle errors from Vosk Api
    override fun onError(p0: java.lang.Exception?) {
        //Display the Errors if there are any
        result_text.append("${p0.toString()}\n")
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

    //Set the ErrorState
    fun setErrorState(message: String){
        result_text.text = message
        buttonTrigger.isEnabled = false
    }

    //Start recognition via Microphone
    fun recognizeMicrophone(){
            if (recognizer != null) {
                setUiState(STATE_DONE)
                recognizer!!.cancel()
                recognizer = null
            } else {
                setUiState(STATE_MIC)
                try {
                    recognizer = SpeechRecognizer(model)
                    recognizer!!.addListener(this)
                    recognizer!!.startListening()
                } catch (e: IOException) {
                    setErrorState(e.message!!)
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
        val success = myDb.addFaceData(encodedString, msg)
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

    //Method to handle speech output
    fun speak(msg: String){
        //Set rate at which speech should happen
        tts.setSpeechRate(1.7f)
        //add the stuff to say to queue
        tts.speak(msg, TextToSpeech.QUEUE_ADD, null)
    }

    //Receive result from Vosk Api and process it
    fun receive(msg: String){
        //Output all entries from Sqlite database
        if (msg == "datenbank anzeigen"){
            //Get data from database
            val cursor = myDb.getAllData()
            if (cursor.count == 0){
                Log.e("ERROR", "Database is empty")
                speak("Keine Daten vorhanden")
            }

            val stringBuffer = StringBuffer()

            //Use Cursor to get properties from current ID and store them in a stringBuffer
            while (cursor.moveToNext()){
                stringBuffer.append("\nKennung: ${cursor.getString(0)}")
                stringBuffer.append("\nName: ${cursor.getString(1)}")
                stringBuffer.append("\nGeboren: ${cursor.getString(2)}")
            }

            //Output data via speak method
            speak(stringBuffer.toString())

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
            myDb.deleteFaceData(split[3])
            speak("Gesichtsdaten erfolgreich gelöscht")
        }
        //Output all data from requested ID
        else if (msg.contains("info kennung")){
            //Split msg to get ID
            val split = msg.split(" ")
            //convert ID from word into Integer
            val id = BackgroundTasks().getNumbersFromWords(split[2]).toString()
            //Select entry from DB
            val cursor = myDb.selectEntry(id)

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

            //Output data via speak method
            speak(stringBuffer.toString())

        }

        else if (msg == "herunterfahren"){
            speak("Shut down initialisiert,\n bis dann Sir")
            while (tts.isSpeaking){}
            tts.shutdown()
            finish()
        }
    }
}
/*
INDEX LIST OF ID'S IN SQLITE DATABASE
    1 Marlon
    2 Schatz
    3 Michi
    4 Max
    5 Tim
    6 Beverly
    7 TestUser
*/