import manager


/* ----------- start ----------- */

import groovy.json.JsonSlurper
import java.text.SimpleDateFormat

def buildResult = manager.getResult()
def jobName = manager.getEnvVariable("JOB_NAME")
def gitBranch = manager.getEnvVariable("GIT_BRANCH")
def buildType = manager.getEnvVariable("Build").toLowerCase()
def buildUrl = manager.getEnvVariable("BUILD_URL")
def flutter = manager.getEnvVariable("Flutter")
def test = manager.getEnvVariable("Test")
def webhook = ""
String[] webhooksToDebug = ["3fc9a5f488d1de93bcd35b3d8b6a6c8fd16dc7cfdc99f8692f5ffc4554dfcc7c", "3fc9a5f488d1de93bcd35b3d8b6a6c8fd16dc7cfdc99f8692f5ffc4554dfcc7c"]
String[] webhooksToRelease = ["014c0f1e2b0b44c8854d755819692bb7835ebded35241638c44ec462c10e606a", "51e1a57ab205feee4ef6526f2cb4d796256e309c6d023820282f92c1f5128eda"]
String[] webhooks = [];
if (test) {
    webhooks = webhooksToDebug;
} else {
    webhooks = webhooksToRelease;
}
if (buildResult == "SUCCESS") {
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
    def response = connection.inputStream.text
    manager.listener.logger.println("· 获取上传Token: ${response}")

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
    def build = manager.getEnvVariable("Build").toLowerCase()
    def apkFilePath = ""
    def apkLastModified = 0
    def directoryPath = String.format('%s/%s/build/outputs/apk/%s', workspace, module, build)
    def directory = new File(directoryPath)
    directory.eachFile { file ->
        if (file.isFile() && file.name.endsWith('.apk')) {
            if (file.lastModified() > apkLastModified) {
                apkLastModified = file.lastModified();
                apkFilePath = file.absolutePath
            }
        }
    }
    def file = new File(apkFilePath)
    manager.listener.logger.println("· 上传App文件: ${file.path}")
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
    response = connection.inputStream.text

    def tryCount = 0
    def buildInfo
    while (tryCount < 10) {
        try {
            tryCount++;
            url = new URL(String.format("https://www.pgyer.com/apiv2/app/buildInfo?_api_key=%s&buildKey=%s", _api_key, buildKey))
            connection = url.openConnection()
            def inputStream = connection.inputStream
            response = inputStream.text
            manager.listener.logger.println("· 查询App发布状态: ${response}")
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
    webhook = "https://oapi.dingtalk.com/robot/send?access_token=" + webhooks[0];
    def apkDownloadUrl = String.format("https://www.pgyer.com/%s", buildInfo.buildShortcutUrl).replace("pgyer", "xcxwo")
    def apkQrCode = buildInfo.buildQRCodeURL.replace("pgyer", "xcxwo")
    def appVersion = buildInfo.buildVersion
    def appVersionNo = buildInfo.buildVersionNo
    manager.listener.logger.println("· 下载二维码: ${buildInfo.buildQRCodeURL}")
    dingding("金十数据", "应用名称：" + jobName + "（构建成功）\n\n构建分支：" + gitBranch + "，flutter/" + flutter + "\n\n构建版本：" + appVersion + "_" + appVersionNo + "_" + buildType + "\n\n构建时间：" + getNowTime() + "\n\n下载链接：[地址](" + apkDownloadUrl + ")" + "\n![](" + apkQrCode + ")", webhook)
} else if (buildResult == "ABORTED") {
    webhook = "https://oapi.dingtalk.com/robot/send?access_token=" + webhooks[1];
    dingding("金十数据", "应用名称：" + jobName + "（构建被终止）\n\n" + "\n\n构建时间：" + getNowTime() + "\n\n[查看详情](" + buildUrl + ")", webhook)
} else {
    webhook = "https://oapi.dingtalk.com/robot/send?access_token=" + webhooks[1];
    dingding("金十数据", "应用名称：" + jobName + "（构建失败）\n\n构建分支：" + gitBranch + "，flutter/" + flutter + "\n\n构建时间：" + getNowTime() + "\n\n[查看详情](" + buildUrl + ")", webhook)
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
    manager.listener.logger.println("· 钉钉文本: ${p_text.replace("\n", " | ")}")
    manager.listener.logger.println("· 钉钉推送结果: ${respText}")
}

def getNowTime() {
    def str = "";
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Calendar lastDate = Calendar.getInstance();
    str = sdf.format(lastDate.getTime());
    return str;
}





