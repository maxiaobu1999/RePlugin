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

package com.qihoo360.replugin.gradle.plugin.injector.loaderactivity

import com.qihoo360.replugin.gradle.plugin.injector.BaseInjector
import com.qihoo360.replugin.gradle.plugin.inner.CommonData
import com.qihoo360.replugin.gradle.plugin.manifest.ManifestAPI
import javassist.CannotCompileException
import javassist.ClassPool
import javassist.CtClass
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

/**
 * Activity代码注入器
 * 替换插件中的Activity的继承相关代码 为 replugin-plugin-library 中的XXPluginActivity父类
 * LOADER_ACTIVITY_CHECK_INJECTOR
 *
 * 修改普通的 Activity 为 PluginActivity
 *
 * @author RePlugin Team
 */
public class LoaderActivityInjector extends BaseInjector {

    def private static LOADER_PROP_FILE = 'loader_activities.properties'

    /* LoaderActivity 替换规则 */
    def private static loaderActivityRules = [
            'android.app.Activity'                    : 'com.qihoo360.replugin.loader.a.PluginActivity',
            'android.app.TabActivity'                 : 'com.qihoo360.replugin.loader.a.PluginTabActivity',
            'android.app.ListActivity'                : 'com.qihoo360.replugin.loader.a.PluginListActivity',
            'android.app.ActivityGroup'               : 'com.qihoo360.replugin.loader.a.PluginActivityGroup',
            'androidx.fragment.app.FragmentActivity'  : 'com.qihoo360.replugin.loader.a.PluginFragmentActivity',
            'androidx.appcompat.app.AppCompatActivity': 'com.qihoo360.replugin.loader.a.PluginAppCompatActivity',
            'android.preference.PreferenceActivity'   : 'com.qihoo360.replugin.loader.a.PluginPreferenceActivity',
            'android.app.ExpandableListActivity'      : 'com.qihoo360.replugin.loader.a.PluginExpandableListActivity'
    ]

    @Override
    def injectClass(ClassPool pool, String dir, Map config) {
        // pool = [class path: /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/intermediates/javac/release/classes:/Users/v_maqinglong/Documents/AndroidProject/MaLong/common/lib_runtime/build/intermediates/runtime_library_classes/release/classes:/Users/v_maqinglong/Documents/AndroidProject/MaLong/common/lib_util/build/intermediates/runtime_library_cl...
        // Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/intermediates/exploded-aar/4c476879a887f50ba2146f03ff09eb5d0a37e6fd/class
        // dir = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/intermediates/exploded-aar/000b85d5f8d1c1c3582c1d516d6f04a2ab524fbc/class

        init()

        /* 遍历程序中声明的所有 Activity */
        //每次都new一下，否则多个variant一起构建时只会获取到首个manifest
        new ManifestAPI().getActivities(project, variantDir).each {
            // it = com.norman.videocapture.MainActivity
            //  dir = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/buildintermediates/javac/debug/classes
            // 处理没有被忽略的 Activity
            if (!(it in CommonData.ignoredActivities)) {
                handleActivity(pool, it, dir)
            }
        }
    }

