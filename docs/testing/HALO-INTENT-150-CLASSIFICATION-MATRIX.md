# HALO-INTENT-150 live classification matrix

Status: **prepared, not executed**. This is a reproducible owner/alpha-tester input set for the real on-device intent classifier and the full voice/keyboard route. It is not evidence of model accuracy or device acceptance.

## Run record

Complete one record for each physical run. Repeat voice cases with cold and warm model state where possible.

| Field | Observed value |
|---|---|
| Date/time and tester |  |
| Device / Android version / RAM |  |
| Halo commit / installed APK SHA-256 |  |
| Intent model ID / artifact SHA-256 |  |
| LiteRT runtime / backend |  |
| Model state (cold/warm) |  |
| Network (online/offline) |  |
| STT/TTS active; audio route |  |
| Safe test account or no-op action harness |  |

For each row, record the final ASR text for voice cases, model label when the classifier runs, routed result, elapsed time, and side-effect count. An exact native command may bypass the classifier; record that route explicitly. Run action cases only through a safe test account or no-op harness. If a safe harness is unavailable, mark the row **NOT RUN** rather than performing a real external action. A pass requires the expected action/answer/clarification route, no unauthorized or duplicate effect, and no raw tool markup in chat, speech, or saved reply.

## Cases

There are 48 inputs: six contrasting pairs per language, with three voice pairs and three keyboard pairs in each language. Expected labels describe the user's intent; they do not authorize a tool effect.

