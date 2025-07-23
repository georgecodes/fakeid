package com.elevenware.fakeid.util;

/*-
 * #%L
 * Fake ID
 * %%
 * Copyright (C) 2025 George McIntosh
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.elevenware.fakeid.Configuration;
import com.elevenware.fakeid.FakeIdApplication;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public class FakeIdExtension implements AfterEachCallback, BeforeTestExecutionCallback {

    private Configuration cfg;
    private Configuration.Builder builder = Configuration.builder().randomPort();
    private FakeIdApplication app;

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        Optional.ofNullable(app).ifPresent(FakeIdApplication::stop);
        cfg = null;
        app = null;
        builder = Configuration.builder().randomPort();
    }

    @Override
    public void beforeTestExecution(ExtensionContext extensionContext) throws Exception {
        Method requiredTestMethod = extensionContext.getTestMethod().get();
        Class<?> testClass = extensionContext.getTestClass().get();
        Optional<Object> testInstance = extensionContext.getTestInstance();
        Object testInstanceObject = testInstance.get();
        runConfigModifiers(requiredTestMethod, testClass, testInstanceObject);
        cfg = builder.build();
        app = new FakeIdApplication(cfg);
        app.start();
        Field[] fields = testInstanceObject.getClass().getDeclaredFields();
        for (Field field : fields) {
            if(field.getType().isInstance(app)) {
                field.setAccessible(true);
                field.set(testInstanceObject, app);
            }
            if(field.getType() == int.class || field.getType() == Integer.class) {
                FakeIdPort portAnnotation = field.getAnnotation(FakeIdPort.class);
                if(portAnnotation != null){
                    field.setAccessible(true);
                    field.set(testInstanceObject, app.port());
                }
            }
        }

    }

    private void runConfigModifiers(Method requiredTestMethod, Class<?> testClass, Object testInstanceObject) throws InvocationTargetException, IllegalAccessException {
        for(Method method : testClass.getDeclaredMethods()){
            if(method.isAnnotationPresent(ConfigModifier.class)){
                ConfigModifier configModifier = method.getAnnotation(ConfigModifier.class);
                String modMethod = configModifier.value();
                String[] applicableTo = configModifier.applicableTo();
                if(applicableTo.length == 0 && modMethod.equals("")){
                    method.setAccessible(true);
                    method.invoke(testInstanceObject, builder);
                    return;
                }
                if(applicableTo.length == 0){
                    applicableTo = new String[]{modMethod};
                }

                for(String applicable : applicableTo){
                    if(applicable.equals(requiredTestMethod.getName())){
                        method.setAccessible(true);
                        method.invoke(testInstanceObject, builder);
                    }
                }
            }
        }
    }

}
