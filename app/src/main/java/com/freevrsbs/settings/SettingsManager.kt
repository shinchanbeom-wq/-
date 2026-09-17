package com.freevrsbs.settings
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
private val Context.dataStore by preferencesDataStore("vr_settings")
data class VrSettings(val ipd: Float=63f,val separation:Float=0f,val fov:Float=90f,val lens:Float=0f,val barrel:Float=0f,val curvature:Float=0f,val sensitivity:Float=1f,val cursor:Boolean=false,val performance:String="balanced")
class SettingsManager(private val context: Context) {
 private val IPD=floatPreferencesKey("ipd"); private val SEP=floatPreferencesKey("separation"); private val FOV=floatPreferencesKey("fov"); private val LENS=floatPreferencesKey("lens"); private val BARREL=floatPreferencesKey("barrel"); private val CURVE=floatPreferencesKey("curve"); private val SENS=floatPreferencesKey("sensitivity"); private val CURSOR=booleanPreferencesKey("cursor"); private val PERF=stringPreferencesKey("performance")
 val settings: Flow<VrSettings> = context.dataStore.data.map { p -> VrSettings(p[IPD]?:63f,p[SEP]?:0f,p[FOV]?:90f,p[LENS]?:0f,p[BARREL]?:0f,p[CURVE]?:0f,p[SENS]?:1f,p[CURSOR]?:false,p[PERF]?:"balanced") }
 suspend fun save(s:VrSettings) { context.dataStore.edit { p -> p[IPD]=s.ipd;p[SEP]=s.separation;p[FOV]=s.fov;p[LENS]=s.lens;p[BARREL]=s.barrel;p[CURVE]=s.curvature;p[SENS]=s.sensitivity;p[CURSOR]=s.cursor;p[PERF]=s.performance } }
}
