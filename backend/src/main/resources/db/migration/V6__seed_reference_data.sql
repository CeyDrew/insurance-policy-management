-- Reference data: 81 provinces with risk factors, products, tariff parameters and demo users.

-- Cities. Risk factor reflects traffic density / loss history (1.000 = average).
INSERT INTO city (plate_code, name, risk_factor)
SELECT plate_code, name, risk_factor FROM (
              SELECT  1 plate_code, 'Adana' name, 1.150 risk_factor FROM dual
    UNION ALL SELECT  2, 'Adıyaman', 0.950 FROM dual
    UNION ALL SELECT  3, 'Afyonkarahisar', 1.000 FROM dual
    UNION ALL SELECT  4, 'Ağrı', 0.900 FROM dual
    UNION ALL SELECT  5, 'Amasya', 0.950 FROM dual
    UNION ALL SELECT  6, 'Ankara', 1.200 FROM dual
    UNION ALL SELECT  7, 'Antalya', 1.150 FROM dual
    UNION ALL SELECT  8, 'Artvin', 0.900 FROM dual
    UNION ALL SELECT  9, 'Aydın', 1.050 FROM dual
    UNION ALL SELECT 10, 'Balıkesir', 1.000 FROM dual
    UNION ALL SELECT 11, 'Bilecik', 0.950 FROM dual
    UNION ALL SELECT 12, 'Bingöl', 0.900 FROM dual
    UNION ALL SELECT 13, 'Bitlis', 0.900 FROM dual
    UNION ALL SELECT 14, 'Bolu', 0.950 FROM dual
    UNION ALL SELECT 15, 'Burdur', 0.950 FROM dual
    UNION ALL SELECT 16, 'Bursa', 1.150 FROM dual
    UNION ALL SELECT 17, 'Çanakkale', 0.950 FROM dual
    UNION ALL SELECT 18, 'Çankırı', 0.900 FROM dual
    UNION ALL SELECT 19, 'Çorum', 0.950 FROM dual
    UNION ALL SELECT 20, 'Denizli', 1.050 FROM dual
    UNION ALL SELECT 21, 'Diyarbakır', 1.050 FROM dual
    UNION ALL SELECT 22, 'Edirne', 0.950 FROM dual
    UNION ALL SELECT 23, 'Elazığ', 0.950 FROM dual
    UNION ALL SELECT 24, 'Erzincan', 0.900 FROM dual
    UNION ALL SELECT 25, 'Erzurum', 0.950 FROM dual
    UNION ALL SELECT 26, 'Eskişehir', 1.050 FROM dual
    UNION ALL SELECT 27, 'Gaziantep', 1.100 FROM dual
    UNION ALL SELECT 28, 'Giresun', 0.900 FROM dual
    UNION ALL SELECT 29, 'Gümüşhane', 0.900 FROM dual
    UNION ALL SELECT 30, 'Hakkari', 0.900 FROM dual
    UNION ALL SELECT 31, 'Hatay', 1.050 FROM dual
    UNION ALL SELECT 32, 'Isparta', 0.950 FROM dual
    UNION ALL SELECT 33, 'Mersin', 1.100 FROM dual
    UNION ALL SELECT 34, 'İstanbul', 1.350 FROM dual
    UNION ALL SELECT 35, 'İzmir', 1.200 FROM dual
    UNION ALL SELECT 36, 'Kars', 0.900 FROM dual
    UNION ALL SELECT 37, 'Kastamonu', 0.900 FROM dual
    UNION ALL SELECT 38, 'Kayseri', 1.050 FROM dual
    UNION ALL SELECT 39, 'Kırklareli', 0.950 FROM dual
    UNION ALL SELECT 40, 'Kırşehir', 0.900 FROM dual
    UNION ALL SELECT 41, 'Kocaeli', 1.150 FROM dual
    UNION ALL SELECT 42, 'Konya', 1.050 FROM dual
    UNION ALL SELECT 43, 'Kütahya', 0.950 FROM dual
    UNION ALL SELECT 44, 'Malatya', 0.950 FROM dual
    UNION ALL SELECT 45, 'Manisa', 1.000 FROM dual
    UNION ALL SELECT 46, 'Kahramanmaraş', 1.000 FROM dual
    UNION ALL SELECT 47, 'Mardin', 0.950 FROM dual
    UNION ALL SELECT 48, 'Muğla', 1.050 FROM dual
    UNION ALL SELECT 49, 'Muş', 0.900 FROM dual
    UNION ALL SELECT 50, 'Nevşehir', 0.900 FROM dual
    UNION ALL SELECT 51, 'Niğde', 0.900 FROM dual
    UNION ALL SELECT 52, 'Ordu', 0.950 FROM dual
    UNION ALL SELECT 53, 'Rize', 0.900 FROM dual
    UNION ALL SELECT 54, 'Sakarya', 1.050 FROM dual
    UNION ALL SELECT 55, 'Samsun', 1.050 FROM dual
    UNION ALL SELECT 56, 'Siirt', 0.900 FROM dual
    UNION ALL SELECT 57, 'Sinop', 0.900 FROM dual
    UNION ALL SELECT 58, 'Sivas', 0.950 FROM dual
    UNION ALL SELECT 59, 'Tekirdağ', 1.050 FROM dual
    UNION ALL SELECT 60, 'Tokat', 0.950 FROM dual
    UNION ALL SELECT 61, 'Trabzon', 1.000 FROM dual
    UNION ALL SELECT 62, 'Tunceli', 0.900 FROM dual
    UNION ALL SELECT 63, 'Şanlıurfa', 1.050 FROM dual
    UNION ALL SELECT 64, 'Uşak', 0.950 FROM dual
    UNION ALL SELECT 65, 'Van', 0.950 FROM dual
    UNION ALL SELECT 66, 'Yozgat', 0.900 FROM dual
    UNION ALL SELECT 67, 'Zonguldak', 0.950 FROM dual
    UNION ALL SELECT 68, 'Aksaray', 0.950 FROM dual
    UNION ALL SELECT 69, 'Bayburt', 0.900 FROM dual
    UNION ALL SELECT 70, 'Karaman', 0.900 FROM dual
    UNION ALL SELECT 71, 'Kırıkkale', 0.950 FROM dual
    UNION ALL SELECT 72, 'Batman', 0.950 FROM dual
    UNION ALL SELECT 73, 'Şırnak', 0.900 FROM dual
    UNION ALL SELECT 74, 'Bartın', 0.900 FROM dual
    UNION ALL SELECT 75, 'Ardahan', 0.900 FROM dual
    UNION ALL SELECT 76, 'Iğdır', 0.900 FROM dual
    UNION ALL SELECT 77, 'Yalova', 1.000 FROM dual
    UNION ALL SELECT 78, 'Karabük', 0.900 FROM dual
    UNION ALL SELECT 79, 'Kilis', 0.900 FROM dual
    UNION ALL SELECT 80, 'Osmaniye', 0.950 FROM dual
    UNION ALL SELECT 81, 'Düzce', 0.950 FROM dual
);

