# ИНСТРУКЦИЯ ПО УСТАНОВКЕ И НАСТРОЙКЕ ПО APACHE NiFi версии 2.5.0
## Аннотация
Настоящий документ содержит пошаговую инструкцию по установке, настройке и запуску Apache NiFi версии 2.5.0 на операционной системе МОС.ОС. 
В руководстве описаны подготовка системы, установка OpenJDK 21, настройка сервиса, конфигурация безопасности, параметров производительности и веб-интерфейса. Инструкция предназначена для системных администраторов и специалистов по интеграции, ответственных за развёртывание и сопровождение платформы NiFi в защищённой среде.

## 1	Предварительная подготовка
### 1.1 Подготовка утилит

Установите необходимые утилиты для загрузки, распаковки и редактирования конфигураций:

```bash
# Обновление системы
sudo zypper refresh
sudo zypper update -y

# Установка базовых утилит
sudo zypper install -y wget unzip tar gzip nano curl procps
```

> 💡 **Примечание:**  
> - В МОС.ОС используется пакетный менеджер `zypper` (аналог `dnf` в RHEL или `apt` в Debian).  
> - Утилиты `nano`, `wget`, `unzip` — необходимы для скачивания дистрибутива и редактирования конфигурационных файлов.


### 1.2 Рекомендованные настройки ОС

Для стабильной и производительной работы Apache NiFi 2.5.0 рекомендуется выполнить базовую настройку операционной системы МОС.ОС, включающую увеличение лимитов ресурсов и оптимизацию параметров ядра.

Настройки соответствуют рекомендациям Apache NiFi и адаптированы под SUSE-совместимые системы.

#### 1.2.1 Лимиты открытых файлов и процессов

NiFi может открывать большое количество файлов и сетевых соединений. Увеличьте лимиты для пользователя, от которого будет запускаться NiFi (например, `nifi`).

Откройте файл:

```bash
sudo nano /etc/security/limits.conf
```

Добавьте строки:

```conf
# Увеличение лимитов для пользователя nifi
nifi    soft    nofile    50000
nifi    hard    nofile    50000
nifi    soft    nproc     10000
nifi    hard    nproc     10000

# Опционально: для всех пользователей
*       soft    nofile    50000
*       hard    nofile    50000
*       soft    nproc     10000
*       hard    nproc     10000
```

> 💡 **Важно:**  
> Лимиты применяются при новом входе в систему. Перезайдите, чтобы изменения вступили.

#### 1.2.2 Настройка параметров ядра
Откройте файл конфигурации ядра:
```bash
sudo nano /etc/sysctl.conf
```

Добавьте или измените следующие параметры:
```conf
# Расширенный диапазон локальных портов (для множества исходящих соединений)
net.ipv4.ip_local_port_range = 10000 65000

# Ограничение использования свопинга (рекомендуется при достаточном объёме RAM)
vm.swappiness = 0

# Увеличение максимального количества соединений в очереди
net.core.somaxconn = 1000

# Защита от SYN-флуд атак
net.ipv4.tcp_syncookies = 1

# Увеличение буферов сети (опционально, для высокой нагрузки)
net.core.rmem_default = 262144
net.core.rmem_max = 16777216
net.core.wmem_default = 262144
net.core.wmem_max = 16777216
```

Примените изменения:
```bash
sudo sysctl -p
```

> ⚠️ **Примечание:**  
> vm.swappiness=0 ≠ отключение swap, но система будет его использовать только в крайних случаях.
> Параметр `vm.swappiness = 0` означает, что система будет избегать использования swap.  
> Устанавливайте его, если у сервера **достаточно RAM (16 ГБ и более)**.  
> При малом объёме RAM — используйте `vm.swappiness = 1`.

##### 1.2.3. Настройка лимитов (в /etc/security/limits.conf):

```text
nifi soft nofile 50000
nifi hard nofile 50000
nifi soft nproc 10000
nifi hard nproc 10000
```

> 💡 **Важно:**
> - Все команды выполняются через `sudo`
> - Для применения изменений требуется перезагрузка системы или перезапуск сервиса

Проверка параметров ядра (требует root-прав):
```bash
# Проверка портов
sudo /sbin/sysctl net.ipv4.ip_local_port_range
# Ожидаемый вывод: net.ipv4.ip_local_port_range = 10000    65000

# Проверка swappiness
sudo /sbin/sysctl vm.swappiness
# Ожидаемое значение: 0-10 (для серверов)
```

### 1.3 Установка OpenJDK 21

Apache NiFi 2.5.0 требует **Java 21**. В системе доступен пакет `java-21-openjdk` из репозитория.

