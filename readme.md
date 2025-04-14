# NiFi_Lab

Добро пожаловать в проект **NiFi_Lab**! Этот репозиторий создан для популяризации Apache NiFi — мощного инструмента для автоматизации потоков данных. Здесь вы найдёте практические примеры (лабораторные работы) и инструкции по настройке NiFi для решения реальных задач.

---

## О проекте

Цель этого проекта — сделать NiFi более доступным для новичков и продемонстрировать его возможности на практических примерах.
Для продвинутого пользователя тоже будет достаточно контента.
Новые пользователи смогут изучить разные приёмы решения одной задачи и изучить правильные методы решения задач.
Упрощается всё тем, что в проекте есть готовые потоки, которые можно импортировать и не тратить время на его составление.
У фотографов это называется насмотренность. 

---

## Структура репозитория

- **Introduction**: Руководство по установке и настройке Apache NiFi на Windows.
- **Lesson1, Lesson2, ...**: Папки с лабораторными работами, каждая из которых посвящена определённой задаче.

---

## Как начать

1. **Установка и настройка NiFi**:
   - Перейдите в раздел [Introduction](/Introduction/readme.md), чтобы узнать, как развернуть NiFi на Windows.
   
2. **Лабораторные работы**:
   - Изучите папки **Lesson1**, **Lesson2** и другие, чтобы ознакомиться с практическими примерами использования NiFi.

3. **Примеры flow**:
   - В каждой лабораторной работе вы найдёте готовые NiFi flow (JSON файлы), которые можно импортировать и использовать.

---

## Лабораторные работы
### Модуль 1

1. **Lesson1**: Удаление пустых строк из файла с использованием различных процессоров.
2. **Lesson2**: Фильтрация текста на содержание строки или фразы (например, "Chuck Norris").
3. **Lesson3**: Меняем один атрибут записи по значению другого.
4. **Lesson4**: Читаем файлы.
5. **Lesson5**: Использование Lookup для замены значений (например, замена фамилий).
6. **Lesson6**: Fork - Join Enrichment.
7. **Lesson7**: Фильтруем объекты JSON
8. **Lesson8**: Преобразование JSON с экранированными символами (Unescape JSON).
9. **Lesson9**: Запись данных в виде JSON в БД.
10. **Lesson10**: Чтение всей таблицы из базы данных и её обработка.
11. **Lesson11**: Преобразование XML в JSON с сохранением структуры данных.
12. **Lesson12**: Back Pressure & retry
13.  **Lesson13**: Преобразование Excel в CSV (тут пример кастомного процессора на Python)
14. **Lesson14**: DQ
15. **Lesson15**: MockScript
16.  **Lesson16**: InvokeScriptedProcessor

### Модуль 2
Постороение пайплайнов

### Модуль 3
Использование ИИ в процессе обработки данных

---

## Популяризация NiFi

Этот проект создан для того, чтобы:
- Показать, насколько просто и эффективно использовать Apache NiFi.
- Предоставить готовые примеры для быстрого старта.
- Помочь новичкам разобраться в основах работы с NiFi.

---

## Лицензия

Этот проект распространяется под лицензией [MIT](LICENSE).

---

## Ссылки

- [Официальная документация Apache NiFi](https://nifi.apache.org/docs.html)
- [Скачать Apache NiFi](https://nifi.apache.org/download.html)
- [Скачать JDK](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- [ExecuteScript Cookbook](https://community.cloudera.com/t5/Community-Articles/ExecuteScript-Cookbook-part-1/ta-p/248922)