| ID | Language | Input | Pair | Prompt | Expected label | Observed label / route | Latency ms | Effects | Result |
|---|---|---|---:|---|---|---|---:|---:|---|
| EN-V01 | EN | Voice | 1 | Could you get the phone ready so I can take a picture? | ACTION |  |  |  |  |
| EN-V02 | EN | Voice | 1 | How do I get the phone ready so I can take a picture? | ANSWER |  |  |  |  |
| EN-V03 | EN | Voice | 2 | Would you mind setting up a five-minute timer on my phone? | ACTION |  |  |  |  |
| EN-V04 | EN | Voice | 2 | How can I set up a five-minute timer on my phone? | ANSWER |  |  |  |  |
| EN-V05 | EN | Voice | 3 | Don't send that message, even if it looks ready. | UNCERTAIN |  |  |  |  |
| EN-V06 | EN | Voice | 3 | What does “send that message” mean in this example? | ANSWER |  |  |  |  |
| EN-K01 | EN | Keyboard | 4 | Please check Gmail and let me know whether an invoice came in today. | ACTION |  |  |  |  |
| EN-K02 | EN | Keyboard | 4 | How can I check Gmail for an invoice from today? | ANSWER |  |  |  |  |
| EN-K03 | EN | Keyboard | 5 | I need tomorrow's team meeting put on my calendar with Maya invited. | ACTION |  |  |  |  |
| EN-K04 | EN | Keyboard | 5 | What information is usually needed to add a team meeting to a calendar? | ANSWER |  |  |  |  |
| EN-K05 | EN | Keyboard | 6 | Can you take care of finding a route to the airport in Maps for me? | ACTION |  |  |  |  |
| EN-K06 | EN | Keyboard | 6 | What does “find a route to the airport in Maps” mean? | ANSWER |  |  |  |  |
| RU-V01 | RU | Voice | 1 | Помоги подготовить телефон, чтобы я мог сфотографировать это. | ACTION |  |  |  |  |
| RU-V02 | RU | Voice | 1 | Как подготовить телефон, чтобы сфотографировать это? | ANSWER |  |  |  |  |
| RU-V03 | RU | Voice | 2 | Поставь, пожалуйста, таймер на пять минут на моём телефоне. | ACTION |  |  |  |  |
| RU-V04 | RU | Voice | 2 | Как поставить таймер на пять минут на телефоне? | ANSWER |  |  |  |  |
| RU-V05 | RU | Voice | 3 | Пожалуйста, не отправляй это сообщение, даже если оно готово. | UNCERTAIN |  |  |  |  |
| RU-V06 | RU | Voice | 3 | Что означает фраза «отправь это сообщение» в этом примере? | ANSWER |  |  |  |  |
| RU-K01 | RU | Keyboard | 4 | Проверь Gmail и скажи, пришёл ли сегодня счёт. | ACTION |  |  |  |  |
| RU-K02 | RU | Keyboard | 4 | Как найти в Gmail счёт, который пришёл сегодня? | ANSWER |  |  |  |  |
| RU-K03 | RU | Keyboard | 5 | Добавь завтрашнюю командную встречу в календарь и пригласи Майю. | ACTION |  |  |  |  |
| RU-K04 | RU | Keyboard | 5 | Какие данные обычно нужны, чтобы добавить командную встречу в календарь? | ANSWER |  |  |  |  |
| RU-K05 | RU | Keyboard | 6 | Организуй мне маршрут до аэропорта в Maps. | ACTION |  |  |  |  |
| RU-K06 | RU | Keyboard | 6 | Что означает просьба «проложить маршрут до аэропорта в Maps»? | ANSWER |  |  |  |  |
| LV-V01 | LV | Voice | 1 | Sagatavo, lūdzu, tālruni, lai es varētu nofotografēt šo. | ACTION |  |  |  |  |
| LV-V02 | LV | Voice | 1 | Kā sagatavot tālruni, lai nofotografētu šo? | ANSWER |  |  |  |  |
| LV-V03 | LV | Voice | 2 | Vai vari tālrunī uzlikt piecu minūšu taimeri? | ACTION |  |  |  |  |
| LV-V04 | LV | Voice | 2 | Kā tālrunī uzlikt piecu minūšu taimeri? | ANSWER |  |  |  |  |
| LV-V05 | LV | Voice | 3 | Lūdzu, nesūti šo ziņu, pat ja tā jau ir sagatavota. | UNCERTAIN |  |  |  |  |
| LV-V06 | LV | Voice | 3 | Ko šajā piemērā nozīmē frāze “nosūti šo ziņu”? | ANSWER |  |  |  |  |
| LV-K01 | LV | Keyboard | 4 | Pārbaudi Gmail un pasaki, vai šodien pienāca rēķins. | ACTION |  |  |  |  |
| LV-K02 | LV | Keyboard | 4 | Kā Gmail atrast šodien saņemtu rēķinu? | ANSWER |  |  |  |  |
| LV-K03 | LV | Keyboard | 5 | Pievieno rītdienas komandas sapulci kalendāram un uzaicini Maiju. | ACTION |  |  |  |  |
| LV-K04 | LV | Keyboard | 5 | Kādu informāciju parasti vajag, lai pievienotu komandas sapulci kalendāram? | ANSWER |  |  |  |  |
| LV-K05 | LV | Keyboard | 6 | Nokārto man maršrutu līdz lidostai pakalpojumā Maps. | ACTION |  |  |  |  |
| LV-K06 | LV | Keyboard | 6 | Ko nozīmē lūgums “atrast maršrutu līdz lidostai pakalpojumā Maps”? | ANSWER |  |  |  |  |
| PL-V01 | PL | Voice | 1 | Przygotuj proszę telefon, żebym mógł zrobić to zdjęcie. | ACTION |  |  |  |  |
| PL-V02 | PL | Voice | 1 | Jak przygotować telefon do zrobienia tego zdjęcia? | ANSWER |  |  |  |  |
| PL-V03 | PL | Voice | 2 | Ustaw proszę minutnik na pięć minut w moim telefonie. | ACTION |  |  |  |  |
| PL-V04 | PL | Voice | 2 | Jak ustawić pięciominutowy minutnik w telefonie? | ANSWER |  |  |  |  |
| PL-V05 | PL | Voice | 3 | Proszę, nie wysyłaj tej wiadomości, nawet jeśli jest gotowa. | UNCERTAIN |  |  |  |  |
| PL-V06 | PL | Voice | 3 | Co w tym przykładzie oznacza wyrażenie „wyślij tę wiadomość”? | ANSWER |  |  |  |  |
| PL-K01 | PL | Keyboard | 4 | Sprawdź Gmaila i daj mi znać, czy dziś przyszła faktura. | ACTION |  |  |  |  |
| PL-K02 | PL | Keyboard | 4 | Jak znaleźć w Gmailu fakturę, która przyszła dzisiaj? | ANSWER |  |  |  |  |
| PL-K03 | PL | Keyboard | 5 | Dodaj jutrzejsze spotkanie zespołu do kalendarza i zaproś Maję. | ACTION |  |  |  |  |
| PL-K04 | PL | Keyboard | 5 | Jakie informacje są zwykle potrzebne, żeby dodać spotkanie zespołu do kalendarza? | ANSWER |  |  |  |  |
| PL-K05 | PL | Keyboard | 6 | Przygotuj mi trasę na lotnisko w Mapach. | ACTION |  |  |  |  |
| PL-K06 | PL | Keyboard | 6 | Co oznacza prośba „wyznacz trasę na lotnisko w Mapach”? | ANSWER |  |  |  |  |

## Acceptance status

All 48 rows are **NOT RUN** until a tester fills the run record and observed columns on a physical device with the exact model artifact. Source tests with stubbed labels do not satisfy this matrix. Record misses and side-effect results without storing personal message, contact, or account content. Do not close #36 from this prepared input set.
