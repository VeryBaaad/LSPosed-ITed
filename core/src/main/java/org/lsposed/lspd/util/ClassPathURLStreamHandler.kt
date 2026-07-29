package org.lsposed.lspd.util

import sun.net.www.ParseUtil
import sun.net.www.protocol.jar.Handler
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.JarURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.util.jar.JarFile
import java.util.zip.ZipEntry

internal class ClassPathURLStreamHandler(jarFileName: String) : Handler() {
    private val fileUri: String = File(jarFileName).toURI().toString()
    private val _jarFile: JarFile = JarFile(jarFileName)

    fun getEntryUrlOrNull(entryName: String?): URL? {
        if (_jarFile.getEntry(entryName) != null) {
            try {
                val encodedName = ParseUtil.encodePath(entryName, false)
                return URL("jar", null, -1, "$fileUri!/$encodedName", this)
            } catch (e: MalformedURLException) {
                throw RuntimeException("Invalid entry name", e)
            }
        }
        return null
    }

    @Throws(IOException::class)
    override fun openConnection(url: URL?): URLConnection {
        return ClassPathURLConnection(url)
    }

    @Throws(IOException::class)
    fun finalize() {
        _jarFile.close()
    }

    private inner class ClassPathURLConnection(url: URL?) : JarURLConnection(url) {
        private var connectionJarFile: JarFile? = null
        private var jarEntry: ZipEntry? = null
        private var jarInput: InputStream? = null
        private var closed = false

        init {
            setUseCaches(false)
        }

        override fun setUseCaches(usecaches: Boolean) {
            super.setUseCaches(false)
        }

        @Throws(IOException::class)
        override fun connect() {
            check(!closed) { "JarURLConnection has been closed" }
            if (!connected) {
                jarEntry = _jarFile.getEntry(entryName)
                if (jarEntry == null) {
                    throw FileNotFoundException("URL=" + url + ", zipfile=" + _jarFile.name)
                }
                connected = true
            }
        }

        @Throws(IOException::class)
        override fun getJarFile(): JarFile {
            connect()
            if (connectionJarFile != null) return connectionJarFile!!
            return JarFile(_jarFile.name).also { connectionJarFile = it }
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            connect()
            if (jarInput != null) return jarInput!!
            return object : FilterInputStream(_jarFile.getInputStream(jarEntry)) {
                @Throws(IOException::class)
                override fun close() {
                    super.close()
                    closed = true
                    _jarFile.close()
                    if (connectionJarFile != null) connectionJarFile!!.close()
                }
            }.also { jarInput = it }
        }

        override fun getContentType(): String {
            var cType = guessContentTypeFromName(entryName)
            if (cType == null) {
                cType = "content/unknown"
            }
            return cType
        }

        override fun getContentLength(): Int {
            try {
                connect()
                return getJarEntry().size.toInt()
            } catch (_: IOException) {
            }
            return -1
        }
    }
}
