package com.example.basics;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;

@SpringBootApplication
@ImportRuntimeHints(BasicsApplication.MyHints.class)
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

    static class MyHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            System.out.println("take a hint!");
            hints.resources().registerResource(new ClassPathResource("/test.xml"));
        }
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

record Customer( Integer id, String name) {
}
//
//interface CustomerRepository extends CrudRepository<Customer, Integer> {
//}