-- Products. CASCO and HOME are value-rated (VALUE_RATE * insured value), TRAFFIC and HEALTH start from base_premium.
INSERT INTO product (code, name, description, asset_type, base_premium, min_premium, tax_rate, max_installments, term_months)
VALUES ('TRAFFIC', 'Zorunlu Trafik Sigortası', 'Karayolları motorlu araçlar zorunlu mali sorumluluk sigortası', 'VEHICLE', 4500, 2500, 0.05, 4, 12);
INSERT INTO product (code, name, description, asset_type, base_premium, min_premium, tax_rate, max_installments, term_months)
VALUES ('CASCO', 'Kasko Sigortası', 'Aracın çarpma, çalınma, yanma ve doğal afet hasarlarına karşı teminat', 'VEHICLE', 0, 6000, 0.05, 9, 12);
INSERT INTO product (code, name, description, asset_type, base_premium, min_premium, tax_rate, max_installments, term_months)
VALUES ('HOME', 'Konut Sigortası', 'Konut binası ve eşyalarına yangın, hırsızlık, su basması teminatı', 'PROPERTY', 0, 1500, 0.05, 6, 12);
INSERT INTO product (code, name, description, asset_type, base_premium, min_premium, tax_rate, max_installments, term_months)
VALUES ('HEALTH', 'Tamamlayıcı Sağlık Sigortası', 'SGK anlaşmalı özel hastanelerde fark ücreti teminatı', 'PERSON', 9000, 4000, 0, 12, 12);

