ALTER TABLE public_restaurant
    ADD FULLTEXT INDEX ft_public_restaurant_search (
        name,
        category_large_name,
        category_medium_name,
        category_small_name,
        road_address,
        lot_address
    ) WITH PARSER ngram;
