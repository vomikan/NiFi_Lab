# Урок 14: Валидация потоковых данных в NiFi с использованием Great Expectations

## 🎯 Цель урока
Настройка автоматизированной проверки качества данных (DQ), поступающих через Apache NiFi, с использованием:
- Микросервиса на FastAPI
- Библиотеки Great Expectations
- Пакетной валидации JSON-данных

## 📋 Основные компоненты

### 1. FastAPI Сервер (`main.py`)
```python
from fastapi import FastAPI, File, UploadFile, HTTPException
import json
from validation_logic import validate_data

app = FastAPI()

@app.post("/validate")
async def validate_file(file: UploadFile = File(...)):
    try:
        content = await file.read()
        data = json.loads(content)
        validated_data = validate_data(data)
        return validated_data
    except Exception as e:
        return {
            "message": f"Error: {str(e)}",
            "data": []
        }
```

### 2. Модуль валидации (`validation_logic.py`)
```python
import pandas as pd
from uuid import uuid4  # Для генерации уникальных имён
import great_expectations as gx

# Инициализация GX Core
context = gx.get_context()

# Создаем Datasource и ассет один раз при запуске сервера
def initialize_datasource_and_asset():
    datasource_names = [ds["name"] for ds in context.list_datasources()]
    if "pandas" not in datasource_names:
        data_source = context.data_sources.add_pandas("pandas")
    else:
        data_source = context.data_sources.get("pandas")

    try:
        data_asset = data_source.get_asset("json_data_asset")
    except LookupError:
        data_asset = data_source.add_dataframe_asset(name="json_data_asset")

    return data_asset

data_asset = initialize_datasource_and_asset()

# Определяем Expectations один раз
expectation_id_unique = gx.expectations.ExpectColumnValuesToBeUnique(column="id")
expectation_id_not_null = gx.expectations.ExpectColumnValuesToNotBeNull(column="id")

expectation_surname = gx.expectations.ExpectColumnValuesToNotBeNull(column="surname")
expectation_birthdate_not_null = gx.expectations.ExpectColumnValuesToNotBeNull(column="birthdate")
expectation_birthdate_format = gx.expectations.ExpectColumnValuesToMatchRegex(
    column="birthdate",
    regex=r"^\d{4}-\d{2}-\d{2}$"
)
expectation_phone_no_not_null = gx.expectations.ExpectColumnValuesToNotBeNull(column="phone_no")
expectation_phone_no_format = gx.expectations.ExpectColumnValuesToMatchRegex(
    column="phone_no",
    regex=r"^\+\d{1,2} \d{3}-\d{3}-\d{4}$"
)

def validate_data(data):
    """
    Валидирует данные с использованием Great Expectations.
    
    Args:
        data (list): Список словарей с данными для валидации.
    
    Returns:
        list: Входные данные с добавленным полем is_valid для каждой записи.
    """
    try:
        # Преобразуем данные в DataFrame
        df = pd.DataFrame(data)

        # Генерируем уникальное имя для Batch Definition
        batch_definition_name = f"batch_definition_{uuid4()}"

        # Создаем определение пакета (Batch Definition)
        batch_definition = data_asset.add_batch_definition_whole_dataframe(batch_definition_name)

        # Получаем пакет (Batch) с данными
        batch = batch_definition.get_batch(batch_parameters={"dataframe": df})

        # Выполняем валидацию
        validation_result_id_not_empty = batch.validate(expectation_id_not_null)
        validation_result_id_unique = batch.validate(expectation_id_unique)
        validation_result_surname = batch.validate(expectation_surname)
        validation_result_birthdate_not_null = batch.validate(expectation_birthdate_not_null)
        validation_result_birthdate_format = batch.validate(expectation_birthdate_format)
        validation_result_phone_not_empty = batch.validate(expectation_phone_no_not_null)
        validation_result_phone_no = batch.validate(expectation_phone_no_format)

        # Удаляем временный Batch Definition после использования
        data_asset.delete_batch_definition(batch_definition_name)

        # Получаем индексы невалидных записей
        invalid_indices = set()
        invalid_indices.update(validation_result_id_not_empty.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_id_unique.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_surname.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_surname.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_surname.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_birthdate_not_null.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_birthdate_format.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_phone_not_empty.result.get("partial_unexpected_index_list", []))
        invalid_indices.update(validation_result_phone_no.result.get("partial_unexpected_index_list", []))

        # Добавляем поле is_valid к каждой записи
        for i, record in enumerate(data):
            record["is_valid"] = i not in invalid_indices

        return data

    except Exception as e:
        raise ValueError(f"Error during validation: {str(e)}")

```

## 🛠 Практическая часть

### Шаг 1: Настройка NiFi
Импортируйте пайплайн из json файла и запустите соответсвующие сервисы, если они представлены.

### Шаг 2: Установка зависимостей
1. **Создание виртуального окружения**  
   Откройте командную строку (cmd), перейдите в созданную для этого директорию и выполните:
   ```
   python -m venv .venv
   ```

2. **Активация окружения**  
   ```
   .\.venv\Scripts\activate
   ```

3. **Обновление pip и установка зависимостей**  
   ```
   .\.venv\Scripts\python.exe -m pip install --upgrade pip
   .\.venv\Scripts\pip.exe install fastapi uvicorn pandas great-expectations python-multipart
   ```


### Шаг 3: Запуск и проверка
1. В командном интерпретаторе перейдите в каталог с файлами 
2. Запустите сервер:
```
python map.py
```

2. Отправьте тестовые данные из NiFi
3. Проверьте ответ:
   - Успешная валидация: статус 200
   - Ошибки: детализация в теле ответа

## 💡 Ключевые моменты
- **Пакетная обработка**: Одновременная проверка массива записей
- **Гибкие правила**: Возможность добавлять/удалять expectations
- **Интеграция**: Простое подключение к любому ETL-процессу

## 🚀 Домашнее задание
1. Добавьте проверку email через regex
2. Реализуйте логирование ошибок в файл
3. Настройте автоматический перезапуск сервера при сбоях

## 📚 Дополнительные материалы
- [Great Expectations Docs](https://docs.greatexpectations.io/)
- [NiFi REST API Guide](https://nifi.apache.org/docs.html)
