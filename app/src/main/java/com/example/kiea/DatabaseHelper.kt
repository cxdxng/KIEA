package com.example.kiea

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VER) {

    companion object {

        val DATABASE_NAME = "information.db"
        val DATABASE_VER = 1
        val TABLE_NAME = "information_data"
        val COL_0 = "kennung"
        val COL_1 = "name"
        val COL_2 = "date"
        val COL_3 = "facedata"

    }


    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = ("CREATE TABLE $TABLE_NAME ($COL_0 INTEGER PRIMARY KEY, $COL_1 TEXT, $COL_2 INTEGER, $COL_3 TEXT)")
        //db!!.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        db!!.execSQL(createTable)

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db!!.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getAllData():Cursor{
        val db = this.writableDatabase
        val res = db.rawQuery("select kennung, name, date from $TABLE_NAME", null)
        return res
    }

    fun deleteAllData(){
        val db = this.writableDatabase
        db.execSQL("delete from $TABLE_NAME")
        //onCreate(db)

    }

    fun selectEntry(kennung: String):Cursor{
        val db = this.writableDatabase
        return db.rawQuery("select * from $TABLE_NAME where kennung = ?", arrayOf(kennung))
    }

    fun deleteEntry(kennung: String):Int{
        val db = this.writableDatabase
        return db.delete(TABLE_NAME, "kennung = ?", arrayOf(kennung))
    }

    fun addFaceData(faceBase64:String, kennung:String):Boolean{

        val split = kennung.split( " ")
        val id = BackgroundTasks().getNumbersFromWords(split[3])

        val db = this.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(COL_3, faceBase64)
        db.execSQL("update $TABLE_NAME set facedata = '$faceBase64' where kennung = '${id}'")

        return true
    }

    fun deleteFaceData(kennung: String){
        val db = this.writableDatabase
        db.execSQL("update $TABLE_NAME set facedata = 'nicht vorhanden' where kennung = '$kennung'")
    }

    fun insertData(name:String, date:Int, facedata:String):String?{
        val db = this.writableDatabase
        val contentValues = ContentValues()

        contentValues.put(COL_1, name)
        contentValues.put(COL_2, date)
        contentValues.put(COL_3, facedata)
        val result = db.insert(TABLE_NAME,null,contentValues).toInt()
        val lul = db.rawQuery("select kennung from $TABLE_NAME where name = ? and date = ?", arrayOf(name, date.toString()))
        while (lul.moveToNext()){
            return lul.getString(0)
        }
        return null
    }
}