package com.masterthesis.johannes.annotationtool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import android.view.ViewConfiguration
import java.lang.Exception
import android.webkit.MimeTypeMap
import android.content.ContentResolver
import android.provider.OpenableColumns
import java.nio.file.Files.size
import android.R.attr.src
import java.io.File.separator
import android.system.Os.mkdir
import java.nio.file.Files.exists
import android.R.attr.data
import java.nio.file.Files.size
import android.R.attr.src
import java.io.File.separator
import android.system.Os.mkdir
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.Files.exists
import android.widget.Toast
import java.nio.file.Files.exists
import android.system.Os.mkdir
import java.io.File.separator
import android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
import android.provider.DocumentsContract
import android.util.Log
import androidx.constraintlayout.widget.Constraints.TAG
import android.provider.Settings.System.canWrite
import java.nio.file.Files.isDirectory
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader


val SHARED_PREFERENCES_KEY = "Shared_Preferences_Key"
val LAST_OPENED_PROJECT_DIR = "projectDir"
val USER_FLOWER_LIST = Pair(mutableSetOf("Sonnenblume", "Löwenzahn"),"flowerListKey")

val DEFAULT_MAX_ZOOM_VALUE = Pair(30F,"MAX_ZOOM_KEY")
val DEFAULT_ANNOTATION_SHOW_VALUE = Pair(0.001F,"ANNOTATION_SHOW_KEY")
val LOCATION_PERMISSION_REQUEST = 349
val TURN_ON_LOCATION_USER_REQUEST = 347
val OPEN_IMAGE_REQUEST_CODE: Int = 42
val READ_PHONE_STORAGE_RETURN_CODE: Int = 345
val READ_PHONE_STORAGE_RETURN_CODE_STARTUP: Int = 343

val IMAGE_VIEW_STATE_KEY = "imageViewStateKey"
val CURRENT_FRAGMENT_KEY = "currentFragmentKey"


public fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
    var drawable = ContextCompat.getDrawable(context, drawableId)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        drawable = DrawableCompat.wrap(drawable!!).mutate()
    }

    val bitmap = Bitmap.createBitmap(
        drawable!!.intrinsicWidth,
        drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return bitmap
}


public fun isAClick(startX: Float, endX: Float, startY: Float, endY: Float, startTime: Long, endTime: Long, context: Context): Boolean {

    val MAX_CLICK_DURATION = ViewConfiguration.getTapTimeout()

    if(endTime-startTime > MAX_CLICK_DURATION){
        return false
    }

    val CLICK_ACTION_THRESHOLD = ViewConfiguration.get(context).getScaledTouchSlop()
    val differenceX = Math.abs(startX - endX)
    val differenceY = Math.abs(startY - endY)
    return !(differenceX > CLICK_ACTION_THRESHOLD || differenceY > CLICK_ACTION_THRESHOLD)
}


fun isExternalStorageWritable(): Boolean {
    return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
}






