(ns livadator.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [livadator.core :refer [options find-schema register-schema reset-schemas unregister-schema valid? validate validate-schema]]))

(deftest validate-schema-test
  (testing "Empty schema has no error"
    (is (= {:key {:validators {:value {}, :validators [:empty]}}} (validate-schema {}))))
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
                     (options {:stop-on-first-error? false
                               :ignore-excess-keys?  false})))))
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

(deftest validate-value-test
  (testing "Multiple"
    (is (= [] (validate [1 2 3] {:validators int?})))
    (is (= [] (validate [1 2 3] {:validators [int?]})))
    (is (= [{:value 1 :validators [1]}]
           (validate [1 2 3] {:validators [int? (partial < 2)]})))
    (is (= [{:value 1 :validators [1]}
            {:value 2 :validators [1]}]
           (validate [1 2 3] {:validators [int? (partial < 2)]} (options {:stop-on-first-error? false}))))
    (is (= [{:value 1 :validators [1]}
            {:value 2 :validators [1]}]
           (validate [1 2 3] {:validators [int? (partial < 2)]} (options {:stop-on-first-error? false})))))
  (testing "Singular"
    (is (= {} (validate 1 {:validators int?})))
    (is (= {:value 2 :validators [0]} (validate 2 {:validators (partial < 2)})))))

(deftest complex-validators-test
  (testing "message"
    (is (= {:value :invalid :validators ["this value is invalid"]}
           (validate :invalid {:validators (fn [value] (str "this value is " (name value)))}))))
  (testing "exceptions"
    (is (= {:value :invalid :validators ["exception: :invalid"]}
           (validate :invalid {:validators (fn [value] (throw (RuntimeException. (str "exception: " value))))})))))

(deftest alias-test
  (testing "register alias"
    (is (= {} (register-schema :correct-schema {:key int?})))
    (is (= {:key int?} (find-schema :correct-schema))))
  (testing "invalid schema cannot be registered"
    (is (= {:schema-invalid {:key {:validators {:validators [:missing]
                                                :value      nil}}}}
           (register-schema :invalid-schema {:key {}})))
    (is (nil? (find-schema :invalid-schema))))
  (testing "can delete schema"
    (is (= {:key int?} (find-schema :correct-schema)))
    (unregister-schema :correct-schema)
    (is (nil? (find-schema :correct-schema))))
  (testing "can validate by alias"
    (register-schema :schema {:key int?})
    (is (= {} (validate {:key 1} :schema))))
  (testing "validation by missing schema in registry produces specific error"
    (is (= {:schema-invalid :missing} (validate {:key 1} :not-existing))))
  (testing "nested aliases"
    (reset-schemas)
    (is (= {:schema-invalid {:key {:validators {:validators [{:schema-invalid :missing}]
                                                :value      :nested-schema}}}}
           (validate {:key {:nested-key 1}} {:key {:validators :nested-schema}})))
    (register-schema :nested-schema {:nested-key int?})
    (register-schema :schema {:key {:validators :nested-schema}})
    (is (= {} (validate {:key {:nested-key 1}} :schema)))
    (is (= {:key {:validators {:nested-key {:validators [0]
                                            :value      "1"}}
                  :value      {:nested-key "1"}}}
           (validate {:key {:nested-key "1"}} :schema)))
    (testing "as alias as not, same err"
      (is (= (validate {:key {:nested-key "1"}} :schema)
             (validate {:key {:nested-key "1"}} {:key {:validators {:nested-key int?}}}))))
    (testing "cant even register non existing nested alias"
      (reset-schemas)
      (is (= {:schema-invalid {:key {:validators {:validators [{:schema-invalid :missing}]
                                                  :value      :nested-schema}}}}
             (register-schema :schema {:key {:validators :nested-schema}}))))))


(deftest options-test
  (testing "Stop on first error"
    (is (= [{:value 0 :validators [0]}
            {:value 1 :validators [0]}]
           (validate [0 1 2 3] {:validators (partial < 1)} (options {:stop-on-first-error? false}))))
    (is (= [{:value 0 :validators [0]}]
           (validate [0 1 2 3] {:validators (partial < 1)} (options)))))
  (testing "Ignore excess keys"
    (is (= {} (validate {:a 1 :b 2} {:a int?} (options))))
    (is (= {:unknown-keys {:b 2}} (validate {:a 1 :b 2} {:a int?} (options {:ignore-excess-keys? false})))))
  (testing "verbose"
    (is (= [{:value 0 :validators [0]}
            {:value 1 :validators [0]}]
           (validate [0 1 2 3] {:validators (partial < 1)} (options {:stop-on-first-error? false}))))
    (is (false? (validate [0 1 2 3] {:validators (partial < 1)} (options {:stop-on-first-error? false
                                                                          :verbose?             false}))))
    (is (true? (validate [0 1 2 3] {:validators int?} (options {:stop-on-first-error? false
                                                                :verbose?             false})))))
  (testing "skip nested"
    (is (= {} (validate {:key {:nested-key :invalid}}
                        {:key {:validators {:nested-key int?}}}
                        (options {:skip-nested? true}))))
    (is (= {:key {:validators {:key-invalid :map-expected}
                  :value      1}}
           (validate {:key 1}
                     {:key {:validators {:nested-key int?}}}
                     (options {:skip-nested? true}))))
    (is (= {:key {:validators {:nested-key {:validators [0]
                                            :value      :invalid}}
                  :value      {:nested-key :invalid}}}
           (validate {:key {:nested-key :invalid}}
                     {:key {:validators {:nested-key int?}}})))))

(deftest valid?-test
  (testing "valid is just true-false"
    (is (false? (valid? [0 1 2 3] {:validators (partial < 1)})))
    (is (true? (valid? [0 1 2 3] {:validators int?})))))

(deftest interpreter-test
  (testing "Custom logging interpreter"
    (let [logger (atom [])]
      (validate [0 1 2 3] {:validators identity}
                (options {:interpreter (fn [value] (swap! logger conj value) {:ok? true})}))
      (is (= [0 1 2 3] @logger))))
  (testing "Custom always fail interpreter"
    (is (= [{:value 0 :validators [0]}] (validate [0 1 2 3] {:validators int?}
                                                  (options {:interpreter (fn [_] {:ok? false})}))))))
