package com.halo.app.nativeactions

import android.content.Context
import com.halo.app.everyday.presentation.*
import org.json.JSONObject

data class EverydayWidgetInstance(val id: Int, val version: Long, val config: EverydayCardConfig)

internal object EverydayWidgetGeneration {
    private val value=java.util.concurrent.atomic.AtomicLong()
    fun current()=value.get()
    fun invalidate(): Long = value.incrementAndGet()
}
internal class WidgetPublicationChanged : IllegalStateException()

internal object EverydayWidgetReview {
    fun encode(value: EverydayWidgetInstance): String = JSONObject().put("id",value.id).put("version",value.version)
        .put("config",JSONObject(value.config.fields())).toString()
    fun restore(raw: String): EverydayWidgetInstance {
        require(raw.length<=8192)
        val json=JSONObject(raw);val fields=json.getJSONObject("config")
        val id=json.getInt("id");val version=json.getLong("version");require(id>0 && version>=0)
        return EverydayWidgetInstance(id,version,EverydayCardConfig.from(fields.keys().asSequence().associateWith { fields.getString(it) },EverydayCardKind.CLOCK))
    }
}

/** Independent durable instances. Atomic files avoid SharedPreferences' memory-before-disk commit boundary. */
class EverydayWidgetStore internal constructor(context: Context,private val beforeFinish: ()->Unit) {
    constructor(context: Context):this(context,{})
    private val directory=java.io.File(context.applicationContext.filesDir,"everyday-widgets")
    private val file=android.util.AtomicFile(java.io.File(directory,"instances.json"))
    private val legacy=context.applicationContext.getSharedPreferences("halo_everyday_widgets_v1",Context.MODE_PRIVATE)
    private fun state(): JSONObject {
        if(file.baseFile.exists() || java.io.File(file.baseFile.path+".bak").exists()) return JSONObject(String(file.readFully(),Charsets.UTF_8))
        return JSONObject().apply { legacy.all.forEach { (key,value) ->
            if(key.startsWith("widget:") && key.removePrefix("widget:").toIntOrNull()?.let {it>0}==true && value is String)
                put(key,JSONObject(value))
        } }
    }
    private fun write(value: JSONObject) {
        check(directory.isDirectory || directory.mkdirs())
        val bytes=value.toString().toByteArray(Charsets.UTF_8)
        val stream=file.startWrite()
        try {
            stream.write(bytes);stream.fd.sync();beforeFinish();file.finishWrite(stream)
            check(file.readFully().contentEquals(bytes)) { "Widget settings were not durably replaced" }
        } catch(error: Throwable) {file.failWrite(stream);throw error}
    }
    fun get(id: Int): EverydayWidgetInstance? = synchronized(lock) {
        if(id<=0) return@synchronized null
        val json=state().optJSONObject("widget:$id") ?: return@synchronized null
        val fields=json.getJSONObject("config")
        EverydayWidgetInstance(id,json.getLong("version"),EverydayCardConfig.from(fields.keys().asSequence().associateWith {fields.getString(it)},EverydayCardKind.CLOCK))
    }
    fun save(id: Int,expectedVersion: Long?,config: EverydayCardConfig,operationId: String? = null,allowed: ()->Unit = {}): EverydayWidgetInstance = synchronized(lock) {
        require(operationId==null || operationId.matches(Regex("[a-f0-9-]{36}")))
        require(id>0 && config.hidePrivate && config.limit<=3 && config.sections.size<=3);allowed()
        val current=get(id)
        check(current?.version==expectedVersion) { "Widget configuration changed" }
        val next=EverydayWidgetInstance(id,(current?.version ?: 0)+1,config)
        val data=state();val completions=data.optJSONObject("widget:$id")?.optJSONArray("completions") ?: org.json.JSONArray()
        val retained=org.json.JSONArray()
        for(index in maxOf(0,completions.length()-7) until completions.length()) retained.put(completions.getString(index))
        operationId?.let {retained.put(it)}
        data.put("widget:$id",JSONObject().put("version",next.version).put("config",JSONObject(config.fields())).put("completions",retained))
        allowed();EverydayWidgetGeneration.invalidate();write(data);next
    }
    /** Receipt and settings share one verified atomic disk write. Never consult an uncommitted memory map. */
    internal fun completed(id: Int,operationId: String): Boolean = synchronized(lock) {
        state().optJSONObject("widget:$id")?.optJSONArray("completions")?.let {entries ->
            (0 until entries.length()).any {entries.getString(it)==operationId}
        } ?: false
    }
    fun remove(id: Int) = synchronized(lock) {
        val data=state();data.remove("widget:$id");EverydayWidgetGeneration.invalidate();write(data)
        legacy.edit().remove("widget:$id").apply()
    }
    internal fun publishIfCurrent(instance: EverydayWidgetInstance,generation: Long,publish: ()->Unit) = synchronized(lock) {
        if((get(instance.id)?.version ?: 0)!=instance.version || generation!=EverydayWidgetGeneration.current()) throw WidgetPublicationChanged()
        publish()
    }
    fun restored(oldId: Int,newId: Int) = synchronized(lock) {
        get(oldId)?.let {old -> if(get(newId)==null) save(newId,null,old.config)}
        if(oldId!=newId) remove(oldId)
    }
    companion object {private val lock=Any()}
}
