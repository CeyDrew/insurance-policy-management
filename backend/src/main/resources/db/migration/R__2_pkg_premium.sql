-- Premium engine: tariff-based premium calculation and installment plans.
--
-- Net premium formula (all factors come from TARIFF_PARAMETER, valid on the policy start date):
--   TRAFFIC : base_premium               x city risk x driver age x vehicle age x usage type x no-claims step
--   CASCO   : vehicle value x VALUE_RATE x city risk x driver age x vehicle age x usage type x no-claims step
--   HOME    : property value x VALUE_RATE x city risk (property's city) x building age x construction type
--   HEALTH  : base_premium x insured age
-- The result is never below the product's min_premium. Tax = net x product.tax_rate, gross = net + tax.
-- Corporate customers have no birth date, so the driver age factor is not applied to them.

CREATE OR REPLACE PACKAGE pkg_premium AS

    c_entry_ncd_step CONSTANT PLS_INTEGER := 4;

    /** Net premium (before tax), rounded to 2 decimals. */
    FUNCTION calculate_premium(
        p_product_code IN product.code%TYPE,
        p_customer_id  IN customer.id%TYPE,
        p_vehicle_id   IN vehicle.id%TYPE  DEFAULT NULL,
        p_property_id  IN property.id%TYPE DEFAULT NULL,
        p_ncd_step     IN NUMBER           DEFAULT c_entry_ncd_step,
        p_start_date   IN DATE             DEFAULT TRUNC(SYSDATE)
    ) RETURN NUMBER;

    /** Net premium plus tax breakdown; this is the entry point used by the application. */
    PROCEDURE calculate(
        p_product_code  IN  product.code%TYPE,
        p_customer_id   IN  customer.id%TYPE,
        p_vehicle_id    IN  vehicle.id%TYPE,
        p_property_id   IN  property.id%TYPE,
        p_ncd_step      IN  NUMBER,
        p_start_date    IN  DATE,
        p_net_premium   OUT NUMBER,
        p_tax_amount    OUT NUMBER,
        p_gross_premium OUT NUMBER
    );

    /** Factor of a numeric-range tariff row (e.g. DRIVER_AGE = 37). Raises TARIFF_NOT_FOUND if no row matches. */
    FUNCTION tariff_factor(
        p_product_id IN product.id%TYPE,
        p_param_code IN tariff_parameter.param_code%TYPE,
        p_value      IN NUMBER,
        p_date       IN DATE
    ) RETURN NUMBER;

    /** Factor of a categorical tariff row (e.g. USAGE_TYPE = 'TAXI'). Raises TARIFF_NOT_FOUND if no row matches. */
    FUNCTION tariff_key_factor(
        p_product_id IN product.id%TYPE,
        p_param_code IN tariff_parameter.param_code%TYPE,
        p_key        IN VARCHAR2,
        p_date       IN DATE
    ) RETURN NUMBER;

    /**
     * Creates the installment plan of a policy: equal monthly installments starting on the policy start date,
     * the last one absorbing the rounding difference. p_count defaults to the policy's installment_count.
     */
    PROCEDURE generate_installments(
        p_policy_id  IN policy.id%TYPE,
        p_count      IN NUMBER   DEFAULT NULL,
        p_created_by IN VARCHAR2 DEFAULT 'system'
    );

END pkg_premium;
/

CREATE OR REPLACE PACKAGE BODY pkg_premium AS

    TYPE t_numbers IS TABLE OF NUMBER INDEX BY PLS_INTEGER;
    TYPE t_dates   IS TABLE OF DATE   INDEX BY PLS_INTEGER;

    FUNCTION param_label(p_param_code IN VARCHAR2) RETURN VARCHAR2 IS
    BEGIN
        RETURN CASE p_param_code
                   WHEN 'DRIVER_AGE'        THEN 'Sürücü yaşı'
                   WHEN 'INSURED_AGE'       THEN 'Sigortalı yaşı'
                   WHEN 'VEHICLE_AGE'       THEN 'Araç yaşı'
                   WHEN 'BUILDING_AGE'      THEN 'Bina yaşı'
                   WHEN 'VALUE_RATE'        THEN 'Sigorta bedeli'
                   WHEN 'NCD_STEP'          THEN 'Hasarsızlık basamağı'
                   WHEN 'USAGE_TYPE'        THEN 'Kullanım tipi'
                   WHEN 'CONSTRUCTION_TYPE' THEN 'Yapı tipi'
                   ELSE p_param_code
               END;
    END param_label;

    FUNCTION tariff_factor(
        p_product_id IN product.id%TYPE,
        p_param_code IN tariff_parameter.param_code%TYPE,
        p_value      IN NUMBER,
        p_date       IN DATE
    ) RETURN NUMBER IS
        l_factor tariff_parameter.factor%TYPE;
    BEGIN
        SELECT factor
          INTO l_factor
          FROM tariff_parameter
         WHERE product_id = p_product_id
           AND param_code = p_param_code
           AND param_key IS NULL
           AND p_value BETWEEN min_value AND max_value
           AND p_date >= valid_from
           AND (valid_to IS NULL OR p_date <= valid_to);
        RETURN l_factor;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            pkg_errors.raise_error(pkg_errors.c_tariff_not_found, 'TARIFF_NOT_FOUND',
                param_label(p_param_code) || ' (' || p_value || ') için tarife bulunmuyor.');
        WHEN TOO_MANY_ROWS THEN
            pkg_errors.raise_error(pkg_errors.c_tariff_not_found, 'TARIFF_AMBIGUOUS',
                param_label(p_param_code) || ' (' || p_value || ') için birden fazla tarife satırı var.');
    END tariff_factor;

    FUNCTION tariff_key_factor(
        p_product_id IN product.id%TYPE,
        p_param_code IN tariff_parameter.param_code%TYPE,
        p_key        IN VARCHAR2,
        p_date       IN DATE
    ) RETURN NUMBER IS
        l_factor tariff_parameter.factor%TYPE;
    BEGIN
        SELECT factor
          INTO l_factor
          FROM tariff_parameter
         WHERE product_id = p_product_id
           AND param_code = p_param_code
           AND param_key = p_key
           AND p_date >= valid_from
           AND (valid_to IS NULL OR p_date <= valid_to);
        RETURN l_factor;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            pkg_errors.raise_error(pkg_errors.c_tariff_not_found, 'TARIFF_NOT_FOUND',
                param_label(p_param_code) || ' (' || p_key || ') için tarife bulunmuyor.');
        WHEN TOO_MANY_ROWS THEN
            pkg_errors.raise_error(pkg_errors.c_tariff_not_found, 'TARIFF_AMBIGUOUS',
                param_label(p_param_code) || ' (' || p_key || ') için birden fazla tarife satırı var.');
    END tariff_key_factor;

    FUNCTION get_product(p_code IN product.code%TYPE) RETURN product%ROWTYPE IS
        l_product product%ROWTYPE;
    BEGIN
        SELECT * INTO l_product FROM product WHERE code = p_code;
        IF l_product.active = 0 THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'PRODUCT_INACTIVE', 'Bu ürün satışa kapalı.');
        END IF;
        RETURN l_product;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            pkg_errors.raise_error(pkg_errors.c_not_found, 'PRODUCT_NOT_FOUND', 'Ürün bulunamadı: ' || p_code);
    END get_product;

    FUNCTION get_customer(p_id IN customer.id%TYPE) RETURN customer%ROWTYPE IS
        l_customer customer%ROWTYPE;
    BEGIN
        SELECT * INTO l_customer FROM customer WHERE id = p_id;
        RETURN l_customer;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            pkg_errors.raise_error(pkg_errors.c_not_found, 'CUSTOMER_NOT_FOUND', 'Müşteri bulunamadı: ' || p_id);
    END get_customer;

    FUNCTION city_risk(p_city_id IN city.id%TYPE) RETURN NUMBER IS
        l_risk city.risk_factor%TYPE;
    BEGIN
        SELECT risk_factor INTO l_risk FROM city WHERE id = p_city_id;
        RETURN l_risk;
    END city_risk;

    FUNCTION age_at(p_birth_date IN DATE, p_date IN DATE) RETURN NUMBER IS
    BEGIN
        RETURN TRUNC(MONTHS_BETWEEN(p_date, p_birth_date) / 12);
    END age_at;

    FUNCTION vehicle_premium(
        p_product    IN product%ROWTYPE,
        p_customer   IN customer%ROWTYPE,
        p_vehicle_id IN vehicle.id%TYPE,
        p_ncd_step   IN NUMBER,
        p_start_date IN DATE
    ) RETURN NUMBER IS
        l_vehicle vehicle%ROWTYPE;
        l_premium NUMBER;
    BEGIN
        IF p_vehicle_id IS NULL THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'VEHICLE_REQUIRED', 'Bu ürün için araç seçilmelidir.');
        END IF;
        BEGIN
            SELECT * INTO l_vehicle FROM vehicle WHERE id = p_vehicle_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_not_found, 'VEHICLE_NOT_FOUND', 'Araç bulunamadı: ' || p_vehicle_id);
        END;
        IF l_vehicle.customer_id <> p_customer.id THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'ASSET_OWNER_MISMATCH', 'Seçilen araç bu müşteriye ait değil.');
        END IF;

        l_premium := CASE p_product.code
                         WHEN 'CASCO' THEN l_vehicle.vehicle_value
                                           * tariff_factor(p_product.id, 'VALUE_RATE', l_vehicle.vehicle_value, p_start_date)
                         ELSE p_product.base_premium
                     END;

        l_premium := l_premium
                     * city_risk(p_customer.city_id)
                     * tariff_factor(p_product.id, 'VEHICLE_AGE',
                                     GREATEST(0, EXTRACT(YEAR FROM p_start_date) - l_vehicle.model_year), p_start_date)
                     * tariff_key_factor(p_product.id, 'USAGE_TYPE', l_vehicle.usage_type, p_start_date)
                     * tariff_factor(p_product.id, 'NCD_STEP', p_ncd_step, p_start_date);

        IF p_customer.birth_date IS NOT NULL THEN
            l_premium := l_premium
                         * tariff_factor(p_product.id, 'DRIVER_AGE', age_at(p_customer.birth_date, p_start_date), p_start_date);
        END IF;
        RETURN l_premium;
    END vehicle_premium;

    FUNCTION property_premium(
        p_product     IN product%ROWTYPE,
        p_customer    IN customer%ROWTYPE,
        p_property_id IN property.id%TYPE,
        p_start_date  IN DATE
    ) RETURN NUMBER IS
        l_property property%ROWTYPE;
    BEGIN
        IF p_property_id IS NULL THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'PROPERTY_REQUIRED', 'Bu ürün için konut seçilmelidir.');
        END IF;
        BEGIN
            SELECT * INTO l_property FROM property WHERE id = p_property_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_not_found, 'PROPERTY_NOT_FOUND', 'Konut bulunamadı: ' || p_property_id);
        END;
        IF l_property.customer_id <> p_customer.id THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'ASSET_OWNER_MISMATCH', 'Seçilen konut bu müşteriye ait değil.');
        END IF;

        RETURN l_property.property_value
               * tariff_factor(p_product.id, 'VALUE_RATE', l_property.property_value, p_start_date)
               * city_risk(l_property.city_id)
               * tariff_factor(p_product.id, 'BUILDING_AGE',
                               GREATEST(0, EXTRACT(YEAR FROM p_start_date) - l_property.building_year), p_start_date)
               * tariff_key_factor(p_product.id, 'CONSTRUCTION_TYPE', l_property.construction_type, p_start_date);
    END property_premium;

    FUNCTION person_premium(
        p_product    IN product%ROWTYPE,
        p_customer   IN customer%ROWTYPE,
        p_start_date IN DATE
    ) RETURN NUMBER IS
    BEGIN
        IF p_customer.customer_type <> 'INDIVIDUAL' THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'INDIVIDUAL_REQUIRED',
                'Sağlık sigortası sadece bireysel müşterilere yapılır.');
        END IF;
        RETURN p_product.base_premium
               * tariff_factor(p_product.id, 'INSURED_AGE', age_at(p_customer.birth_date, p_start_date), p_start_date);
    END person_premium;

    FUNCTION calculate_premium(
        p_product_code IN product.code%TYPE,
        p_customer_id  IN customer.id%TYPE,
        p_vehicle_id   IN vehicle.id%TYPE  DEFAULT NULL,
        p_property_id  IN property.id%TYPE DEFAULT NULL,
        p_ncd_step     IN NUMBER           DEFAULT c_entry_ncd_step,
        p_start_date   IN DATE             DEFAULT TRUNC(SYSDATE)
    ) RETURN NUMBER IS
        l_product  product%ROWTYPE := get_product(p_product_code);
        l_customer customer%ROWTYPE := get_customer(p_customer_id);
        l_start    DATE := TRUNC(NVL(p_start_date, SYSDATE));
        l_premium  NUMBER;
    BEGIN
        IF p_ncd_step IS NULL OR p_ncd_step NOT BETWEEN 1 AND 8 THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'INVALID_NCD_STEP',
                'Hasarsızlık basamağı 1 ile 8 arasında olmalıdır.');
        END IF;

        CASE l_product.asset_type
            WHEN 'VEHICLE' THEN
                l_premium := vehicle_premium(l_product, l_customer, p_vehicle_id, p_ncd_step, l_start);
            WHEN 'PROPERTY' THEN
                l_premium := property_premium(l_product, l_customer, p_property_id, l_start);
            ELSE
                l_premium := person_premium(l_product, l_customer, l_start);
        END CASE;

        RETURN ROUND(GREATEST(l_premium, l_product.min_premium), 2);
    END calculate_premium;

    PROCEDURE calculate(
        p_product_code  IN  product.code%TYPE,
        p_customer_id   IN  customer.id%TYPE,
        p_vehicle_id    IN  vehicle.id%TYPE,
        p_property_id   IN  property.id%TYPE,
        p_ncd_step      IN  NUMBER,
        p_start_date    IN  DATE,
        p_net_premium   OUT NUMBER,
        p_tax_amount    OUT NUMBER,
        p_gross_premium OUT NUMBER
    ) IS
        l_tax_rate product.tax_rate%TYPE;
    BEGIN
        p_net_premium := calculate_premium(p_product_code, p_customer_id, p_vehicle_id, p_property_id,
                                           p_ncd_step, p_start_date);
        SELECT tax_rate INTO l_tax_rate FROM product WHERE code = p_product_code;
        p_tax_amount := ROUND(p_net_premium * l_tax_rate, 2);
        p_gross_premium := p_net_premium + p_tax_amount;
    END calculate;

    PROCEDURE generate_installments(
        p_policy_id  IN policy.id%TYPE,
        p_count      IN NUMBER   DEFAULT NULL,
        p_created_by IN VARCHAR2 DEFAULT 'system'
    ) IS
        l_policy   policy%ROWTYPE;
        l_max      product.max_installments%TYPE;
        l_count    PLS_INTEGER;
        l_existing PLS_INTEGER;
        l_regular  NUMBER;
        l_nos      t_numbers;
        l_dues     t_dates;
        l_amounts  t_numbers;
    BEGIN
        BEGIN
            SELECT * INTO l_policy FROM policy WHERE id = p_policy_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                pkg_errors.raise_error(pkg_errors.c_not_found, 'POLICY_NOT_FOUND', 'Poliçe bulunamadı: ' || p_policy_id);
        END;
        SELECT max_installments INTO l_max FROM product WHERE id = l_policy.product_id;

        l_count := NVL(p_count, l_policy.installment_count);
        IF l_count NOT BETWEEN 1 AND l_max THEN
            pkg_errors.raise_error(pkg_errors.c_validation, 'TOO_MANY_INSTALLMENTS',
                'Bu üründe taksit sayısı 1 ile ' || l_max || ' arasında olmalıdır.');
        END IF;

        SELECT COUNT(*) INTO l_existing FROM installment WHERE policy_id = p_policy_id;
        IF l_existing > 0 THEN
            pkg_errors.raise_error(pkg_errors.c_invalid_state, 'INSTALLMENTS_EXIST',
                'Poliçenin taksit planı zaten oluşturulmuş.');
        END IF;

        l_regular := TRUNC(l_policy.gross_premium / l_count, 2);
        FOR i IN 1 .. l_count LOOP
            l_nos(i) := i;
            l_dues(i) := ADD_MONTHS(l_policy.start_date, i - 1);
            l_amounts(i) := CASE WHEN i = l_count
                                 THEN l_policy.gross_premium - l_regular * (l_count - 1)
                                 ELSE l_regular
                            END;
        END LOOP;

        FORALL i IN 1 .. l_count
            INSERT INTO installment (policy_id, installment_no, due_date, amount, status, created_by)
            VALUES (p_policy_id, l_nos(i), l_dues(i), l_amounts(i), 'PENDING', p_created_by);

        IF l_policy.installment_count <> l_count THEN
            UPDATE policy
               SET installment_count = l_count,
                   updated_at = SYSTIMESTAMP,
                   updated_by = p_created_by,
                   version = version + 1
             WHERE id = p_policy_id;
        END IF;
    END generate_installments;

END pkg_premium;
/
