package com.example.backend.recommendation.ai;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DocumentTestRunner implements CommandLineRunner {


    private final PublicRestaurantRepository publicRestaurantRepository;

    private final DocumentBuilder documentBuilder;


    public DocumentTestRunner(
            PublicRestaurantRepository publicRestaurantRepository,
            DocumentBuilder documentBuilder
    ) {
        System.out.println("DocumentTestRunner Bean 생성");
        this.publicRestaurantRepository = publicRestaurantRepository;
        this.documentBuilder = documentBuilder;

    }



@Override
public void run(String... args) {

    List<PublicRestaurant> restaurants =
            publicRestaurantRepository.findTop10By();


    for(PublicRestaurant restaurant : restaurants){

        String document =
                documentBuilder.build(restaurant);


        System.out.println("=================");
        System.out.println(document);
        System.out.println("=================");

    }

    }

}
