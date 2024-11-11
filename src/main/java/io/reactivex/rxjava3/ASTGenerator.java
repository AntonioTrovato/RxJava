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

package io.reactivex.rxjava3;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The aim of this class is identifying new or modified methods to benchmark
 */
public class ASTGenerator {

    /**
     * Main method
     * @param args filepath of a .txt file with the list of classes to inspect
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java ASTGenerator <file_with_modified_classes>");
            return;
        }

        String filePath = args[0];

        try {
            List<String> modifiedClasses = Files.readAllLines(Paths.get(filePath));
            JavaParser javaParser = new JavaParser();
            ArrayList<String> all_modified_methods = new ArrayList<>();

            for (String className : modifiedClasses) {
                String filePathJava = className.replace('.', '/') + ".java";
                String currentFullPath = "src/main/java/" + filePathJava;

                // Make the current version AST
                List<MethodDeclaration> currentMethods = createASTFromFile(javaParser, currentFullPath, "Current", className);

                // Make the previous version AST
                String previousContent = getPreviousCommitContent(filePathJava);
                if (previousContent != null) {
                    List<MethodDeclaration> previousMethods = createASTFromContent(javaParser, previousContent, "Previous", className);
                    // Compare methods
                    all_modified_methods.addAll(compareMethods(className,currentMethods, previousMethods));
                } else {
                    System.out.println("No previous version found for class: " + className);

                    Set<String> currentMethodSignatures = new HashSet<>();

                    // Add methods of the current version
                    for (MethodDeclaration method : currentMethods) {
                        currentMethodSignatures.add(getMethodSignature(method));
                    }

                    Set<String> newMethods = new HashSet<>(currentMethodSignatures);

                    for (String new_method : newMethods) {
                        String new_method_fully_qualified_name = className + "." + extractMethodNameAndParameters(new_method);
                        if (!all_modified_methods.contains(new_method_fully_qualified_name)) {
                            all_modified_methods.add(new_method_fully_qualified_name);
                        }
                    }
                }
            }

            System.out.println("All the modified methods: ");
            System.out.println(all_modified_methods);

            // write in file the final modified methods
            String outputPath = "modified_methods.txt";
            try {
                // rewrite the file if it already exists
                Files.write(Paths.get(outputPath), all_modified_methods);
                System.out.println("Modified methods have been written to " + outputPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to create the current AST and return the methods
     * @param javaParser the parser
     * @param filePath filepath of the class
     * @param version version of the class
     * @param className name of the class
     * @return list of method declarations
     */
    private static List<MethodDeclaration> createASTFromFile(JavaParser javaParser, String filePath, String version, String className) {
        File file = new File(filePath);
        List<MethodDeclaration> methods = new ArrayList<>();
        if (file.exists()) {
            try {
                CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
                if (cu != null) {
                    /*System.out.println("AST for " + version + " version of class: " + className);
                    System.out.println(cu.toString());*/
                    methods.addAll(cu.findAll(MethodDeclaration.class));
                } else {
                    System.out.println("Could not parse the " + version + " version file: " + filePath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(version + " version file not found: " + filePath);
        }
        return methods;
    }

    /**
     * Method to create the previous AST and return methods
     * @param javaParser parser
     * @param content the previous version class script
     * @param version previous version of the class
     * @param className name of the class
     * @return list of method declarations
     */
    private static List<MethodDeclaration> createASTFromContent(JavaParser javaParser, String content, String version, String className) {
        List<MethodDeclaration> methods = new ArrayList<>();
        CompilationUnit cu = javaParser.parse(content).getResult().orElse(null);
        if (cu != null) {
            /*System.out.println("AST for " + version + " version of class: " + className);
            System.out.println(cu.toString());*/
            methods.addAll(cu.findAll(MethodDeclaration.class));
        } else {
            System.out.println("Could not parse the " + version + " version content for class: " + className);
        }
        return methods;
    }

    /**
     * Method to compare the two versions
     * @param className name of the class to compare between its two consecutive versions
     * @param currentMethods list of methods currently in the class
     * @param previousMethods list of methods in the previous version of the class
     * @return list of actually modified methods
     */
    private static ArrayList<String> compareMethods(String className, List<MethodDeclaration> currentMethods, List<MethodDeclaration> previousMethods) {
        Set<String> currentMethodSignatures = new HashSet<>();
        Set<String> previousMethodSignatures = new HashSet<>();

        // Add methods of the current version
        for (MethodDeclaration method : currentMethods) {
            currentMethodSignatures.add(getMethodSignature(method));
        }

        // Add methods of the previous version
        for (MethodDeclaration method : previousMethods) {
            previousMethodSignatures.add(getMethodSignature(method));
        }

        // Find added methods
        Set<String> newMethods = new HashSet<>(currentMethodSignatures);
        newMethods.removeAll(previousMethodSignatures);

        // Find modified methods
        Set<String> modifiedMethods = new HashSet<>();
        for (MethodDeclaration method : currentMethods) {
            String signature = getMethodSignature(method);
            if (previousMethodSignatures.contains(signature)) {
                // If method exists in the previous version, verify the modifications
                if (hasMethodChanged(method, previousMethods)) {
                    modifiedMethods.add(signature);
                }
            }
        }

        // Print results
        System.out.println("New Methods: " + newMethods);
        System.out.println("Modified Methods: " + modifiedMethods);

        ArrayList<String> modified_methods = new ArrayList<>();
        for (String new_method : newMethods) {
            String new_method_fully_qualified_name = className + "." + extractMethodNameAndParameters(new_method);
            if (!modified_methods.contains(new_method_fully_qualified_name)) {
                modified_methods.add(new_method_fully_qualified_name);
            }
        }
        for (String modified_method : modifiedMethods) {
            String modified_method_fully_qualified_name = className + "." + extractMethodNameAndParameters(modified_method);
            if (!modified_methods.contains(modified_method_fully_qualified_name)) {
                modified_methods.add(modified_method_fully_qualified_name);
            }
        }

        return modified_methods;
    }

    /**
     * Method to obtain the signature
     * @param method the method to analyze
     * @return signature of the analyzed method
     */
    private static String getMethodSignature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();

        // Add access modifiers, type and name
        method.getModifiers().forEach(modifier -> signature.append(modifier.getKeyword().asString()).append(" "));
        signature.append(getTypeAsString(method.getType())).append(" ");
        signature.append(method.getNameAsString()).append("(");

        // Add parameters
        method.getParameters().forEach(param -> signature.append(getTypeAsString(param.getType())).append(", "));
        if (method.getParameters().size() > 0) {
            signature.setLength(signature.length() - 2); // Remove last comma and space
        }
        signature.append(")");

        return signature.toString();
    }

    /**
     * Handle generic types
     * @param type type of method's parameter or modifier
     * @return type as a string
     */
    private static String getTypeAsString(com.github.javaparser.ast.type.Type type) {
        StringBuilder typeString = new StringBuilder();

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classOrInterfaceType = type.asClassOrInterfaceType();
            typeString.append(classOrInterfaceType.getNameAsString());

            classOrInterfaceType.getTypeArguments().ifPresent(typeArgs -> {
                typeString.append("<");
                typeArgs.forEach(arg -> typeString.append(getTypeAsString(arg)).append(", "));
                if (typeArgs.size() > 0) {
                    typeString.setLength(typeString.length() - 2); // Remove last comma and space
                }
                typeString.append(">");
            });
        } else {
            typeString.append(type.asString());
        }

        return typeString.toString();
    }

    /**
     * Extract method name and parameters to obtain the final signature
     * @param methodSignature signature of a method
     * @return string like method_name(par_type_1, ..., par_type_n)
     */
    private static String extractMethodNameAndParameters(String methodSignature) {
        //TODO: ADD PARAMETERS

        // Define the regexes
        String regex = "(\\w+)\\s*\\((.*?)\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(methodSignature);

        if (matcher.find()) {
            String methodName = matcher.group(1); // Method name
            String parameters = matcher.group(2); // Method parameters
            //return methodName + "(" + parameters + ")";
            return methodName;
        }

        return null; // Null if no match
    }

    /**
     * Method to verify if a method has been modified
     * @param currentMethod method of the current version of the class
     * @param previousMethods correspondent method of the previous version of the class
     * @return true if the method has changed, false otherwise
     */
    private static boolean hasMethodChanged(MethodDeclaration currentMethod, List<MethodDeclaration> previousMethods) {
        //TODO: COMMENTS ADDED ARE RECOGNIZED AS MODIFICATIONS, INVESTIGATE FOR OTHERS AND RESOLVE
        String currentSignature = getMethodSignature(currentMethod);
        for (MethodDeclaration previousMethod : previousMethods) {
            if (currentSignature.equals(getMethodSignature(previousMethod))) {
                // Here's were the body is compared
                return !currentMethod.getBody().equals(previousMethod.getBody());
            }
        }
        return false; // If no match, return false
    }

    /**
     * Method to get the content of a previous version file on Git
     * @param filePathJava path to the previous version of the class
     * @return content of the previous version of the class as a string
     */
    private static String getPreviousCommitContent(String filePathJava) {
        try {
            Process process = new ProcessBuilder("git", "show", "HEAD^:" + "src/main/java/" + filePathJava).start();
            process.waitFor();
            if (process.exitValue() == 0) {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
                return content.toString();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