    /**
     * 处理 Activity
     *
     * @param pool
     * @param activity Activity 名称 com.norman.videocapture.MainActivity
     * @param classesDir class 文件目录 /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/buildintermediates/javac/debug/classes
     */
    private def handleActivity(ClassPool pool, String activity, String classesDir) {
        // com.norman.videocapture.VideoCaptureActivity
        // /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/intermediatesjavac/debug/classes/com/norman/videocapture/VideoCaptureActivity.class
        def clsFilePath = classesDir + File.separatorChar + activity.replaceAll('\\.', '/') + '.class'

        if (!new File(clsFilePath).exists()) {
            return
        }

//        println ">>> Handle $activity"

        def stream, ctCls
        try {
            stream = new FileInputStream(clsFilePath)
            // 从文件流中加载.class文件，创建一个CtClass实例，这个实例表示.class文件
            // 对应的类或接口。通过CtClass可以很方便的对.class文件进行自定义操作，比如添加方法，
            // 改方法参数，添加类成员，改继承关系等。
            ctCls = pool.makeClass(stream)
/*
             // 打印当前 Activity 的所有父类
            CtClass tmpSuper = ctCls.superclass
            while (tmpSuper != null) {
                println(tmpSuper.name)
                tmpSuper = tmpSuper.superclass
            }
*/
            // ctCls 之前的父类
            // [public class androidx.fragment.app.FragmentActivity extends anroidx.activity.ComponentActivity
            def originSuperCls = ctCls.superclass

            /* 从当前 Activity 往上回溯，直到找到需要替换的 Activity */
            def superCls = originSuperCls
            while (superCls != null && !(superCls.name in loaderActivityRules.keySet())) {
                // println ">>> 向上查找 $superCls.name"
                ctCls = superCls
                superCls = ctCls.superclass
            }

            // 如果 ctCls 已经是 LoaderActivity，则不修改
            if (ctCls.name in loaderActivityRules.values()) {
                // println "    跳过 ${ctCls.getName()}"
                return
            }

            /* 找到需要替换的 Activity, 修改 Activity 的父类为 LoaderActivity */
            if (superCls != null) {
                // ctCls.getName() = com.norman.videocapture.VideoCaptureActivity
                // targetSuperClsName = com.qihoo360.replugin.loader.a.PluginFragmentActivity
                def targetSuperClsName = loaderActivityRules.get(superCls.name)
                CtClass targetSuperCls = pool.get(targetSuperClsName)

                // 如果class被冻结，则通过defrost()解冻class，以便class重新允许被修改。
                // 注：当CtClass 调用writeFile()、toClass()、toBytecode() 这些方法的时候，Javassist会冻结CtClass Object，将不允许对CtClass object进行修改。
                if (ctCls.isFrozen()) {
                    ctCls.defrost()
                }
                // 根据初始化中设置的Activity替换规则，修改 此Activity类 的父类为 对应的插件库中的父类。例：
                //public class MainActivity extends Activity {修改为public class MainActivity extends PluginActivity {
                ctCls.setSuperclass(targetSuperCls)

                // 修改声明的父类后，还需要方法中所有的 super 调用。
                ctCls.getDeclaredMethods().each { outerMethod ->
                    outerMethod.instrument(new ExprEditor() {
                        @Override
                        void edit(MethodCall call) throws CannotCompileException {
                            if (call.isSuper()) {
                                //  call.getMethodName() = onCreate
                                if (call.getMethod().getReturnType().getName() == 'void') {
                                    call.replace('{super.' + call.getMethodName() + '($$);}')
                                } else {
                                    call.replace('{$_ = super.' + call.getMethodName() + '($$);}')
                                }
                            }
                        }
                    })
                }
                //  CommonData.getClassPath(ctCls.name) = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapure/build/intermediates/javac/debug/classes
                ctCls.writeFile(CommonData.getClassPath(ctCls.name))
            }

        } catch (Throwable t) {
            println "    [Warning]LoaderActivityInjector： --> ${t.toString()}"
        } finally {
            // 最后调用detach()方法，把CtClass object 从ClassPool中移除，避免当加载过多的CtClass object的时候，
            // 会造成OutOfMemory的异常。因为ClassPool是一个CtClass objects的装载容器。加载CtClass object后，默认是不释放的。
            if (ctCls != null) {
                ctCls.detach()
            }
            if (stream != null) {
                stream.close()
            }
        }
    }

    // 不会执行写死了
    def private init() {
        /* 延迟初始化 loaderActivityRules */
        // todo 从配置中读取，而不是写死在代码中
        if (loaderActivityRules == null) {
            def buildSrcPath = project.project(':buildsrc').projectDir.absolutePath
            println "norman+++ buildSrcPath = ${buildSrcPath} "
            def loaderConfigPath = String.join(File.separator, buildSrcPath, 'res', LOADER_PROP_FILE)
            println "norman+++ loaderConfigPath = ${loaderConfigPath} "

            loaderActivityRules = new Properties()
            new File(loaderConfigPath).withInputStream {
                loaderActivityRules.load(it)
            }

//            println '\n>>> Activity Rules：'
            loaderActivityRules.each {
                // it = /Users/v_maqinglong/Documents/AndroidProject/MaLong/subprojects/videocapture/build/intermediates/exploded-aar/a2f80ac814910200306870f5a6dfbb0807926f9c/class
                println "norman+++ it2 = ${it} "
            }
            println()
        }
    }
}
