package com.halo.app.ui.workspace

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.screens.notebook.inkBounds
import com.halo.host.android.ink.AndroidInkInput
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.math.*

/** Input is portable Ink; every displayed stroke is composed by the canonical renderer at its real stack position. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StudioPaintSurface(service:StudioService,session:StudioInkSession,tool:String,onSaveCopy:()->Unit,
    modifier:Modifier=Modifier,busy:Boolean=false) {
    val project=session.initial
    val document=project.session.current
    val layer=session.initialLayer
    val initial=requireNotNull(InkCodec.decode(layer.ink))
    val editor=remember(session) {InkEditor(initial)}
    var tick by remember(session) {mutableIntStateOf(0)}
    val ink=remember(editor,tick) {editor.preview()}
    val encoded=remember(ink) {runCatching {InkCodec.encode(ink)}}
    val draft=remember(session,encoded) {if(session.fitsPreview(ink)) encoded.getOrNull()?.let {session.preview(it)} else null}
    var lastDocument by remember(session) {mutableStateOf(document)}
    SideEffect {draft?.let {lastDocument=it}}
    val render=rememberStudioPixels(service,draft ?: lastDocument)
    // A previous live preview is explicitly marked while the next canonical composition is being rendered.
    // A failed source/render clears it; no colour sampling is available from stale paint previews.
    var retained by remember(session) {mutableStateOf<Bitmap?>(null)}
    SideEffect {if(render.failed) retained=null else render.bitmap?.let {retained=it}}
    val display=if(render.failed) null else render.bitmap ?: retained
    val failed by session.failed.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var selectionJob by remember(session) {mutableStateOf<Job?>(null)}
    var selectionStart by remember(session) {mutableStateOf<Pair<Float,Float>?>(null)}
    var selectionEnd by remember(session) {mutableStateOf<Pair<Float,Float>?>(null)}
    var selection by remember(session) {mutableStateOf<InkSelectionBounds?>(null)}
    var board by remember {mutableStateOf(IntSize.Zero)}
    var zoom by remember(session) {mutableFloatStateOf(1f)}
    var rotation by remember(session) {mutableFloatStateOf(0f)}
    var pan by remember(session) {mutableStateOf(Offset.Zero)}
    var multi by remember {mutableStateOf(false)}
    var pinchDistance by remember {mutableFloatStateOf(0f)}
    var pinchAngle by remember {mutableFloatStateOf(0f)}
    var pinchCenter by remember {mutableStateOf(Offset.Zero)}
    var lastPan by remember {mutableStateOf(Offset.Zero)}
    val currentTool by rememberUpdatedState(tool)
    val disabled by rememberUpdatedState(busy||failed||render.failed)
    val density=LocalDensity.current
    val disallow=remember {RequestDisallowInterceptTouchEvent()}
    val output=document.recipe.outputDimensions(document.width,document.height)
    fun frame()=inkBounds(board.width.toFloat(),board.height.toFloat(),(output.first*1000f/output.second).roundToInt())
    fun inkPoint(x:Float,y:Float):Pair<Float,Float>? {
        val f=frame();if(f.width<=0||f.height<=0) return null
        val v=(Offset(x,y)-f.center-pan)/zoom;val a=Math.toRadians(-rotation.toDouble())
        val p=Offset((v.x*cos(a)-v.y*sin(a)).toFloat(),(v.x*sin(a)+v.y*cos(a)).toFloat())+f.center
        if(!f.contains(p)) return null
        val source=StudioCanvasGeometry.inverse(document,(p.x-f.left)/f.width,(p.y-f.top)/f.height)
        return StudioInkCoordinates.toInk(document,layer,source.first,source.second)
    }
    fun clearSelection() {
        selection=null;selectionStart=null;selectionEnd=null
        selectionJob?.cancel();service.clearSelection(project.id,session.ownerToken)
    }
    val input=remember(session) {AndroidInkInput(editor,position={x,y->inkPoint(x,y)},
        eraserRadius={
            val f=StudioInkCoordinates.frame(document,layer)
            val full=document.recipe.copy(maxEdge=4096).outputDimensions(document.width,document.height)
            val scale=frame().width/full.first.coerceAtLeast(1)*zoom
            val radius=with(density) {18.dp.toPx()}
            (radius*1000/(f.width*document.width/1000*scale).coerceAtLeast(1f)) to
                (radius*1000/(f.height*document.height/1000*scale).coerceAtLeast(1f))
        },eraseSelected={currentTool=="ERASE"},onPreview={clearSelection();tick++},
        onCommit={session.submit(InkCodec.encode(editor.document));tick++})}
    LaunchedEffect(tool,busy) {input.abort();clearSelection();tick++}
    DisposableEffect(session) {onDispose {input.abort();selectionJob?.cancel()}}
    fun finishSelection() {
        val start=selectionStart;val end=selectionEnd
        selectionStart=null;selectionEnd=null
        if(start==null||end==null) {clearSelection();return}
        val left=minOf(start.first,end.first).toInt().coerceIn(0,999)
        val top=minOf(start.second,end.second).toInt().coerceIn(0,999)
        val right=ceil(maxOf(start.first,end.first)).toInt().coerceIn(1,1000)
        val bottom=ceil(maxOf(start.second,end.second)).toInt().coerceIn(1,1000)
        if(right<=left||bottom<=top) {clearSelection();return}
        val bounds=InkSelectionBounds(left,top,right,bottom);selection=bounds
        val value=editor.document
        val digest=MessageDigest.getInstance("SHA-256").digest((project.revision.toString()+"\n"+InkCodec.encode(value)).toByteArray(Charsets.UTF_8))
            .joinToString("") {"%02x".format(it)}
        val context=InkSelections.create(value,project.id,layer.id,digest,bounds,InkSelectionOrigin.BOX,System.currentTimeMillis())
        selectionJob?.cancel()
        if(context!=null) selectionJob=scope.launch {
            if(!session.awaitPersisted()||!service.publishSelection(project.id,layer.id,project.revision,session.ownerToken,context,session.persistedRevision))
                service.clearSelection(project.id,session.ownerToken)
        }
    }
    val checker=MaterialTheme.colorScheme.surfaceContainerHigh
    val accent=MaterialTheme.colorScheme.primary
    val status=uiText(if(render.failed) "Preview unavailable. Your project is kept." else if(draft==null) "The paint layer is full. Keep this drawing before adding more marks." else if(render.bitmap==null) "Updating preview…" else "Ready")
    Column(modifier.fillMaxSize()) {
        if(failed) {
            Text(uiText("Paint could not be kept. Save a recovery copy before continuing."),color=MaterialTheme.colorScheme.error)
            TextButton(onSaveCopy,enabled=!busy) {Text(uiText("Save recovery copy"))}
        }
        if(render.failed) Text(status,color=MaterialTheme.colorScheme.error)
        if(encoded.isFailure||draft==null||editor.limitReached) Text(uiText("The paint layer is full. Keep this drawing before adding more marks."),color=MaterialTheme.colorScheme.error)
        Box(Modifier.fillMaxWidth().height(4.dp)) {if(render.bitmap==null&&!render.failed) LinearProgressIndicator(Modifier.fillMaxWidth())}
        Canvas(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("studio-paint-artboard").semantics {
            contentDescription=layer.name;stateDescription=status
        }.onSizeChanged {input.abort();board=it}.pointerInteropFilter(disallow) {event->
            if(disabled) {input.abort();false} else {
                if(event.actionMasked==MotionEvent.ACTION_DOWN) {multi=false;pinchDistance=0f;lastPan=Offset(event.x,event.y);disallow(true)}
                if(event.pointerCount>=2) {
                    input.abort();multi=true
                    val center=Offset((event.getX(0)+event.getX(1))/2,(event.getY(0)+event.getY(1))/2)
                    val dx=event.getX(1)-event.getX(0);val dy=event.getY(1)-event.getY(0)
                    val distance=hypot(dx,dy);val angle=Math.toDegrees(atan2(dy,dx).toDouble()).toFloat()
                    if(pinchDistance>0&&event.actionMasked==MotionEvent.ACTION_MOVE) {
                        zoom=(zoom*distance/pinchDistance).coerceIn(.5f,6f)
                        rotation=(rotation+angle-pinchAngle)%360;pan+=center-pinchCenter
                    }
                    pinchDistance=distance;pinchAngle=angle;pinchCenter=center
                    true
                } else if(multi) {
                    if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL) {multi=false;disallow(false)}
                    true
                } else if(currentTool=="PAN") {
                    if(event.actionMasked==MotionEvent.ACTION_MOVE) {val next=Offset(event.x,event.y);pan+=next-lastPan;lastPan=next}
                    if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL) disallow(false)
                    true
                } else if(currentTool=="SELECT") {
                    when(event.actionMasked) {
                        MotionEvent.ACTION_DOWN->{selectionStart=inkPoint(event.x,event.y);selectionEnd=selectionStart}
                        MotionEvent.ACTION_MOVE->selectionEnd=inkPoint(event.x,event.y)
                        MotionEvent.ACTION_UP->{selectionEnd=inkPoint(event.x,event.y);finishSelection();disallow(false)}
                        MotionEvent.ACTION_CANCEL->{clearSelection();disallow(false)}
                    }
                    true
                } else {
                    if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL) disallow(false)
                    input.motion(event)
                }
            }
        }) {
            val f=frame()
            withTransform({translate(pan.x,pan.y);rotate(rotation,f.center);scale(zoom,zoom,f.center)}) {
                clipRect(f.left,f.top,f.right,f.bottom) {
                    val tile=14.dp.toPx()
                    for(y in 0..(f.height/tile).toInt()) for(x in 0..(f.width/tile).toInt())
                        drawRect(if((x+y)%2==0) checker else Color.White,Offset(f.left+x*tile,f.top+y*tile),androidx.compose.ui.geometry.Size(tile,tile))
                    display?.let {drawImage(it.asImageBitmap(),dstOffset=IntOffset(f.left.roundToInt(),f.top.roundToInt()),
                        dstSize=IntSize(f.width.roundToInt().coerceAtLeast(1),f.height.roundToInt().coerceAtLeast(1)))}
                    val box=selection ?: run {
                        val a=selectionStart;val b=selectionEnd
                        if(a==null||b==null) null else {
                            val l=minOf(a.first,b.first).toInt().coerceIn(0,999);val t=minOf(a.second,b.second).toInt().coerceIn(0,999)
                            val r=ceil(maxOf(a.first,b.first)).toInt().coerceIn(1,1000);val bottom=ceil(maxOf(a.second,b.second)).toInt().coerceIn(1,1000)
                            if(r<=l||bottom<=t) null else InkSelectionBounds(l,t,r,bottom)
                        }
                    }
                    box?.let {
                        fun point(x:Float,y:Float):Offset {
                            val p=StudioInkCoordinates.toProject(document,layer,x,y)
                            val q=StudioCanvasGeometry.forward(document,p.first,p.second)
                            return Offset(f.left+q.first*f.width,f.top+q.second*f.height)
                        }
                        val points=listOf(point(it.left.toFloat(),it.top.toFloat()),point(it.right.toFloat(),it.top.toFloat()),
                            point(it.right.toFloat(),it.bottom.toFloat()),point(it.left.toFloat(),it.bottom.toFloat()))
                        drawPath(Path().apply {moveTo(points[0].x,points[0].y);points.drop(1).forEach {p->lineTo(p.x,p.y)};close()},accent,style=Stroke(2.dp.toPx()/zoom))
                    }
                }
            }
        }
    }
}
