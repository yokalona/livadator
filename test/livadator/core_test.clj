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
                                   :excess-key :value}}))))
  (testing "Validating nested schema, which is, so happens, invalid"
    (is (= {:key {:validators {:value      {:another-key {}}
                               :validators [{:another-key {:validators {:value      nil,
                                                                        :validators [:missing]}}}]}}}
           (validate-schema {:key {:validators {:another-key {}}}}))))
  (testing "But this one is definitely valid"
    (is (= {} (validate-schema {:key {:validators {:another-key int?}}})))))


(deftest validate-test
  (testing "Validating with invalid schema is not possible"
    (is (= {:schema-invalid {:key {:validators {:value nil, :validators [:missing]}}}} (validate {} {:key {}})))
    (testing "Even for nested keys"
      (is (= {:schema-invalid {:key {:validators {:value      {:another-key {}}
                                                  :validators [{:another-key {:validators {:value      nil,
                                                                                           :validators [:missing]}}}]}}}}
             (validate {} {:key {:validators {:another-key {}}}})))))
  (testing "Simple validation"
    (is (= {} (validate {:key 1} {:key int?})))
    (is (= {} (validate {:key 1} {:key {:validators int?}})))
    (is (= {} (validate {:key 1} {:key {:validators [int?]}}))))
  (testing "Complex validators"
    (is (= {:key {:value 1 :validators ["Value is less than 2"]}}
           (validate {:key 1} {:key {:validators (fn [value] (if (< value 2)
                                                               "Value is less than 2"
                                                               true))}}))))
  (testing "Multiple key validation"
    (is (= {} (validate {:key []} {:key int?})))
    (is (= {} (validate {:key [0 1 2 3]} {:key int?})))
    (is (= {:key [{:value false :validators [0]}]} (validate {:key [0 1 2 3 false]} {:key int?})))
    (is (= {:key [{:validators [1] :value -3}
                  {:validators [1] :value -2}
                  {:validators [1] :value -1}
                  {:validators [1 2] :value 0}
                  {:validators [1 2] :value 1}
                  {:validators [1 2] :value 2}
                  {:validators [2] :value 3}]}
           (validate {:key [-3 -2 -1 0 1 2 3]}
                     {:key [int? (partial < 2) (partial > 0)]}
                     (Options. false false)))))
  (testing "Nested key validation"
    (is (= {} (validate {:key {:another-key 1}} {:key {:validators {:another-key int?}}})))
    (is (= {} (validate {:key {:another-key "1"}} {:key {:validators {:another-key string?}}})))
    (is (= {:key {:validators {:another-key {:validators [0]
                                             :value      :invalid}}
                  :value      {:another-key :invalid}}}
           (validate {:key {:another-key :invalid}} {:key {:validators {:another-key int?}}}))))
  (testing "Multiple? and Required?"
    (is (= {:key {:validators [:missing]
                  :value      nil}}
           (validate {} {:key {:required?  true
                               :validators int?}})))
    (is (= {} (validate {} {:key {:required?  false
                                  :validators int?}})))
    (is (= {} (validate {:key [0 1 2 3]} {:key {:multiple?  true
                                                :validators int?}})))
    (is (= {:key {:validators [:singular]
                  :value      1}}
           (validate {:key 1} {:key {:multiple?  true
                                     :validators int?}})))
    (is (= {:key {:validators [:multiple]
                  :value      [0 1 2 3]}}
           (validate {:key [0 1 2 3]} {:key {:multiple?  false
                                             :validators int?}})))
    (testing "If no multiple? key might be either way"
      (is (= {} (validate {:key [0 1 2 3]} {:key {:validators int?}})))
      (is (= {} (validate {:key 0} {:key {:validators int?}}))))))

(deftest complex-test
  (let [stop-on-first-error? true
        ignore-not-in-schema? true]
    (pprint (validate {:a-1  1
                       :a-2  ""
                       :a-3  []
                       :a-6  [0 1 2 3]
                       :a-7  [:a :b :c "a" "b" "c"]
                       :a-8  {:a-8-1 1
                              :a-8-2 "2"
                              :a-8-3 [{:a-8-3-1 true}]}
                       :a-9  []
                       :a-10 0}
                      {:a-1  int?
                       :a-2  [string?]
                       :a-3  {:multiple? true :validators int?}
                       :a-4  {:required? false :validators int?}
                       :a-5  {:required? true :validators string?}
                       :a-6  [int? (partial < -1)]
                       :a-7  {:multiple?  true
                              :validators (fn [val] (not (= :b (keyword val))))}
                       :a-8  {:validators {:a-8-1 int?
                                           :a-8-2 string?
                                           :a-8-3 {:multiple?  true
                                                   :validators {:a-8-3-1 boolean?
                                                                :a-8-3-2 {:required?  true
                                                                          :validators int?}}}}}
                       :a-9  {:multiple? false :validators int?}
                       :a-10 {:multiple? true :validators int?}}
                      (Options. (dont stop-on-first-error?) (dont ignore-not-in-schema?))))))