#### 1.3.1 Обновите список пакетов

```bash
sudo zypper refresh
```

#### 1.3.2. Установите OpenJDK 21

Рекомендуется установить:
- `java-21-openjdk-headless` — JVM без GUI
- `java-21-openjdk-devel` — инструменты разработки (javac, jstat и др.)

```bash
sudo zypper install java-21-openjdk-devel java-21-openjdk-headless
```

> 💡 Пакеты предоставлены в рамках **АТ (Астра Технологии)** — версия `21.0.3.0-at154.1.1`.

#### 1.3.3. Проверьте установку

```bash
java -version
javac -version
```

Ожидаемый вывод:
```
openjdk version "21.0.3" 2024-04-16
OpenJDK Runtime Environment AstraLinux (build 21.0.3+0-154)
OpenJDK 64-Bit Server VM AstraLinux (build 21.0.3+0-154, mixed mode)
```

#### 1.3.4. Настройка JAVA_HOME
Поскольку NiFi запускается как systemd-сервис, `JAVA_HOME` задаётся в конфигурации сервиса (раздел 2.1.5).


### 2.1 Скачивание и установка NiFi

Apache NiFi 2.5.0 устанавливается вручную из бинарного архива. Рекомендуется использовать **символическую ссылку** для независимости от версии.

> ⚠️ **Внимание!**  
> - На момент написания, актуальная версия: **NiFi 2.5.0**  
> - Убедитесь, что `JAVA_HOME` уже настроен на OpenJDK 21 (см. раздел 1.3)  
> - Все действия выполняются от имени `root` или с `sudo`

#### 2.1.1. Перейдите в домашнюю директорию и скачайте дистрибутив

```bash
cd ~
wget https://dlcdn.apache.org/nifi/2.5.0/nifi-2.5.0-bin.zip
```

> 💡 Альтернатива: используйте локальную копию, если нет доступа к dlcdn.apache.org

#### 2.1.2. Распакуйте архив

```bash
unzip nifi-2.5.0-bin.zip
```

#### 2.1.3. Переместите в /opt

```bash
sudo mv nifi-2.5.0 /opt/
```

#### 2.1.4 создание пользователя для NiFi (сервисный аккаунт)
Для безопасной работы NiFi создадим пользователя nifi.

##### 2.1.4.1. Создание пользователя и группы:

```bash
sudo groupadd nifi

# Создаём обычного пользователя
sudo useradd \
  --create-home \
  --home-dir /home/nifi \
  --shell /bin/bash \
  --comment "Apache NiFi Service Account" \
  --gid nifi \
  nifi
```

##### 2.1.4.2. Настройка прав доступа к файлам:

```bash
sudo chown -R nifi:nifi /opt/nifi-2.5.0/
sudo chmod 755 /opt/nifi-2.5.0
```

##### 2.1.4.3. Проверка создания пользователя:

```bash
id nifi
grep nifi /etc/passwd
```


#### 2.1.5. Установите NiFi как сервис
Найдите путь к JDK:
```bash
readlink -f $(which java)
# Пример вывода: /usr/lib64/jvm/java-21-openjdk/bin/java
```
Этот путь надо будет указать в файле сервиса без /bin/java

Так как команда install была удалена из скрипта nifi.sh в новых версиях, создаем сервис вручную:
Создаем файл сервиса.

```bash
sudo nano /etc/systemd/system/nifi.service
```
Вставляем конфигурацию (адаптируйте пути):
```ini
[Unit]
Description=Apache NiFi
After=network.target

[Service]
Type=forking
User=nifi
Group=nifi
ExecStart=/opt/nifi-2.5.0/bin/nifi.sh start
ExecStop=/opt/nifi-2.5.0/bin/nifi.sh stop
Restart=on-failure
RestartSec=10s
TimeoutStartSec=120
LimitNOFILE=50000
Environment="JAVA_HOME=/usr/lib64/jvm/java-21-openjdk"

[Install]
WantedBy=multi-user.target
```

Активируем сервис:
```bash
sudo systemctl daemon-reload
sudo systemctl enable nifi
```

> ✅ После установки сервис может управляться командами:
> - `sudo systemctl start nifi`
> - `sudo systemctl stop nifi`
> - `systemctl status nifi`

> ✅ После этого NiFi будет автоматически запускаться при загрузке системы.

#### Проверка статуса автозагрузки

```bash
systemctl is-enabled nifi
```
Ожидаемый вывод: `enabled`

#### 2.1.8. Проверьте, что сервис добавлен

