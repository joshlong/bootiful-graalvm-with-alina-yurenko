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





