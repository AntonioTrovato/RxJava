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

package io.reactivex.rxjava3.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

public class JacocoCoverageListener extends TestWatcher {

    private static final String JACOCO_MBEAN_NAME = "org.jacoco:type=Runtime";
    private static final String COVERAGE_MATRIX_FILE = "coverage-matrix.json";

    @Override
    protected void succeeded(Description description) {
        updateCoverageMatrix(description);
    }

    @Override
    protected void failed(Throwable e, Description description) {
        updateCoverageMatrix(description);
    }

    private void updateCoverageMatrix(Description description) {
        try {
            // Connect to the platform MBean server
            MBeanServerConnection mbsc = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(JACOCO_MBEAN_NAME);

            // Invoke the dump command with no reset (you can set to true if you want to reset coverage after each dump)
            byte[] executionData = (byte[]) mbsc.invoke(objectName, "getExecutionData", new Object[]{true}, new String[]{"boolean"});

            // Use JaCoCo's ExecutionDataReader to parse the data
            ExecutionDataStore executionDataStore = new ExecutionDataStore();
            SessionInfoStore sessionInfoStore = new SessionInfoStore();
            ExecutionDataReader reader = new ExecutionDataReader(new ByteArrayInputStream(executionData));
            reader.setExecutionDataVisitor(executionDataStore);
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.read();

            System.out.println("Covered methods for test: " + description.getMethodName());

            // Analyze the covered classes to determine methods
            CoverageBuilder coverageBuilder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);

            // Specify the directory where your compiled classes are located
            File classesDir = new File("build/classes/java/main"); // Adjust the path as needed

            ArrayList<String> fullyQualifiedCurrentMethods = new ArrayList<>();

            // Analyze each class file to extract covered methods
            for (ExecutionData data : executionDataStore.getContents()) {
                if (data.hasHits()) {
                    String className = data.getName().replace("/", ".");
                    System.out.println("Class: " + className);

                    // Analyze the corresponding .class file
                    File classFile = new File(classesDir, data.getName() + ".class");
                    if (classFile.exists()) {
                        try (FileInputStream classStream = new FileInputStream(classFile)) {
                            analyzer.analyzeClass(classStream, data.getName());
                        }
                    }

                    // Print the covered method names
                    Set<String> coveredMethods = getCoveredMethods(coverageBuilder, className);
                    ArrayList<String> coveredMethodsFullyQualified = new ArrayList<>();
                    for (String method : coveredMethods) {
                        if(method.equals("<init>"))
                            method = getSimpleClassName(className);
                        System.out.println("Method covered: " + method);
                        fullyQualifiedCurrentMethods.add(className+"."+method);
                        coveredMethodsFullyQualified.add(className+"."+method);
                    }

                    // Update the json coverage-matrix file
                    updateCoverageMatrixFile(description.getClassName()+"."+description.getMethodName(),coveredMethodsFullyQualified);
                }
            }

            deleteOlderCoveredMethodsFromMatrix(description.getClassName()+"."+description.getMethodName(),fullyQualifiedCurrentMethods);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteOlderCoveredMethodsFromMatrix(String testName, ArrayList<String> fullyQualifiedCurrentMethods) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Set<String>> coverageMatrix = new HashMap<>();

        // Read existing coverage-matrix.json if it exists
        File coverageFile = new File(COVERAGE_MATRIX_FILE);
        if (coverageFile.exists()) {
            try {
                coverageMatrix = objectMapper.readValue(coverageFile, new TypeReference<Map<String, Set<String>>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to read coverage-matrix.json");
            }
        }

        // Update the coverage matrix
        Set<String> existingMethods = coverageMatrix.computeIfAbsent(testName, k -> new HashSet<>());

        Set<String> methodsToRemove = new HashSet<>(existingMethods);
        for (String method : methodsToRemove) {
            if (!fullyQualifiedCurrentMethods.contains(method)) {
                existingMethods.remove(method);
            }
        }

        // Write the updated coverage matrix back to the file, creating the file if it doesn't exist
        try {
            if (!coverageFile.exists()) {
                coverageFile.createNewFile();
            }
            try (FileWriter fileWriter = new FileWriter(coverageFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileWriter, coverageMatrix);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write coverage-matrix.json");
        }
    }

    public String getSimpleClassName(String className) {
        if (className.contains(".")) {
            return className.substring(className.lastIndexOf('.') + 1);
        }
        return className;
    }

    private Set<String> getCoveredMethods(CoverageBuilder coverageBuilder, String className) {
        //TODO: ADD PARAMETERS
        Set<String> coveredMethods = new HashSet<>();
        className = className.replace(".", "/");
        for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
            if (classCoverage.getName().equals(className)) {
                for (IMethodCoverage methodCoverage : classCoverage.getMethods()) {
                    if (methodCoverage.getInstructionCounter().getCoveredCount() > 0) {
                        String methodName = methodCoverage.getName(); // Get method name
                        /*String methodDescriptor = methodCoverage.getDesc(); // Get method descriptor

                        // Extract parameter types
                        String paramTypes = extractParameterTypes(methodDescriptor);
                        coveredMethods.add(methodName + "(" + paramTypes + ")"); // Add formatted method name to the set*/

                        coveredMethods.add(methodName);
                    }
                }
            }
        }
        return coveredMethods;
    }

    private void updateCoverageMatrixFile(String testName, ArrayList<String> coveredMethods) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Set<String>> coverageMatrix = new HashMap<>();

        // Read existing coverage-matrix.json if it exists
        File coverageFile = new File(COVERAGE_MATRIX_FILE);
        if (coverageFile.exists()) {
            try {
                coverageMatrix = objectMapper.readValue(coverageFile, new TypeReference<Map<String, Set<String>>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to read coverage-matrix.json");
            }
        }

        // Update the coverage matrix
        coverageMatrix.computeIfAbsent(testName, k -> new HashSet<>());

        for (String method : coveredMethods) {
            coverageMatrix.get(testName).add(method);
        }

        // Write the updated coverage matrix back to the file, creating the file if it doesn't exist
        try {
            if (!coverageFile.exists()) {
                coverageFile.createNewFile();
            }
            try (FileWriter fileWriter = new FileWriter(coverageFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileWriter, coverageMatrix);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write coverage-matrix.json");
        }
    }

    // Method to extract parameter types from the method descriptor
    private String extractParameterTypes(String descriptor) {
        StringBuilder paramTypes = new StringBuilder();

        // The descriptor starts with '(' and ends with ')'
        if (descriptor.startsWith("(") && descriptor.contains(")")) {
            // Extract the substring between '(' and ')'
            String params = descriptor.substring(descriptor.indexOf('(') + 1, descriptor.indexOf(')'));
            String[] paramArray = params.split(","); // Split by ',' to get individual parameter types

            for (String param : paramArray) {
                // Clean up the parameter type and add it to the StringBuilder
                param = param.replaceAll("^L", "").replaceAll(";$", ""); // Remove 'L' prefix and ';' suffix
                paramTypes.append(param).append(", ");
            }

            // Remove trailing comma and space if there are any parameters
            if (paramTypes.length() > 0) {
                paramTypes.setLength(paramTypes.length() - 2); // Remove last ", "
            }
        }

        return paramTypes.toString();
    }
}

