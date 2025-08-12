# ИНСТРУКЦИЯ ДЛЯ УСТАНОВКИ И НАСТРОЙКИ СПО APACHE NiFi (версия 2.5.0)

### 1.1 Подготовка утилит

Установите необходимые утилиты для загрузки, распаковки и редактирования конфигураций:

~~~bash
# Обновление системы
sudo zypper refresh
sudo zypper update -y

# Установка базовых утилит
sudo zypper install -y wget unzip tar gzip nano curl procps
~~~

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

> ✅ **Рекомендация:**  
> Создайте отдельного пользователя `nifi`:
> ```bash
> sudo useradd -m -s /bin/bash nifi
> ```

> 💡 **Важно:**  
> Лимиты применяются при новом входе в систему. Перезайдите под пользователем `nifi`, чтобы изменения вступили.

#### 1.2.2 Настройка параметров ядра

Откройте файл конфигурации ядра:

```bash
sudo nano /etc/sysctl.conf
```

Добавьте или измените следующие параметры:

```conf
# Расширенный диапазон локальных портов (для множества исходящих соединений)
net.ipv4.ip_local_port_range = 10000 65000

# Отключение свопинга (рекомендуется при достаточном объёме RAM)
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
> Параметр `vm.swappiness = 0` означает, что система будет избегать использования swap.  
> Устанавливайте его, если у сервера **достаточно RAM (16 ГБ и более)**.  
> При малом объёме RAM — используйте `vm.swappiness = 1`.

#### 1.2.3 Проверка настроек

После входа под пользователем `nifi` проверьте лимиты:

```bash
ulimit -n    # должно быть 50000
ulimit -u    # должно быть 10000
```

Проверка параметров ядра:

```bash
sysctl net.ipv4.ip_local_port_range
sysctl vm.swappiness
```

### 1.3 Установка OpenJDK 21

Apache NiFi 2.5.0 требует **Java 21**. В системе доступен пакет `java-21-openjdk` из репозитория.

#### 1. Обновите список пакетов

```bash
sudo zypper refresh
```

#### 2. Установите OpenJDK 21

Рекомендуется установить:
- `java-21-openjdk-headless` — JVM без GUI
- `java-21-openjdk-devel` — инструменты разработки (javac, jstat и др.)

```bash
sudo zypper install java-21-openjdk-devel java-21-openjdk-headless
```

> 💡 Пакеты предоставлены в рамках **АТ (Астра Технологии)** — версия `21.0.3.0-at154.1.1`.

#### 3. Проверьте установку

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

#### 4. Настройка JAVA_HOME

Найдите путь к JDK:

```bash
readlink -f $(which java)
# Пример вывода: /usr/lib64/jvm/java-21-openjdk/bin/java
```

Используйте родительский каталог как `JAVA_HOME`.

Создайте скрипт окружения:

```bash
sudo nano /etc/profile.d/java.sh
```

Добавьте:

```bash
export JAVA_HOME=/usr/lib64/jvm/java-21-openjdk
export PATH=$JAVA_HOME/bin:$PATH
```

Примените:

```bash
source /etc/profile.d/java.sh
```

> ✅ Проверьте:
> ```bash
> echo $JAVA_HOME
> ```
