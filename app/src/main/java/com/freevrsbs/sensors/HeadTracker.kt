package com.freevrsbs.sensors
import android.content.Context
import android.hardware.*
import android.util.Log
class HeadTracker(context: Context, private val onUpdate:(FloatArray)->Unit): SensorEventListener {
 private val manager=context.getSystemService(SensorManager::class.java); private val sensor=manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR); private val base=FloatArray(3)
 fun start():Boolean { if(sensor==null)return false; manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME); return true }
 fun stop()=manager.unregisterListener(this); fun recenter(){ base.fill(0f) }
 override fun onSensorChanged(e:SensorEvent){ val r=FloatArray(9); val o=FloatArray(3); SensorManager.getRotationMatrixFromVector(r,e.values);SensorManager.getOrientation(r,o); if(base.all{it==0f}) o.copyInto(base); onUpdate(floatArrayOf(o[0]-base[0],o[1]-base[1],o[2]-base[2])) }
 override fun onAccuracyChanged(s:Sensor?,a:Int){} }
