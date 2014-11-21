/*
 * Copyright 2013 Xebia and Séven Le Mesle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package fr.xebia.extras.selma.codegen;

import com.squareup.javawriter.JavaWriter;
import fr.xebia.extras.selma.SelmaConstants;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.*;

import static javax.lang.model.element.Modifier.*;

/**
 * Handles the generation of the Mapper class
 */
public class MapperClassGenerator {


    public static final String GENERATED_BY_SELMA = "GENERATED BY S3LM4";
    private final Collection<ExecutableElement> mapperMethods;
    private final String origClasse;
    private final ProcessingEnvironment processingEnv;
    private final MapperGeneratorContext context;
    private final TypeElement element;
    private final MapperWrapper mapper;
    private List<MethodWrapper> methodWrappers;

    public MapperClassGenerator(String classe, Collection<ExecutableElement> executableElements, ProcessingEnvironment processingEnvironment) {
        this.origClasse = classe;
        this.mapperMethods = executableElements;
        this.processingEnv = processingEnvironment;
        context = new MapperGeneratorContext(processingEnv);

        element = context.getTypeElement(classe);

        mapper = new MapperWrapper(context, element);


        methodWrappers = validateTypes();
    }


    private List<MethodWrapper> validateTypes() {

        List<MethodWrapper> res = new ArrayList<MethodWrapper>();

        for (ExecutableElement mapperMethod : mapperMethods) {

            MethodWrapper methodWrapper = new MethodWrapper(mapperMethod, context);
            res.add(methodWrapper);

            mapper.buildEnumForMethod(methodWrapper);

            InOutType inOutType = methodWrapper.inOutType();
            if (inOutType.differs()) {
                MappingBuilder builder = MappingBuilder.getBuilderFor(context, inOutType);

                if ((inOutType.in().getKind() != TypeKind.DECLARED || inOutType.out().getKind() != TypeKind.DECLARED) && builder == null) {
                    context.error(mapperMethod, "In type : %s and Out type : %s differs and this kind of conversion is not supported here", inOutType.in(), inOutType.out());
                } else {
                    context.mappingMethod(methodWrapper.inOutType(), methodWrapper.getSimpleName());
                }
            }

        }
        return res;
    }

    public void build() throws IOException {

        boolean firstMethod = true;
        JavaWriter writer = null;
        JavaFileObject sourceFile = null;

        final TypeElement type = processingEnv.getElementUtils().getTypeElement(origClasse);
        final String adapterName = new StringBuilder(type.toString()).append(SelmaConstants.MAPPER_CLASS_SUFFIX).toString();

        for (MethodWrapper mapperMethod : methodWrappers) {

            if (firstMethod) {
                String packageName = getPackage(mapperMethod.element()).getQualifiedName().toString();
                String strippedTypeName = strippedTypeName(type.getQualifiedName().toString(), packageName);

                sourceFile = processingEnv.getFiler().createSourceFile(adapterName, type);
                writer = new JavaWriter(sourceFile.openWriter());

                writer.emitSingleLineComment(GENERATED_BY_SELMA);
                writer.emitPackage(packageName);
                writer.emitEmptyLine();
                if (mapper.isFinalMappers()) {
                    writer.beginType(adapterName, "class", EnumSet.of(PUBLIC, FINAL), null, strippedTypeName);
                } else {
                    writer.beginType(adapterName, "class", EnumSet.of(PUBLIC), null, strippedTypeName);
                }
                writer.emitEmptyLine();
                firstMethod = false;
            }
            // Write mapping method
            MapperMethodGenerator mapperMethodGenerator = new MapperMethodGenerator(writer, mapperMethod, mapper);
            mapperMethodGenerator.build();

            mapper.collectMaps(mapperMethodGenerator.maps());

            writer.emitEmptyLine();
        }

        buildConstructor(writer, adapterName);

        writer.endType();
        writer.close();

        mapper.reportUnused();
    }

    private void buildConstructor(JavaWriter writer, String adapterName) throws IOException {
        mapper.emitSourceFields(writer);
        mapper.emitCustomMappersFields(writer, false);

        // First build default constructor
        writer.emitEmptyLine();
        writer.emitJavadoc("Single constructor");
        writer.beginMethod(null, adapterName, EnumSet.of(PUBLIC), mapper.sourceConstructorArgs());

        // assign source in parameters to instance fields
        mapper.emitSourceAssigns(writer);

        // Add customMapper instantiation
        mapper.emitCustomMappersFields(writer, true);

        writer.endMethod();
        writer.emitEmptyLine();
    }


    public PackageElement getPackage(Element type) {
        while (type.getKind() != ElementKind.PACKAGE) {
            type = type.getEnclosingElement();
        }
        return (PackageElement) type;
    }

    public String strippedTypeName(String type, String packageName) {
        return type.substring(packageName.isEmpty() ? 0 : packageName.length() + 1);
    }

}
