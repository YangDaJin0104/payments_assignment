insert into products (
    id,
    name,
    price,
    max_purchase_quantity
) values (
             1,
             '남성 아우터 게릴라 기획전',
             59000,
             1
         );

insert into product_stocks (
    id,
    product_id,
    total_stock,
    reserved_stock,
    sold_stock
) values (
             1,
             1,
             100,
             0,
             0
         );
