-- 돈 단위: 골드 하나였던 것을 골드 · 실버 · 쿠퍼로 (1 골드 = 100 실버 = 10 000 쿠퍼).
-- 예전 1 = 지금의 1 실버. 지갑 · 장부 · 거래 · 경매 · 땅 · 개인 상점 값은 이제 쿠퍼로 저장한다 → 모두 100 배 (가치는 그대로).
UPDATE wallet SET balance = balance * 100;
UPDATE ledger SET amount = amount * 100;
UPDATE trade SET a_money = a_money * 100, b_money = b_money * 100;
UPDATE auction_listing SET price = price * 100;
UPDATE land_plot SET price = price * 100;
UPDATE shop_stock SET price = price * 100;
