# livadator

**[li-va-datór]** - library for data structure validation.

## Validation
Validation is driven by schema - a map, where each key describes allowed data, for example:

```clojure
{:a-1 int?
 :a-2 {:required? true
       :multiple? false}
```

means, that

## Usage

```clojure
(validate {:a-1  1
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
           :a-4  {:required false :validators int?}
           :a-5  {:required true :validators string?}
           :a-6  [int? (partial > -1)]
           :a-7  {:multiple?  true
                  :validators (fn [val] (case (keyword val)
                                          :a true
                                          :b false
                                          true))}
           :a-8  {:a-8-1 int?
                  :a-8-2 string?
                  :a-8-3 {:multiple?  true
                          :validators {:a-8-3-1 boolean?
                                       :a-8-3-2 {:required?  true
                                                 :validators int?}}}}
           :a-9  {:multiple? false :validator int?}
           :a-10 {:multiple? true :validators int?}}
          false)