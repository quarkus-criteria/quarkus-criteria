package com.github.adminfaces.quarkus.criteria;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class MainApp {

    public static void main(String... args) {
        try {
            Quarkus.run(QuarkusCriteriaApp.class, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