fun getFileName(uri: Uri, context:Context): String {
    var result: String? = null
    if (uri.scheme == "content") {

        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor!!.moveToFirst()) {
                result = cursor!!.getString(cursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        } finally {
            cursor!!.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result!!.lastIndexOf('/')
        if (cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

fun getFirstImageTile(projectDirectory: Uri, context:Context): Uri{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    for (file in documentFile!!.listFiles()) {
        if(file.type.equals("image/png") || file.type.equals("image/jpg")){
            return file.uri
        }
    }
    throw Exception("Did not find an Image inside the selected Project Directory.")
}

fun getAllAnnotationFileUris(projectDirectory: Uri, context:Context): ArrayList<Uri>{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    var allFiles = arrayListOf<Uri>()
    for (file in documentFile!!.listFiles()) {
        if(file.name!!.endsWith("_annotations.json")){
            allFiles.add(file.uri)
        }
    }
    return allFiles
}


fun getAnnotationFileUri(projectDirectory: Uri, context:Context): Uri{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    var imageName = "annotations.json"
    for (file in documentFile!!.listFiles()) {
        if(file.name!!.contains("annotations.json")){
            return file.uri
        }
    }

    for (file in documentFile!!.listFiles()) {
        if(file.type.equals("image/png") || file.type.equals("image/jpg")|| file.type.equals("image/tif")) {
            imageName = file.name!!
            return makeFile(projectDirectory,imageName.substring(0,imageName.length-4) + "_annotations.json",context)
        }
    }

    return makeFile(projectDirectory,imageName ,context)
}










fun findFile(projectDirectory: Uri,context: Context,fileName:String):Uri?{
    println(fileName)
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    for (subdir in documentFile!!.listFiles()) {
        println(subdir.name)
        if (subdir.isDirectory) {
            println(subdir.listFiles().size)
            var file = subdir.findFile(fileName)
            if (file != null){
                return file.uri
            }
        }
    }
    return null
}


fun isValidProjectDirectory(projectDirectory: Uri,context: Context):Boolean{
    return DocumentsContract.isDocumentUri(context,projectDirectory) || DocumentsContract.isTreeUri(projectDirectory)
}



fun fillTileUriMatrix(matrix:Array<Array<Array<Uri?>>>,projectDirectory: Uri, context: Context){
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    var levelRegex: Regex = "_level([0-9]|[0-9][0-9]|[0-9][0-9][0-9]|[0-9][0-9][0-9][0-9])_x".toRegex()
    var xRegex: Regex = "_x([0-9]|[0-9][0-9]|[0-9][0-9][0-9]|[0-9][0-9][0-9][0-9])_y".toRegex()
    var yRegex: Regex = "_y([0-9]|[0-9][0-9]|[0-9][0-9][0-9]|[0-9][0-9][0-9][0-9])\\.".toRegex()


    for (file in documentFile!!.listFiles()) {
        if (file.isDirectory){
            for (tile in file.listFiles()){
                val level:Int = levelRegex.find(file.name!!)!!.value.substringAfterLast("_level").substringBeforeLast("_x").toInt()
                val x:Int = xRegex.find(file.name!!)!!.value.substringAfterLast("_x").substringBeforeLast("_y").toInt()
                val y:Int = yRegex.find(file.name!!)!!.value.substringAfterLast("_y").substringBeforeLast(".").toInt()
                matrix[level][x][y] = file.uri
            }
        }
    }
}

fun hasMetadata(projectDirectory: Uri,context: Context):Boolean{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    for (file in documentFile!!.listFiles()) {
        if (file.name.equals("metadata.json")){
            return true
        }
    }
    return false
}

fun getMetadata(projectDirectory: Uri,context: Context):Metadata{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    for (file in documentFile!!.listFiles()) {
        if (file.name.equals("metadata.json")){
            val gson = Gson()
            val reader = JsonReader(InputStreamReader(context.contentResolver.openInputStream(file.uri)))
            val myType = object : TypeToken<Metadata>() {}.type
            var metadata = gson.fromJson<Metadata>(reader, myType)
            return metadata
        }
    }
    throw FileNotFoundException("There seems to be no metadata file present.")
}


















fun getGeoInfoUri(projectDirectory: Uri, imageUri:Uri, context:Context): Uri?{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    val imageFileName = getFileName(imageUri,context)
    val geoInfoFileName = imageFileName.dropLast(4).plus("_geoinfo.json")
    for (file in documentFile!!.listFiles()) {
        if(file.name.equals(geoInfoFileName)){
            return file.uri
        }
    }
    return null
}

fun getUri(projectDirectory: Uri, fileName:String, context:Context): Uri?{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    for (file in documentFile!!.listFiles()) {
        if(file.name.equals(fileName)){
            return file.uri
        }
    }
    return null
}


fun doesFileExist(projectDirectory: Uri,fileName: String, context: Context): Boolean{
    val documentFile = DocumentFile.fromTreeUri(context, projectDirectory)
    for (file in documentFile!!.listFiles()) {
        if(file.name.equals(fileName)){
            return true
        }
    }
    return false
}


fun makeFile(projectDirectory: Uri, fileName: String, context: Context): Uri{
    val contentResolver = context.getContentResolver()
    val docUri = DocumentsContract.buildDocumentUriUsingTree(projectDirectory, DocumentsContract.getTreeDocumentId(projectDirectory))
    var mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfter(".",fileName))
    val newFileUri = DocumentsContract.createDocument(contentResolver, docUri, mimetype, fileName)
    return newFileUri
}

fun makeFile(uri: Uri, context:Context){
    val directory = File(context.getFilesDir().absolutePath + separator + "MyFolder")

    if (!directory.exists())
        directory.mkdir()

    val newFile = File(directory, "projectFolder")

    if (!newFile.exists()) {
        try {
            newFile.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }
    try {
        val fIn = FileInputStream(context.getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor()).channel

        val fOut = FileOutputStream(newFile).channel
        fIn.transferTo(0, fIn.size(), fOut);

        fIn.close()
        fOut.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    var allFiles = context.filesDir.listFiles()

}




fun importProject(uri: Uri, context: Context){
    val documentFile = DocumentFile.fromTreeUri(context, uri)
    var textInfo: String = ""
    var filelist = documentFile!!.listFiles()
    for (file in documentFile!!.listFiles()) {
        textInfo += (file.name!! + "\n")

        if (file.isDirectory) {
            textInfo +=("is a Directory\n")
        } else {
            textInfo += (file.type!! + "\n")
        }

        textInfo += ("file.canRead(): " + file.canRead() + "\n")
        textInfo += ("file.canWrite(): " + file.canWrite() + "\n")

        textInfo += (file.uri.toString() + "\n")
        textInfo += ("---------------------\n")
    }
    println(textInfo)

}








fun uriToPath(uri: Uri, context:Context): String{

    val uristring = uri.toString()
    var newuri = Uri.parse(uristring)

    val documentFile = DocumentFile.fromTreeUri(context, newuri)
    var textInfo: String = ""
    var filelist = documentFile!!.listFiles()
    for (file in documentFile!!.listFiles()) {
        textInfo += (file.name!! + "\n")

        if (file.isDirectory) {
            textInfo +=("is a Directory\n")
        } else {
            textInfo += (file.type!! + "\n")
        }

        textInfo += ("file.canRead(): " + file.canRead() + "\n")
        textInfo += ("file.canWrite(): " + file.canWrite() + "\n")

        textInfo += (file.uri.toString() + "\n")
        textInfo += ("---------------------\n")
    }
    println(textInfo)





    val contentResolver = context.getContentResolver()
    val docUri = DocumentsContract.buildDocumentUriUsingTree(
        uri,
        DocumentsContract.getTreeDocumentId(uri)
    )
    val directoryUri = DocumentsContract
        .createDocument(contentResolver, docUri, DocumentsContract.Document.MIME_TYPE_DIR, "aaanewdir")

    var mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension("csv")


    return uri.path.substringAfter(":")
}

fun getValueFromPreferences(id: Pair<Float,String>, context: Context): Float{
    val prefs = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    val restoredValue = prefs.getFloat(id.second,id.first)
    return restoredValue
}
fun setValueToPreferences(id: Pair<Float,String>, value: Float, context: Context){
    val editor = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).edit()
    editor.putFloat(id.second,value)
    editor.apply()
}
fun putFlowerListToPreferences(items:MutableList<String>, context: Context){
    val editor = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE).edit()
    editor.putStringSet(USER_FLOWER_LIST.second,items.toMutableSet())
    editor.commit()
}
fun getFlowerListFromPreferences(context:Context):MutableList<String>{
    val prefs = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
    val restoredValue = prefs.getStringSet(USER_FLOWER_LIST.second, USER_FLOWER_LIST.first)
    var items = restoredValue.toMutableList()
    items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it })).toMutableList()
    return items
}

fun sortList(list:MutableList<String>):MutableList<String>{
    var new_list = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it })).toMutableList()
    return new_list
}



