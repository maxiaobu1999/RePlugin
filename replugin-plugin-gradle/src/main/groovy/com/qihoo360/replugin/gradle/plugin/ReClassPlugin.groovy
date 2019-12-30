/*
 * Copyright (C) 2005-2017 Qihoo 360 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.qihoo360.replugin.gradle.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.qihoo360.replugin.gradle.plugin.compat.VariantCompat
import com.qihoo360.replugin.gradle.plugin.debugger.PluginDebugger
import com.qihoo360.replugin.gradle.plugin.inner.CommonData
import com.qihoo360.replugin.gradle.plugin.inner.ReClassTransform
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 插件动态编译方案入口
 * @author RePlugin Team
 */
public class ReClassPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        println "${AppConstant.TAG} Welcome to replugin world ! "

        // 保存ReClassConfig类的常量配置信息
        project.extensions.create(AppConstant.USER_CONFIG, ReClassConfig)

        // 判断project中是否含有AppPlugin类型插件
        def isApp = project.plugins.hasPlugin(AppPlugin)
        if (isApp) {

            def config = project.extensions.getByName(AppConstant.USER_CONFIG)

            // 获取project中的AppExtension类型extension，
            def android = project.extensions.getByType(AppExtension)

            def forceStopHostAppTask = null
            def startHostAppTask = null
            def restartHostAppTask = null

            android.applicationVariants.all { variant ->
                // 初始化PluginDebugger类实例，主要配置了最终生成的插件应用的文件路径，以及adb文件的路径，是为了后续基于adb命令做push apk到SD卡上做准备。
                PluginDebugger pluginDebugger = new PluginDebugger(project, config, variant)

                def variantData = variant.variantData
                def scope = variantData.scope

                // 获取assemble task(即打包apk的task)，后续的task需要依赖此task，比如安装插件的task，
                // 肯定要等到assemble task打包生成apk后，才能去执行。
                def assembleTask = VariantCompat.getAssembleTask(variant)

                // 【rpInstallPluginDebug】安装插件
                def installPluginTaskName = scope.getTaskName(AppConstant.TASK_INSTALL_PLUGIN, "")
                def installPluginTask = project.task(installPluginTaskName)
                installPluginTask.doLast {
                    pluginDebugger.startHostApp() // 启动宿主
                    pluginDebugger.uninstall() // 卸载插件
                    pluginDebugger.forceStopHostApp()// 强制停止宿主
                    pluginDebugger.startHostApp()// 启动宿主
                    pluginDebugger.install() // 安装插件
                }
                installPluginTask.group = AppConstant.TASKS_GROUP

                // 【rpUninstallPluginDebug】卸载插件
                def uninstallPluginTaskName = scope.getTaskName(AppConstant.TASK_UNINSTALL_PLUGIN, "")
                def uninstallPluginTask = project.task(uninstallPluginTaskName)
                uninstallPluginTask.doLast {
                    //generate json
                    pluginDebugger.uninstall()
                }
                uninstallPluginTask.group = AppConstant.TASKS_GROUP


                if (null == forceStopHostAppTask) {
                    forceStopHostAppTask = project.task(AppConstant.TASK_FORCE_STOP_HOST_APP)
                    forceStopHostAppTask.doLast {
                        //generate json
                        pluginDebugger.forceStopHostApp()
                    }
                    forceStopHostAppTask.group = AppConstant.TASKS_GROUP
                }

                if (null == startHostAppTask) {
                    startHostAppTask = project.task(AppConstant.TASK_START_HOST_APP)
                    startHostAppTask.doLast {
                        //generate json
                        pluginDebugger.startHostApp()
                    }
                    startHostAppTask.group = AppConstant.TASKS_GROUP
                }

                if (null == restartHostAppTask) {
                    restartHostAppTask = project.task(AppConstant.TASK_RESTART_HOST_APP)
                    restartHostAppTask.doLast {
                        //generate json
                        pluginDebugger.startHostApp()
                    }
                    restartHostAppTask.group = AppConstant.TASKS_GROUP
                    restartHostAppTask.dependsOn(forceStopHostAppTask)
                }


                if (assembleTask) {
                    installPluginTask.dependsOn assembleTask
                }

                // 【rpRunPluginDebug】
                def runPluginTaskName = scope.getTaskName(AppConstant.TASK_RUN_PLUGIN, "")
//                println "norman+++ runPluginTaskName = ${runPluginTaskName} "
                def runPluginTask = project.task(runPluginTaskName)
                runPluginTask.doLast {
                    pluginDebugger.run()
                }
                runPluginTask.group = AppConstant.TASKS_GROUP

                def installAndRunPluginTaskName = scope.getTaskName(AppConstant.TASK_INSTALL_AND_RUN_PLUGIN, "")
                def installAndRunPluginTask = project.task(installAndRunPluginTaskName)
                installAndRunPluginTask.doLast {
                    pluginDebugger.run()
                }
                installAndRunPluginTask.group = AppConstant.TASKS_GROUP
                installAndRunPluginTask.dependsOn installPluginTask
            }

            // null
            CommonData.appPackage = android.defaultConfig.applicationId

//            println ">>> APP_PACKAGE " + CommonData.appPackage

            // 动态编译方案，修改activity基类等
            def transform = new ReClassTransform(project)
            // 将 transform 注册到 android
            android.registerTransform(transform)
        }
    }
}

class ReClassConfig {

    /** 编译的 App Module 的名称 */
    def appModule = ':app'

    /** 用户声明要忽略的注入器 */
    def ignoredInjectors = []

    /** 执行 LoaderActivity 替换时，用户声明不需要替换的 Activity */
    def ignoredActivities = []

    /** 自定义的注入器 */
    def customInjectors = []

    /** 插件名字,默认null */
    def pluginName = null

    /** 手机存储目录,默认"/sdcard/" */
    def phoneStorageDir = "/sdcard/"

    /** 宿主包名,默认null */
    def hostApplicationId = null

    /** 宿主launcherActivity,默认null */
    def hostAppLauncherActivity = null
}
