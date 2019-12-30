# RePlugin Plugin Gradle

RePlugin Plugin Gradle是一个Gradle插件，由 **插件** 负责引入。

该Gradle插件主要负责在插件的编译期中做一些事情，是“动态编译方案”的主要实现者。此外，开发者可通过修改其属性而做一些自定义的操作。

大致包括：

* 动态修改主要调用代码，改为调用RePlugin Plugin Gradle（如Activity的继承、Provider的重定向等）

开发者需要依赖此Gradle插件，以实现对RePlugin的接入。请参见WiKi以了解接入方法。

有关RePlugin Host Gradle的详细描述，请访问我们的WiKi，以了解更多的内容。
（文档正在完善，请耐心等待）

## me
1、定义一系列task
2、动态编译方案实现

Javassit 是一个处理Java字节码的类库。
CtMethod：是一个class文件中的方法的抽象表示。一个CtMethod对象表示一个方法。（Javassit 库API）
CtClass：是一个class文件的抽象表示。一个CtClass（compile-time class)对象可以用来处理一个class文件。（Javassit 库API）
ClassPool：是一个CtClass对象的容器类。（Javassit 库API）
.class文件：.class文件是一种存储Java字节码的二进制文件，里面包含一个Java类或者接口。
## 命令
 【查看所有task】./gradlew :subprojects:videocapture:task

##task
【rpInstallPluginDebug】安装插件
【rpUninstallPluginDebug】卸载插件


rpInstallAndRunPluginDebug
rpInstallAndRunPluginRelease
rpInstallPluginDebug
rpInstallPluginRelease
rpRestartHostApp
rpRunPluginDebug
rpRunPluginRelease
rpStartHostApp
rpUninstallPluginDebug
rpUninstallPluginRelease

## 什么是 Transform？
Transform 是 Android Gradle API ，允许第三方插件在class文件转为dex文件前操作编译完成的class文件，
这个API的引入是为了简化class文件的自定义操作而无需对Task进行处理。在做代码插桩时，
本质上是在merge{ProductFlavor}{BuildType}Assets Task 之后，
transformClassesWithDexFor{ProductFlavor}{BuildType} Transform 之前,
插入一个transformClassesWith{YourTransformName}For{ProductFlavor}{BuildType} Transform，此Transform中完成对class文件的自定义操作（包括修改父类继承，方法中的super方法调用，方法参数替换等等，这个class交给你，理论上是可以改到怀疑人生）。
###怎么用
1、继承Transform
2、注册
```
   // 动态编译方案，修改activity基类等
   def transform = new ReClassTransform(project)
   // 将 transform 注册到 android
   android.registerTransform(transform)
```