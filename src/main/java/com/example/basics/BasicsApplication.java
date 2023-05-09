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