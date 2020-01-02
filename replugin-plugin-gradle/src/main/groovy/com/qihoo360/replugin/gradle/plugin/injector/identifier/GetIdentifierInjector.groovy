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

package com.qihoo360.replugin.gradle.plugin.injector.identifier

import com.qihoo360.replugin.gradle.plugin.injector.BaseInjector
import com.qihoo360.replugin.gradle.plugin.inner.Util
import javassist.ClassPool

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * 替换 插件中的 Resource.getIdentifier 调用代码的参数 为 动态适配的参数
 * 资源id前面加上插件包名
 * @author RePlugin Team
 */
public class GetIdentifierInjector extends BaseInjector {

    // 表达式编辑器
    def editor

    @Override
    def injectClass(ClassPool pool, String dir, Map config) {

        if (editor == null) {
            editor = new GetIdentifierExprEditor()
        }

//        Util.newSection()
//        println dir

        Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                //todo only .class
                String filePath = file.toString()

                editor.filePath = filePath

                def stream, ctCls
                try {
                    stream = new FileInputStream(filePath)
                    ctCls = pool.makeClass(stream)

                    // println ctCls.name
                    if (ctCls.isFrozen()) {
                        ctCls.defrost()
                    }

                    // 遍历全部方法，并执行instrument方法，逐个扫描每个方法体内每一行代码，
                    // 并交由 GetIdentifierExprEditor 的edit()处理对方法体代码的修改。
                    ctCls.getDeclaredMethods().each {
                        it.instrument(editor)
                    }

                    ctCls.getMethods().each {
                        it.instrument(editor)
                    }

                    ctCls.writeFile(dir)
                } catch (Throwable t) {
//                    println "    [Warning] GetIdentifierInjector --> ${t.toString()}"
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
