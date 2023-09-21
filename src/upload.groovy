import manager


/* ----------- start ----------- */

import groovy.json.JsonSlurper

def _api_key = "5e5e94fa4453d5e055739cf37cb280ee"
def url = new URL("https://www.pgyer.com/apiv2/app/getCOSToken")
def connection = url.openConnection()
connection.setRequestMethod("POST")
connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
def formData = [
        "_api_key" : _api_key,
        "buildType": "apk"
]
def encodedFormData = formData.collect { key, value ->
    "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
}.join("&")
connection.doOutput = true
connection.outputStream.withWriter { writer ->
    writer.write(encodedFormData)
}
def responseCode = connection.responseCode
manager.listener.logger.println("Response Code: ${responseCode}")
def response = connection.inputStream.text
manager.listener.logger.println("Response: ${response}")

def jsonSlurper = new JsonSlurper()
def jsonObject = jsonSlurper.parseText(response)
def buildKey = jsonObject.data.params.key
url = new URL(jsonObject.data.endpoint)
connection = url.openConnection() as HttpURLConnection
connection.setRequestMethod("POST")
connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=----Boundary")
def boundary = "----Boundary"
def crlf = "\r\n"
formData = [
        "signature"           : jsonObject.data.params.signature,
        "x-cos-security-token": jsonObject.data.params.'x-cos-security-token',
        "key"                 : buildKey
]
def workspace = manager.getEnvVariable("WORKSPACE")
def module = manager.getEnvVariable("Module")
def apkFilePath = ""
def directoryPath = String.format('%s/%s/build/outputs/apk', workspace, module)
def directory = new File(directoryPath)
directory.eachFile { file ->
    if (file.isFile() && file.name.endsWith('.txt')) {
        apkFilePath = file.absolutePath
    }
}
def file = new File(apkFilePath)
manager.listener.logger.println("file: ${file.exists() && file.isFile()}, path: ${file.path}")
connection.doOutput = true
DataOutputStream outputStream = new DataOutputStream(connection.outputStream)
formData.each { key, value ->
    outputStream.writeBytes("--${boundary}${crlf}")
    outputStream.writeBytes("Content-Disposition: form-data; name=\"${key}\"${crlf}")
    outputStream.writeBytes(crlf)
    outputStream.writeBytes("${value}${crlf}")
}
if (file.exists() && file.isFile()) {
    outputStream.writeBytes("--${boundary}${crlf}")
    outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"${crlf}")
    outputStream.writeBytes("Content-Type: application/octet-stream${crlf}")
    outputStream.writeBytes(crlf)
    FileInputStream fileInputStream = new FileInputStream(file)
    byte[] buffer = new byte[4096]
    int bytesRead
    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead)
    }
    fileInputStream.close()
    outputStream.writeBytes(crlf)
}
outputStream.writeBytes("--${boundary}--${crlf}")
outputStream.flush()
outputStream.close()
responseCode = connection.responseCode
manager.listener.logger.println("Response Code: ${responseCode}")
response = connection.inputStream.text
manager.listener.logger.println("Response: ${response}")

Thread.sleep(5 * 1000)

url = new URL(String.format("https://www.pgyer.com/apiv2/app/buildInfo?_api_key=%s&buildKey=%s", _api_key, buildKey))
connection = url.openConnection()
def inputStream = connection.inputStream
response = inputStream.text
manager.listener.logger.println("Response: ${response}")





