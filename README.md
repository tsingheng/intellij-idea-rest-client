# intellij-idea-rest-client

Intellij IDEA的restClient插件非常有用，但是遗憾的是不能在执行请求前执行脚本，实际上接口都是有签名校验的，所以请求前执行脚本的特性是非常必要的。  
这个特性也有人向IDEA团队提出过，[Allow-scripts-to-run-before-the-request-is-executed](https://youtrack.jetbrains.com/issue/IDEA-202272/REST-Client-Allow-scripts-to-run-before-the-request-is-executed)，遗憾的是好几年过去了，依然没有看到这个特性。  
又没找到其他可以本地保存数据又能团队共享的接口调试工具，所以先自己改了restClient插件支持该特性，也没有任何计划。  

## 使用说明
1. 下载intellij-dea-rest-client_yyyy.a.b.jar，yyyy.a.b为你使用的IDEA版本
2. 删除%IDEA_HOME%\plugins\restClient\lib目录下的restClient.jar，并将上一步下载的jar包放到这个目录
3. 重启IDEA

## 使用范例

[intellij-idea-rest-client-demo](https://github.com/tsingheng/intellij-idea-rest-client-demo)

