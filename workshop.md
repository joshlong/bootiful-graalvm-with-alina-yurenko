# Bootiful GraalVM


## graalvm 101 
 - truffle 
 - native-image 101 
 - optimized builds 
 - pgo && oracle graalvm 
 - lots of libraries have native configuration https://www.graalvm.org/native-image/libraries-and-frameworks/ (here's h2 for a specific example https://github.com/h2database/h2database/blob/master/h2/src/main/META-INF/native-image/reflect-config.json). What if it doesnt? then what? the [graalvm reachability repository](https://github.com/oracle/graalvm-reachability-metadata). 
 - but no seriously, tons of stuff should just work... watch

## everything works fine 
 - service (loom, java 21, testcontainers)
 - gateway 
 - SAS
 - and fyi it all compiles to native! 
 - everything works...

## everything works! except... when it doesn't. back to basics
 - beans 
 - configuration formats (XML, java config, component scanning, etc)
 - you know about some of the options like `@Configuration`, `@Controller`, etc. You can also use functional configuration. Its dope. and fast. and reflection-free. looks like this:

```java
var context  = new SpringApplicationBuilder()
        .sources(BasicsApplication.class)
        .main(BasicsApplication.class)
        .initializers((ApplicationContextInitializer<GenericApplicationContext>) ac ->
                ac.registerBean(ApplicationRunner.class, () -> args1 -> System.out.println("hello functional Spring!")))
        .run(args);
```



its kinda ugly, but it is more efficient and uses less memory. 

In kotlin its super nice: 


```
val context = beans {

     bean {
        ApplicationRunner { 
         println( "hello functional Kotlin Spring!")
        }
     }
}
addInitializer (context)
```



 - bean post processors 
 - bean factory post processor
 - the important thing to remember is that there's a compile time lifecycle.

so what are some of the common things that might break? 

## resources
introduce some code that reads from a classpath resource:

```java

package com.example.basics;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;


public class BasicsApplication {
    public static void main(String[] args) throws Exception {
        var xml = new ClassPathResource("test.xml");
        var str = FileCopyUtils.copyToString(new InputStreamReader(xml.getInputStream()));
        System.out.println("str: " + str);
    }
}

```

Add a sample file called `test.xml`

this wont work! we need to add a config file to help graalvm account for this fun dynamic java like behavior: add `src/main/resources/META-INF/native-image/group/artifact/resource-config.json` and put the following in it: 


```json
{
  "resources": {
    "includes": [
      {
        "pattern": "hello"
      }
    ]
  }
}

```


Run the compile: 

```shell
./mvnw  clean native:compile -Pnative && ./target/basics 
```

It works! let's turn to reflection. 


## reflection 

clean out the `main` method

add the following code: 

```
package com.example.basics;

public class BasicsApplication {

    public static void main(String[] args) throws Exception {
        var clazz = Class.forName("bootiful.aot.Album"); // does it blend??
        System.out.println("got a class? " + (clazz != null ));
        var instance = (Album) clazz.getDeclaredConstructors()[0]
                .newInstance("Guardians of the GraalVM, Soundtrack Volume 23");
        System.out.println("title: " + instance.title());
    }
}

record Album(String title) {
}
```

Run the compilation again. It'll successfully get the `class` instance, but it'll fail to enumerate the constructors. GraalVM is smart enough to detect _some_ cases of reflection, [as described in this document](https://www.graalvm.org/22.0/reference-manual/native-image/Reflection/). But don't tell the audience that. What's going wrong? Who knows. The java agent knows. let's plug that in. We could do it ourselves, manually, but it's easier to use Spring Boot to do this. 

Configure it thusly: 

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>
            -agentlib:native-image-agent=config-output-dir=target/native-image
        </jvmArguments>
    </configuration>
</plugin>

```

then run the Java agent like this:

```shell 
./mvnw -DskipTests clean package spring-boot:run
```

You'll see `.json` configuration files in `target/native-image`. Analyze the `reflect-config.json`: it worked! the `.json` for the `record Album` is there. Copy and paste it to the `META-INF` folder and then comment out the Spring Boot Maven plugin again. Rebuild the native image. 



## the new AOT engine in Spring Boot 3

What happens when the code you're using in turn uses your code? that is, what happens when youre dealing with a framework and not just a library for whom a static `.json` configuration file is suitable enough? Something needs to provide the configuration, dynamicaly, based on the types on the classpath. Spring is well situated here. It has a new AOT engine. 


## a special case for reflection 

We could write the `.json` file by hand, or you could have spring do it for you: 

```java
@RegisterReflectionForBinding( Album.class)
```

how does this java code end up contributing to the compile time configuration? that's the aot engine, and we'll get to it next. 

## Hints, a bit more control 

What about the resource example from earlier?  Reintroduce the `test.xml` scenario as an `ApplicationRunner`. It'll run fine on the JVM, but fail as we've deleted the `resource-config.json`. We could add it back, but it's also possible to use the Spring AOT component model. 

Show the simple `RuntimeHintsRegistrar` that defines a hint for the `Resource`.

```java
@ImportRuntimeHints(BasicsApplication.MyHints.class)
```

And then show the actual implementation itself: 

```java

    static class MyHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerResource(new ClassPathResource("hello"));
            hints.reflection().registerType(Album.class, MemberCategory.values());
        }
    }

```

## serialization 
... easy demo ... 



## proxies 

- jdk proxies (register hints)

```java

    
    package com.example.work;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Service;

import java.lang.reflect.Proxy;

