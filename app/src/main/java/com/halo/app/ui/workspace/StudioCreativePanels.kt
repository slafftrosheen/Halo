@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.halo.app.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.zone5.*
import kotlin.math.roundToInt

@Composable
internal fun StudioModeSwitch(mode:StudioMode,onMode:(StudioMode)->Unit,enabled:Boolean=true) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        StudioMode.entries.forEach { next ->
            FilterChip(mode==next,{onMode(next)},enabled=enabled,label={Text(uiText(when(next) {
                StudioMode.DESIGN->"Design";StudioMode.PAINT->"Paint";StudioMode.AI->"AI"
            }))},leadingIcon={Zone5Icon(when(next) { StudioMode.DESIGN->"studio_shapes";StudioMode.PAINT->"studio_brush";StudioMode.AI->"cognition_think" },HaloGlyph.TOOL,Modifier.size(20.dp))},
                modifier=Modifier.heightIn(min=48.dp))
        }
    }
}

@Composable
internal fun StudioCreativeTools(project:StudioProject,mode:StudioMode,busy:Boolean,
    onMode:(StudioMode)->Unit,
    brush:StudioBrushPreset,color:Int,onBrush:(StudioBrushPreset)->Unit,onColor:(Int)->Unit,
    onDraw:()->Unit,onPick:()->Unit,onFill:()->Unit,onBucket:()->Unit,onEdit:(StudioLayer)->Unit,onEffects:(StudioLayer)->Unit,
    onLayers:()->Unit,onTransform:()->Unit,onFit:()->Unit,onCommand:(StudioCommand)->Unit,onDismiss:()->Unit) {
    val document=project.session.current
    val selected=document.layers.find { it.id==document.selectedLayerId }
    val editable=selected?.let(document::isEditable)==true && !busy
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(uiText("Tools"),style=MaterialTheme.typography.titleLarge)
        StudioModeSwitch(mode,onMode,!busy)
        Text(selected?.name ?: uiText("Select a layer on the artboard or in Layers."),style=MaterialTheme.typography.bodyMedium)
        if(selected!=null && !document.isEditable(selected)) Text(uiText("Unlock this layer or its group before editing."))
        if(mode==StudioMode.PAINT) {
            Text(uiText("Brush presets"),style=MaterialTheme.typography.titleMedium)
            Text(uiText("A changed brush starts a new paint layer, keeping earlier marks unchanged."),style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                StudioCreativePresets.brushes.forEach { preset->FilterChip(brush.id==preset.id,{onBrush(preset)},label={Text(uiText(preset.id.name.lowercase().replaceFirstChar { it.uppercase() }))}) }
            }
            Text(uiText("Brush size")+" · "+brush.width)
            Slider(brush.width.toFloat(),{onBrush(brush.copy(width=it.roundToInt()))},valueRange=1f..100f)
            Text(uiText("Brush opacity")+" · "+brush.opacity/10+"%")
            Slider(brush.opacity.toFloat(),{onBrush(brush.copy(opacity=it.roundToInt()))},valueRange=1f..1000f)
            StudioColorChoices("#"+color.toUInt().toString(16).padStart(8,'0'),{onColor(it.drop(1).toLong(16).toInt())})
            var colourText by remember(color) {mutableStateOf("#"+color.toUInt().toString(16).padStart(8,'0'))}
            OutlinedTextField(colourText,{text->colourText=text.take(9);if(text.matches(Regex("#[0-9A-Fa-f]{8}"))) onColor(text.drop(1).toLong(16).toInt())},
                label={Text(uiText("Colour · #AARRGGBB"))},singleLine=true,modifier=Modifier.fillMaxWidth(),isError=!colourText.matches(Regex("#[0-9A-Fa-f]{8}")))
            Zone5ActionButton(uiText("Paint on artboard"),"studio_brush",!busy,Modifier.fillMaxWidth()) { onDismiss();onDraw() }
            Zone5ActionButton(uiText("Eyedropper"),"studio_palette",!busy,Modifier.fillMaxWidth()) { onDismiss();onPick() }
            Zone5ActionButton(uiText("Add solid fill layer"),"studio_fill",!busy,Modifier.fillMaxWidth()) { onDismiss();onFill() }
            Zone5ActionButton(uiText("Fill connected area"),"studio_fill",!busy&&color.ushr(24)>0,Modifier.fillMaxWidth()) {onDismiss();onBucket()}
            if(color.ushr(24)==0) Text(uiText("Choose a visible colour before filling an area."),style=MaterialTheme.typography.bodySmall)
        }
        if(selected!=null) {
            if(selected.kind !in setOf(StudioLayerKind.IMAGE,StudioLayerKind.GROUP)) FlowRow {
                StudioCreativePresets.styles.forEach { style->TextButton({onCommand(StudioCommand.Replace(StudioCreativePresets.styled(selected,style.id)))},enabled=editable) {Text(uiText(style.id.lowercase().replaceFirstChar {it.uppercase()}))} }
            }
            Zone5ActionButton(uiText("Edit layer"),"office_edit_text",!busy,Modifier.fillMaxWidth()) { onDismiss();onEdit(selected) }
            Zone5ActionButton(uiText("Effects and lighting"),"setting_appearance",editable,Modifier.fillMaxWidth()) { onDismiss();onEffects(selected) }
            Zone5ActionButton(uiText("Duplicate"),"action_copy",editable,Modifier.fillMaxWidth()) { onCommand(StudioCommand.Duplicate(selected.id)) }
            val siblings=document.layers.filter { it.parentId==selected.parentId }
            Zone5ActionButton(uiText("Bring to front"),"action_up",editable,Modifier.fillMaxWidth()) {
                onCommand(StudioCommand.Move(selected.id,document.layers.indexOf(siblings.last())))
            }
            Zone5ActionButton(uiText("Send to back"),"action_down",editable,Modifier.fillMaxWidth()) {
                onCommand(StudioCommand.Move(selected.id,document.layers.indexOf(siblings.first())))
            }
            Text(uiText("Move in small steps"),style=MaterialTheme.typography.titleMedium)
            FlowRow {
                listOf("Left" to (-10 to 0),"Right" to (10 to 0),"Up" to (0 to -10),"Down" to (0 to 10)).forEach { (label,delta)->
                    TextButton({onCommand(StudioCommand.Replace(selected.copy(bounds=selected.bounds.copy(
                        x=(selected.bounds.x+delta.first).coerceIn(-1000,2000),y=(selected.bounds.y+delta.second).coerceIn(-1000,2000)))))},enabled=editable) { Text(uiText(label)) }
                }
                TextButton({onCommand(StudioCommand.Replace(selected.copy(rotation=(selected.rotation+15+360)%360)))},enabled=editable) { Text(uiText("Rotate 15°")) }
            }
        }
        Zone5ActionButton(uiText("Layers and alignment"),"studio_align",!busy,Modifier.fillMaxWidth()) { onDismiss();onLayers() }
        Zone5ActionButton(uiText("Artboard crop and transform"),"studio_crop",!busy,Modifier.fillMaxWidth()) { onDismiss();onTransform() }
        Zone5ActionButton(uiText("Fit artboard"),"studio_resize",true,Modifier.fillMaxWidth()) { onDismiss();onFit() }
        Text(uiText("Drag to move. Use the corner to resize, the top handle to rotate, or two fingers to move the view."),style=MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioLayerManager(project:StudioProject,busy:Boolean,onCommand:(StudioCommand)->Unit,onEdit:(StudioLayer)->Unit,onEffects:(StudioLayer)->Unit) {
    val document=project.session.current
    var multiple by remember(project.id) { mutableStateOf(setOf<String>()) }
    var groupName by rememberSaveable(project.id) { mutableStateOf("") }
    val selected=document.layers.filter { it.id in multiple }
    val canGroup=selected.isNotEmpty() && selected.map { it.parentId }.distinct().size==1 && selected.all(document::isEditable) && !busy
    Column(Modifier.fillMaxWidth().heightIn(max=640.dp).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(uiText("Layers"),style=MaterialTheme.typography.titleLarge)
        Text(uiText("Select one layer to edit, or mark siblings to group and align."),style=MaterialTheme.typography.bodySmall)
        if(document.layers.isEmpty()) Text(uiText("Add a layer to start creating."))
        fun ordered(parent:String?):List<StudioLayer> = document.layers.filter { it.parentId==parent }.asReversed().flatMap { listOf(it)+ordered(it.id) }
        ordered(null).forEach { layer ->
            val editable=document.isEditable(layer)
            val depth=document.ancestors(layer).size
            val active=layer.id==document.selectedLayerId
            Surface(color=if(active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start=(8+depth*12).dp,end=8.dp,top=8.dp,bottom=8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(layer.id in multiple,{checked->multiple=if(checked) multiple+layer.id else multiple-layer.id},enabled=!busy)
                        Zone5Icon(when(layer.kind) { StudioLayerKind.IMAGE->"type_image";StudioLayerKind.INK->"studio_draw";StudioLayerKind.TEXT->"studio_text";StudioLayerKind.GROUP->"studio_group";StudioLayerKind.TEXTURE->"studio_palette";else->"studio_shapes" },HaloGlyph.FILE,Modifier.size(24.dp))
                        TextButton({onCommand(StudioCommand.Select(layer.id))},enabled=!busy,modifier=Modifier.weight(1f).semantics { this.selected=active }) { Text(layer.name) }
                    }
                    if(layer.effects.isNotEmpty() || layer.mask!=null) Text(uiText("Effects / mask")+" · "+layer.effects.size,style=MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        TextButton({onCommand(StudioCommand.Replace(layer.copy(visible=!layer.visible)))},enabled=!busy&&editable) { Text(uiText(if(layer.visible) "Hide" else "Show")) }
                        TextButton({onCommand(StudioCommand.Replace(layer.copy(locked=!layer.locked)))},enabled=!busy&&document.ancestors(layer).none { it.locked }) { Text(uiText(if(layer.locked) "Unlock" else "Lock")) }
                        TextButton({onEdit(layer)},enabled=!busy) { Text(uiText("Edit")) }
                        TextButton({onEffects(layer)},enabled=!busy&&editable) { Text(uiText("Effects")) }
                        val siblings=document.layers.filter { it.parentId==layer.parentId };val index=siblings.indexOf(layer)
                        TextButton({onCommand(StudioCommand.Move(layer.id,document.layers.indexOf(siblings[index+1])))},enabled=!busy&&editable&&index<siblings.lastIndex) { Text(uiText("Up")) }
                        TextButton({onCommand(StudioCommand.Move(layer.id,document.layers.indexOf(siblings[index-1])))},enabled=!busy&&editable&&index>0) { Text(uiText("Down")) }
                        TextButton({onCommand(StudioCommand.Duplicate(layer.id))},enabled=!busy&&editable&&document.layers.size+document.descendants(layer.id).size+1<=StudioDocument.MAX_LAYERS) { Text(uiText("Duplicate")) }
                        TextButton({onCommand(StudioCommand.Remove(layer.id))},enabled=!busy&&editable) { Text(uiText("Delete")) }
                        if(layer.kind==StudioLayerKind.GROUP) TextButton({onCommand(StudioCommand.Ungroup(layer.id))},enabled=!busy&&editable&&layer.visible&&layer.opacity==1000&&layer.blend==StudioBlend.NORMAL&&layer.effects.isEmpty()&&layer.mask==null) { Text(uiText("Ungroup")) }
                    }
                }
            }
        }
        OutlinedTextField(groupName,{groupName=it.take(120)},label={Text(uiText("Group name"))},modifier=Modifier.fillMaxWidth())
        TextButton({onCommand(StudioCommand.Group(selected.map { it.id },groupName.trim()));multiple=emptySet()},enabled=canGroup&&groupName.isNotBlank()) { Text(uiText("Group selected layers")) }
        if(selected.size>=2) FlowRow {
            StudioLayout.entries.forEach { operation->
                val distributed=operation in setOf(StudioLayout.DISTRIBUTE_HORIZONTAL,StudioLayout.DISTRIBUTE_VERTICAL)
                TextButton({onCommand(StudioCommand.Layout(selected.map { it.id },operation))},enabled=canGroup&&(!distributed||selected.size>=3)) { Text(uiText(operation.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() })) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioEffectsDialog(service:StudioService,project:StudioProject,layer:StudioLayer,onApply:(StudioLayer)->Unit,onDismiss:()->Unit) {
    var effects by remember(layer) { mutableStateOf(layer.effects) }
    var selected by remember(layer) { mutableStateOf(effects.firstOrNull()?.id) }
    val draft=layer.copy(effects=effects)
    val preview=project.session.current.copy(layers=project.session.current.layers.map { if(it.id==layer.id) draft else it })
    StudioAlertDialog(onDismiss,{Text(uiText("Effects and lighting"))},{
        Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            StudioDocumentPreview(service,preview,Modifier.fillMaxWidth().height(180.dp))
            Text(uiText("Original layers stay editable. Toggle or remove an effect at any time."),style=MaterialTheme.typography.bodySmall)
            effects.forEachIndexed { index,effect->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Switch(effect.enabled,{v->effects=effects.map { if(it.id==effect.id) it.copy(enabled=v) else it }})
                    TextButton({selected=effect.id},modifier=Modifier.weight(1f)) { Text(uiText(effect.kind.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() })) }
                    TextButton({effects=effects.toMutableList().apply { add(index-1,removeAt(index)) }},enabled=index>0) { Text(uiText("Up")) }
                    TextButton({effects=effects.filterNot { it.id==effect.id }}) { Text(uiText("Remove")) }
                }
            }
            effects.find { it.id==selected }?.let { effect->
                Text(uiText("Amount")+" · "+effect.amount/10+"%")
                Slider(effect.amount.toFloat(),{v->effects=effects.map { if(it.id==effect.id) it.copy(amount=v.roundToInt()) else it }},valueRange=-1000f..1000f)
                Text(uiText("Radius")+" · "+effect.radius)
                Slider(effect.radius.toFloat(),{v->effects=effects.map { if(it.id==effect.id) it.copy(radius=v.roundToInt()) else it }},valueRange=0f..64f)
                StudioColorChoices("#"+effect.color.toUInt().toString(16).padStart(8,'0'),{v->effects=effects.map { if(it.id==effect.id) it.copy(color=v.drop(1).toLong(16).toInt()) else it }})
            }
            Text(uiText("Add effect"),style=MaterialTheme.typography.titleMedium)
            FlowRow {
                StudioEffectKind.entries.forEach { kind->TextButton({val effect=StudioEffect(kind=kind);effects=effects+effect;selected=effect.id},enabled=effects.size<8) { Text(uiText(kind.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() })) } }
            }
        }
    },{TextButton({onApply(draft);onDismiss()}) {Text(uiText("Apply"))}},{TextButton(onDismiss) {Text(uiText("Cancel"))}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioTextureDialog(onAdd:(StudioLayer)->Unit,onDismiss:()->Unit) {
    var texture by remember { mutableStateOf(StudioTexture.PAPER) }
    var color by remember { mutableStateOf("#ffded4be") }
    val name=uiText(texture.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() })
    StudioAlertDialog(onDismiss,{Text(uiText("Texture"))},{Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(uiText("Local procedural materials. No download needed."))
        FlowRow { StudioTexture.entries.forEach { value->FilterChip(value==texture,{texture=value},label={Text(uiText(value.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }))}) } }
        StudioColorChoices(color,{color=it})
    }},{TextButton({onAdd(StudioLayer(name=name,kind=StudioLayerKind.TEXTURE,color=color.drop(1).toLong(16).toInt(),texture=texture));onDismiss()}) {Text(uiText("Add layer"))}},{TextButton(onDismiss) {Text(uiText("Cancel"))}})
}
