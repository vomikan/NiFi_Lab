@GrabResolver(name='central', root='https://repo1.maven.org/maven2/')
@Grab(group='org.kordamp.json', module='json-lib-core', version='3.0.2')
@Grab(group='net.sf.ezmorph', module='ezmorph', version='1.0.6')

import org.apache.nifi.processor.io.StreamCallback
import java.nio.charset.StandardCharsets
import org.apache.commons.io.IOUtils
import org.kordamp.json.xml.XMLSerializer  // класс из json‑lib

// Получаем FlowFile
def flowFile = session.get()
if (!flowFile) return

flowFile = session.write(flowFile, { inputStream, outputStream ->
    // Читаем XML из FlowFile
    def xmlContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8)
    
    // Создаём экземпляр XMLSerializer (json‑lib)
    def serializer = new XMLSerializer()
    
    // Преобразуем XML в JSON (результат – JSONObject или JSONArray)
    def jsonObj = serializer.read(xmlContent)
    
    // Преобразуем объект в строку (можно форматировать вывод, например, через toString(4))
    def jsonString = jsonObj.toString(4)
    
    // Записываем JSON в выходной поток
    outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8))
} as StreamCallback)

// Передаём FlowFile в REL_SUCCESS
session.transfer(flowFile, REL_SUCCESS)
