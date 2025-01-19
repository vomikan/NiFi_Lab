@Grab(group='org.springframework.data', module='spring-data-redis', version='3.1.0')
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory

import org.apache.nifi.processor.io.StreamCallback
import java.nio.charset.StandardCharsets

// Получение FlowFile
def flowFile = session.get()
if (!flowFile) return

try {
    // Получение значения свойства batch_size
    def batchSize = batch_size.value ? batch_size.value.toInteger() : 1000
    if (batchSize <= 0) {
        throw new IllegalStateException("Свойство batch_size должно быть положительным числом")
    }
    log.info("Размер батча: ${batchSize}")

    // Получение значения динамического свойства для Redis
    def redisServiceUuid = redis_service_uuid.value
    if (!redisServiceUuid) {
        throw new IllegalStateException("Свойство redis_service_uuid не указано")
    }
    log.info("UUID сервиса Redis: ${redisServiceUuid}")

    // Получение сервиса RedisConnectionPoolService по UUID
    def redisService = context.getControllerServiceLookup().getControllerService(redisServiceUuid)
    if (!redisService) {
        throw new IllegalStateException("Сервис Redis не найден: ${redisServiceUuid}")
    }
    log.info("Сервис Redis найден: ${redisService}")

    // Получение RedisConnection
    def redisConnection = redisService.getConnection()
    if (!redisConnection) {
        throw new IllegalStateException("Не удалось получить RedisConnection из сервиса: ${redisServiceUuid}")
    }
    log.info("RedisConnection успешно получен из сервиса")

    // Получение сервиса RecordReaderFactory
    def recordReaderFactory = context.getProperty('RecordReader.in').asControllerService(org.apache.nifi.serialization.RecordReaderFactory)
    if (!recordReaderFactory) {
        throw new IllegalStateException("Сервис RecordReader не найден")
    }
    log.info("Сервис RecordReader найден: ${recordReaderFactory}")

    // Получение сервиса RecordSetWriterFactory
    def recordWriterFactory = context.getProperty('RecordWriter.out').asControllerService(org.apache.nifi.serialization.RecordSetWriterFactory)
    if (!recordWriterFactory) {
        throw new IllegalStateException("Сервис RecordWriter не найден")
    }
    log.info("Сервис RecordWriter найден: ${recordWriterFactory}")

    // Получение динамических свойств для ключа и результата
    def keyField = key_field.value
    if (!keyField) {
        throw new IllegalStateException("Свойство key_field не указано")
    }
    log.info("Поле для ключа: ${keyField}")

    def resultField = result_field.value
    if (!resultField) {
        throw new IllegalStateException("Свойство result_field не указано")
    }
    log.info("Поле для результата: ${resultField}")

    // Обработка FlowFile с использованием RecordReader и RecordWriter
    flowFile = session.write(flowFile, { inputStream, outputStream ->
        def recordReader = null
        def recordWriter = null
        try {
            // Создание RecordReader
            recordReader = recordReaderFactory.createRecordReader(flowFile, inputStream, log)

            // Получение схемы из RecordWriterFactory
            def schema = recordWriterFactory.getSchema(flowFile.getAttributes(), null)
            if (schema == null) {
                throw new IllegalStateException("Схема не найдена в RecordWriterFactory")
            }

            // Добавление нового поля в схему, если его нет
            def newSchemaFields = schema.fields.collect { it }
            if (!schema.fieldNames.contains(resultField)) {
                newSchemaFields.add(new org.apache.nifi.serialization.record.RecordField(resultField, org.apache.nifi.serialization.record.RecordFieldType.STRING.dataType))
                schema = new org.apache.nifi.serialization.SimpleRecordSchema(newSchemaFields)
            }

            // Создание RecordWriter с использованием обновленной схемы
            recordWriter = recordWriterFactory.createWriter(log, schema, outputStream, flowFile)

            // Начало записи набора записей
            recordWriter.beginRecordSet()

            def batch = []
            def keys = []

            // Чтение записей
            def record
            while ((record = recordReader.nextRecord()) != null) {
                try {
                    // Извлечение ключа для обогащения (поле, указанное в key_field)
                    def key = record.getAsString(keyField)
                    if (key != null) {
                        // Добавляем запись и ключ в батч
                        batch.add(record)
                        keys.add(key.toString().getBytes(StandardCharsets.UTF_8))

                        // Если батч достиг размера, обрабатываем его
                        if (batch.size() >= batchSize) {
                            processBatch(batch, keys, redisConnection, recordWriter, resultField)
                            batch.clear()
                            keys.clear()
                        }
                    } else {
                        log.warn("Пропущена запись из-за отсутствия ключа: ${record}")
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке записи: ${record}", e)
                }
            }

            // Обработка оставшихся записей
            if (!batch.isEmpty()) {
                processBatch(batch, keys, redisConnection, recordWriter, resultField)
            }

            // Завершение записи набора записей
            recordWriter.finishRecordSet()
        } catch (Exception e) {
            log.error("Ошибка при обработке FlowFile: ${e.message}", e)
        } finally {
            // Закрытие RecordReader
            if (recordReader != null) {
                recordReader.close()
            }
            // Закрытие RecordWriter
            if (recordWriter != null) {
                recordWriter.close()
            }
            // Закрытие соединения с Redis
            redisConnection.close()
        }
    } as StreamCallback)

    // Перенаправление FlowFile в успешный выход
    session.transfer(flowFile, REL_SUCCESS)
} catch (Exception e) {
    // Обработка ошибок
    log.error("Ошибка при обработке FlowFile: ${e.message}", e)
    session.transfer(flowFile, REL_FAILURE)
}

// Метод для обработки батча
def processBatch(batch, keys, redisConnection, recordWriter, resultField) {
    try {
        // Извлечение значений из Redis с использованием MGET
        def values = redisConnection.stringCommands().mGet(keys as byte[][])

        // Обогащение записей
        batch.eachWithIndex { record, index ->
            try {
                // Получение значения из Redis
                def value = values[index]
                if (value != null) {
                    // Логирование ключа и значения
                    log.info("Ключ: ${new String(keys[index], StandardCharsets.UTF_8)}, Значение: ${new String(value, StandardCharsets.UTF_8)}")

                    // Запись значения в поле, указанное в resultField
                    record.setValue(resultField, new String(value, StandardCharsets.UTF_8))
                } else {
                    log.warn("Значение не найдено в Redis для ключа: ${new String(keys[index], StandardCharsets.UTF_8)}")

                    // Если значение не найдено, записываем null в поле resultField
                    record.setValue(resultField, null)
                }

                // Запись обогащенной записи в выходной поток через RecordWriter
                recordWriter.write(record)
            } catch (Exception e) {
                log.error("Ошибка при обработке записи: ${record}", e)
            }
        }
    } catch (Exception e) {
        log.error("Ошибка при извлечении данных из Redis: ${e.message}", e)
    }
}
