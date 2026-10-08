-- 돈 눈금 다시: 원작처럼 초반은 쿠퍼. 예전 단위 1 = 1쿠퍼로 (V10 이 100 배 한 것을 되돌린다).
-- V10 을 거친 서버도, 처음 만드는 서버도 결과는 같다 (예전 값 그대로 = 쿠퍼).
UPDATE wallet SET balance = balance / 100;
UPDATE ledger SET amount = MAX(1, amount / 100);
UPDATE trade SET a_money = a_money / 100, b_money = b_money / 100;
UPDATE auction_listing SET price = MAX(1, price / 100);
UPDATE land_plot SET price = price / 100;
UPDATE shop_stock SET price = MAX(1, price / 100);
