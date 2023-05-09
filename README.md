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

theres a question: how did X, on the classpath, get configured. One obfvious thing; the libjrary ships with the `.json` config files. 

what if it doesnt? then what? the [graalvm reachability repository](https://github.com/oracle/graalvm-reachability-metadata). 

Make sure you uncomment the PostgreSQL dependency:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

