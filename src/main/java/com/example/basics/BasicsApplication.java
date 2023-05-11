package com.example.basics;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Instant;

@ImportRuntimeHints(Hints.class)
@SpringBootApplication
//@RegisterReflectionForBinding (Album.class)
public class BasicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicsApplication.class, args);
    }


    @Bean
    ApplicationRunner resources() {
        return args -> {
            var xml = new ClassPathResource("hello");
            var str = FileCopyUtils.copyToString(new InputStreamReader(xml.getInputStream()));
            System.out.println("str: " + str);
        };
    }

    @Bean
    ApplicationRunner reflection() {
        return args -> {
            var clazz = Class.forName("bootiful.aot.Album"); // does it blend??
            System.out.println("got a class? " + (clazz != null));
            var instance = (Album) clazz.getDeclaredConstructors()[0]
                    .newInstance("Guardians of the GraalVM, Soundtrack Volume 23");
            System.out.println("title: " + instance.title());
        };
    }

    @Bean
    static SimpleServiceAotProcessor simpleServiceAotProcessor() {
        return new SimpleServiceAotProcessor();
    }

    @Bean
    static MyBeanRegistrationAotProcessor myBeanRegistrationAotProcessor() {
        return new MyBeanRegistrationAotProcessor();
    }
}

class MyBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {

        if (registeredBean.getBeanName().equals("joshsSpecialBean")) {
            return new BeanRegistrationAotContribution() {
                @Override
                public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
                    generationContext.getRuntimeHints()
                            .reflection().registerType(Album.class, MemberCategory.values());
                }
            };
        }
        return null;
    }
}

class Hints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(Album.class, MemberCategory.values());
        hints.resources().registerResource(new ClassPathResource("/hello"));
    }
}

record Album(String title) {
}


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