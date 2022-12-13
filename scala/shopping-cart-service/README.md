## Running the sample code

1. Start a first node:

    ```
    sbt -Dconfig.resource=local1.conf run
    ```

2. (Optional) Start another node with different ports:

    ```
    sbt -Dconfig.resource=local2.conf run
    ```

3. Check for service readiness

    ```
    curl http://localhost:9101/ready
    ```
   
## Commands

Start docker containers
```
docker-compose up -d
```

Stop docker containers
```
docker-compose down
```

Create a postgresql database inside the docker container
```
docker exec -i shopping-cart-service_postgres-db_1 psql -U shopping-cart -t < ddl-scripts/create_tables.sql
docker exec -i shopping-cart-service_postgres-db_1 psql -U shopping-cart -t < ddl-scripts/create_user_tables.sql
```

Start the sbt project with a specific configuration
```
sbt -Dconfig.resource=local1.conf run
```

GRPC calls: AddItem, Checkout, GetCart, GetItemPopularity
``` 
grpcurl -d '{"cartId":"cart2", "itemId":"socks", "quantity":3}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem
grpcurl -d '{"cartId":"cart2"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.Checkout
grpcurl -d '{"cartId":"cart2"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetCart
grpcurl -d '{"itemId":"hoodie"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetItemPopularity
```