@ImportRuntimeHints(WorkApplication.Hints.class)
@SpringBootApplication
public class WorkApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(CustomerService customerService) {
        return args -> customerService.addToCart("sku");
    }

    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.proxies().registerJdkProxy(CustomerService.class);
        }
    }

    interface CustomerService {
        void addToCart(String sku);
    }

    @Service
    static class DefaultCustomerService implements CustomerService {
        @Override
        public void addToCart(String sku) {
        }
    }

    @Bean
    static LoggedBeanPostProcessor loggedBeanPostProcessor() {
        return new LoggedBeanPostProcessor();
    }

    static class LoggedBeanPostProcessor implements
            SmartInstantiationAwareBeanPostProcessor {

        private static Object proxy(Class<?> targetClass, Object t) {
            return Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
                    new Class[]{targetClass}, (target, method, args) -> {
                        var methodName = method.getName();
                        System.out.println("before " + methodName);
                        var result = method.invoke(t, args);
                        System.out.println("after " + methodName);
                        return result;
                    });
        }

        private static boolean matches(Class<?> clazzName) {
            return CustomerService.class.isAssignableFrom(clazzName);
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            var beanClass = bean.getClass();
            if (matches(beanClass)) {
                return proxy(CustomerService.class, bean);
            }
            return bean;
        }
    }
}




```

- spring proxies (`SmartInstantiationAwareBeanPostProcessor`)

```java


    @Bean
    ApplicationRunner runner(CustomerService customerService) {
        return args -> customerService.addToCart("sku");
    }


    @Service
    static class CustomerService {

        public void addToCart(String sku) {
        }
    }

    @Bean
    static LoggedBeanPostProcessor loggedBeanPostProcessor() {
        return new LoggedBeanPostProcessor();
    }

    static class LoggedBeanPostProcessor implements
            SmartInstantiationAwareBeanPostProcessor {

        @Override
        public Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {
            return matches(beanClass) ? proxy(null, beanClass).getProxyClass(WorkApplication.class.getClassLoader()) : beanClass;
        }

        private static ProxyFactory proxy(Object target, Class<?> targetClass) {
            var pf = new ProxyFactory();
            pf.setTargetClass(targetClass);
            pf.setInterfaces(targetClass.getInterfaces());
            pf.setProxyTargetClass(true); // <4>
            pf.addAdvice((MethodInterceptor) invocation -> {
                var methodName = invocation.getMethod().getName();
                System.out.println("before " + methodName);
                var result = invocation.getMethod().invoke(target, invocation.getArguments());
                System.out.println("after " + methodName);
                return result;
            });
            if (null != target) {
                pf.setTarget(target);
            }
            return pf;
        }

        private static boolean matches(Class<?> clazzName) {
            var test = CustomerService.class.equals(clazzName);
            if (test) System.out.println(clazzName.getName() + " equals CustomerService? " + test);
            return test;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            var beanClass = bean.getClass();
            if (matches(beanClass)) {
                return proxy(bean, beanClass).getProxy();
            }
            return bean;
        }
    }

```

proxies are _not_ a common thing. definitely a framework-y kinda thing. you'renot expected to understand this. (show cartoon).

## lets talk about beans 



### the new AOT lifecycle phases are a great chance to transform the code


Wouldn't it be nice if we could automatically derive functional bean definitions from reflection-heavy Java `@Configuration` style bean definitions? 

Did you know you can [run this stuff on the jvm?](https://github.com/spring-tips/spring-boot-3-aot#run-the-aot-code-on-the-jre)

Show the generated functional style configuration that's done automatically for you!


## Writing AOT Processors 

what if you want to make decisions based on the presence of annotations, interfaces, methods, etc? You need something like the `BeanFactoryPostProcessor` from earlier. Enter the `BeanFactoryInitializationAotProcessor`. 

Show a simple `BeanFactoryInitializationAotProcessor`

```java
class BRAPForJson implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        var clzz = registeredBean.getBeanClass();
        if (Serializable.class.isAssignableFrom(clzz)) {
            return (context, beanRegistrationCode) -> {
                var hints = context.getRuntimeHints();
                hints.serialization().registerType(TypeReference.of(clzz.getName()));
            };
        }
        return null;
    }
}
```




## Code Generation with Java



```java
@Service
class SimpleService {

    void provideOrigin(Instant instant, File file) {
        System.out.println("compiled at " + instant);
        System.out.println("in file " + file);
    }
}


class SimpleServiceAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {

        var clzz = SimpleService.class;

        if (!clzz.isAssignableFrom(registeredBean.getBeanClass()))
            return null;

        return (ctx, code) -> {

            var generatedClasses = ctx.getGeneratedClasses();

            var generatedClass = generatedClasses.getOrAddForFeatureComponent(
                    clzz.getSimpleName() + "Feature", clzz,
                    b -> b.addModifiers(Modifier.PUBLIC));

            var generatedMethod = generatedClass.getMethods().add("codeGenerationFtw", build -> {

                build.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(RegisteredBean.class, "registeredBean") //
                        .addParameter(clzz, "inputBean")//
                        .returns(clzz)
                        .addCode(String.format("""
                                inputBean.provideOrigin(
                                    java.time.Instant.ofEpochMilli(
                                    %s
                                    )  , 
                                    new java.io.File ("%s")     
                                 );
                                 return  inputBean; 
                                 
                                 """, System.currentTimeMillis() + "L", new File(".").getAbsolutePath()));
            });
            var methodReference = generatedMethod.toMethodReference();
            code.addInstancePostProcessor(methodReference);
        };
    }

}
```





