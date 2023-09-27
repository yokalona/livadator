(ns livadator.core-test
  (:require [clojure.test :refer :all]
            [livadator.core :refer :all]
            [clojure.pprint :refer :all]))

(deftest validate-value-test
  (testing "Validate value with errors"
    (is (empty? (validate-value 0 true int? false)))
    (is (= [0] (validate-value false true int? false)))
    (is (= [0 1] (validate-value false true [int? (partial < 0)] false)))
    (is (= [0] (validate-value false true [int? (partial < 0)] true))))
  (testing "Multiple"
    (is (= [0] (validate-value [0 1 2 3 false] true int? false)))
    (is (= [1 2] (validate-value [0 1 2 3 4 5] true [int? (partial > 2) (partial > 4)] false)))))

(deftest validate-field-test
  (testing ""
    (is (empty? (validate-field {:a 1} :a {:a int?} true)))
    (is (empty? (validate-field {:a 1} :a {:a [int? (partial < 0)]} true)))
    (is (= {:a {:value 1 :validators [1]}} (validate-field {:a 1} :a {:a [int? (partial > 0)]} true)))
    (is (= {:a {:value false :validators [0]}} (validate-field {:a false} :a {:a int?} true)))
    (is (= {:a {:value false :validators [0]}} (validate-field {:a false} :a {:a [int? (partial < 0)]} true)))
    (is (= {:a {:value false :validators [0 1]}} (validate-field {:a false} :a {:a [int? (partial < 0)]} false)))
    (is (= {:a {:value [0 1 2 3 4 5] :validators [1 2]}} (validate-field {:a [0 1 2 3 4 5]} :a {:a [int? (partial > 2) (partial > 4)]} false)))))

(def config-schema {:name  string?
                    :token string?
                    :async []})

(println (validate {:a [0 1 2 3 4 5]} :a {:a [int? (partial > 2) (partial > 4)]}))

(deftest validate-test
  (testing ""
    (println (validate {:a [0 1 2 3 4 5]} {:a [int? (partial > 2) (partial > 4)]} false))

    (pprint
      (validate {:name              "123"
                 :commands          [{:name "1234" :description "123"}
                                     {:name "abc" :description "37373"}]
                 :webhook           {:drop-pending-updates? true
                                     :certificate           "1234"
                                     :hook-url              "bla"}}
                {:name              string?
                 :async?            {:required? true :validator boolean?}
                 :short-description [string? #(<= (count %) 200)]
                 :description       [string? #(<= (count %) 800)]
                 :commands          [{:name        {:required? true
                                                    :validator string?}
                                      :description string?}]
                 :webhook           {:drop-pending-updates? boolean?
                                     :certificate           string?
                                     :hook-url              string?}}))))
