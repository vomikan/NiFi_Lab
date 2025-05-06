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
from uuid import uuid4
import great_expectations as gx

context = gx.get_context()

# Инициализация Datasource
data_source = context.data_sources.add_pandas("pandas")
data_asset = data_source.add_dataframe_asset(name="json_data_asset")

# Определение правил валидации
expectations = [
    gx.expectations.ExpectColumnValuesToNotBeNull(column="id"),
    gx.expectations.ExpectColumnValuesToBeUnique(column="id"),
    gx.expectations.ExpectColumnValuesToMatchRegex(
        column="phone_no",
        regex=r"^\+\d{1,2} \d{3}-\d{3}-\d{4}$"
    )
]
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
