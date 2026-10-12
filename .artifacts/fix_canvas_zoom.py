from pathlib import Path
p=Path('app/src/main/java/com/halo/app/ui/screens/notebook/CanvasEditorScreen.kt')
s=p.read_text(encoding='utf-8')
s=s.replace('val width = (node.width * state.viewportScale).coerceAtLeast(100f)','val width = node.width * state.viewportScale',1).replace('val height = (node.height * state.viewportScale).coerceAtLeast(96f)','val height = node.height * state.viewportScale',1)
s=s.replace('''if (visible && !state.loading && !state.missing) Surface(
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(width.coerceAtLeast(100f).dp)
                            .height(height.coerceAtLeast(96f).dp).pointerInput(node.id, state.viewportScale) {''','''if (visible && !state.loading && !state.missing) CanvasCardFrame(
                        width = node.width, height = node.height, scale = state.viewportScale,
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.pointerInput(node.id, state.viewportScale) {''',1)
s=s.replace('''                            },
                        onClick = { viewModel.selectNode(node.id) }, shape = MaterialTheme.shapes.medium,''','''                            },
                    ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        onClick = { viewModel.selectNode(node.id) }, shape = MaterialTheme.shapes.medium,''',1)
s=s.replace('Modifier.fillMaxWidth().height((110f * state.viewportScale).coerceAtLeast(48f).dp)','Modifier.fillMaxWidth().height(110.dp)')
needle='''                    }
                }
            }
        }
        if (selected != null && state.edges.any'''
assert needle in s
s=s.replace(needle,'''                    }
                    }
                }
            }
        }
        if (selected != null && state.edges.any''',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/halo/app/ui/screens/notebook/CanvasCardFrame.kt')
p.write_text('''package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Measure content in board coordinates, then scale its complete visual and input layer. */
@Composable
internal fun CanvasCardFrame(
    width: Float,
    height: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.size((width * scale).dp, (height * scale).dp)) {
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
            .graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f))
            .size(width.dp, height.dp)) { content() }
    }
}
''',encoding='utf-8')
