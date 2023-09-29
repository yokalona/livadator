(ns livadator.core-test
  (:require [clojure.test :refer :all]
            [livadator.core :refer :all]
            [clojure.pprint :refer :all]))

(defn dont
  [val]
  (not val))

(deftest validate-value-test
  (testing "Validate single value"
    (let [proceed-even-if-error false]
      (is (= {} (validate-value 0 int? proceed-even-if-error)))
      (is (= {:value "0" :validators [0]} (validate-value "0" int? proceed-even-if-error)))
      (is (= {:value "0" :validators [0 1]} (validate-value "0" [int? (partial < 0)] proceed-even-if-error)))
      (is (= {:value 0 :validators [1]} (validate-value 0 [int? (partial < 0)] proceed-even-if-error)))
      (testing "If stop on first error - then only first validator will fail here"
        (is (= {:value "0" :validators [0]} (validate-value "0" [int? (partial < 0)] (dont proceed-even-if-error)))))
      (testing "If required field is missing it produces :missing validator failed"
        (is (= {:value nil :validators [:missing]} (validate-value nil {:required? true :validators int?} proceed-even-if-error))))
      (is (= {} (validate-value nil [int? (partial < 0)] proceed-even-if-error)))
      (is (= {:value 0 :validators [:singular]} (validate-value 0 {:multiple? true :validators int?} proceed-even-if-error)))))

  (testing "Validate multiple values"
    (let [proceed-even-if-error false]
      (is (= [] (validate-value [0 1 2 3] {:multiple? true :validators int?} proceed-even-if-error)))
      (is (= [{:value false :validators [0]}] (validate-value [0 1 2 3 false] {:multiple? true :validators int?} proceed-even-if-error)))
      (is (= [{:value false :validators [0 1]}] (validate-value [0 1 2 3 false] {:multiple? true :validators [int? (partial <= 0)]} proceed-even-if-error)))
      (is (= [{:value 3 :validators [1]}] (validate-value [0 1 2 3] {:multiple? true :validators [int? (partial >= 2)]} proceed-even-if-error)))
      (is (= [{:value false :validators [0]}] (validate-value [0 1 2 3 false] {:multiple? true :validators [int? (partial <= 0)]} (dont proceed-even-if-error))))
      (testing "Non multiple field will produce :multiple validator failed"
        (is (= {:value [0 1 2 3] :validators [:multiple]} (validate-value [0 1 2 3] {:multiple? false :validators [int? (partial <= 0)]} (dont proceed-even-if-error)))))
      (testing "Singular on multiple = :singular"
        (is (= {:value 0 :validators [:singular]} (validate-value 0 {:multiple? true :validators int?} proceed-even-if-error)))))))

(deftest validate-field-test
  (testing "Validate single value"
    (let [proceed-even-if-error false]
      (is (= {} (validate-field {:a 0} :a {:a int?} proceed-even-if-error)))
      (is (= {:a {:value "0" :validators [0]}} (validate-field {:a "0"} :a {:a int?} proceed-even-if-error)))
      (is (= {:a {:value "0" :validators [0 1]}} (validate-field {:a "0"} :a {:a [int? (partial < 0)]} proceed-even-if-error)))
      (is (= {:a {:value 0 :validators [1]}} (validate-field {:a 0} :a {:a [int? (partial < 0)]} proceed-even-if-error)))
      (testing "If stop on first error - then only first validator will fail here"
        (is (= {:a {:value "0" :validators [0]}} (validate-field {:a "0"} :a {:a [int? (partial < 0)]} (dont proceed-even-if-error)))))
      (testing "If required field is missing it produces :missing validator failed"
        (is (= {:a {:value nil :validators [:missing]}} (validate-field {} :a {:a {:required? true :validators int?}} proceed-even-if-error))))
      (is (= {} (validate-field {} :a {:a [int? (partial < 0)]} proceed-even-if-error)))))

  (testing "Validate multiple values"
    (let [proceed-even-if-error false]
      (is (= {} (validate-field {:a [0 1 2 3]} :a {:a {:multiple? true :validators int?}} proceed-even-if-error)))
      (is (= {:a [{:value false :validators [0]}]} (validate-field {:a [0 1 2 3 false]} :a {:a {:multiple? true :validators int?}} proceed-even-if-error)))
      (is (= {:a [{:value false :validators [0 1]}]} (validate-field {:a [0 1 2 3 false]} :a {:a {:multiple? true :validators [int? (partial <= 0)]}} proceed-even-if-error)))
      (is (= {:a [{:value 3 :validators [1]}]} (validate-field {:a [0 1 2 3]} :a {:a {:multiple? true :validators [int? (partial >= 2)]}} proceed-even-if-error)))
      (is (= {:a [{:value false :validators [0]}]} (validate-field {:a [0 1 2 3 false]} :a {:a {:multiple? true :validators [int? (partial <= 0)]}} (dont proceed-even-if-error))))
      (testing "Non multiple field will produce :multiple validator failed"
        (is (= {:a {:value [0 1 2 3] :validators [:multiple]}} (validate-field {:a [0 1 2 3]} :a {:a {:multiple? false :validators [int? (partial <= 0)]}} (dont proceed-even-if-error))))))))

(deftest validate-test
  (testing ""
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
                              :validators (fn [val] (case (keyword val)
                                                      :a true
                                                      :b false
                                                      true))}
                       :a-8  {:validators {:a-8-1 int?
                                           :a-8-2 string?
                                           :a-8-3 {:multiple?  true
                                                   :validators {:a-8-3-1 boolean?
                                                                :a-8-3-2 {:required?  true
                                                                          :validators int?}}}}}
                       :a-9  {:multiple? false :validators int?}
                       :a-10 {:multiple? true :validators int?}}
                      false
                      false))))

(println (validate {:key [0 1 2 3 4]} {:key {:multiple? true :validators [int? (partial >= 2)]}} false false))