```bash
systemctl list-unit-files | grep nifi
```

> 💡 **Ожидаемо:**
> - `nifi.service enabled disabled`


### 2.2 Настройка конфигурации NiFi

После установки необходимо настроить ключевые параметры NiFi:  
Все настройки выполняются от имени `root` или с `sudo`.

#### 2.2.1	Название хоста и порт
Укажите хост и порт для web доступа, отредактировав файл /opt/nifi/conf/nifi.properties:
```bash
sudo nano /opt/nifi-2.5.0/conf/nifi.properties
```

```ini
NiFi.web.https.host=0.0.0.0
NiFi.web.https.port=8443
```
> 💡 **Важно!**
> - Порт должен быть **открыт в брандмауэре**
> - Сертификаты должны быть настроены на соответствующие IP и имена


### 2.2.2 Настройка аутентификации: username + password

Задайте пользователя и пароль для WEB-интерфейса NiFi, выполнив команду:

sudo /opt/nifi-2.5.0/bin/nifi.sh set-single-user-credentials nifi_user nif1_passw4d_
где: `nifi_user` – имя пользователя; `nif1_passw4d_` – пароль пользователя.


#### 2.2.3 Настройка памяти (Xms / Xmx)

Откройте файл `bootstrap.conf`:

```bash
sudo nano /opt/nifi-2.5.0/conf/bootstrap.conf
```

Найдите строки:
```conf
# JVM memory settings
java.arg.2=-Xms1G
java.arg.3=-Xmx1G
```

Измените значения в зависимости от объёма RAM сервера:
- **Xms (минимальная память)** — начальное выделение
- **Xmx (максимальная память)** — не более **60–70% от общей RAM**
- Рекомендуется: **одинаковые Xms и Xmx**, чтобы избежать динамического выделения

> 💡 Примеры:
> - Сервер с 32 ГБ RAM → `Xms=16g`, `Xmx=16g`
> - Сервер с 64 ГБ RAM → `Xms=32g`, `Xmx=32g`

Обновите параметры:

```conf
# JVM memory settings
java.arg.2=-Xms16g
java.arg.3=-Xmx16g
```

> ⚠️ Не указывайте `Xmx` больше, чем доступно в системе — это вызовет `OutOfMemoryError` и падение NiFi.

---

#### 2.2.4 Настройка сборщика мусора (Garbage Collector)

NiFi 2.5.0 рекомендует использовать **G1GC** (Garbage-First Collector) для больших heap-объёмов.

Добавьте строку или расскоментируйте:

```conf
java.arg.13=-XX:+UseG1GC
```

> 💡 Дополнительные рекомендации (опционально):
>
> ```conf
> java.arg.14=-XX:MaxGCPauseMillis=200
> java.arg.15=-XX:InitiatingHeapOccupancyPercent=35
> ```
>
> Это улучшает стабильность при высокой нагрузке.

---


#### 2.2.5 Настройка поддержки UTF-8
Для корректной обработки **не-латинских символов** (например, русских) необходимо явно задать кодировку.

```bash
sudo nano /opt/nifi-2.5.0/conf/bootstrap.conf
```

```conf
# Поддержка UTF-8 для всех компонентов
java.arg.57=-Dfile.encoding=UTF8
java.arg.58=-Dcalcite.default.charset=utf-8
java.arg.59=-Dsun.jnu.encoding=UTF8
```

> ✅ Это гарантирует, что:
> - Все процессоры (включая QueryRecord, JoltTransform, Scripting)
> - Calcite (SQL-like обработка)
> - Системные строки — работают с UTF-8

В `nifi-env.sh` задайте переменные окружения:

```bash
sudo nano /opt/nifi-2.5.0/bin/nifi-env.sh
```

Добавьте в конец файла:

```bash
# Установка кодировки по умолчанию
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
```

> 💡 Даже если система использует `ru_RU.UTF-8`, лучше оставить `en_US.UTF-8` — NiFi стабильнее работает с английской локалью.

---

#### 2.2.6 Указание JAVA_HOME

Можно захардкодить `JAVA_HOME` **в `nifi-env.sh`**, чтобы NiFi не зависел от глобальной переменной.

Откройте:
```bash
sudo nano /opt/nifi-2.5.0/bin/nifi-env.sh
```

Найдите строку:
```bash
# The java implementation to use.
# export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk
```

Раскомментируйте и укажите путь к OpenJDK 21:

```bash
# The java implementation to use.
export JAVA_HOME=/usr/lib64/jvm/java-21-openjdk
```

