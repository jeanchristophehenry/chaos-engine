/*
 *    Copyright (c) 2019 Thales Group
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.function.Predicate.not;

public class MethodUtils {
    private MethodUtils () {
    }

    public static List<Method> getMethodsWithAnnotation (Class<?> clazz, Class<? extends Annotation> annotation) {
        final List<Method> methods = new ArrayList<>();
        while (clazz != Object.class) {
            List<Method> allMethods = Arrays.asList(clazz.getDeclaredMethods());
            allMethods.stream().filter(not(method -> Modifier.isAbstract(method.getModifiers())))
                      .filter(method -> method.isAnnotationPresent(annotation))
                      .forEach(methods::add);
            clazz = clazz.getSuperclass();
        }
        return methods;
    }
}
