/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */
package org.lsposed.lspd.util

import pxb.android.axml.AxmlReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarFile

class MetaDataReader private constructor(apk: File?) {
    private val metaData = HashMap<String?, Any?>()

    init {
        JarFile(apk).use { zip ->
            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { `is` ->
                val reader: AxmlReader = AxmlReader(getBytesFromInputStream(`is`))
                reader.accept(object : AxmlVisitor() {
                    public override fun child(ns: String?, name: String?): NodeVisitor? {
                        val child: NodeVisitor? = super.child(ns, name)
                        return ManifestTagVisitor(child)
                    }
                })
            }
        }
    }

    private inner class ManifestTagVisitor(child: NodeVisitor?) : NodeVisitor(child) {
        public override fun child(ns: String?, name: String?): NodeVisitor? {
            val child: NodeVisitor? = super.child(ns, name)
            if ("application" == name) {
                return ApplicationTagVisitor(child)
            }
            return child
        }

        private inner class ApplicationTagVisitor(child: NodeVisitor?) : NodeVisitor(child) {
            public override fun child(ns: String?, name: String?): NodeVisitor? {
                val child: NodeVisitor? = super.child(ns, name)
                if ("meta-data" == name) {
                    return MetaDataVisitor(child)
                }
                return child
            }
        }
    }

    private inner class MetaDataVisitor(child: NodeVisitor?) : NodeVisitor(child) {
        var name: String? = null
        var value: Any? = null

        public override fun attr(
            ns: String?,
            name: String?,
            resourceId: Int,
            type: Int,
            obj: Any?
        ) {
            if (type == 3 && "name" == name) {
                this.name = obj as String?
            }
            if ("value" == name) {
                value = obj
            }
            super.attr(ns, name, resourceId, type, obj)
        }

        public override fun end() {
            if (name != null && value != null) {
                metaData.put(name, value)
            }
            super.end()
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun getMetaData(apk: File?): MutableMap<String?, Any?> {
            return MetaDataReader(apk).metaData
        }

        @Throws(IOException::class)
        fun getBytesFromInputStream(inputStream: InputStream): ByteArray {
            ByteArrayOutputStream().use { bos ->
                val b = ByteArray(1024)
                var n: Int
                while ((inputStream.read(b).also { n = it }) != -1) {
                    bos.write(b, 0, n)
                }
                return bos.toByteArray()
            }
        }

        @JvmStatic
        fun extractIntPart(str: String): Int {
            var result = 0
            val length = str.length
            for (offset in 0..<length) {
                val c = str.get(offset)
                if (c in '0'..'9') result = result * 10 + (c.code - '0'.code)
                else break
            }
            return result
        }
    }
}
