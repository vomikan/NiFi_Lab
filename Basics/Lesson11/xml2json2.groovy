import groovy.xml.XmlSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonGenerator

// Универсальная функция для сравнения путей
def isPathMatch(jsonPath, targetPaths) {
    def isMatch = targetPaths.contains("|${jsonPath}|")
    if (log.isDebugEnabled()) {
        log.debug("${jsonPath} | Исключительный: ${isMatch}")
    }
    return isMatch
}

// Функция для преобразования значения в соответствующий тип
def castValue(value, jsonPath, skipCastingPaths, commaSeparator) {
    // Если путь указан в исключениях, возвращаем значение как строку
    if (isPathMatch(jsonPath, skipCastingPaths)) {
        if (log.isInfoEnabled()) {
            log.info("Элемент ${jsonPath} | Значение \"${value}\" | Исключительный")
        }
        return value
    }

    // Если значение null, пустая строка или строка "null", возвращаем null
    if (value == null || value.toString().trim().isEmpty() || value.toString().trim().toLowerCase() == "null") {
        if (log.isInfoEnabled()) {
            log.info("Элемент ${jsonPath} | Значение null | Преобразован в null")
        }
        return null // Возвращаем настоящий null
    }

    // Попытка преобразовать в integer
    if (value.toString().isInteger()) {
        if (log.isInfoEnabled()) {
            log.info("Элемент ${jsonPath} | Значение ${value} | Преобразован в integer")
        }
        return value.toInteger()
    }

    // Попытка преобразовать в double с учетом разделителя
    try {
        // Заменяем разделитель на точку, если используется запятая
        def normalizedValue = commaSeparator == "," ? value.toString().replace(",", ".") : value.toString()
        if (normalizedValue.isDouble()) {
            if (log.isInfoEnabled()) {
                log.info("Элемент ${jsonPath} | Значение ${value} | Преобразован в double")
            }
            return normalizedValue.toDouble()
        }
    } catch (Exception e) {
        // Если преобразование не удалось, продолжаем
    }

    // Попытка преобразовать в boolean
    if (value.toString().toLowerCase() in ["true", "false"]) {
        if (log.isInfoEnabled()) {
            log.info("Элемент ${jsonPath} | Значение ${value} | Преобразован в boolean")
        }
        return value.toString().toBoolean()
    }

    // Если не удалось преобразовать, возвращаем как строку
    if (log.isInfoEnabled()) {
        log.info("Элемент ${jsonPath} | Значение \"${value}\"")
    }
    return value
}

// Функция для рекурсивной обработки XML
def processXml(node, jsonPath, skipCastingPaths, commaSeparator, forceArrayPaths) {
    if (log.isInfoEnabled()) {
        log.info("\n\n-----> ${jsonPath}")
    }

    // Если элемент пустой, возвращаем null
    if (node.children().isEmpty() && node.attributes().isEmpty()) {
        def value = node.text() ?: null
        return castValue(value, jsonPath, skipCastingPaths, commaSeparator)
    }

    // Если элемент содержит только текстовое значение и не имеет атрибутов
    if (node.children().size() == 1 && node.children()[0] instanceof String && node.attributes().isEmpty()) {
        def value = node.text()
        return castValue(value, jsonPath, skipCastingPaths, commaSeparator)
    }

    // Обработка сложных элементов (с атрибутами или вложенными элементами)
    def result = [:]

    // Обработка атрибутов
    node.attributes().each { attrName, attrValue ->
        result[attrName] = castValue(attrValue, "${jsonPath}.@${attrName}", skipCastingPaths, commaSeparator)
    }

    // Обработка дочерних элементов
    node.children().each { child ->
        def childName = child.name()
        def childPath = "${jsonPath}.${childName}"
        def childValue = processXml(child, childPath, skipCastingPaths, commaSeparator, forceArrayPaths)

        if (result[childName] == null) {
            // Если элемент добавляется впервые, проверяем, нужно ли его сразу преобразовать в массив
            if (isPathMatch("${jsonPath}.${childName}", forceArrayPaths)) {
                result[childName] = [childValue] // Преобразуем в массив
				if (log.isInfoEnabled()) {
                    log.info("\n!!! Элемент ${jsonPath}.${childName} | Принудительно преобразован в массив при первом добавлении")
				}
            } else {
                result[childName] = childValue // Оставляем как одиночный элемент
            }
        } else {
            // Если элемент уже существует, преобразуем его в массив (если он еще не массив)
            if (!(result[childName] instanceof List)) {
                result[childName] = [result[childName]]
            }
            result[childName] << childValue // Добавляем новый элемент в массив
        }
    }

    return result
}

// Получение FlowFile
def flowFile = session.get()
if (!flowFile) return

try {
    // Получение динамического атрибута skip_casting
	def skipCasting = skip_casting.value
    def skipCastingPaths = "|" + skipCasting
        .trim() // Удаляем пробелы в начале и конце
        .replaceAll("\\s+", "|") + "|" // Заменяем пробелы и переносы строк на |
    if (log.isInfoEnabled()) {
		log.info("\n\n-=========================================-")
        log.info("Поля для пропуска преобразования: ${skipCastingPaths}")
    }

    // Получение динамического атрибута comma_separator
    def commaSeparator = comma_separator.value ?: "." // По умолчанию точка
    if (log.isInfoEnabled()) {
        log.info("Используемый разделитель для чисел: ${commaSeparator}")
    }

    // Получение динамического атрибута force_array
    def forceArray = force_array.value
    def forceArrayPaths = "|" + forceArray
	    .trim()
        .replaceAll("\\s+", "|") + "|" 
    if (log.isInfoEnabled()) {
        log.info("Поля для принудительного преобразования в массив: ${forceArrayPaths}") // Вывод в кавычках
    }

    // Обработка FlowFile
    flowFile = session.write(flowFile, { inputStream, outputStream ->
        try {
            // Парсинг XML
            def xmlSlurper = new XmlSlurper()
            xmlSlurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            def xml = xmlSlurper.parse(inputStream)

            // Обработка XML и преобразование в JSON
            def jsonMap = [:]
            jsonMap[xml.name()] = processXml(xml, "\$.${xml.name()}", skipCastingPaths, commaSeparator, forceArrayPaths)

            // Отключение экранирования Unicode
            def generator = new JsonGenerator.Options()
                .disableUnicodeEscaping()
                .build()

            // Преобразование JSON в строку
            def jsonString = generator.toJson(jsonMap)

            // Запись JSON в выходной поток
            outputStream.write(jsonString.getBytes("UTF-8"))
        } catch (Exception e) {
            log.error("Ошибка при обработке FlowFile: ${e.message}", e)
        }
    } as StreamCallback)

    // Установка MIME-типа FlowFile в application/json
    flowFile = session.putAttribute(flowFile, "mime.type", "application/json")

    // Перенаправление FlowFile в успешный выход
    session.transfer(flowFile, REL_SUCCESS)
} catch (Exception e) {
    // Обработка ошибок
    log.error("Ошибка при обработке FlowFile: ${e.message}", e)
    session.transfer(flowFile, REL_FAILURE)
}
