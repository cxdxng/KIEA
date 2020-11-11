package com.example.kiea

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.util.Log
import android.view.View
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.face.FaceDetector
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream

class BackgroundTasks {



    fun getNumbersFromWords(word:String):Int{

        var id =  0

        when (word){

            "eins" -> id =1
            "zwei" -> id = 2
            "drei" -> id = 3
            "vier" -> id = 4
            "fünf" -> id = 5
            "sechs" -> id = 6
            "sieben" -> id = 7
            "acht" -> id = 8
            "neun" -> id = 9
            "zehn" -> id = 10
            "elf" -> id = 11
            "zwölf" -> id = 12
            "dreizehn" -> id = 13
            "vierzehn" -> id = 14
            "fünfzehn" -> id = 15
            "sechszehn" -> id = 16
            "siebzehn" -> id = 17
            "achtzehn" -> id = 18
            "neunzehn" -> id = 19
            "zwanzig" -> id = 20
            "zweitausend" -> id = 2000
            "zweitausedeins" -> id = 2001
            "zweitausedzwei" -> id = 2002
            "zweitausenddrei" -> id = 2003
            "zweitausendvier" -> id = 2004
            "zweitausendfünf" -> id = 2005
            "zweitausendsechs" -> id = 2006
            "zweitausendsieben" -> id = 2007
            "zweitausendacht" -> id = 2008
            "zweitausendneun" -> id = 2009
            "zweitausendzehn" -> id = 2010

        }
        return id
    }


    fun recognizeFace(bitmap: Bitmap, ctx:Context): Bitmap{

        /*
        val options = BitmapFactory.Options()
        options.inMutable = true
        val bitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.woman, options)

         */

        val rectPaint = Paint()
        rectPaint.strokeWidth = 5f
        rectPaint.color = Color.GREEN
        rectPaint.style = Paint.Style.STROKE

        val tempBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val tempCanvas: Canvas = Canvas(tempBitmap)
        tempCanvas.drawBitmap(bitmap, 0f, 0f, null)

        val faceDetector = FaceDetector.Builder(ctx).setTrackingEnabled(false).setLandmarkType(FaceDetector.ALL_LANDMARKS).build()
        if (!faceDetector.isOperational) {
            Log.e("Error","Could not set up the face detector!")

        }
        val frame = Frame.Builder().setBitmap(bitmap).build()
        val faces = faceDetector.detect(frame)

        for (i in 0 until faces.size()) {

            val thisFace = faces.valueAt(i)
            val x1 = thisFace.position.x
            val y1 = thisFace.position.y
            val x2 = x1 + thisFace.width
            val y2 = y1 + thisFace.height
            tempCanvas.drawRoundRect(RectF(x1, y1, x2, y2), 2f, 2f, rectPaint)
        }

        for (i in 0 until faces.size()) {
            val face = faces.valueAt(i)
            for (landmark in face.landmarks) {
                val cx = (landmark.position.x)
                val cy = (landmark.position.y )
                tempCanvas.drawCircle(cx, cy, 10f, rectPaint)
            }
        }


        return tempBitmap


    }

}