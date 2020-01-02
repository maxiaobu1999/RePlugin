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

package com.qihoo360.replugin.gradle.plugin.debugger

import com.qihoo360.replugin.gradle.plugin.compat.ScopeCompat
import com.qihoo360.replugin.gradle.plugin.AppConstant
import com.qihoo360.replugin.gradle.plugin.util.CmdUtil
import org.gradle.api.Project

/**
 * 用于插件调试的gradle task实现
 * <p>
 *  初始化PluginDebugger类实例，主要配置了最终生成的插件应用的文件路径，以及adb文件的路径，是为了后续基于adb命令做push apk到SD卡上做准备。
 *  <p>
 *      基于adb shell + am 命令，实现 发送广播，push apk 等功能。
 * @author RePlugin Team
 */
class PluginDebugger {

    def project
    def config
    def variant
    /** 最终生成的插件应用的文件路径
     * apkFile = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/outputs/apk/videocapture-debug.apk
     */
    File apkFile
    /** adb文件的路径
     * adbFile = /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb
     */
    File adbFile

    public PluginDebugger(Project project, def config, def variant) {
        this.project = project
        this.config = config
        this.variant = variant
        def variantData = this.variant.variantData
        def scope = variantData.scope
        def globalScope = scope.globalScope
        def variantConfiguration = variantData.variantConfiguration
        // archivesBaseName = videocapture
        String archivesBaseName = globalScope.getArchivesBaseName();
        // apkBaseName = videocapture-debug
        String apkBaseName = archivesBaseName + "-" + variantConfiguration.getBaseName()

        // apkDir = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/outputs/apk
        File apkDir = new File(globalScope.getBuildDir(), "outputs" + File.separator + "apk")

        String unsigned = (variantConfiguration.getSigningConfig() == null
                ? "-unsigned.apk"
                : ".apk")
        // apkName = videocapture-debug.apk
        String apkName = apkBaseName + unsigned

        // apkFile = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/outputs/apk/videocapture-debug.apk
        apkFile = new File(apkDir, apkName)

        if (!apkFile.exists() || apkFile.length() == 0) {
            // 会走，适配studio3。0
            // apkFile = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/outputs/apk/debug/videocapture-debug.apk
            apkFile = new File(apkDir, variantConfiguration.getBaseName() + File.separator + apkName)
        }

        //  adbFile = /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb
        adbFile = ScopeCompat.getAdbExecutable(globalScope)
//        println "norman+++ adbFile = ${adbFile} "

    }

    /**
     * 安装插件
     * @return 是否命令执行成功
     */
    public boolean install() {

        if (isConfigNull()) {
            return false
        }

        //推送apk文件到手机
        //  pushCmd = /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb push /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/outputs/apk/debug/videocapture-debug.apk /sdcard/
        String pushCmd = "${adbFile.absolutePath} push ${apkFile.absolutePath} ${config.phoneStorageDir}"
        println "norman+++ pushCmd = ${pushCmd} "
        if (0 != CmdUtil.syncExecute(pushCmd)) {
            return false
        }

        //此处是在安卓机上的目录，直接"/"路径
        // apkPath = /sdcard/
        String apkPath = "${config.phoneStorageDir}"
        if (!apkPath.endsWith("/")) {
            //容错处理
            apkPath += "/"
        }
        // apkPath = /sdcard/videocapture-debug.apk
        apkPath += "${apkFile.name}"

        //发送安装广播
        //installBrCmd=  /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb shell am broadcast -a com.norman.malong.replugin.install -e path /sdcard/videocapture-debug.apk -e immediately true
        String installBrCmd = "${adbFile.absolutePath} shell am broadcast -a ${config.hostApplicationId}.replugin.install -e path ${apkPath} -e immediately true "
        println "norman+++ installBrCmd = ${installBrCmd} "
        if (0 != CmdUtil.syncExecute(installBrCmd)) {
            return false
        }

        return true
    }

    /**
     * 卸载插件
     * @return 是否命令执行成功
     */
    public boolean uninstall() {

        if (isConfigNull()) {
            return false
        }

        // /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb shell am broadcast -a com.norman.malong.replugin.uninstall -e plugin videocapture
        String cmd = "${adbFile.absolutePath} shell am broadcast -a ${config.hostApplicationId}.replugin.uninstall -e plugin ${config.pluginName}"
        if (0 != CmdUtil.syncExecute(cmd)) {
            return false
        }
        return true
    }

    /**
     * 强制停止宿主app
     * @return 是否命令执行成功
     */
    public boolean forceStopHostApp() {

        if (isConfigNull()) {
            return false
        }

        // cmd:forceStopHostApp = /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb shell am force-stop com.norman.malong
        String cmd = "${adbFile.absolutePath} shell am force-stop ${config.hostApplicationId}"
        if (0 != CmdUtil.syncExecute(cmd)) {
            return false
        }
        return true
    }

    /**
     * 启动宿主app
     * <p>
     *     基于adb shell + am 命令，实现 发送广播，push apk 等功能。
     * @return 是否命令执行成功
     */
    public boolean startHostApp() {

        if (isConfigNull()) {
            return false
        }

        //  cmd = /Users/v_maqinglong/Library/Android/sdk/platform-tools/adb shell am start -n "com.norman.malong/com.norman.malong.SplashActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
        String cmd = "${adbFile.absolutePath} shell am start -n \"${config.hostApplicationId}/${config.hostAppLauncherActivity}\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"

        if (0 != CmdUtil.syncExecute(cmd)) {
            return false
        }
        return true
    }

    /**
     * 运行插件
     * @return 是否命令执行成功
     */
    public boolean run() {

        if (isConfigNull()) {
            return false
        }

        String installBrCmd = "${adbFile.absolutePath} shell am broadcast -a ${config.hostApplicationId}.replugin.start_activity -e plugin ${config.pluginName}"
        println "norman+++ installBrCmd = ${installBrCmd} "
        if (0 != CmdUtil.syncExecute(installBrCmd)) {
            return false
        }
        return true
    }

    /**
     * 检查用户配置项是否为空
     * @param config
     * @return
     */
    private boolean isConfigNull() {

        //检查adb环境
        if (null == adbFile || !adbFile.exists()) {
            System.err.println "${AppConstant.TAG} Could not find the adb file !!!"
            return true
        }

        if (null == config) {
            System.err.println "${AppConstant.TAG} the config object can not be null!!!"
            System.err.println "${AppConstant.CONFIG_EXAMPLE}"
            return true
        }

        if (null == config.hostApplicationId) {
            System.err.println "${AppConstant.TAG} the config hostApplicationId can not be null!!!"
            System.err.println "${AppConstant.CONFIG_EXAMPLE}"
            return true
        }

        if (null == config.hostAppLauncherActivity) {
            System.err.println "${AppConstant.TAG} the config hostAppLauncherActivity can not be null!!!"
            System.err.println "${AppConstant.CONFIG_EXAMPLE}"
            return true
        }

        return false
    }


}
