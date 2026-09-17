package com.freevrsbs.input
enum class VRAction { RECENTER, MENU, BACK, HOME, RECENTS, CLICK, SCROLL, DIRECTION }
data class InputMapping(val keyCode:Int,val action:VRAction)
class InputMapper(private var mappings:List<InputMapping> = listOf(InputMapping(111,VRAction.MENU))) { fun actionFor(key:Int)=mappings.firstOrNull{it.keyCode==key}?.action; fun replace(v:List<InputMapping>){mappings=v} }
