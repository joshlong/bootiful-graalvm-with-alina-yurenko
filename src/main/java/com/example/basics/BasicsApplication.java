package com.example.basics;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Instant;

@SpringBootApplication
@ImportRuntimeHints(MyRuntimeHintsRegistrar.class)
@RegisterReflectionForBinding(Album.class)
public class BasicsApplication {


    @Bean
    ApplicationRunner resources() {
        return args -> {
            var xml = new ClassPathResource("test.xml");
            var str = FileCopyUtils.copyToString(new InputStreamReader(xml.getInputStream()));
            System.out.println("str: " + str);
        };
    }

    @Bean
    ApplicationRunner reflection() {
        return args -> {
            var clazz = Class.forName("com.example.basics.Album"); // does it blend??
            System.out.println("got a class? " + (clazz != null));
            var instance = (Album) clazz.getDeclaredConstructors()[0]
                    .newInstance("Guardians of the GraalVM, Soundtrack Volume 23");
            System.out.println("title: " + instance.title());

        };
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(BasicsApplication.class, args);
         /* new SpringApplicationBuilder()
                .sources(BasicsApplication.class)
                .main(BasicsApplication.class)
                .initializers((ApplicationContextInitializer<GenericApplicationContext>) ac ->
                        ac.registerBean(ApplicationRunner.class, () -> args1 -> System.out.println("hello functional Spring!")))
                .run(args);*/
    }

    @Bean
    static MyBeanFactoryBeanPostProcessor myBeanFactoryBeanPostProcessor() {
        return new MyBeanFactoryBeanPostProcessor();
    }

    @Bean
    static SimpleServiceAotProcessor aotProcessor() {
        return new SimpleServiceAotProcessor();
    }

    @Bean
    static MyBeanRegistrationAotProcessor myBeanRegistrationAotProcessor() {
        return new MyBeanRegistrationAotProcessor();
    }

    @Bean
    static MyBeanFactoryInitializationAotProcessor myBeanFactoryInitializationAotProcessor() {
        return new MyBeanFactoryInitializationAotProcessor();
    }
}


class MyBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(
            ConfigurableListableBeanFactory beanFactory) {

        System.out.println("i can register beans at compile time");

        return null;
    }
}


class MyBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {

        var beanClass = registeredBean.getBeanClass();

        System.out.println("going to analyze the RegisteredBean called [" +
                           registeredBean.getBeanName() +
                           "] with class [" + beanClass +
                           "]");

        return null;
    }


}


class MyRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        System.out.println("take a hint!");
        hints.resources().registerResource(new ClassPathResource("/test.xml"));
        hints.reflection().registerType(Album.class, MemberCategory.values());
    }
}

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

@Component
class MyBean implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("hello MyBean");
    }
}

record Album(String title) {
}

record Customer(Integer id, String name) {
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