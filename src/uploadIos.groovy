import manager


/* ----------- start ----------- */

import groovy.json.JsonSlurper
import java.text.SimpleDateFormat

def buildResult = manager.getResult()
def jobName = manager.getEnvVariable("JOB_NAME")
def gitBranch = manager.getEnvVariable("GitTag")
def buildUrl = manager.getEnvVariable("BUILD_URL")
def webhook = ""
if (buildResult == "SUCCESS") {
    def _api_key = "5e5e94fa4453d5e055739cf37cb280ee"
    def url = new URL("https://www.pgyer.com/apiv2/app/getCOSToken")
    def connection = url.openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    def formData = [
            "_api_key" : _api_key,
            "buildType": "ipa"
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
    def directoryPath = String.format('%s/ipa', workspace)
    def directory = new File(directoryPath)
    directory.eachFile { file ->
        manager.listener.logger.println("${file.absolutePath}")
        if (file.isFile() && file.name.endsWith('.ipa')) {
            apkFilePath = file.absolutePath
        }
    }
    def file = new File(apkFilePath)
    manager.listener.logger.println("ipa路径: ${file.path}")
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

    def tryCount = 0
    def buildInfo
    while (tryCount < 10) {
        try {
            tryCount++;
            url = new URL(String.format("https://www.pgyer.com/apiv2/app/buildInfo?_api_key=%s&buildKey=%s", _api_key, buildKey))
            connection = url.openConnection()
            def inputStream = connection.inputStream
            response = inputStream.text
            manager.listener.logger.println("Response: ${response}")
            jsonSlurper = new JsonSlurper()
            jsonObject = jsonSlurper.parseText(response)
            if (jsonObject.code == 1246 || jsonObject.code == 1247) {
                Thread.sleep(5 * 1000)
            } else if (jsonObject.code == 0) {
                buildInfo = jsonObject.data
                break
            } else {
                break
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    webhook = "https://oapi.dingtalk.com/robot/send?access_token=b3bc6265f248f18e088948e8734007487eda5966f0c57a059220519a284602b6"
    def apkDownloadUrl = String.format("https://www.pgyer.com/%s", buildInfo.buildShortcutUrl).replace("pgyer", "xcxwo")
    def apkQrCode = buildInfo.buildQRCodeURL.replace("pgyer", "xcxwo")
    def appVersion = buildInfo.buildVersion
    def appVersionNo = buildInfo.buildVersionNo
    manager.listener.logger.println("二维码: ${buildInfo.buildQRCodeURL}")
    dingding("金十数据", "应用名称：" + jobName + "（构建成功）\n\n构建分支：" + gitBranch + "\n\n构建版本：" + appVersion + "_" + appVersionNo  + "\n\n构建时间：" + getNowTime() + "\n\n下载链接：[地址](" + apkDownloadUrl + ")" + "\n\n安装密码：" + "123456" + "\n![](" + apkQrCode + ")", webhook)
} else if (buildResult == "ABORTED") {
    webhook = "https://oapi.dingtalk.com/robot/send?access_token=b3bc6265f248f18e088948e8734007487eda5966f0c57a059220519a284602b6"
    dingding("金十数据", "应用名称：" + jobName + "（构建被终止）\n\n" + "\n\n构建时间：" + getNowTime() + "\n\n[查看详情](" + buildUrl + ")", webhook)
} else {
    webhook = "https://oapi.dingtalk.com/robot/send?access_token=b3bc6265f248f18e088948e8734007487eda5966f0c57a059220519a284602b6"
    dingding("金十数据", "应用名称：" + jobName + "（构建失败）\n\n构建分支：" + gitBranch + "\n\n构建时间：" + getNowTime() + "\n\n[查看详情](" + buildUrl + ")", webhook)
}


def dingding(p_title, p_text, webhook) {
    def json = new groovy.json.JsonBuilder()
    json {
        msgtype "markdown"
        markdown {
            title p_title
            text p_text
        }
        at {
            atMobiles([])
            isAtAll false
        }
    }

    def connection = new URL(webhook).openConnection()
    connection.setRequestMethod('POST')
    connection.doOutput = true
    connection.setRequestProperty('Content-Type', 'application/json')

    def writer = new OutputStreamWriter(connection.outputStream)
    writer.write(json.toString());
    writer.flush()
    writer.close()
    connection.connect()

    def respText = connection.content.text
}

def getNowTime() {
    def str = "";
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Calendar lastDate = Calendar.getInstance();
    str = sdf.format(lastDate.getTime());
    return str;
}





