class manager {
    class listener {
        class logger {
            static void println(String text) {
                System.properties.println(text)
            }
        }
    }

    static String getEnvVariable(String s) {
        if (s.equals("WORKSPACE")) {
            return "/Volumes/External/IdeaProjects/Groovy/JenkinsUploadPGYER/src"
        } else if (s.equals("Module")) {
            return "appnormal"
        } else if (s.equals("Build")) {
            return "Debug"
        } else if (s.equals("JOB_NAME")) {
            return "金十数据"
        } else if (s.equals("GIT_BRANCH")) {
            return "origin/dev_6.3.0"
        } else if (s.equals("Build")) {
            return "Debug"
        } else if (s.equals("BUILD_URL")) {
            return "https://www.baidu.com"
        }
        return ""
    }

    static String getResult() {
        return "SUCCESS"
    }
}