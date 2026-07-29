package org.lsposed.lspd.impl

import android.content.SharedPreferences
import android.os.Bundle
import android.util.ArraySet
import org.lsposed.lspd.service.ILSPInjectedModuleService
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class LSPosedRemotePreferences(service: ILSPInjectedModuleService, group: String?) :
    SharedPreferences {
    private val mMap: MutableMap<String?, Any?> = ConcurrentHashMap<String, Any>()

    val mListeners: HashSet<SharedPreferences.OnSharedPreferenceChangeListener?> =
        HashSet<SharedPreferences.OnSharedPreferenceChangeListener?>()

    var callback: IRemotePreferenceCallback = object : Stub() {
        @Synchronized
        public override fun onUpdate(bundle: Bundle) {
            val changes: MutableSet<String?> = ArraySet<String?>()
            if (bundle.containsKey("delete")) {
                val deletes = bundle.getSerializable("delete") as MutableSet<String?>?
                changes.addAll(deletes!!)
                for (key in deletes) {
                    mMap.remove(key)
                }
            }
            if (bundle.containsKey("put")) {
                val puts = bundle.getSerializable("put") as MutableMap<String?, Any?>?
                mMap.putAll(puts!!)
                changes.addAll(puts.keys)
            }
            synchronized(mListeners) {
                for (key in changes) {
                    mListeners.forEach(Consumer { listener: OnSharedPreferenceChangeListener? ->
                        listener.onSharedPreferenceChanged(
                            this@LSPosedRemotePreferences,
                            key
                        )
                    })
                }
            }
        }
    }

    init {
        val output: Bundle = service.requestRemotePreferences(group, callback)
        if (output.containsKey("map")) {
            mMap.putAll((output.getSerializable("map") as kotlin.collections.MutableMap<kotlin.String?, kotlin.Any?>?)!!)
        }
    }

    val all: MutableMap<String?, *>
        get() = TreeMap<String?, Any?>(mMap)

    override fun getString(key: String?, defValue: String?): String? {
        val v = mMap.getOrDefault(key, defValue) as String?
        if (v != null) return v
        return defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String?>?): MutableSet<String?>? {
        val v = mMap.getOrDefault(key, defValues) as MutableSet<String?>?
        if (v != null) return v
        return defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val v = mMap.getOrDefault(key, defValue) as Int?
        if (v != null) return v
        return defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        val v = mMap.getOrDefault(key, defValue) as Long?
        if (v != null) return v
        return defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        val v = mMap.getOrDefault(key, defValue) as Float?
        if (v != null) return v
        return defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val v = mMap.getOrDefault(key, defValue) as Boolean?
        if (v != null) return v
        return defValue
    }

    override fun contains(key: String?): Boolean {
        return mMap.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor? {
        throw UnsupportedOperationException("Read only implementation")
    }

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        synchronized(mListeners) {
            mListeners.add(listener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        synchronized(mListeners) {
            mListeners.remove(listener)
        }
    }
}
