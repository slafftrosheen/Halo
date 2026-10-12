"""List destinations from AppShell, including Screen.route declarations and nested routes."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
shell = root / "app/src/main/java/com/halo/app/ui/shell/AppShell.kt"
source = shell.read_text(encoding="utf-8")
screens = dict(re.findall(r'data object (\w+) : Screen\("([^"\n]+)"', source))
destinations = list(re.finditer(r'composable\(\s*(?:"([^"\n]+)"|Screen\.(\w+)\.route)', source))
print("| Destination | AppShell line | Screen implementations | Audit / validation / gaps |")
print("|---|---:|---|---|")
for index, match in enumerate(destinations):
    route = match.group(1) or screens[match.group(2)]
    end = destinations[index + 1].start() if index + 1 < len(destinations) else len(source)
    body = source[match.end():end]
    implementations = sorted(set(re.findall(r'\b(\w+Screen)\(', body)))
    print(f"| `{route}` | {source[:match.start()].count(chr(10)) + 1} | {', '.join(implementations) or 'Inline content'} | Pending |")
print(f"\n{len(destinations)} destinations extracted from AppShell. Aliases remain listed to preserve deep-link contracts.")
