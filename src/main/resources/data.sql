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
insert into feeds (
    id,
    product_id,
    title,
    video_url,
    thumbnail_url,
    created_at
) values
      (
          1,
          1,
          '남성 아우터 숏폼 1',
          'https://example.com/video/1.mp4',
          'https://example.com/thumb/1.jpg',
          TIMESTAMP '2026-05-11 10:00:00'
      ),
      (
          2,
          1,
          '남성 아우터 숏폼 2',
          'https://example.com/video/2.mp4',
          'https://example.com/thumb/2.jpg',
          TIMESTAMP '2026-05-11 10:01:00'
      ),
      (
          3,
          1,
          '남성 아우터 숏폼 3',
          'https://example.com/video/3.mp4',
          'https://example.com/thumb/3.jpg',
          TIMESTAMP '2026-05-11 10:02:00'
      ),
      (
          4,
          1,
          '남성 아우터 숏폼 4',
          'https://example.com/video/4.mp4',
          'https://example.com/thumb/4.jpg',
          TIMESTAMP '2026-05-11 10:03:00'
      ),
      (
          5,
          1,
          '남성 아우터 숏폼 5',
          'https://example.com/video/5.mp4',
          'https://example.com/thumb/5.jpg',
          TIMESTAMP '2026-05-11 10:04:00'
      );
