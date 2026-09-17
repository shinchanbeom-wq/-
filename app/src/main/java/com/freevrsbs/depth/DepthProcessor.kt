package com.freevrsbs.depth
import java.nio.ByteBuffer
data class DepthFrame(val width:Int,val height:Int,val values:ByteBuffer?)
interface DepthProcessor { fun processFrame(inputFrame: ByteBuffer, width:Int, height:Int): DepthFrame }
object DepthProcessorFactory { fun create(): DepthProcessor = object: DepthProcessor { override fun processFrame(inputFrame:ByteBuffer,width:Int,height:Int)=DepthFrame(width,height,null) } }
