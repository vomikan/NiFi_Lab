# Урок 3: Трансформация данных в Apache NiFi

## Цель урока
Научиться применять различные процессоры NiFi для преобразования данных на примере изменения значений в JSON-документах.

## Описание задачи
Требуется изменить значение атрибута `attr2` в зависимости от значения атрибута `attr1`:

**Исходные данные:**
```json
[
  {"attr1":"start","attr2":"B"},
  {"attr1":"pause","attr2":"Y"},
  {"attr1":"halt","attr2":"Y"}
]
```
**Ожидаемый результат:**
```json
[
  {"attr1":"start","attr2":"A"},
  {"attr1":"pause","attr2":"N"},
  {"attr1":"halt","attr2":"N"}
]
```

## Визуализация
Ниже представлена схема flow:

![NiFi Flow](pipeline.png)

## Реализация с использованием различных процессоров
1. JoltTransformRecord
Назначение: Преобразование данных с помощью JOLT-спецификации

```json
[
  {
    "operation": "modify-overwrite-beta",
    "spec": {
      "*": {
        "attr2": "=ifEquals(attr1,'start','A',ifEquals(attr1,'pause','N','N'))"
      }
    }
  }
]
```

2. QueryRecord
Назначение: Использование SQL-подобных запросов

```sql
SELECT 
  attr1,
  CASE 
    WHEN attr1 = 'start' THEN 'A'
    ELSE 'N'
  END AS attr2
FROM FLOWFILE
```

3. UpdateRecord
Назначение: Прямое обновление записей

```/attr2 = ${field.value:equals('start'):ifElse('A','N')}```

4. ScriptedTransformRecord
Назначение: Скриптовая трансформация на Groovy

```groovy
record.setValue('attr2', record.getValue('attr1') == 'start' ? 'A' : 'N')
```

5. ExecuteGroovyScript
Назначение: Полноценная скриптовая обработка

```groovy
import groovy.json.*

def flowFile = session.get()
if(!flowFile) return

flowFile = session.write(flowFile, {inputStream, outputStream ->
    def json = new JsonSlurper().parse(inputStream)
    json.each { record ->
        record.attr2 = (record.attr1 == 'start') ? 'A' : 'N'
    }
    outputStream.withWriter { it << new JsonBuilder(json).toString() }
} as StreamCallback)

session.transfer(flowFile, REL_SUCCESS)
```
## Сравнительный анализ процессоров
| Процессор               | Преимущества                          | Недостатки                     | Рекомендуемый случай использования      |
|-------------------------|---------------------------------------|--------------------------------|-----------------------------------------|
| JoltTransformRecord     | Мощные трансформации, декларативный   | Сложный синтаксис JOLT         | Сложные преобразования с четкой логикой |
| QueryRecord             | Простота SQL-синтаксиса               | Ограниченная функциональность  | Простые преобразования табличных данных |
| UpdateRecord            | Простота конфигурации                 | Ограниченная логика            | Простые пакетные обновления полей       |
| ScriptedTransformRecord | Гибкость Groovy                       | Требует знаний Groovy          | Сложная бизнес-логика                   |
| ExecuteGroovyScript     | Максимальная гибкость                 | Наибольшие накладные расходы   | Нестандартные преобразования            |

## Практическое задание
Импортируйте flow из файла Lesson3.json

Включите все контроллер-сервисы

Запустите генератор тестовых данных

Сравните:

Скорость обработки каждого процессора

Потребление ресурсов

Читаемость конфигурации

Модифицируйте преобразования по своему усмотрению

## Выводы
Для простых преобразований оптимальны UpdateRecord и QueryRecord

Для сложной логики лучше использовать JoltTransformRecord

ScriptedTransformRecord предлагает баланс между гибкостью и производительностью

ExecuteGroovyScript следует использовать только для нестандартных сценариев

Данный урок демонстрирует различные подходы к трансформации данных в NiFi, позволяя выбрать оптимальное решение для конкретной задачи.

