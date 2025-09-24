### Генерация тестовых данных
Запустите группу через контекстное меню, дождитесь появления файлов, затем остановите всю группу.
На "маленьких" файлах вы можете проверить, что все процессоры выполняют необходимые трансформации.
Это извлечение массива из входящего JSON файла. 

#### 1. Группа "Little files"
**Назначение:** Проверка базовой функциональности процессоров  
**Характеристики:**
- Размер файлов: 10 MB
- Количество записей: 100
- Формат: JSON
```
{"id":{"results":[массив из 100000 записей]},"metadata":"test"}
```

#### 2. Группа "Big file"
**Назначение:** Тестирование обработки больших объемов данных  
**Характеристики:**
- Размер файла: ~1GB
- Количество записей: 1
- Структура: JSON
```
{
  "id": {
    "results": [массив из миллионов элементов]
  }
}
```

## Проблемные процессоры
Следующие процессоры завершаются с OutOfMemoryError:
- EvaluateJsonPath (ошибка: java.lang.OutOfMemoryError: Java heap space)
- JoltTransformJSON
- JSLTTransformJSON

## Рабочие решения

### 1. ExecuteGroovyScript с потоковой обработкой
Скрипт выдаст One Line Per Object тесктовый файл, который по сути является record, но его можно разбить на части как текст процессором SplitText, что быстрее и эффективнее чем SplitJson.
```groovy
@Grab('com.fasterxml.jackson.core:jackson-core:2.13.0')
@Grab('com.fasterxml.jackson.core:jackson-databind:2.13.0')

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

def flowFile = session.get()
if (!flowFile) return

def newFlowFile = session.create(flowFile)

try {
    session.read(flowFile).withStream { rawInputStream ->
        def jsonFactory = new JsonFactory()
        def parser = jsonFactory.createParser(rawInputStream)
        def objectMapper = new ObjectMapper()
        parser.codec = objectMapper

        session.write(newFlowFile).withStream { outputStream ->
            def writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
            boolean inResultsArray = false

            while (parser.nextToken() != null) {
                // Ищем поле "results"
                if (parser.currentToken() == JsonToken.FIELD_NAME && parser.getCurrentName() == "results") {
                    parser.nextToken() // Переходим к START_ARRAY
                    inResultsArray = true
                }

                // Обрабатываем элементы массива results
                if (inResultsArray) {
                    if (parser.currentToken() == JsonToken.START_OBJECT) {
                        // Читаем объект и записываем его в выходной поток
                        writer.write(objectMapper.writeValueAsString(parser.readValueAsTree()))
                        writer.newLine() // Добавляем новую строку после каждого объекта
                    } else if (parser.currentToken() == JsonToken.END_ARRAY) {
                        break // Завершаем обработку после массива results
                    }
                }
            }
            writer.flush()
        }
        parser.close()
    }

    session.transfer(newFlowFile, REL_SUCCESS)
    session.remove(flowFile)
} catch (Exception e) {
    log.error("Ошибка обработки JSON: ${e.message}", e)
    session.remove(newFlowFile)
    session.transfer(flowFile, REL_FAILURE)
}
```

### 2. ExecuteStreamCommand + jq
**Конфигурация:**
- Command Path: wsl
- Command Arguments: -d Ubuntu bash -c "jq -c '.id.results'"

### 3. ConvertRecord с JsonTreeReader
**Ключевая настройка:**
- Starting Field Name: results

## Практическое задание
Генерируйет тестовые файлы и направлятей их на разные процессоры, переключая соединительную линию. Предварительно надо остановить задействованные процессоры. 

## Выводы

1. __Проблемы обработки больших JSON:__  
```
- Стандартные процессоры пытаются загрузить весь файл в память
- При размерах >100MB возникает OutOfMemoryError
- Требуются специальные подходы к обработке
```

2. __Рабочие стратегии:__  
```
✓ __Потоковая обработка__ (Groovy, Jackson)  
- Плюсы: полный контроль над процессом
- Минусы: требует навыков программирования

✓ __Внешние утилиты__ (jq через ExecuteStreamCommand)  
- Плюсы: максимальная производительность
- Минусы: зависимость от внешних компонентов

✓ __Оптимизированные ридеры__ (JsonTreeReader)  
- Плюсы: нативная интеграция с NiFi
- Минусы: ограниченная гибкость
```

3. __Рекомендации:__  
```
- Для файлов до 50MB можно использовать стандартные процессоры
- Для 50MB-1GB использовать ConvertRecord с JsonTreeReader
- Для файлов >1GB применять ExecuteStreamCommand + jq
- Для нестандартных преобразований - ExecuteGroovyScript
```

4. __Оптимизация памяти:__  
```
- Увеличьте Xmx параметры для JVM
- Настройте параметры процессоров:
  - "Maximum Buffer Size" в JsonTreeReader
  - "Streaming Buffer Size" в потоковых решениях
```
