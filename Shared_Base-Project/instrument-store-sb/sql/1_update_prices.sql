-- Run ONCE on your existing instrument_store database (F5). Sets realistic rupee prices (LKR).
USE instrument_store;
GO
UPDATE products SET price = 55000.00  WHERE name = 'Acoustic Guitar';
UPDATE products SET price = 72500.00  WHERE name = 'Electric Keyboard 61-key';
UPDATE products SET price = 165000.00 WHERE name = '5-Piece Drum Set';
UPDATE products SET price = 58000.00  WHERE name = 'Student Trumpet';
-- Any sale prices you set earlier were in the old scale; clear them so they do not show as a tiny discount.
UPDATE products SET promo_price = NULL WHERE promo_price IS NOT NULL AND promo_price < 1000;
GO
SELECT name, price, promo_price FROM products;