> ✅ Проверить путь можно командами:
> ```bash
> ls /usr/lib64/jvm/java-21-openjdk
> readlink -f $(which java)
> ```

---

#### 2.2.7 Настройка уровня логирования

NiFi использует `logback` для логирования. По умолчанию уровень — `INFO`.

Для **продуктивной среды** рекомендуется повысить уровень до `WARN`, чтобы уменьшить объём логов.

Откройте файл:
```bash
sudo nano /opt/nifi/conf/logback.xml
```

Найдите и измените:
```xml
<logger name="org.apache.nifi" level="INFO"/>
```

на:
```xml
<logger name="org.apache.nifi" level="WARN"/>
```

##### Отключение логирования в стандартных процессорах

Процессоры `LogAttribute` и `LogMessage` могут генерировать много `INFO`-логов. На проде — отключите:
```xml
<logger name="org.apache.nifi.processors.standard.LogAttribute" level="WARN"/>
<logger name="org.apache.nifi.processors.standard.LogMessage" level="WARN"/>
```

И обновите корневой уровень:
```xml
<root level="WARN">
```

##### Отключение архивации контента (экономия диска)

По умолчанию NiFi архивирует содержимое FlowFile'ов. Это полезно для отладки, но **занимает много места**.

Для продакшена — отключите:
```bash
sudo nano /opt/nifi-2.5.0/conf/nifi.properties
```

Найдите строку:
```properties
nifi.content.repository.archive.enabled=true
```

Измените на:
```properties
nifi.content.repository.archive.enabled=false
```

> 💡 Также можно настроить ротацию и TTL, но полное отключение — самое эффективное для экономии диска.

#### 2.4.2 Пробрый запуск NiFi
```bash
sudo systemctl start nifi
```

#### 2.4.3. Проверка состояния сервиса
```bash
sudo systemctl status nifi
```
Ожидаемо: active (running)

Если статус inactive или failed — смотрите логи (см. ниже).


#### 2.4.4. Проверка прослушивания портов
Убедитесь, что NiFi слушает порт 8443 (HTTPS) и 2181 (кластерный протокол zookeeper):

```bash
ss -tulnp | grep ':8443\|:2181'
```
Ожидаемый вывод:
```
tcp LISTEN 0 50 *:8443 *:* users:(("java",pid=...,fd=...))
...
```
> 🔍 Порт 2181 — используется для внутреннего взаимодействия между нодами кластера.

#### 2.4.5. Проверка ответа веб-интерфейса (через curl)

Проверьте, отвечает ли веб-сервер. Выполните команду на самой ноде:

```bash
curl https://localhost:8443/nifi/ -s --insecure | head -10
```

> ⚠️ **Примечание:**  
> - Сервис может начать отвечать спустя 2–5 минут после запуска.  
> - Флаг `--insecure` необходим, так как используется самоподписанный сертификат.  
> - `$(uname -n)` подставляет имя текущей ноды (например, nifi-node-1).

Ожидаемый ответ — HTML-заголовки NiFi UI:

```html
<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
```

#### 2.4.6. Проверка через веб-браузер
До настройки сертификатов, можно прокинуть ssh тунель командой на рабочей станции.
```cmd
ssh -N -L 8443:localhost:8443 user@10.25.75.121
```

Откройте веб-интерфейс в браузере:
```
https://localhost:8443/nifi/
```

> ✅ После успешного входа откроется главный интерфейс NiFi — рабочая область для создания и управления сценариями.

#### 2.4.7. Проверка логов
Если NiFi не запускается или не отвечает — проверьте логи:

```bash
tail -f /opt/nifi-2.5.0/logs/nifi-app.log
```
Ищите ключевые строки:

`Web Server Started` — веб-интерфейс запущен
`Cluster Node started` — нода присоединилась к кластеру
`Handshake failed` — ошибка TLS (проверьте сертификаты)
`Connection refused` — проблема с сетью или портами

#### 2.4.8. Дополнительные полезные команды
```bash
# Проверить, какой процесс слушает порт
lsof -i :8443

# Посмотреть последние ошибки в systemd
journalctl -u nifi -n 50 --no-pager

# Проверить переменные окружения процесса NiFi
ps aux | grep nifi | grep java
```


### 2.5 Настройка TLS-сертификатов для NiFi

Для безопасной работы Apache NiFi требует настройки TLS-сертификатов.  
**Самоподписанные сертификаты** могут использоваться на этапе тестирования или в изолированных сетях.  
В production-средах рекомендуется использовать **сертификаты от доверенного УЦ**.

