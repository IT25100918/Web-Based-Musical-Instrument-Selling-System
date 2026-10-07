-- Run this ONLY AFTER the photo files exist in src/main/resources/static/images/instruments/
-- (file names below). Run it once in SSMS (F5), then restart the app.
USE instrument_store;
GO
UPDATE products SET image_url = '/images/instruments/acoustic-guitar.jpg'   WHERE name = 'Acoustic Guitar';
UPDATE products SET image_url = '/images/instruments/electric-keyboard.jpg' WHERE name = 'Electric Keyboard 61-key';
UPDATE products SET image_url = '/images/instruments/drum-set.jpg'          WHERE name = '5-Piece Drum Set';
UPDATE products SET image_url = '/images/instruments/student-trumpet.jpg'   WHERE name = 'Student Trumpet';

UPDATE categories SET image_url = '/images/instruments/cat-strings.jpg'    WHERE name = 'Strings';
UPDATE categories SET image_url = '/images/instruments/cat-keyboards.jpg'  WHERE name = 'Keyboards';
UPDATE categories SET image_url = '/images/instruments/cat-percussion.jpg' WHERE name = 'Percussion';
UPDATE categories SET image_url = '/images/instruments/cat-wind.jpg'       WHERE name = 'Wind';
GO
SELECT name, image_url FROM products;
SELECT name, image_url FROM categories;
