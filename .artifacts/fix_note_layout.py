from pathlib import Path
p=Path('app/src/main/java/com/halo/app/ui/screens/notebook/NoteEditorScreen.kt')
s=p.read_text(encoding='utf-8')
start=s.index('    SelectionToolbar(',s.index('private fun ColumnScope.EditModeView') if 'private fun ColumnScope.EditModeView' in s else s.index('private fun EditModeView'))
end=s.index('        items(state.blocks, key = NoteBlock::id)',start)
section=s[start:end]
cut=section.index('    if (state.status == SaveStatus.LOADING)')
controls=section[:cut]
s=s[:start]+'''    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "editor-controls") {
            Column {
'''+controls+'''            }
        }
        if (state.status == SaveStatus.LOADING) item(key = "loading") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
'''+s[end:]
p.write_text(s,encoding='utf-8')
