/*
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.rxjava3.suite;


import java.lang.reflect.Method;
import io.reactivex.rxjava3.listener.JacocoCoverageRunListener;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class MainSuiteTest {
    @Test
    public void test() throws IOException, ClassNotFoundException {
        JUnitCore junit = new JUnitCore();
        junit.addListener(new JacocoCoverageRunListener());

        String basePackage = "io.reactivex.rxjava3.core";
        String basePath = "build/classes/java/test";

        List<Class<?>> classList = new ArrayList<>();
        File baseDir = new File(basePath + "/" + basePackage.replace(".", "/"));

        findClassesRecursively(baseDir, basePackage, classList);
        /* Print out all found classes
        classList.forEach(System.out::println);
        */

        if (classList.isEmpty()) {
            System.out.println("No test classes found.");
        } else {
            System.out.println("Running " + classList.size() + " test classes...");

            // Run the discovered test classes
            Result result = junit.run(classList.toArray(new Class[0]));

            // Print results
            for (Failure failure : result.getFailures()) {
                System.out.println("Test failed: " + failure.toString());
            }

            System.out.println("All tests finished. Successful: " + result.wasSuccessful());
        }
    }

    public static void findClassesRecursively(File dir, String packageName, List<Class<?>> classList)
            throws ClassNotFoundException, IOException {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                // Recurse into subdirectories
                findClassesRecursively(file, packageName + "." + file.getName(), classList);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                // Load the class if it's a .class file and not an inner class
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = loadClass(className);

                // Check if the class has at least one @Test method
                if (containsTestMethod(clazz)) {
                    classList.add(clazz);
                }
            }
        }
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException, IOException {
        URL[] urls = {new File("Project/build/classes/java/test").toURI().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, MainSuiteTest.class.getClassLoader())) {
            return Class.forName(className, true, loader);
        }
    }

    public static boolean containsTestMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                return true;
            }
        }
        return false;
    }
}
