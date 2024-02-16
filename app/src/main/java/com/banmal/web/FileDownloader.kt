package com.banmal.web

import android.os.AsyncTask
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FileDownloader(private val url: String, private val destination: String) :
    AsyncTask<Void, Void, Boolean>() {

    override fun doInBackground(vararg params: Void?): Boolean {
        return try {
            downloadFile()
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun downloadFile(): Boolean {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()

        // 파일 크기를 알고 있다면 setFixedLengthStreamingMode()을 사용할 수 있습니다.
        // connection.setFixedLengthStreamingMode(fileSize)

        val input = BufferedInputStream(connection.inputStream)
        val output = FileOutputStream(destination)

        val data = ByteArray(1024)
        var total: Long = 0
        var count: Int

        while (input.read(data).also { count = it } != -1) {
            total += count.toLong()
            output.write(data, 0, count)
        }

        output.flush()
        output.close()
        input.close()

        return true
    }
}