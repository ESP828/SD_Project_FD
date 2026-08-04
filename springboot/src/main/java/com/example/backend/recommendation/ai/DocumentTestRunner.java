package com.example.backend.recommendation.ai;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


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

    System.out.println(
            "PublicRestaurant 조회 시작"
    );


    var restaurants =
            publicRestaurantRepository.findAll();


    System.out.println(
            "조회 데이터 개수 : "
            + restaurants.size()
    );


    PublicRestaurant restaurant =
            restaurants.stream()
                    .findFirst()
                    .orElse(null);
        if (restaurant == null) {

            System.out.println(
                    "테스트할 음식점 데이터가 없습니다."
            );

            return;
        }


        String document =
                documentBuilder.build(restaurant);


        System.out.println(
                "============================"
        );

        System.out.println(
                "AI DOCUMENT TEST"
        );

        System.out.println(
                "============================"
        );


        System.out.println(document);


        System.out.println(
                "============================"
        );

    }

}