> ⚠️ **Внимание!**  
> - NiFi **не работает без TLS** в кластерном режиме.
> - Каждая нода **должна иметь свой уникальный сертификат**.
> - **Один сертификат нельзя использовать на всех нодах** — это нарушает безопасность и вызовет ошибки аутентификации.


#### 2.3.1 Подготовка: имена хостов и IP-адреса

Даже если хосты **не прописаны в DNS**, необходимо использовать **уникальные имена** для каждой ноды.  
Имя хоста можно задать локально и использовать в сертификате.

##### Как определить имя хоста?
```bash
hostname
# или
hostnamectl
```
Пример вывода:
```
nifi-node-1
```
Если имя не задано — установите:

```bash
sudo hostnamectl set-hostname nifi-node-1
```
> ✅ Рекомендуемые имена: nifi-node-1, nifi-node-2, nifi-node-3 

#### 2.4.1. Проверка разрешения имён
Убедитесь, что все ноды могут разрешать имена друг друга:
```bash
ping nifi-node-1
ping nifi-node-2
ping nifi-node-3
```
Если нет DNS — добавьте в /etc/hosts:
```text
10.25.75.121 nifi-node-1
10.25.75.122 nifi-node-2
10.25.75.123 nifi-node-3
```

#### 2.4.2. Замена сертификатов
Процесс обновления сертификата в NiFi 2.5.0 (без изменения текущей конфигурации)
Для безопасного обновления сертификатов в существующей конфигурации NiFi выполните следующие шаги:

1. Подготовка нового сертификата
```bash
# Переходим в директорию с конфигами NiFi
cd /opt/nifi-2.5.0/conf

# Создаем резервную копию текущих хранилищ
cp keystore.jks keystore.jks.bak
cp truststore.jks truststore.jks.bak

# Генерируем новый ключ и CSR (Certificate Signing Request)
keytool -genkeypair -alias nifi -keyalg RSA -keysize 2048 \
  -keystore keystore.jks -validity 365 \
  -dname "CN=nifi_1_hostname, OU=NiFi" \
  -ext SAN=DNS:nifi_1_hostname,IP:10.25.75.121 \
  -storepass ваш_текущий_пароль -keypass ваш_текущий_пароль

keytool -certreq -alias nifi -keystore keystore.jks \
  -file nifi.csr -storepass ваш_текущий_пароль \
  -ext SAN=DNS:nifi_1_hostname,IP:10.25.75.21
```

2. Подпись сертификата (для self-signed)
```bash
# Подписываем CSR своим же ключом (self-signed)
keytool -gencert -alias nifi -keystore keystore.jks \
  -infile nifi.csr -outfile nifi.crt \
  -ext SAN=DNS:nifi_1_hostname,IP:10.25.75.19 \
  -storepass ваш_текущий_пароль

# Импортируем новый сертификат
keytool -importcert -alias nifi -keystore keystore.jks \
  -file nifi.crt -storepass ваш_текущий_пароль -noprompt
```

3. Обновление truststore (если требуется)
```bash
# Экспортируем новый сертификат
keytool -exportcert -alias nifi -keystore keystore.jks \
  -file nifi_new.crt -storepass ваш_текущий_пароль

# Обновляем truststore
keytool -importcert -alias nifi -keystore truststore.jks \
  -file nifi_new.crt -storepass ваш_текущий_пароль -noprompt
```
4. Перезагрузка NiFi
```bash
# Останавливаем NiFi
/opt/nifi-2.5.0/bin/nifi.sh stop

# Запускаем NiFi
/opt/nifi-2.5.0/bin/nifi.sh start
```
Важные замечания:
Срок действия: Убедитесь, что новый сертификат имеет достаточный срок действия (параметр -validity в днях).

Пароли: Используйте те же пароли, что и в текущей конфигурации (можно найти в nifi.properties).
```bash
grep -E 'nifi\.security\.keystorePasswd=' /opt/nifi-2.5.0/conf/nifi.properties
grep -E 'nifi\.security\.truststorePasswd=' /opt/nifi-2.5.0/conf/nifi.properties
```
SAN-записи: Сохраните все Subject Alternative Names из старого сертификата.

Время простоя: Запланируйте обновление на период минимальной нагрузки.

Проверка: После перезагрузки проверьте:

```bash
openssl s_client -connect nifi_1_hostname:8443 -showcerts
```
Откат: В случае проблем можно восстановить из резервных копий:

bash
cp keystore.jks.bak keystore.jks
cp truststore.jks.bak truststore.jks
Этот процесс сохранит текущую конфигурацию NiFi, обновив только криптографические материалы. Все настройки в nifi.properties останутся без изменений.

