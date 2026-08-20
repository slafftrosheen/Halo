package com.halo.runtime.observer.replay

import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.ObservationPayload
import com.halo.sdk.runtime.RuntimeLifecycleEvent
import com.halo.sdk.runtime.EngineLifecycleEvent
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.host.ConnectionState

class PolymorphicInterfaceAdapter<T : Any>(private val baseType: Class<T>) : JsonAdapter<T>() {
    private var moshi: Moshi? = null

    override fun toJson(writer: JsonWriter, value: T?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        val clazz = value.javaClass
        writer.beginObject()
        writer.name("@class").value(clazz.name)
        writer.name("data")
        @Suppress("UNCHECKED_CAST")
        val adapter = moshi!!.adapter(clazz) as JsonAdapter<T>
        adapter.toJson(writer, value)
        writer.endObject()
    }

    override fun fromJson(reader: JsonReader): T? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        
        var className: String? = null
        var data: T? = null
        
        @Suppress("UNCHECKED_CAST")
        val map = moshi!!.adapter(Map::class.java).fromJson(reader) as Map<String, Any>?
            ?: return null
            
        className = map["@class"] as String?
        if (className != null) {
            val clazz = Class.forName(className)
            val adapter = moshi!!.adapter(clazz)
            val dataMap = map["data"] as? Map<*, *>
            val innerJson = moshi!!.adapter(Map::class.java).toJson(dataMap)
            @Suppress("UNCHECKED_CAST")
            data = adapter.fromJson(innerJson) as T?
        }
        return data
    }

    fun setMoshi(moshi: Moshi) {
        this.moshi = moshi
    }
}

class PolymorphicAdapterFactory : JsonAdapter.Factory {
    override fun create(type: java.lang.reflect.Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        val rawType = Types.getRawType(type)
        if (rawType == HaloEvent::class.java || 
            rawType == RuntimeLifecycleEvent::class.java || 
            rawType == EngineLifecycleEvent::class.java ||
            rawType == ObservationPayload::class.java ||
            rawType == ObservationSource::class.java ||
            rawType == ConnectionState::class.java) {
            val adapter = PolymorphicInterfaceAdapter(rawType as Class<Any>)
            adapter.setMoshi(moshi)
            return adapter
        }
        return null
    }
}

class RuntimeJsonSerializer {
    val moshi: Moshi = Moshi.Builder()
        .add(PolymorphicAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()
        
    private val eventAdapter = moshi.adapter(HaloEvent::class.java)

    fun serialize(event: HaloEvent): String {
        return eventAdapter.toJson(event)
    }

    fun deserialize(json: String): HaloEvent? {
        return eventAdapter.fromJson(json)
    }
}
