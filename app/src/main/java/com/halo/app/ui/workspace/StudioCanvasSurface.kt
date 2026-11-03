package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.screens.notebook.inkBounds
import kotlinx.coroutines.CancellationException
import kotlin.math.*

/** Gesture previews are ephemeral; one exact-revision command is submitted at contact end. */
@Composable
internal fun StudioCanvasSurface(service:StudioService, project:StudioProject, busy:Boolean,
    onSelect:(String?)->Unit,onEdit:(StudioLayer)->Unit,onInspect:(StudioLayer)->Unit,
    modifier:Modifier=Modifier,fitToken:Int=0,onPick:((Int)->Unit)?=null,onPointPick:((Int,Int)->Unit)?=null) {
    val document=project.session.current
    val selected=document.layers.find { it.id==document.selectedLayerId }
    val pixels=document.copy(selectedLayerId=null)
    var failed by remember(pixels) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null,pixels) {
        try { value=service.previewDocument(pixels) }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) { failed=true }
    }
    var zoom by remember(project.id,fitToken) { mutableFloatStateOf(1f) }
    var rotation by remember(project.id,fitToken) { mutableFloatStateOf(0f) }
    var pan by remember(project.id,fitToken) { mutableStateOf(Offset.Zero) }
    var draft by remember(project.revision) { mutableStateOf<StudioLayer?>(null) }
    val latestSelect by rememberUpdatedState(onSelect)
    val latestEdit by rememberUpdatedState(onEdit)
    val latestInspect by rememberUpdatedState(onInspect)
    val latestPicker by rememberUpdatedState(onPick)
    val latestPointPicker by rememberUpdatedState(onPointPick)
    val haptic=LocalHapticFeedback.current
    val checker=MaterialTheme.colorScheme.surfaceContainerHigh
    val accent=MaterialTheme.colorScheme.primary
    val fitLabel=uiText("Fit artboard")
    val selectionLabel=selected?.name ?: uiText("Artboard")
    val renderStatus=uiText(if(failed) "Preview unavailable. Your project is kept." else if(bitmap==null) "Loading…" else "Ready")
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().testTag("studio-artboard").semantics {
            contentDescription=selectionLabel
            stateDescription=renderStatus
            customActions=listOf(CustomAccessibilityAction(fitLabel) { zoom=1f;rotation=0f;pan=Offset.Zero;true })+
                (selected?.let { layer->listOf(CustomAccessibilityAction(layer.name) { latestInspect(layer);true }) } ?: emptyList())
        }.pointerInput(project.id,project.revision,busy,fitToken,onPick!=null,onPointPick!=null) {
            val output=document.recipe.outputDimensions(document.width,document.height)
            fun frame()=inkBounds(size.width.toFloat(),size.height.toFloat(),(output.first*1000f/output.second).roundToInt())
            fun unview(point:Offset):Offset {
                val center=frame().center;val p=(point-center-pan)/zoom;val a=Math.toRadians(-rotation.toDouble())
                return Offset((p.x*cos(a)-p.y*sin(a)).toFloat(),(p.x*sin(a)+p.y*cos(a)).toFloat())+center
            }
            fun docPoint(point:Offset):Pair<Float,Float> {
                val p=unview(point);val f=frame()
                return StudioCanvasGeometry.inverse(document,(p.x-f.left)/f.width,(p.y-f.top)/f.height)
            }
            fun screenPoint(x:Float,y:Float):Offset {
                val p=StudioCanvasGeometry.forward(document,x,y);val f=frame();val v=Offset(f.left+p.first*f.width,f.top+p.second*f.height)-f.center
                val a=Math.toRadians(rotation.toDouble())
                return f.center+pan+Offset((v.x*cos(a)-v.y*sin(a)).toFloat(),(v.x*sin(a)+v.y*cos(a)).toFloat())*zoom
            }
            fun layerPoint(layer:StudioLayer,u:Float,v:Float):Offset {
                val b=layer.bounds;val radians=Math.toRadians(layer.rotation.toDouble())
                val x=(u-.5f)*b.width;val y=(v-.5f)*b.height
                return screenPoint((b.x+b.width/2f+x*cos(radians)-y*sin(radians)).toFloat(),(b.y+b.height/2f+x*sin(radians)+y*cos(radians)).toFloat())
            }
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                if(busy) return@awaitEachGesture
                val start=docPoint(down.position)
                val hit=StudioCanvasGeometry.hit(document,start.first,start.second)
                val corner=selected?.let { layerPoint(it,1f,1f) }
                val turn=selected?.let { layerPoint(it,.5f,0f)+(layerPoint(it,.5f,0f)-layerPoint(it,.5f,.5f)).let {v->v/v.getDistance().coerceAtLeast(1f)*36.dp.toPx()} }
                val resizing=corner!=null && (down.position-corner).getDistance()<24.dp.toPx()
                val rotating=turn!=null && (down.position-turn).getDistance()<24.dp.toPx()
                val target=if(resizing||rotating||selected?.kind==StudioLayerKind.GROUP&&hit!=null&&document.ancestors(hit).any {it.id==selected.id}) selected else hit
                var multi=false;var moved=false;var cancelled=false
                var last=down.position
                var candidate:StudioLayer?=null
                do {
                    val event=awaitPointerEvent()
                    val pressed=event.changes.count { it.pressed }
                    if(pressed>=2) {
                        multi=true;candidate=null;draft=null
                        zoom=(zoom*event.calculateZoom()).coerceIn(.5f,6f)
                        rotation=(rotation+event.calculateRotation())%360
                        pan+=event.calculatePan()
                    } else if(!multi) {
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        if(change.isConsumed) { cancelled=true;break }
                        val delta=change.position-down.position
                        if(delta.getDistance()>viewConfiguration.touchSlop) moved=true
                        if(moved && latestPicker==null && latestPointPicker==null) {
                            val point=docPoint(change.position)
                            if(target!=null && document.isEditable(target)) {
                                candidate=when {
                                    resizing->{val a=Math.toRadians(-target.rotation.toDouble());val dx=point.first-start.first;val dy=point.second-start.second
                                        target.copy(bounds=target.bounds.copy(width=(target.bounds.width+dx*cos(a)-dy*sin(a)).roundToInt().coerceIn(1,3000),height=(target.bounds.height+dx*sin(a)+dy*cos(a)).roundToInt().coerceIn(1,3000)))}
                                    rotating->{ val b=target.bounds
                                        val angle=atan2((point.second-b.y-b.height/2f).toDouble(),(point.first-b.x-b.width/2f).toDouble())-atan2((start.second-b.y-b.height/2f).toDouble(),(start.first-b.x-b.width/2f).toDouble())
                                        target.copy(rotation=((target.rotation+Math.toDegrees(angle).roundToInt()+540)%360)-180) }
                                    else->target.copy(bounds=StudioCanvasGeometry.snapped(document,target,target.bounds.x+(point.first-start.first).roundToInt(),target.bounds.y+(point.second-start.second).roundToInt()))
                                }
                                draft=candidate
                            } else if(target==null) pan+=change.position-last
                        }
                        last=change.position
                    }
                    event.changes.forEach { it.consume() }
                } while(event.changes.any { it.pressed })
                draft=null
                if(!cancelled && !multi) {
                    if(!moved) {
                        val pick=latestPicker;val image=bitmap
                        val pointPicker=latestPointPicker
                        if(pointPicker!=null && start.first in 0f..1000f&&start.second in 0f..1000f) pointPicker(start.first.roundToInt(),start.second.roundToInt())
                        else if(pick!=null && image!=null) {
                            val normalized=StudioCanvasGeometry.forward(document,start.first,start.second)
                            if(normalized.first in 0f..1f && normalized.second in 0f..1f)
                                pick(image.getPixel((normalized.first*image.width).toInt().coerceIn(0,image.width-1),(normalized.second*image.height).toInt().coerceIn(0,image.height-1)))
                        } else {if(hit?.id!=document.selectedLayerId) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);latestSelect(hit?.id)}
                    } else candidate?.let { if(it!=target) latestEdit(it) }
                }
            }
        }) {
            val image=bitmap
            val output=document.recipe.outputDimensions(document.width,document.height)
            val f=inkBounds(size.width,size.height,(output.first*1000f/output.second).roundToInt())
            withTransform({ translate(pan.x,pan.y);rotate(rotation,f.center);scale(zoom,zoom,f.center) }) {
                clipRect(f.left,f.top,f.right,f.bottom) {
                    val tile=14.dp.toPx()
                    for(y in 0..(f.height/tile).toInt()) for(x in 0..(f.width/tile).toInt())
                        drawRect(if((x+y)%2==0) checker else Color.White,Offset(f.left+x*tile,f.top+y*tile),Size(tile,tile))
                    image?.let { drawImage(it.asImageBitmap(),dstOffset=IntOffset(f.left.roundToInt(),f.top.roundToInt()),dstSize=IntSize(f.width.roundToInt().coerceAtLeast(1),f.height.roundToInt().coerceAtLeast(1))) }
                }
                val layer=draft ?: selected
                if(layer!=null && document.isVisible(layer)) {
                    val b=layer.bounds
                    fun point(u:Float,v:Float):Offset {
                        val a=Math.toRadians(layer.rotation.toDouble());val dx=(u-.5f)*b.width;val dy=(v-.5f)*b.height
                        val p=StudioCanvasGeometry.forward(document,(b.x+b.width/2f+dx*cos(a)-dy*sin(a)).toFloat(),(b.y+b.height/2f+dx*sin(a)+dy*cos(a)).toFloat())
                        return Offset(f.left+p.first*f.width,f.top+p.second*f.height)
                    }
                    val points=listOf(point(0f,0f),point(1f,0f),point(1f,1f),point(0f,1f))
                    val outline=Path().apply {moveTo(points[0].x,points[0].y);points.drop(1).forEach {lineTo(it.x,it.y)};close()}
                    drawPath(outline,accent,style=Stroke(2.dp.toPx()/zoom))
                    if(document.isEditable(layer)) {
                        val top=point(.5f,0f);val direction=top-point(.5f,.5f);val turn=top+direction/direction.getDistance().coerceAtLeast(1f)*36.dp.toPx()/zoom
                        drawCircle(accent,8.dp.toPx()/zoom,points[2])
                        drawLine(accent,top,turn,strokeWidth=2.dp.toPx()/zoom)
                        drawCircle(accent,8.dp.toPx()/zoom,turn)
                    }
                }
            }
        }
        if(bitmap==null) {
            if(failed) Text(uiText("Preview unavailable. Your project is kept."),Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
            else CircularProgressIndicator(Modifier.padding(16.dp))
        }
    }
}
