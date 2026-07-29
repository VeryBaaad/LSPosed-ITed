package org.lsposed.lspd.util

import android.os.Build
import android.os.SharedMemory
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.annotation.RequiresApi
import de.robv.android.xposed.XposedBridge
import hidden.ByteBufferDexClassLoader
import sun.misc.CompoundEnumeration
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Collections
import java.util.Enumeration
import java.util.Objects
import java.util.function.Predicate
import java.util.jar.JarFile
import java.util.zip.ZipEntry

class LspModuleClassLoader : ByteBufferDexClassLoader {
    private val apk: String?
    private val nativeLibraryDirs: MutableList<File> = ArrayList<File>()

    private constructor(
        dexBuffers: Array<ByteBuffer?>?,
        parent: ClassLoader?,
        apk: String?
    ) : super(dexBuffers, parent) {
        this.apk = apk
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private constructor(
        dexBuffers: Array<ByteBuffer?>?,
        librarySearchPath: String?,
        parent: ClassLoader?,
        apk: String?
    ) : super(dexBuffers, librarySearchPath, parent) {
        initNativeLibraryDirs(librarySearchPath)
        this.apk = apk
    }

    private fun initNativeLibraryDirs(librarySearchPath: String?) {
        nativeLibraryDirs.addAll(splitPaths(librarySearchPath))
        nativeLibraryDirs.addAll(systemNativeLibraryDirs)
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String?, resolve: Boolean): Class<*>? {
        val cl = findLoadedClass(name)
        if (cl != null) {
            return cl
        }
        try {
            return Any::class.java.classLoader!!.loadClass(name)
        } catch (_: ClassNotFoundException) {
        }
        val fromSuper: ClassNotFoundException?
        try {
            return findClass(name)
        } catch (ex: ClassNotFoundException) {
            fromSuper = ex
        }
        try {
            return parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw fromSuper
        }
    }

    override fun findLibrary(libraryName: String): String? {
        val fileName = System.mapLibraryName(libraryName)
        for (file in nativeLibraryDirs) {
            val path = file.path
            if (path.contains(zipSeparator)) {
                val split: Array<String?> =
                    path.split(zipSeparator.toRegex(), limit = 2).toTypedArray()
                try {
                    JarFile(split[0]).use { jarFile ->
                        val entryName = split[1] + '/' + fileName
                        val entry = jarFile.getEntry(entryName)
                        if (entry != null && entry.method == ZipEntry.STORED) {
                            return split[0] + zipSeparator + entryName
                        }
                    }
                } catch (e: IOException) {
                    Log.e(XposedBridge.TAG, "Can not open " + split[0], e)
                }
            } else if (file.isDirectory) {
                val entryPath = File(file, fileName).path
                try {
                    val fd = Os.open(entryPath, OsConstants.O_RDONLY, 0)
                    Os.close(fd)
                    return entryPath
                } catch (_: ErrnoException) {
                }
            }
        }
        return null
    }

    override fun getLdLibraryPath(): String {
        val result = StringBuilder()
        for (directory in nativeLibraryDirs) {
            if (result.isNotEmpty()) {
                result.append(':')
            }
            result.append(directory)
        }
        return result.toString()
    }

    override fun findResource(name: String?): URL? {
        try {
            val urlHandler = ClassPathURLStreamHandler(apk!!)
            val url = urlHandler.getEntryUrlOrNull(name)
            if (url == null) {
                // noinspection FinalizeCalledExplicitly
                urlHandler.finalize()
            }
            return url
        } catch (e: IOException) {
            return null
        }
    }

    override fun findResources(name: String?): Enumeration<URL?> {
        val result = ArrayList<URL?>()
        val url = findResource(name)
        if (url != null) result.add(url)
        return Collections.enumeration<URL?>(result)
    }

    override fun getResource(name: String?): URL? {
        var resource = Any::class.java.classLoader!!.getResource(name)
        if (resource != null) return resource
        resource = findResource(name)
        if (resource != null) return resource
        val cl = getParent()
        return if (cl == null) null else cl.getResource(name)
    }

    @Throws(IOException::class)
    override fun getResources(name: String?): Enumeration<URL?> {
        val resources = arrayOf<Enumeration<*>?>(
            Any::class.java.classLoader!!.getResources(name),
            findResources(name),
            if (getParent() == null) null else parent.getResources(name)
        ) as Array<Enumeration<URL?>?>
        return CompoundEnumeration<URL?>(resources)
    }

    override fun toString(): String {
        if (apk == null) return "LspModuleClassLoader[instantiating]"
        return "LspModuleClassLoader[module=" + apk + ", " + super.toString() + "]"
    }

    companion object {
        private const val zipSeparator = "!/"
        private val systemNativeLibraryDirs: MutableList<File?> =
            splitPaths(System.getProperty("java.library.path"))

        private fun splitPaths(searchPath: String?): MutableList<File?> {
            val result = ArrayList<File?>()
            if (searchPath == null) return result
            for (path in searchPath.split(File.pathSeparator.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()) {
                result.add(File(path))
            }
            return result
        }

        fun loadApk(
            apk: String?,
            dexes: MutableList<SharedMemory?>,
            librarySearchPath: String?,
            parent: ClassLoader?
        ): ClassLoader {
            val dexBuffers = dexes.stream().parallel().map<ByteBuffer?> { dex: SharedMemory? ->
                try {
                    return@map dex!!.mapReadOnly()
                } catch (e: ErrnoException) {
                    Log.w(XposedBridge.TAG, "Can not map " + dex, e)
                    return@map null
                }
            }.filter { obj: Predicate<in T?>? -> Objects.nonNull(obj) }
                .toArray<ByteBuffer?> { _Dummy_.__Array__() }
            val cl: LspModuleClassLoader
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cl = LspModuleClassLoader(dexBuffers, librarySearchPath, parent, apk)
            } else {
                cl = LspModuleClassLoader(dexBuffers, parent, apk)
                cl.initNativeLibraryDirs(librarySearchPath)
            }
            Arrays.stream(dexBuffers).parallel()
                .forEach { buffer: ByteBuffer? -> SharedMemory.unmap(buffer!!) }
            dexes.stream().parallel().forEach { obj: SharedMemory? -> obj!!.close() }
            return cl
        }
    }
}