-- Tariff parameters (range rows).
INSERT INTO tariff_parameter (product_id, param_code, min_value, max_value, factor, description)
SELECT p.id, t.param_code, t.min_value, t.max_value, t.factor, t.description
FROM product p
JOIN (
              SELECT 'TRAFFIC' product_code, 'DRIVER_AGE' param_code, 18 min_value,  25 max_value, 1.400000 factor, 'Genç sürücü' description FROM dual
    UNION ALL SELECT 'TRAFFIC', 'DRIVER_AGE',  26,  35, 1.100000, 'Sürücü 26-35' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'DRIVER_AGE',  36,  55, 1.000000, 'Sürücü 36-55' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'DRIVER_AGE',  56,  65, 1.050000, 'Sürücü 56-65' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'DRIVER_AGE',  66, 120, 1.250000, 'Sürücü 66+' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'VEHICLE_AGE',  0,   5, 1.000000, 'Araç yaşı 0-5' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'VEHICLE_AGE',  6,  15, 1.050000, 'Araç yaşı 6-15' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'VEHICLE_AGE', 16,  99, 1.100000, 'Araç yaşı 16+' FROM dual
    -- No-claims discount steps (hasarsızlık basamakları); step 4 is the entry step.
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 1, 1, 1.350000, '1. basamak (+%35)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 2, 2, 1.150000, '2. basamak (+%15)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 3, 3, 1.050000, '3. basamak (+%5)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 4, 4, 1.000000, '4. basamak (giriş)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 5, 5, 0.900000, '5. basamak (-%10)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 6, 6, 0.800000, '6. basamak (-%20)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 7, 7, 0.700000, '7. basamak (-%30)' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'NCD_STEP', 8, 8, 0.600000, '8. basamak (-%40)' FROM dual
    UNION ALL SELECT 'CASCO', 'VALUE_RATE', 0, 99999999, 0.022000, 'Araç değerinin %2,2si' FROM dual
    UNION ALL SELECT 'CASCO', 'DRIVER_AGE',  18,  25, 1.350000, 'Genç sürücü' FROM dual
    UNION ALL SELECT 'CASCO', 'DRIVER_AGE',  26,  35, 1.100000, 'Sürücü 26-35' FROM dual
    UNION ALL SELECT 'CASCO', 'DRIVER_AGE',  36,  60, 1.000000, 'Sürücü 36-60' FROM dual
    UNION ALL SELECT 'CASCO', 'DRIVER_AGE',  61, 120, 1.150000, 'Sürücü 61+' FROM dual
    UNION ALL SELECT 'CASCO', 'VEHICLE_AGE',  0,   3, 1.000000, 'Araç yaşı 0-3' FROM dual
    UNION ALL SELECT 'CASCO', 'VEHICLE_AGE',  4,   7, 1.100000, 'Araç yaşı 4-7' FROM dual
    UNION ALL SELECT 'CASCO', 'VEHICLE_AGE',  8,  12, 1.250000, 'Araç yaşı 8-12' FROM dual
    UNION ALL SELECT 'CASCO', 'VEHICLE_AGE', 13,  99, 1.400000, 'Araç yaşı 13+' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 1, 1, 1.300000, '1. basamak' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 2, 2, 1.150000, '2. basamak' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 3, 3, 1.050000, '3. basamak' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 4, 4, 1.000000, '4. basamak (giriş)' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 5, 5, 0.850000, '5. basamak' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 6, 6, 0.750000, '6. basamak' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 7, 7, 0.650000, '7. basamak' FROM dual
    UNION ALL SELECT 'CASCO', 'NCD_STEP', 8, 8, 0.600000, '8. basamak' FROM dual
    UNION ALL SELECT 'HOME', 'VALUE_RATE', 0, 999999999, 0.001500, 'Konut değerinin binde 1,5i' FROM dual
    UNION ALL SELECT 'HOME', 'BUILDING_AGE',  0,  10, 1.000000, 'Bina yaşı 0-10' FROM dual
    UNION ALL SELECT 'HOME', 'BUILDING_AGE', 11,  25, 1.150000, 'Bina yaşı 11-25' FROM dual
    UNION ALL SELECT 'HOME', 'BUILDING_AGE', 26, 300, 1.350000, 'Bina yaşı 26+' FROM dual
    UNION ALL SELECT 'HEALTH', 'INSURED_AGE',  0,  17, 0.700000, 'Sigortalı 0-17' FROM dual
    UNION ALL SELECT 'HEALTH', 'INSURED_AGE', 18,  30, 0.850000, 'Sigortalı 18-30' FROM dual
    UNION ALL SELECT 'HEALTH', 'INSURED_AGE', 31,  45, 1.000000, 'Sigortalı 31-45' FROM dual
    UNION ALL SELECT 'HEALTH', 'INSURED_AGE', 46,  60, 1.450000, 'Sigortalı 46-60' FROM dual
    UNION ALL SELECT 'HEALTH', 'INSURED_AGE', 61, 120, 2.100000, 'Sigortalı 61+' FROM dual
) t ON t.product_code = p.code;

