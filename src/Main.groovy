import groovy.json.JsonBuilder

import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat

//def currentPath = System.getProperty("user.dir")
//println "当前路径：$currentPath"

//def reTryCount = 0;
//while (reTryCount < 10) {
//    reTryCount++
//    Thread.sleep(1 * 1000)
//    try {
//        println("" + reTryCount)
//    } catch (Exception e) {
//        e.printStackTrace()
//    }
//}

//def directoryPath = String.format('%s/%s/build/outputs/apk/%s', workspace, module, build)
//def directory = new File(directoryPath)
//directory.eachFile { file ->
//    manager.listener.logger.println("${file.absolutePath}")
//    if (file.isFile() && file.name.endsWith('.apk')) {
//        apkFilePath = file.absolutePath
//    }
//}

def buildResult = "SUCCESS"
def jobName = "金十数据"
def gitBranch = "origin/dev_6.3.0"
def buildType = "6.3.0_780_Debug"
def buildUrl = ""

if (buildResult == "SUCCESS") {
    def apkDownloadUrl = String.format("https://www.pgyer.com/%s", "ZbaW")
    def apkQrCode = "https://www.xcxwo.com/app/qrcodeHistory/a7da870150ff8e9f516203e2d9279eeced3a75399d97ec3f586bd816c9af6f9b"
    def appVersion = "6.3.0"
    def appVersionNo = "780"
    dingding("金十数据", "应用名称：" + jobName + "（构建成功）\n\n构建分支：" + gitBranch + "\n\n构建版本：" + appVersion + "_" + appVersionNo + "_" + buildType + "\n\n构建时间：" + getNowTime()  + "\n\n下载链接：[地址](" + apkDownloadUrl + ")" + "\n\n安装密码：" + "123456" + "\n![](" + apkQrCode + ")")
} else if (buildResult == "ABORTED") {
    dingding("金十数据", "应用名称：" + jobName + "（构建被终止）\n\n" + "\n\n构建时间：" + getNowTime() + "\n\n[查看详情](" + buildUrl + ")")
} else {
    dingding("金十数据", "应用名称：" + jobName + "（构建失败）\n\n构建分支：" + gitBranch + "\n\n构建时间：" + getNowTime() + "\n\n[查看详情](" + buildUrl + ")")
}

//发送钉钉消息
def dingding(p_title, p_text) {
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

    def connection = new URL("https://oapi.dingtalk.com/robot/send?access_token=b3bc6265f248f18e088948e8734007487eda5966f0c57a059220519a284602b6").openConnection()
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
//获取当前时间
def getNowTime() {
    def str = "";
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Calendar lastDate = Calendar.getInstance();
    str = sdf.format(lastDate.getTime());
    return str;
}

