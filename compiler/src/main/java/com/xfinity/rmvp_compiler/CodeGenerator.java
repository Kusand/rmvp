/*
 * Copyright 2017 Comcast Cable Communications Management, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xfinity.rmvp_compiler;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.tools.javac.util.Pair;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.squareup.javapoet.TypeName.INT;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.PUBLIC;

final class CodeGenerator {
    private final List<RmvpProcessor.ComponentViewInfo> componentViewInfoList;
    private final Map<String, List<Pair<TypeName, String>>> defaultPresenterContructorMap;


    CodeGenerator(List<RmvpProcessor.ComponentViewInfo> componentViewInfoList,
                  Map<String, List<Pair<TypeName, String>>> defaultPresenterContructorMap) {
        this.componentViewInfoList = componentViewInfoList;
        this.defaultPresenterContructorMap = defaultPresenterContructorMap;
    }

    TypeSpec generateComponentRegistry() {
        List<FieldSpec> fields = new ArrayList<>();
        List<String> componentViewSwitchStatements = new ArrayList<>();
        List<String> defaultPresenterSwitchStatements = new ArrayList<>();

        componentViewSwitchStatements.add("switch(viewType) {\n");
        defaultPresenterSwitchStatements.add("switch(viewType) {\n");

        TypeName objectVarArgsType = ArrayTypeName.get(Object[].class);
        ParameterSpec parameterSpec = ParameterSpec.builder(objectVarArgsType, "args").build();

        MethodSpec.Builder getDefaultPresenterMethodbuilder1 =
                MethodSpec.methodBuilder("getDefaultPresenter")
                          .addModifiers(PUBLIC)
                          .addParameter(ClassName.get("com.xfinity.rmvp.view", "ComponentView"), "componentView")
                          .addParameter(parameterSpec)
                          .returns(ClassName.get("com.xfinity.rmvp.presenter", "ComponentPresenter"));

        MethodSpec.Builder getDefaultPresenterMethodbuilder2 =
                MethodSpec.methodBuilder("getDefaultPresenter")
                          .addModifiers(PUBLIC)
                          .addParameter(INT, "viewType")
                          .addParameter(parameterSpec)
                          .returns(ClassName.get("com.xfinity.rmvp.presenter", "ComponentPresenter"));


        List<Pair<TypeName, String>> contructorArgs = new ArrayList<>();

        for (RmvpProcessor.ComponentViewInfo componentViewInfo : componentViewInfoList) {
            String viewTypeFieldName = componentViewInfo.viewTypeName + "_VIEW_TYPE";
            FieldSpec fieldSpec = FieldSpec.builder(INT, viewTypeFieldName, Modifier.PUBLIC, Modifier.STATIC,
                                                    Modifier.FINAL).initializer(String.valueOf(componentViewInfo.viewType)).build();
            fields.add(fieldSpec);

            componentViewSwitchStatements.add("case " + viewTypeFieldName + ":\n");
            componentViewSwitchStatements.add("return new " + componentViewInfo.componentView + "();\n");

            if (componentViewInfo.defaultPresenter != null) {
                getDefaultPresenterMethodbuilder1.addCode("if (componentView instanceof "
                                                          + componentViewInfo.componentView + ") {\n");

                String returnStatement;
                List<Pair<TypeName, String>> defaultPresenterContructorArgs =
                        defaultPresenterContructorMap.get(componentViewInfo.defaultPresenter);
                if (defaultPresenterContructorArgs == null) {
                    returnStatement = "return new " + componentViewInfo.defaultPresenter + "()";
                } else {
                    StringBuilder statementBuilder = new StringBuilder("return new " + componentViewInfo.defaultPresenter + "(");
                    for (int j = 0; j < defaultPresenterContructorArgs.size(); j++) {
                        Pair<TypeName, String> argPair = defaultPresenterContructorArgs.get(j);
                        String argName = argPair.snd;  //arg name
                        statementBuilder.append(argName);
                        if (j < defaultPresenterContructorArgs.size() - 1) {
                            statementBuilder.append(", ");
                        } else {
                            statementBuilder.append(")");
                        }

                        //check if an arg with this name and type was already added to the ComponentRegistry's ctor, if
                        // not, add it
                        if (!contructorArgs.contains(argPair)) {
                            contructorArgs.add(argPair);
                        }
                    }

                    returnStatement = statementBuilder.toString();
                }

                getDefaultPresenterMethodbuilder1.addStatement(returnStatement);
                getDefaultPresenterMethodbuilder1.addCode("}\n");

                defaultPresenterSwitchStatements.add("case " + viewTypeFieldName + ":\n");
                defaultPresenterSwitchStatements.add(returnStatement + ";\n");
            }
        }

        MethodSpec.Builder getComponentViewMethodbuilder = MethodSpec.methodBuilder("getComponentView")
                                                                     .addModifiers(PUBLIC)
                                                                     .addParameter(INT, "viewType")
                                                                     .returns(ClassName.get("com.xfinity.rmvp.view", "ComponentView"));

        for (String statement : componentViewSwitchStatements) {
            getComponentViewMethodbuilder.addCode(statement);
        }

        getComponentViewMethodbuilder.addCode("}\n");
        getComponentViewMethodbuilder.addStatement("return null");

        getDefaultPresenterMethodbuilder1.addStatement("return null");

        for (String statement : defaultPresenterSwitchStatements) {
            getDefaultPresenterMethodbuilder2.addCode(statement);
        }

        getDefaultPresenterMethodbuilder2.addCode("}\n");
        getDefaultPresenterMethodbuilder2.addStatement("return null");


        MethodSpec.Builder componentRegistryConstructorBuilder = MethodSpec.constructorBuilder()
                                                                           .addModifiers(PUBLIC);

        contructorArgs.sort((o1, o2) -> (o1.fst.toString().compareToIgnoreCase(o2.fst.toString())));

        for (Pair<TypeName, String> argPair : contructorArgs) {
            fields.add(FieldSpec.builder(argPair.fst, argPair.snd).addModifiers(Modifier.PRIVATE)
                                .addModifiers(Modifier.FINAL).build());

            componentRegistryConstructorBuilder.addParameter(ParameterSpec.builder(argPair.fst, argPair.snd).build());
            componentRegistryConstructorBuilder.addStatement("this." + argPair.snd + " = " + argPair.snd);
        }

        TypeSpec.Builder classBuilder = classBuilder("AppComponentRegistry")
                .addModifiers(PUBLIC)
                .addSuperinterface(ClassName.get("com.xfinity.rmvp", "ComponentRegistry"))
                .addFields(fields)
                .addMethod(componentRegistryConstructorBuilder.build())
                .addMethod(getComponentViewMethodbuilder.build())
                .addMethod(getDefaultPresenterMethodbuilder1.build())
                .addMethod(getDefaultPresenterMethodbuilder2.build());

        return classBuilder.build();
    }

    List<Pair<String, TypeSpec>> generateViewBaseClasses() {
        List<Pair<String, TypeSpec>> viewDelegatePairs = new ArrayList<>();
        for (RmvpProcessor.ComponentViewInfo componentViewInfo : componentViewInfoList) {
            String componentViewPackageName =
                    componentViewInfo.componentView.substring(0, componentViewInfo.componentView.lastIndexOf("."));
            String viewHolderPackageName =
                    componentViewInfo.viewHolder.substring(0, componentViewInfo.viewHolder.lastIndexOf("."));
            String viewHolderName = componentViewInfo.viewHolder.substring(componentViewInfo.viewHolder.lastIndexOf(".") + 1,
                                                                           componentViewInfo.viewHolder.length());

            TypeName viewHolderTypeName = ClassName.get(viewHolderPackageName, viewHolderName);

            TypeName viewBinderTypeName;
            if (componentViewInfo.viewBinder != null && componentViewInfo.viewBinder.equals(RmvpProcessor.DEFAULT_VIEW_BINDER)) {
                viewBinderTypeName = ClassName.get("com.xfinity.rmvp.view", "ComponentViewBinder");
            } else {
                viewBinderTypeName = ParameterizedTypeName.get(ClassName.get("com.xfinity.rmvp.view", "ComponentViewBinder"),
                                                               viewHolderTypeName);
            }

            FieldSpec viewBinderFieldSpec = null;
            if (componentViewInfo.viewBinder != null) {
                viewBinderFieldSpec = FieldSpec.builder(viewBinderTypeName, "viewBinder", Modifier.PRIVATE,
                                                                  Modifier.FINAL)
                                                         .initializer("new " + componentViewInfo.viewBinder + "()").build();
            }

            FieldSpec viewHolderFieldSpec = FieldSpec.builder(viewHolderTypeName, "viewHolder", Modifier.PRIVATE).build();


            ClassName notNullAnnotation = ClassName.get("org.jetbrains.annotations", "NotNull");
            MethodSpec getViewHolderMethod = MethodSpec.methodBuilder("getViewHolder")
                                                       .addModifiers(PUBLIC)
                                                       .addAnnotation(notNullAnnotation)
                                                       .addAnnotation(Override.class)
                                                       .addStatement("return viewHolder")
                                                       .returns(viewHolderTypeName)
                                                       .build();

            ParameterSpec viewHolderParameterSpec = ParameterSpec.builder(viewHolderTypeName, "viewHolder").build();
            MethodSpec setViewHolderMethod = MethodSpec.methodBuilder("setViewHolder")
                                                       .addModifiers(PUBLIC)
                                                       .addAnnotation(notNullAnnotation)
                                                       .addAnnotation(Override.class)
                                                       .addParameter(viewHolderParameterSpec)
                                                       .build();

            MethodSpec.Builder getComponentViewBinderMethodBuilder = MethodSpec.methodBuilder("getComponentViewBinder")
                                                                .addModifiers(PUBLIC)
                                                                .addAnnotation(notNullAnnotation)
                                                                .addAnnotation(Override.class)
                                                                .returns(viewBinderTypeName);

            if (componentViewInfo.viewBinder != null) {
                getComponentViewBinderMethodBuilder.addStatement("return viewBinder");
            } else {
                getComponentViewBinderMethodBuilder.addStatement("return null");
            }

            MethodSpec getComponentViewBinderMethod = getComponentViewBinderMethodBuilder.build();

            ParameterSpec viewGroupParam = ParameterSpec.builder(ClassName.get("android.view", "ViewGroup"), "parent")
                                                        .addAnnotation(notNullAnnotation).build();

            MethodSpec onCreateViewHolderMethod =
                    MethodSpec.methodBuilder("onCreateViewHolder")
                              .addModifiers(PUBLIC)
                              .addAnnotation(notNullAnnotation)
                              .addAnnotation(Override.class)
                              .addParameter(viewGroupParam)
                              .addStatement("android.view.View view = android.view.LayoutInflater.from(parent.getContext()).inflate(getViewType(), parent, false)")
                              .addStatement("return new " + componentViewInfo.viewHolder + "(view)")
                              .returns(viewHolderTypeName)
                              .build();


            ParameterSpec componentPresenterParam =
                    ParameterSpec.builder(ClassName.get("com.xfinity.rmvp.presenter", "ComponentPresenter"),
                                          "componentPresenter")
                                 .addAnnotation(notNullAnnotation).build();

            ParameterSpec viewHolderParam =
                    ParameterSpec.builder(ClassName.get("android.support.v7.widget", "RecyclerView").nestedClass("ViewHolder"),
                                          "viewHolder")
                                 .addAnnotation(notNullAnnotation).build();

            ParameterSpec positionParam = ParameterSpec.builder(INT, "position").addAnnotation(notNullAnnotation).build();

            TypeName componentViewTypeName = ParameterizedTypeName.get(ClassName.get("com.xfinity.rmvp.view", "ComponentView"),
                                                                       viewHolderTypeName);

            MethodSpec.Builder onBindViewHolderMethodBuilder =
                    MethodSpec.methodBuilder("onBindViewHolder")
                              .addModifiers(PUBLIC)
                              .addAnnotation(Override.class)
                              .addParameters(Arrays.asList(componentPresenterParam, viewHolderParam, positionParam))
                              .addCode("if (viewHolder instanceof " + componentViewInfo.viewHolder + ") {\n")
                              .addStatement("this.viewHolder = (" + componentViewInfo.viewHolder + ") viewHolder")
                              .addCode("} else {\n")
                              .addStatement("throw new IllegalArgumentException(\"You can only attach " + viewHolderName + " to this view object\")")
                              .addCode("}\n");

            if (componentViewInfo.viewBinder != null) {
                onBindViewHolderMethodBuilder.addStatement("viewBinder.bind(componentPresenter, this, this.viewHolder, position)");
            }

            MethodSpec onBindViewHolderMethod = onBindViewHolderMethodBuilder.build();

            MethodSpec getViewTypeMethod =
                    MethodSpec.methodBuilder("getViewType")
                              .addModifiers(PUBLIC)
                              .addAnnotation(Override.class)
                              .returns(INT)
                              .addStatement("return " + componentViewInfo.viewType)
                              .build();


            List<FieldSpec> onBindViewHolderMethodFields = new ArrayList<>();
            if (viewBinderFieldSpec != null) {
                onBindViewHolderMethodFields.add(viewBinderFieldSpec);
            }

            onBindViewHolderMethodFields.add(viewHolderFieldSpec);
            TypeSpec.Builder classBuilder = classBuilder(componentViewInfo.viewTypeName + "Base")
                    .addModifiers(PUBLIC)
                    .addSuperinterface(componentViewTypeName)
                    .addFields(onBindViewHolderMethodFields)
                    .addMethods(Arrays.asList(getViewHolderMethod,
                                              setViewHolderMethod,
                                              getComponentViewBinderMethod,
                                              onCreateViewHolderMethod,
                                              onBindViewHolderMethod,
                                              getViewTypeMethod));

            viewDelegatePairs.add(new Pair<>(componentViewPackageName, classBuilder.build()));
        }

        return viewDelegatePairs;
    }
}
