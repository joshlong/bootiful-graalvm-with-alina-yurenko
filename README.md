# graalvm or bust 

## the happy path for 80% of your usecases: it just works! (TM)

Go to start.spring.io, generate the `pom.xml` for an actual app consisting of `Web`, `Postgres`, `H2` , `GraalVM Native Image`, and `Data JDBC`. Build the usual `record Customer` example with `schema.sql`, `data.sql`, an `ApplicationRunner`, etc. Compile it and then run the resulting native image. Inspect it's RSS.

Here's the code:

```java
package com.example.basics;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.CrudRepository;

@SpringBootApplication
public class BasicsApplication {

    @Bean
    ApplicationRunner runner(CustomerRepository customerRepository) {
        return args -> customerRepository.findAll().forEach(System.out::println);
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(BasicsApplication.class, args);
    }
}


record Customer(@Id Integer id, String name) {
}

interface CustomerRepository extends CrudRepository<Customer, Integer> {
}
```

Add some names to the `data.sql`:

Alina
Josh
Sébastien
Thomas
Mark
Stefan


Everything works. All good, right? Not so much. not all that glitters is golden! there be dragons. blah blah.


## optimizing builds to go a bit faster for development time 

Also, there are things you can do to optimize your build. try this:

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
        <buildArgs>
            <buildArg>-Ob</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```


## back to basics 

create a new start.spring.io project with graalvm, MAVEN, and reactive web 

remove the Spring Boot annotations and gut the main method 

comment out the spring boot maven plugin 


## You Are Not Expected to Understand This

Dennis Ritchie: http://web.archive.org/web/20040206202840/http://cm.bell-labs.com/cm/cs/who/dmr/odd.html



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

Very importantly: for some reason the mechanism _hates_ it when you name the folder I named `artifact` the same as the `artifactId` of your `pom.xml`. Why? Don't know. You are not expected to understand this.

Quick show of hands: how should we pronounce "JSON"? 

1. JSOOOOON (the ON is nasal)
2. JASON (as in the bourne identity)


Run the compile: 

```shell
./mvnw  clean native:compile -Pnative    && ./target/basics 
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
            -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/${project.groupId}/${project.artifactId}
        </jvmArguments>
    </configuration>
</plugin>

```

then run the Java agent like this:

```shell 
./mvnw -DskipTests clean package spring-boot:run
```

You'll see `.json` configuration files in `target/native-image`. Analyze the `reflect-config.json`: it worked! the `.json` for the `record Album` is there. Copy and paste it to the `META-INF` folder and then comment out the Spring Boot Maven plugin again. Rebuild the native image. 


## reachability repo 

Lots of libraries have native configuration https://www.graalvm.org/native-image/libraries-and-frameworks/ (here's h2 for a specific example https://github.com/h2database/h2database/blob/master/h2/src/main/META-INF/native-image/reflect-config.json)

what if it doesnt? then what? the [graalvm reachability repository](https://github.com/oracle/graalvm-reachability-metadata). 


## the new AOT engine in Spring Boot 3

What happens when the code you're using in turn uses your code? that is, what happens when youre dealing with a framework and not just a library for whom a static `.json` configuration file is suitable enough? Something needs to provide the configuration, dynamicaly, based on the types on the classpath. Spring is well situated here. It has a new AOT engine. 


## the common case of reflection

we have seen that the AOT engine generates `.json` config files and even `.java` code, such as with the functional redefinition of the beans. you can contribute to this compile time code generation. for the simplest case, reflection, there's even a convenient annotation. Revisiting the earlier example, you could use:

```java
@RegisterReflectionForBinding( Album.class)
```

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


## lets talk about beans 

### a quick tangent on the lifecycle of a Spring application and its beans 


Before Spring Boot 3:

**RUNTIME**
`BeanFactoryPostProcessor` => `BeanDefinition`s
`BeanPostProcessor` => beans 

After Spring Boot 3:

**COMPILE**

`BeanFactoryInitializationAotProcessor` => `.json`, or `.java` code
`BeanRegistrationAotProcessor` => `.json`, or `.java`

**RUNTIME**
`BeanFactoryPostProcessor` => `BeanDefinition`s
`BeanPostProcessor` => beans


beans first exist in a primordial soup of `BeanDefinitions`

you can interact with beans at this granularity early on in the application context lifecycle as the application starts up (NOT new). 

```java
class MyBeanFactoryBeanPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (var beanName : beanFactory.getBeanDefinitionNames()) {
            System.out.println("there is a bean called ['" + beanName + "']");
            var beanDefinition = beanFactory.getBeanDefinition(beanName);
            System.out.println("\tthe bean class name is " + beanDefinition.getBeanClassName());
        }
    }
}

```


Notice the great pains we went to avoid doing anything to eagerly initialize the beans; it's too early! 

But, even at this early stage we have enough information to understand how the objects are wired together. And we can use that information to analyze the state of the application context. 

What if we had access to this information even earlier, at compile time? that's the core conceit of the new Spring Framework 6 AOT (ahead-of-time) engine. We don't get live-fire objects, just `BeanDefinition`s, but it's enough. We can inspect the `BeanDefinition`s and then do code-generation to write out the state of the context. 

See for example the `target/spring-aot/main/sources/com/example/basics/BasicsApplication__BeanFactoryRegistrations.java`. 


### the new AOT lifecycle phases are a great chance to transform the code

Enter functional config 

you know about some of the options like `@Configuration`, `@Controller`, etc. You can also use functional configuration. Its dope. and fast. and reflection-free. looks like this:

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