-- Tariff parameters (categorical rows).
INSERT INTO tariff_parameter (product_id, param_code, param_key, factor, description)
SELECT p.id, t.param_code, t.param_key, t.factor, t.description
FROM product p
JOIN (
              SELECT 'TRAFFIC' product_code, 'USAGE_TYPE' param_code, 'PRIVATE' param_key, 1.000000 factor, 'Hususi' description FROM dual
    UNION ALL SELECT 'TRAFFIC', 'USAGE_TYPE', 'COMMERCIAL', 1.300000, 'Ticari' FROM dual
    UNION ALL SELECT 'TRAFFIC', 'USAGE_TYPE', 'TAXI', 1.600000, 'Taksi' FROM dual
    UNION ALL SELECT 'CASCO', 'USAGE_TYPE', 'PRIVATE', 1.000000, 'Hususi' FROM dual
    UNION ALL SELECT 'CASCO', 'USAGE_TYPE', 'COMMERCIAL', 1.250000, 'Ticari' FROM dual
    UNION ALL SELECT 'CASCO', 'USAGE_TYPE', 'TAXI', 1.500000, 'Taksi' FROM dual
    UNION ALL SELECT 'HOME', 'CONSTRUCTION_TYPE', 'REINFORCED_CONCRETE', 1.000000, 'Betonarme' FROM dual
    UNION ALL SELECT 'HOME', 'CONSTRUCTION_TYPE', 'STEEL', 0.950000, 'Çelik' FROM dual
    UNION ALL SELECT 'HOME', 'CONSTRUCTION_TYPE', 'MASONRY', 1.300000, 'Yığma' FROM dual
    UNION ALL SELECT 'HOME', 'CONSTRUCTION_TYPE', 'WOOD', 1.500000, 'Ahşap' FROM dual
) t ON t.product_code = p.code;

-- Demo users. Password for all: Demo123! (BCrypt). Demo-only credentials, documented in README.
INSERT INTO app_user (username, password_hash, full_name, email, role)
VALUES ('admin', '$2a$10$xJhG6w4ZcF/pM6YK7eDQuuijR76KhLU.JL14PYVtZqKZgQPRBzImy', 'Sistem Yöneticisi', 'admin@demo.local', 'ADMIN');
INSERT INTO app_user (username, password_hash, full_name, email, role)
VALUES ('agent', '$2a$10$xJhG6w4ZcF/pM6YK7eDQuuijR76KhLU.JL14PYVtZqKZgQPRBzImy', 'Ayşe Yılmaz', 'agent@demo.local', 'AGENT');
INSERT INTO app_user (username, password_hash, full_name, email, role)
VALUES ('agent2', '$2a$10$xJhG6w4ZcF/pM6YK7eDQuuijR76KhLU.JL14PYVtZqKZgQPRBzImy', 'Mehmet Kaya', 'agent2@demo.local', 'AGENT');
INSERT INTO app_user (username, password_hash, full_name, email, role)
VALUES ('claims', '$2a$10$xJhG6w4ZcF/pM6YK7eDQuuijR76KhLU.JL14PYVtZqKZgQPRBzImy', 'Zeynep Demir', 'claims@demo.local', 'CLAIM_HANDLER');
