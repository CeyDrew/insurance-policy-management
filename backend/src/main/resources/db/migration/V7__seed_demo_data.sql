-- Demo data: 50 customers with vehicles/properties, 200 policies with installment plans, payments and claims.
--
-- * Deterministic: DBMS_RANDOM is seeded, so every clean database gets the same data set.
-- * Dates are relative to the migration date, so a fresh database always contains policies that are
--   about to expire, overdue installments, etc. for the batch job demos.
-- * TCKN / VKN values are generated with valid checksums.
-- * Premiums here use a simplified version of the tariff; the real engine is PKG_PREMIUM (phase 4).

DECLARE
    TYPE t_strings IS TABLE OF VARCHAR2(100);
    TYPE t_numbers IS TABLE OF NUMBER INDEX BY PLS_INTEGER;

    c_male_names   CONSTANT t_strings := t_strings(
        'Ahmet', 'Mehmet', 'Mustafa', 'Ali', 'Hüseyin', 'Hasan', 'İbrahim', 'Emre', 'Burak', 'Murat',
        'Can', 'Kerem', 'Oğuz', 'Serkan', 'Tolga', 'Cem', 'Barış', 'Onur', 'Volkan', 'Eren');
    c_female_names CONSTANT t_strings := t_strings(
        'Ayşe', 'Fatma', 'Emine', 'Hatice', 'Zeynep', 'Elif', 'Merve', 'Esra', 'Büşra', 'Selin',
        'Deniz', 'Ebru', 'Gamze', 'Pınar', 'Sevgi', 'Derya', 'Özlem', 'Tuğba', 'Ceren', 'İrem');
    c_last_names   CONSTANT t_strings := t_strings(
        'Yılmaz', 'Kaya', 'Demir', 'Şahin', 'Çelik', 'Yıldız', 'Yıldırım', 'Öztürk', 'Aydın', 'Özdemir',
        'Arslan', 'Doğan', 'Kılıç', 'Aslan', 'Çetin', 'Kara', 'Koç', 'Kurt', 'Özkan', 'Şimşek',
        'Polat', 'Korkmaz', 'Erdoğan', 'Güneş', 'Aksoy');
    c_companies    CONSTANT t_strings := t_strings(
        'Anadolu Lojistik A.Ş.', 'Ege Gıda Sanayi Ltd. Şti.', 'Marmara İnşaat A.Ş.',
        'Karadeniz Tekstil Ltd. Şti.', 'Akdeniz Turizm A.Ş.', 'Başkent Teknoloji A.Ş.',
        'Toros Tarım Ürünleri Ltd. Şti.', 'Boğaziçi Danışmanlık A.Ş.');
    c_streets      CONSTANT t_strings := t_strings(
        'Atatürk Cad.', 'Cumhuriyet Cad.', 'İstiklal Cad.', 'Gazi Mustafa Kemal Bulv.', 'Fevzi Çakmak Sok.',
        'Menekşe Sok.', 'Lale Sok.', 'Hürriyet Cad.', 'Okul Sok.', 'Çamlık Sok.');
    c_brands       CONSTANT t_strings := t_strings(
        'Fiat', 'Renault', 'Toyota', 'Volkswagen', 'Hyundai', 'Ford', 'Honda', 'BMW', 'Mercedes-Benz',
        'Dacia', 'Peugeot', 'Togg', 'Ford');
    c_models       CONSTANT t_strings := t_strings(
        'Egea', 'Clio', 'Corolla', 'Passat', 'i20', 'Focus', 'Civic', '320i', 'C200',
        'Duster', '3008', 'T10X', 'Transit');
    -- New-vehicle list prices (TRY) for the models above, depreciated by vehicle age.
    c_new_values   CONSTANT t_strings := t_strings(
        '1300000', '1250000', '1900000', '2600000', '1200000', '1600000', '2000000', '3800000', '4200000',
        '1500000', '2400000', '2100000', '2200000');
    c_big_cities   CONSTANT t_strings := t_strings('34', '34', '34', '6', '6', '35', '35', '16', '7', '1', '41', '27');
    c_installment_options CONSTANT t_strings := t_strings('1', '2', '3', '4', '6', '9', '12');

    c_customer_count   CONSTANT PLS_INTEGER := 50;
    c_individual_count CONSTANT PLS_INTEGER := 42;
    c_policy_target    CONSTANT PLS_INTEGER := 200;

    v_today         DATE := TRUNC(SYSDATE);
    v_policy_count  PLS_INTEGER := 0;
    v_agent_ids     t_numbers;
    v_handler_id    NUMBER;

    -- customer index -> attributes
    v_cust_ids      t_numbers;
    v_cust_city     t_numbers;
    -- asset lists (index -> id / owner index)
    v_vehicle_ids   t_numbers;
    v_vehicle_owner t_numbers;
    v_property_ids  t_numbers;
    v_property_owner t_numbers;
    v_health_owner  t_numbers;

    v_traffic_id NUMBER;
    v_casco_id   NUMBER;
    v_home_id    NUMBER;
    v_health_id  NUMBER;

    FUNCTION rnd_int(p_low PLS_INTEGER, p_high PLS_INTEGER) RETURN PLS_INTEGER IS
    BEGIN
        RETURN TRUNC(DBMS_RANDOM.VALUE(p_low, p_high + 1));
    END;

    FUNCTION chance(p_probability NUMBER) RETURN BOOLEAN IS
    BEGIN
        RETURN DBMS_RANDOM.VALUE < p_probability;
    END;

    FUNCTION pick(p_list t_strings) RETURN VARCHAR2 IS
    BEGIN
        RETURN p_list(rnd_int(1, p_list.COUNT));
    END;

    FUNCTION ascii_slug(p_text VARCHAR2) RETURN VARCHAR2 IS
    BEGIN
        RETURN LOWER(TRANSLATE(p_text, 'çğıöşüÇĞİÖŞÜ', 'cgiosuCGIOSU'));
    END;

    -- TCKN: d10 = ((d1+d3+d5+d7+d9)*7 - (d2+d4+d6+d8)) mod 10, d11 = (d1+..+d10) mod 10
    FUNCTION generate_tckn RETURN VARCHAR2 IS
        v_digits t_numbers;
        v_odd    PLS_INTEGER := 0;
        v_even   PLS_INTEGER := 0;
        v_result VARCHAR2(11);
    BEGIN
        v_digits(1) := rnd_int(1, 9);
        FOR i IN 2 .. 9 LOOP
            v_digits(i) := rnd_int(0, 9);
        END LOOP;
        FOR i IN 1 .. 9 LOOP
            IF MOD(i, 2) = 1 THEN v_odd := v_odd + v_digits(i); ELSE v_even := v_even + v_digits(i); END IF;
        END LOOP;
        v_digits(10) := MOD(MOD(v_odd * 7 - v_even, 10) + 10, 10);
        v_digits(11) := MOD(v_odd + v_even + v_digits(10), 10);
        FOR i IN 1 .. 11 LOOP
            v_result := v_result || TO_CHAR(v_digits(i));
        END LOOP;
        RETURN v_result;
    END;

    -- VKN (10 digits): weighted checksum over the first 9 digits defines the 10th.
    FUNCTION generate_vkn RETURN VARCHAR2 IS
        v_digits t_numbers;
        v_tmp    PLS_INTEGER;
        v_val    PLS_INTEGER;
        v_sum    PLS_INTEGER := 0;
        v_result VARCHAR2(10);
    BEGIN
        FOR i IN 1 .. 9 LOOP
            v_digits(i) := rnd_int(0, 9);
            v_tmp := MOD(v_digits(i) + 10 - i, 10);
            v_val := MOD(v_tmp * POWER(2, 10 - i), 9);
            IF v_tmp <> 0 AND v_val = 0 THEN
                v_val := 9;
            END IF;
            v_sum := v_sum + v_val;
            v_result := v_result || TO_CHAR(v_digits(i));
        END LOOP;
        RETURN v_result || TO_CHAR(MOD(10 - MOD(v_sum, 10), 10));
    END;

    FUNCTION range_factor(p_product_id NUMBER, p_code VARCHAR2, p_value NUMBER) RETURN NUMBER IS
        v_factor NUMBER;
    BEGIN
        SELECT factor INTO v_factor
          FROM tariff_parameter
         WHERE product_id = p_product_id AND param_code = p_code
           AND p_value BETWEEN min_value AND max_value;
        RETURN v_factor;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN 1;
    END;

    FUNCTION key_factor(p_product_id NUMBER, p_code VARCHAR2, p_key VARCHAR2) RETURN NUMBER IS
        v_factor NUMBER;
    BEGIN
        SELECT factor INTO v_factor
          FROM tariff_parameter
         WHERE product_id = p_product_id AND param_code = p_code AND param_key = p_key;
        RETURN v_factor;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN 1;
    END;

    FUNCTION age_at(p_birth_date DATE, p_date DATE) RETURN NUMBER IS
    BEGIN
        -- Corporate customers have no birth date; treat the driver as a 40-year-old.
        RETURN NVL(TRUNC(MONTHS_BETWEEN(p_date, p_birth_date) / 12), 40);
    END;

    FUNCTION net_premium(p_product_id NUMBER, p_customer_id NUMBER, p_vehicle_id NUMBER,
                         p_property_id NUMBER, p_ncd_step NUMBER, p_start_date DATE) RETURN NUMBER IS
        v_code       product.code%TYPE;
        v_base       NUMBER;
        v_min        NUMBER;
        v_birth_date DATE;
        v_city_risk  NUMBER;
        v_premium    NUMBER;
        v_vehicle    vehicle%ROWTYPE;
        v_property   property%ROWTYPE;
        v_prop_risk  NUMBER;
    BEGIN
        SELECT code, base_premium, min_premium INTO v_code, v_base, v_min FROM product WHERE id = p_product_id;
        SELECT c.birth_date, ci.risk_factor INTO v_birth_date, v_city_risk
          FROM customer c JOIN city ci ON ci.id = c.city_id WHERE c.id = p_customer_id;

        IF v_code IN ('TRAFFIC', 'CASCO') THEN
            SELECT * INTO v_vehicle FROM vehicle WHERE id = p_vehicle_id;
            v_premium := CASE v_code
                             WHEN 'TRAFFIC' THEN v_base
                             ELSE v_vehicle.vehicle_value * range_factor(p_product_id, 'VALUE_RATE', v_vehicle.vehicle_value)
                         END
                         * v_city_risk
                         * range_factor(p_product_id, 'DRIVER_AGE', age_at(v_birth_date, p_start_date))
                         * range_factor(p_product_id, 'VEHICLE_AGE', EXTRACT(YEAR FROM p_start_date) - v_vehicle.model_year)
                         * key_factor(p_product_id, 'USAGE_TYPE', v_vehicle.usage_type)
                         * range_factor(p_product_id, 'NCD_STEP', p_ncd_step);
        ELSIF v_code = 'HOME' THEN
            SELECT * INTO v_property FROM property WHERE id = p_property_id;
            SELECT risk_factor INTO v_prop_risk FROM city WHERE id = v_property.city_id;
            v_premium := v_property.property_value * range_factor(p_product_id, 'VALUE_RATE', v_property.property_value)
                         * v_prop_risk
                         * range_factor(p_product_id, 'BUILDING_AGE', EXTRACT(YEAR FROM p_start_date) - v_property.building_year)
                         * key_factor(p_product_id, 'CONSTRUCTION_TYPE', v_property.construction_type);
        ELSE
            v_premium := v_base * range_factor(p_product_id, 'INSURED_AGE', age_at(v_birth_date, p_start_date));
        END IF;

        RETURN ROUND(GREATEST(v_premium, v_min), 2);
    END;

    FUNCTION create_policy(p_product_id NUMBER, p_customer_id NUMBER, p_vehicle_id NUMBER, p_property_id NUMBER,
                           p_status VARCHAR2, p_start_date DATE, p_renewed_from NUMBER) RETURN NUMBER IS
        v_id            NUMBER;
        v_code          product.code%TYPE;
        v_tax_rate      NUMBER;
        v_max_inst      NUMBER;
        v_ncd           NUMBER := 4;
        v_net           NUMBER;
        v_tax           NUMBER;
        v_gross         NUMBER;
        v_count         PLS_INTEGER;
        v_end_date      DATE := ADD_MONTHS(p_start_date, 12);
        v_issued_at     TIMESTAMP;
        v_cancelled_at  TIMESTAMP;
        v_cancel_reason VARCHAR2(300);
        v_refund        NUMBER;
        v_quote_until   DATE;
        v_prefix        VARCHAR2(3);
        v_inst_amount   NUMBER;
        v_due_date      DATE;
        v_inst_status   VARCHAR2(20);
        v_paid_at       TIMESTAMP;
        v_inst_id       NUMBER;
        v_agent_id      NUMBER;
        v_amount        NUMBER;
        v_method        VARCHAR2(20);
    BEGIN
        SELECT code, tax_rate, max_installments INTO v_code, v_tax_rate, v_max_inst FROM product WHERE id = p_product_id;
        v_prefix := CASE v_code WHEN 'TRAFFIC' THEN 'TRF' WHEN 'CASCO' THEN 'KSK' WHEN 'HOME' THEN 'KNT' ELSE 'SGL' END;

        IF v_code IN ('TRAFFIC', 'CASCO') THEN
            v_ncd := rnd_int(2, 8);
        END IF;

        v_net := net_premium(p_product_id, p_customer_id, p_vehicle_id, p_property_id, v_ncd, p_start_date);
        v_tax := ROUND(v_net * v_tax_rate, 2);
        v_gross := v_net + v_tax;

        LOOP
            v_count := TO_NUMBER(pick(c_installment_options));
            EXIT WHEN v_count <= v_max_inst;
        END LOOP;
        IF p_status = 'SUSPENDED' THEN
            v_count := LEAST(v_max_inst, 6);
        END IF;

        IF p_status = 'QUOTE' THEN
            v_quote_until := v_today + rnd_int(5, 15);
        ELSE
            v_issued_at := CAST(p_start_date - rnd_int(1, 10) AS TIMESTAMP) + NUMTODSINTERVAL(rnd_int(9, 17), 'HOUR');
        END IF;

        IF p_status = 'CANCELLED' THEN
            v_cancelled_at := CAST(p_start_date + rnd_int(10, GREATEST(10, v_today - p_start_date)) AS TIMESTAMP);
            v_cancel_reason := pick(t_strings('Araç satışı', 'Müşteri talebi', 'Başka şirkete geçiş', 'Taşınma'));
            -- Day-based (gün esaslı) refund of the unused period.
            v_refund := ROUND(v_gross * GREATEST(v_end_date - TRUNC(CAST(v_cancelled_at AS DATE)), 0)
                              / (v_end_date - p_start_date), 2);
        END IF;

        -- Local functions cannot be called from SQL, so every random value is resolved into a variable first.
        v_agent_id := v_agent_ids(rnd_int(1, v_agent_ids.COUNT));
        SELECT policy_seq.NEXTVAL INTO v_id FROM dual;
        INSERT INTO policy (id, policy_number, product_id, customer_id, vehicle_id, property_id, agent_id, status,
                            start_date, end_date, ncd_step, net_premium, tax_amount, gross_premium, installment_count,
                            quote_valid_until, issued_at, cancelled_at, cancel_reason, refund_amount,
                            renewed_from_policy_id, created_by)
        VALUES (v_id, v_prefix || '-' || TO_CHAR(p_start_date, 'YYYY') || '-' || LPAD(v_id, 6, '0'),
                p_product_id, p_customer_id, p_vehicle_id, p_property_id, v_agent_id,
                p_status, p_start_date, v_end_date, v_ncd, v_net, v_tax, v_gross, v_count,
                v_quote_until, v_issued_at, v_cancelled_at, v_cancel_reason, v_refund, p_renewed_from, 'seed');
        v_policy_count := v_policy_count + 1;

        IF p_status = 'QUOTE' THEN
            RETURN v_id;
        END IF;

        v_inst_amount := TRUNC(v_gross / v_count, 2);
        FOR n IN 1 .. v_count LOOP
            v_due_date := ADD_MONTHS(p_start_date, n - 1);
            v_paid_at := NULL;
            IF v_due_date >= v_today THEN
                v_inst_status := 'PENDING';
            ELSIF p_status IN ('EXPIRED', 'RENEWED') THEN
                v_inst_status := 'PAID';
            ELSIF p_status = 'CANCELLED' THEN
                v_inst_status := CASE WHEN v_due_date <= CAST(v_cancelled_at AS DATE) THEN 'PAID' ELSE 'CANCELLED' END;
            ELSIF p_status = 'SUSPENDED' THEN
                v_inst_status := CASE WHEN n = 1 THEN 'PAID' ELSE 'OVERDUE' END;
            ELSE
                -- ACTIVE: mostly paid; some already flagged overdue, some still PENDING for the overdue job to catch.
                v_inst_status := CASE
                                     WHEN chance(0.90) THEN 'PAID'
                                     WHEN chance(0.50) THEN 'OVERDUE'
                                     ELSE 'PENDING'
                                 END;
            END IF;
            IF p_status = 'CANCELLED' AND v_due_date >= v_today THEN
                v_inst_status := 'CANCELLED';
            END IF;
            IF v_inst_status = 'PAID' THEN
                v_paid_at := CAST(v_due_date - rnd_int(0, 5) AS TIMESTAMP) + NUMTODSINTERVAL(rnd_int(8, 20), 'HOUR');
            END IF;

            v_amount := CASE WHEN n = v_count THEN v_gross - v_inst_amount * (v_count - 1) ELSE v_inst_amount END;
            SELECT installment_seq.NEXTVAL INTO v_inst_id FROM dual;
            INSERT INTO installment (id, policy_id, installment_no, due_date, amount, status, paid_at, created_by)
            VALUES (v_inst_id, v_id, n, v_due_date, v_amount, v_inst_status, v_paid_at, 'seed');

            IF v_inst_status = 'PAID' THEN
                v_method := pick(t_strings('CREDIT_CARD', 'CREDIT_CARD', 'BANK_TRANSFER', 'CASH'));
                INSERT INTO payment (installment_id, amount, payment_date, payment_method, reference_no, created_by)
                VALUES (v_inst_id, v_amount, v_paid_at, v_method, 'PAY-' || LPAD(v_inst_id, 8, '0'), 'seed');
            END IF;
        END LOOP;

        RETURN v_id;
    END;

    -- Creates the policy history of one insured asset: optionally a previous-term policy
    -- (RENEWED or EXPIRED) followed by the current one.
    PROCEDURE create_chain(p_product_id NUMBER, p_customer_id NUMBER, p_vehicle_id NUMBER, p_property_id NUMBER) IS
        v_prev_id     NUMBER;
        v_prev_start  DATE;
        v_prev_status VARCHAR2(20);
        v_start       DATE;
        v_status      VARCHAR2(20);
        v_roll        NUMBER;
        v_id          NUMBER;
    BEGIN
        IF v_policy_count >= c_policy_target THEN
            RETURN;
        END IF;

        IF v_policy_count <= c_policy_target - 2 AND chance(0.35) THEN
            v_prev_start := v_today - rnd_int(380, 700);
            v_prev_status := CASE WHEN chance(0.75) THEN 'RENEWED' ELSE 'EXPIRED' END;
            v_prev_id := create_policy(p_product_id, p_customer_id, p_vehicle_id, p_property_id,
                                       v_prev_status, v_prev_start, NULL);
            IF v_prev_status = 'EXPIRED' THEN
                RETURN; -- customer did not renew
            END IF;
            v_start := ADD_MONTHS(v_prev_start, 12);
            v_status := 'ACTIVE';
        ELSE
            v_roll := DBMS_RANDOM.VALUE;
            v_status := CASE
                            WHEN v_roll < 0.08 THEN 'QUOTE'
                            WHEN v_roll < 0.17 THEN 'CANCELLED'
                            WHEN v_roll < 0.23 THEN 'SUSPENDED'
                            WHEN v_roll < 0.30 THEN 'EXPIRED'
                            ELSE 'ACTIVE'
                        END;
            v_start := CASE v_status
                           WHEN 'QUOTE'     THEN v_today + rnd_int(1, 20)
                           WHEN 'CANCELLED' THEN v_today - rnd_int(30, 300)
                           WHEN 'SUSPENDED' THEN v_today - rnd_int(100, 200)
                           WHEN 'EXPIRED'   THEN v_today - rnd_int(380, 700)
                           ELSE CASE
                                    WHEN chance(0.04) THEN v_today - rnd_int(366, 375)  -- ended, expiration job pending
                                    WHEN chance(0.15) THEN v_today - rnd_int(336, 364)  -- renewal window (30 days)
                                    ELSE v_today - rnd_int(0, 335)
                                END
                       END;
        END IF;

        v_id := create_policy(p_product_id, p_customer_id, p_vehicle_id, p_property_id, v_status, v_start, v_prev_id);
    END;

    PROCEDURE create_claims IS
        v_claim_id    NUMBER;
        v_incident    DATE;
        v_reported    DATE;
        v_estimated   NUMBER;
        v_approved    NUMBER;
        v_status      VARCHAR2(20);
        v_roll        NUMBER;
        v_decided_at  TIMESTAMP;
        v_reported_at TIMESTAMP;
        v_paid_at     TIMESTAMP;
        v_description VARCHAR2(2000);
    BEGIN
        FOR p IN (SELECT po.id, po.start_date, LEAST(po.end_date, v_today) AS last_day, pr.code, c.city_id
                    FROM policy po
                    JOIN product pr ON pr.id = po.product_id
                    JOIN customer c ON c.id = po.customer_id
                   WHERE po.status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'RENEWED')
                     AND po.start_date < v_today
                   ORDER BY po.id) LOOP
            CONTINUE WHEN NOT chance(0.20);

            v_incident := p.start_date + rnd_int(0, p.last_day - p.start_date - 1);
            v_reported := LEAST(v_incident + rnd_int(0, 5), v_today);
            v_estimated := ROUND(CASE p.code
                                     WHEN 'TRAFFIC' THEN DBMS_RANDOM.VALUE(15000, 180000)
                                     WHEN 'CASCO'   THEN DBMS_RANDOM.VALUE(20000, 350000)
                                     WHEN 'HOME'    THEN DBMS_RANDOM.VALUE(10000, 250000)
                                     ELSE DBMS_RANDOM.VALUE(2000, 40000)
                                 END, -2);
            v_description := CASE p.code
                                 WHEN 'TRAFFIC' THEN pick(t_strings(
                                     'Kavşakta karşı tarafın aracına arkadan çarpma.',
                                     'Park halindeki araca manevra sırasında sürtme.',
                                     'Şerit değiştirirken yan araca çarpma.'))
                                 WHEN 'CASCO' THEN pick(t_strings(
                                     'Dolu nedeniyle kaput ve tavanda göçük.',
                                     'Otoparkta bilinmeyen araç tarafından çizilme.',
                                     'Ön cam kırılması.', 'Bariyere çarpma sonucu ön tampon hasarı.'))
                                 WHEN 'HOME' THEN pick(t_strings(
                                     'Üst kattan gelen su sızıntısı nedeniyle tavan hasarı.',
                                     'Mutfakta çıkan yangın.', 'Konuta hırsız girmesi.'))
                                 ELSE pick(t_strings(
                                     'Anlaşmalı hastanede ayakta tedavi.', 'Planlı ameliyat ve yatarak tedavi.',
                                     'MR ve tahlil giderleri.'))
                             END;

            v_roll := DBMS_RANDOM.VALUE;
            v_status := CASE
                            WHEN v_roll < 0.20 THEN 'REPORTED'
                            WHEN v_roll < 0.40 THEN 'IN_REVIEW'
                            WHEN v_roll < 0.55 THEN 'APPROVED'
                            WHEN v_roll < 0.70 THEN 'REJECTED'
                            ELSE 'PAID'
                        END;
            v_approved := CASE WHEN v_status IN ('APPROVED', 'PAID')
                               THEN ROUND(v_estimated * DBMS_RANDOM.VALUE(0.6, 1.0), -2) END;
            v_decided_at := CASE WHEN v_status IN ('APPROVED', 'REJECTED', 'PAID')
                                 THEN CAST(LEAST(v_reported + rnd_int(3, 20), v_today) AS TIMESTAMP) END;
            v_reported_at := CAST(v_reported AS TIMESTAMP) + NUMTODSINTERVAL(rnd_int(8, 20), 'HOUR');
            v_paid_at := CASE WHEN v_status = 'PAID' THEN v_decided_at + NUMTODSINTERVAL(rnd_int(1, 6), 'HOUR') END;

            SELECT claim_seq.NEXTVAL INTO v_claim_id FROM dual;
            INSERT INTO claim (id, claim_number, policy_id, incident_date, reported_at, incident_city_id, description,
                               status, estimated_amount, approved_amount, paid_amount, handler_id, decision_note,
                               decided_at, paid_at, created_by)
            VALUES (v_claim_id, 'HSR-' || TO_CHAR(v_reported, 'YYYY') || '-' || LPAD(v_claim_id, 6, '0'),
                    p.id, v_incident, v_reported_at,
                    p.city_id, v_description, v_status, v_estimated, v_approved,
                    CASE WHEN v_status = 'PAID' THEN v_approved END,
                    CASE WHEN v_status <> 'REPORTED' THEN v_handler_id END,
                    CASE v_status
                        WHEN 'REJECTED' THEN 'Hasar poliçe teminatı dışında kalmaktadır.'
                        WHEN 'APPROVED' THEN 'Eksper raporu doğrultusunda onaylandı.'
                        WHEN 'PAID' THEN 'Eksper raporu doğrultusunda onaylandı.'
                    END,
                    v_decided_at, v_paid_at, 'seed');
        END LOOP;
    END;

    PROCEDURE create_customers IS
        v_id          NUMBER;
        v_city_id     NUMBER;
        v_plate_code  NUMBER;
        v_first       VARCHAR2(50);
        v_last        VARCHAR2(50);
        v_gender      VARCHAR2(10);
        v_address     VARCHAR2(300);
        v_vehicle_n   PLS_INTEGER;
        v_brand_idx   PLS_INTEGER;
        v_model_year  NUMBER;
        v_usage       VARCHAR2(20);
        v_asset_id    NUMBER;
        v_build_year  NUMBER;
        v_area        NUMBER;
        v_identity    VARCHAR2(11);
        v_birth_date  DATE;
        v_email       VARCHAR2(150);
        v_phone       VARCHAR2(20);
        v_seq_no      PLS_INTEGER;
        v_plate       VARCHAR2(15);
        v_chassis     VARCHAR2(17);
        v_value       NUMBER;
        v_construction VARCHAR2(30);
    BEGIN
        FOR i IN 1 .. c_customer_count LOOP
            v_plate_code := CASE WHEN chance(0.55) THEN TO_NUMBER(pick(c_big_cities)) ELSE rnd_int(1, 81) END;
            SELECT id INTO v_city_id FROM city WHERE plate_code = v_plate_code;
            v_address := pick(c_streets) || ' No: ' || rnd_int(1, 150) || ' Daire: ' || rnd_int(1, 20);

            IF i <= c_individual_count THEN
                IF chance(0.5) THEN
                    v_gender := 'MALE';
                    v_first := pick(c_male_names);
                ELSE
                    v_gender := 'FEMALE';
                    v_first := pick(c_female_names);
                END IF;
                v_last := pick(c_last_names);
                v_identity := generate_tckn;
                v_birth_date := ADD_MONTHS(v_today, -12 * rnd_int(19, 75)) - rnd_int(0, 364);
                v_email := ascii_slug(v_first) || '.' || ascii_slug(v_last) || i || '@example.com';
                v_phone := '05' || rnd_int(30, 55) || LPAD(rnd_int(0, 9999999), 7, '0');
                INSERT INTO customer (customer_type, national_id, first_name, last_name, birth_date, gender,
                                      email, phone, address_line, city_id, created_by)
                VALUES ('INDIVIDUAL', v_identity, v_first, v_last, v_birth_date, v_gender,
                        v_email, v_phone, v_address, v_city_id, 'seed')
                RETURNING id INTO v_id;
                v_vehicle_n := 0;
                IF chance(0.85) THEN
                    v_vehicle_n := CASE WHEN chance(0.15) THEN 2 ELSE 1 END;
                END IF;
            ELSE
                v_identity := generate_vkn;
                v_phone := '0' || (200 + rnd_int(0, 300)) || LPAD(rnd_int(0, 9999999), 7, '0');
                INSERT INTO customer (customer_type, tax_number, company_name, email, phone, address_line,
                                      city_id, created_by)
                VALUES ('CORPORATE', v_identity, c_companies(i - c_individual_count),
                        'info@firma' || i || '.example.com', v_phone, v_address, v_city_id, 'seed')
                RETURNING id INTO v_id;
                v_vehicle_n := rnd_int(3, 5);
            END IF;
            v_cust_ids(i) := v_id;
            v_cust_city(i) := v_city_id;

            FOR n IN 1 .. v_vehicle_n LOOP
                v_brand_idx := rnd_int(1, c_brands.COUNT);
                v_model_year := EXTRACT(YEAR FROM v_today) - rnd_int(0, 16);
                IF i > c_individual_count AND chance(0.7) THEN
                    v_usage := 'COMMERCIAL';
                ELSIF chance(0.05) THEN
                    v_usage := 'TAXI';
                ELSE
                    v_usage := 'PRIVATE';
                END IF;
                v_seq_no := v_vehicle_ids.COUNT + 1;
                -- Plate numbers stay unique thanks to the running vehicle number in the last block.
                v_plate := LPAD(v_plate_code, 2, '0') || ' '
                           || TRANSLATE(DBMS_RANDOM.STRING('U', rnd_int(1, 3)), 'QWX', 'ABC')
                           || ' ' || TO_CHAR(100 + v_seq_no * 7);
                v_chassis := 'NMT' || DBMS_RANDOM.STRING('X', 8) || LPAD(v_seq_no, 6, '0');
                v_value := ROUND(TO_NUMBER(c_new_values(v_brand_idx))
                                 * POWER(0.9, EXTRACT(YEAR FROM v_today) - v_model_year), -3);
                INSERT INTO vehicle (customer_id, plate_number, chassis_number, brand, model, model_year,
                                     usage_type, vehicle_value, created_by)
                VALUES (v_id, v_plate, v_chassis, c_brands(v_brand_idx), c_models(v_brand_idx), v_model_year,
                        v_usage, v_value, 'seed')
                RETURNING id INTO v_asset_id;
                v_vehicle_ids(v_vehicle_ids.COUNT + 1) := v_asset_id;
                v_vehicle_owner(v_vehicle_owner.COUNT + 1) := i;
            END LOOP;

            IF i > c_individual_count OR chance(0.45) THEN
                v_build_year := EXTRACT(YEAR FROM v_today) - rnd_int(1, 45);
                v_area := rnd_int(70, 220);
                v_construction := CASE
                                      WHEN chance(0.80) THEN 'REINFORCED_CONCRETE'
                                      WHEN chance(0.40) THEN 'STEEL'
                                      WHEN chance(0.70) THEN 'MASONRY'
                                      ELSE 'WOOD'
                                  END;
                v_value := ROUND(v_area * rnd_int(25000, 60000), -4);
                INSERT INTO property (customer_id, city_id, address_line, building_year, gross_area_m2,
                                      construction_type, property_value, created_by)
                VALUES (v_id, v_city_id, v_address, v_build_year, v_area, v_construction, v_value, 'seed')
                RETURNING id INTO v_asset_id;
                v_property_ids(v_property_ids.COUNT + 1) := v_asset_id;
                v_property_owner(v_property_owner.COUNT + 1) := i;
            END IF;

            IF i <= c_individual_count AND chance(0.40) THEN
                v_health_owner(v_health_owner.COUNT + 1) := i;
            END IF;
        END LOOP;
    END;

    PROCEDURE create_policies IS
        v_rounds PLS_INTEGER := GREATEST(v_vehicle_ids.COUNT, v_property_ids.COUNT, v_health_owner.COUNT);
        v_idx    PLS_INTEGER;
        v_id     NUMBER;
    BEGIN
        -- Interleave products so that the 200-policy cap does not starve any product.
        FOR r IN 1 .. v_rounds LOOP
            IF r <= v_vehicle_ids.COUNT THEN
                create_chain(v_traffic_id, v_cust_ids(v_vehicle_owner(r)), v_vehicle_ids(r), NULL);
                IF chance(0.55) THEN
                    create_chain(v_casco_id, v_cust_ids(v_vehicle_owner(r)), v_vehicle_ids(r), NULL);
                END IF;
            END IF;
            IF r <= v_property_ids.COUNT THEN
                create_chain(v_home_id, v_cust_ids(v_property_owner(r)), NULL, v_property_ids(r));
            END IF;
            IF r <= v_health_owner.COUNT THEN
                create_chain(v_health_id, v_cust_ids(v_health_owner(r)), NULL, NULL);
            END IF;
        END LOOP;

        -- Top up with open quotes if the asset pool was not large enough.
        WHILE v_policy_count < c_policy_target LOOP
            v_idx := rnd_int(1, v_vehicle_ids.COUNT);
            v_id := create_policy(v_traffic_id, v_cust_ids(v_vehicle_owner(v_idx)), v_vehicle_ids(v_idx), NULL,
                                  'QUOTE', v_today + rnd_int(1, 20), NULL);
        END LOOP;
    END;
BEGIN
    DBMS_RANDOM.SEED(20260101);

    SELECT id BULK COLLECT INTO v_agent_ids FROM app_user WHERE role = 'AGENT' ORDER BY id;
    SELECT id INTO v_handler_id FROM app_user WHERE username = 'claims';
    SELECT id INTO v_traffic_id FROM product WHERE code = 'TRAFFIC';
    SELECT id INTO v_casco_id FROM product WHERE code = 'CASCO';
    SELECT id INTO v_home_id FROM product WHERE code = 'HOME';
    SELECT id INTO v_health_id FROM product WHERE code = 'HEALTH';

    create_customers;
    create_policies;
    create_claims;
END;
/
