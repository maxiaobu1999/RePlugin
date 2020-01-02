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

package com.qihoo360.replugin.gradle.plugin.injector.localbroadcast

import com.qihoo360.replugin.gradle.plugin.injector.BaseInjector
import com.qihoo360.replugin.gradle.plugin.inner.Util
import javassist.ClassPool

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 *  广播代码注入器
 *  替换插件中的LocalBroadcastManager调用代码 为 插件库的调用代码
 * @author RePlugin Team
 */
public class LocalBroadcastInjector extends BaseInjector {

    // 表达式编辑器
    def editor

    /** 遍历class目录并访问到文件时，执行以下这方法。 */
    @Override
    def injectClass(ClassPool pool, String dir, Map config) {

        // 不处理非 build 目录下的类
/*
        if (!dir.contains('build' + File.separator + 'intermediates')) {
            println "跳过$dir"
            return
        }
*/

        if (editor == null) {
            editor = new LocalBroadcastExprEditor()
        }

//        Util.newSection()
//        println dir

        Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                String filePath = file.toString()
                editor.filePath = filePath

                def stream, ctCls
                try {
                    // 不处理 LocalBroadcastManager.class
                    if (filePath.contains('androidx/localbroadcastmanager/content/LocalBroadcastManager')) {
                        println "Ignore ${filePath}"
                        return super.visitFile(file, attrs)
                    }

                    stream = new FileInputStream(filePath)
                    // 创建当前类文件的CtClass实例。
                    ctCls = pool.makeClass(stream)

                    // 如果CtClass实例被冻结，则执行解冻操作。
                    if (ctCls.isFrozen()) {
                        ctCls.defrost()
                    }

                    // 遍历全部方法，并执行instrument方法，逐个扫描每个方法体内每一行代码，
                    // 并交由 LocalBroadcastExprEditor 的edit()处理对方法体代码的修改。
                    /* 检查方法列表 */
                    ctCls.getDeclaredMethods().each {
                        it.instrument(editor)
                    }

                    ctCls.getMethods().each {
                        it.instrument(editor)
                    }

                    ctCls.writeFile(dir)
                } catch (Throwable t) {
//                    println "    [Warning] LocalBroadcastInjector --> ${t.toString()}"
                    // t.printStackTrace()
                } finally {
                    if (ctCls != null) {
                        ctCls.detach()
                    }
                    if (stream != null) {
                        stream.close()
                    }
                }

                return super.visitFile(file, attrs)
            }
        })
    }
}
