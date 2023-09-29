(ns livadator.core-test
  (:require [clojure.test :refer :all]
            [livadator.core :refer :all]
            [clojure.pprint :refer :all])
  (:import (livadator.core Options)))

(defn dont
  [val]
  (not val))

(deftest validate-schema-test
  (testing "Empty schema has no error"
    (is (= {} (validate-schema {}))))
  (testing "Empty schema for a key should have 'validators' block"
    (is (= {:key {:validators {:value nil, :validators [:missing]}}} (validate-schema {:key {}}))))
  (testing "But empty vector as a validators is ok"
    (is (= {} (validate-schema {:key {:validators []}}))))
  (testing "Valid validators"
    (is (= {} (validate-schema {:key int?})))
    (is (= {} (validate-schema {:key {:validators int?}})))
    (is (= {} (validate-schema {:key {:validators [int?]}}))))
  (testing "Required? and Missing? are boolean and not required"
    (is (= {} (validate-schema {:key {:required?  true
                                      :validators []}})))
    (is (= {} (validate-schema {:key {:multiple?  true
                                      :validators []}})))
    (is (= {:key {:required? {:validators [0]
                              :value      :invalid-value}}}
           (validate-schema {:key {:required?  :invalid-value
                                   :validators []}})))
    (is (= {:key {:multiple? {:validators [0]
                              :value      :invalid-value}}}
           (validate-schema {:key {:multiple?  :invalid-value
                                   :validators []}})))
    (is (= {:key {:multiple? {:validators [0]
                              :value      :invalid-multiple}
                  :required? {:validators [0]
                              :value      :invalid-required}}}
           (validate-schema {:key {:multiple?  :invalid-multiple
                                   :required?  :invalid-required
                                   :validators []}}))))
  (testing "Excess keys are not allowed for schema"
    (is (= {:key {:unknown-keys {:excess-key :value}}}
           (validate-schema {:key {:validators []
                                   :excess-key :value}})))))

(deftest validate-test
  (testing "Validating with invalid schema is not possible"
    (is (= {:schema-invalid {:key {:validators {:value nil, :validators [:missing]}}}} (validate {} {:key {}})))
    (testing "Even for nested keys"
      (is (= {:schema-invalid {:key {:validators {:value      {:another-key {}}
                                                  :validators [{:another-key {:validators {:value nil,
                                                                                           :validators [:missing]}}}]}}}}
             (validate {} {:key {:validators {:another-key {}}}}))))
    (testing "Valid validators"
      (is (= {})))))
