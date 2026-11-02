package com.halo.app.everyday.presentation

/** Shared typed presentation patch for Screen and Android instances; never changes a source or fetches. */
object EverydayCardSettings {
    fun patch(base: EverydayCardConfig,values: Map<String,Any?>): EverydayCardConfig {
        val fields=base.fields().toMutableMap()
        require(values.keys.all {it in setOf("kind","zoneId","cityLabel","sourceId","headlineSourceId","recordId","priority","limit","theme","sections",
            "followPhone","clearSource","clearHeadlineSource","clearRecord","clearPriority")})
        values.forEach { (key,value) -> when(key) {
            "followPhone" -> if(value==true) {require("zoneId" !in values);fields.remove("zoneId");fields["cityLabel"]=""}
            "clearSource" -> if(value==true) {require("sourceId" !in values);fields.remove("sourceId")}
            "clearHeadlineSource" -> if(value==true) {require("headlineSourceId" !in values);fields.remove("headlineSourceId")}
            "clearRecord" -> if(value==true) {require("recordId" !in values);fields.remove("recordId")}
            "clearPriority" -> if(value==true) {require("priority" !in values);fields.remove("priority")}
            "sections" -> fields[key]=(value as List<*>).map {it as String}.joinToString(",")
            "limit" -> fields[key]=(value as Number).toInt().toString()
            else -> fields[key]=value as String
        } }
        if(values["followPhone"]==true) {
            require("zoneId" !in values && (values["cityLabel"]==null || values["cityLabel"]==""))
            fields.remove("zoneId");fields["cityLabel"]=""
        }
        return EverydayCardConfig.from(fields,base.kind).also {require(it.kind==base.kind)}
    }
}
