# graalvm or bust 

## back to basics 

create a new start.spring.io project with graalvm, MAVEN, and reactive web 

remove the Spring Boot annotations and gut the main method 

comment out the spring boot maven plugin 



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
        var clazz = Class.forName("com.example.basics.Album"); // does it blend??
        System.out.println("got a class? " + (clazz != null ));
        var instance = (Album) clazz.getDeclaredConstructors()[0]
                .newInstance("Guardians of the GraalVM, Soundtrack Volume 23");
        System.out.println("title: " + instance.title());
    }
}

record Album(String title) {
}
```

Run the compilation again. It'll successfully get the `class` instance, but it'll fail to enumerate the constructors. But don't tell the audience that. What's going wrong? Who knows. the java agent knows. let's plug that in. We could do it ourselves, manually, but it's easier to use Spring Boot to do this. 

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

It'll work


## A more realistic example

Let's restore Spring Boot. Indeed, let's add some dependencies. Go to start.spring.io, generate the `pom.xml` for an actual app consisting of `Web`, `Postgres`, `H2` , `GraalVM Native Image`, and `Data JDBC`. Build the usual `record Customer` example with `schema.sql`, `data.sql`, an `ApplicationRunner`, etc. Compile it and then run the resulting native image. Inspect it's RSS. 

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

## reachability repo 

theres a question: how did X, on the classpath, get configured. One obfvious thing; the libjrary ships with the `.json` config files. H2 does. it's dope like that. 

what if it doesnt? then what? the [graalvm reachability repository](https://github.com/oracle/graalvm-reachability-metadata). 

Make sure you uncomment the PostgreSQL dependency:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

if we compile the application again, it _still_ works, even though PostgreSQL doesn't ship with it. What gives? [Enter the GraalVM reachability repository](https://github.com/oracle/graalvm-reachability-metadata)! 


## the new AOT engine in Spring Boot 3

What happens when the code youre using in turn uses your code? that is, what happens when youre dealing with a framework and not just a library for whom a static `.json` configuration file is suitable enough? Something needs to provide the configuration, dynamicaly, based on the types on the classpath. Spring is well situated here. It has a new AOT engine. 

Make sure to sort of remove all the stuff beyond `spring-boot-starter`. We don't need Spring Data JDBC, the web support, etc. Comment it all out. We just want core Spring Boot and Spring Framework.

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

### a quick background thread on functional configuration 

```java
var context  = new SpringApplicationBuilder()
                .sources(BasicsApplication.class)
                .main(BasicsApplication.class)
                .initializers((ApplicationContextInitializer<GenericApplicationContext>) ac ->
                        ac.registerBean(ApplicationRunner.class, () -> args1 -> System.out.println("hello functional Spring!")))
                .run(args);
```

wouldn't it be nice if we could automatically derive functional bean definitions from reflection-heavy Java `@Configuration` style bean definnitions? We can thanks to Spring Boot 3s new AOT engine, which extends the bean graph to compile time.

### a quick background thread on the `BeanFactory`

spring doesn't care where you source the configuration from: it could come from `@Configuration`, component-scanning (`@Service`, `@Controller` , etc), XML, functional config, etc. 

spring has a lifecycle 

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

But, even at this early stage we have enough infromation to understand how the objects are wired together. And we can use that information to analzye the state of the application context. 

What if we had access to this information even earlier, at compile time? that's the core conceit of the new Spring Framework 6 AOT (ahead-of-time) engine. We don't get live-fire objects, just `BeanDefinition`s, but it's enough. We can inspect the `BeanDefinition`s and then do code-generation to write out the state of the context. 

See for example the `target/spring-aot/main/sources/com/example/basics/BasicsApplication__BeanFactoryRegistrations.java`. 

## contributing to the `BeanFactory` AOT meta model at compile time 

we have seen that the AOT engine generates `.json` config files and even `.java` code, such as with the functional redefinition of the beans. you can contribute to this compile time code generation. for the simplest case, reflection, there's even a convenient annotation. Revisiting the earlier example, you could use:

```java
@RegisterReflectionForBinding(BasicsApplication.Album.class)
```

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
            hints.resources().registerResource(new ClassPathResource("/test.xml"));
        }
    }

```


