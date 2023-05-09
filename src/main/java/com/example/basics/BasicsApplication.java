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