# livadator

_**[li-va-datór]**_ - library for data structure validation.

>Schema-driven validation entails that each structure can incorporate yet another validating structure.
<div style="text-align: right"><i>chatGPT</i></div>

## Schema

It's a data structure designed to confirm correctness and store detailed information about an object. It ensures that the object follows specific rules and formatting. In essence, it acts as a **validator** and information container.

#### Examples

```clojure
{:key int?}
```
Describes a map containing a key `:key` which is of type **integer**.

```clojure
{:key [int? (partial > 2)]}
```
Describes a map containing a key `:key` which is of type **integer** and is less than **2**.

```clojure
{:key {:validators (fn [val] (not (= :b (keyword val))))}}
```
Describes a map containing a key `:key` whereas the value, when treated as a keyword, must not equal :b

## Required? and multiple?
Each key can have special validators: **required?** and **multiple?**.

Required keys are mandatory and can not be absent from validating map.
However multiple keys are a bit different:
* If key is multiple(:multiple? true) -  it cannot have just one value
* If key is singular(:multiple false) - it cannot have multiple values; only one value is allowed
* If nothing is said about the key - it can be either multiple or singular

#### Examples
```clojure
{:key {:required? true 
       :multiple? false 
       :validators int?}}
```
Describes a map containing a key `:key` which is of type **integer**, required and is not multiple:

`{:key 1}` is valid

`{:key [1 2 3]}` is invalid

`{:another-key 1}` is also invalid

## Validating nested maps
The schema itself serves as a validator, meaning that if it's defined as the validator for a key, the corresponding 
value will undergo validation against this schema.

_in other words_

>The schema, in its own right, possesses the inherent capability to function as a validator. This implies that when the schema is designated as the validation criterion for a specific key, the value associated with that key is subjected to a rigorous validation process, scrutinized against the standards and constraints outlined within the schema itself. In essence, the schema acts as a gatekeeper, ensuring that the value adheres strictly to the rules and structure it prescribes."
<div style="text-align: right"><i>chatGPT</i></div>

#### Examples
```clojure
{:key {:validators {:another-key int?}}}
```
Describes a map containing a key `:key` which is also a map containing a key `:another-key` of type integer:

`{:key {:another-key 1}}` is a valid map for that schema

## Validating multiple values
If a key is marked as **multiple?** it means that all the values linked to that key will be individually checked using the specified validators. This ensures that each value adheres to the defined rules.

#### Examples
```clojure
{:key {:multiple? true
       :validators {:another-key int?}}}
```
Describes a map containing a multiple key `key` and every element of that key must adhere to the `{:another-key int?}` schema:

`{:key [{:another-key 1} 
        {:another-key 2}]}` is a valid map for that schema

## Schema of schemas

Using the same livadator schema one can describe schema of all schemas:

```clojure
{:required?  {:required?  false
              :multiple?  false
              :validators boolean?}
 :multiple?  {:required?  false
              :multiple?  false
              :validators boolean?}
 :validators {:required?  true
              :validators (fn [validators]
                            (if (map? validators)
                              (validate-schema validators)
                              (not (nil? validators))))}}
```

* **required?** is not actually required, is singular and should be boolean.
* **multiple?** is not actually multiple, is not required and should be boolean.
* **validators** is either multiple or singular, is required and should be either a valid schema or just not nil.

## Output
A map of all failed keys and a index of failed validators.

#### For singular value
```clojure
{:key [{:value 3, :validators [1]} {:value 4, :validators [1]}]}
;>
{:value "0" :validators [0 1]}
```

#### For multiple values:
```clojure
(validate {:key [0 1 2 3 4]} {:key {:multiple? true :validators [int? (partial >= 2)]}}
          (dont stop-on-first-error?)
          (dont ignore-not-in-schema?))
;>
{:key [{:value 3, :validators [1]} 
       {:value 4, :validators [1]}]}
```
Only failed values with according validators is shown

#### For nested maps
```clojure
(validate {:key {:another-key 1}} 
          {:key {:validators {:another-key boolean?}}})
;=>
{:key {:value {:another-key 1}, 
       :validators {:another-key {:value 1, 
                                  :validators [0]}}}}
```

## Special modes
There are two very special modes:
* **stop-on-first-error?** will stop on very first failed validator it encounters, saves time, but doesn't provide full report
* **ignore-not-in-schema?** will ignore every key, that is not specified in schema, i.e. will not fail on any key not in schema, which is less strict, but will not show errors in non required fields names

## Complex example
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
          (dont stop-on-first-error?)
          (dont ignore-not-in-schema?))

;=>
{:a-5  {:value      nil,
        :validators [:missing]},
 :a-8
 {:value      {:a-8-1 1,
               :a-8-2 "2",
               :a-8-3 [{:a-8-3-1 true}]},
  :validators {:a-8-3
               [{:value      {:a-8-3-1 true},
                 :validators {:a-8-3-2 {:value      nil,
                                        :validators [:missing]}}}]}},
 :a-10 {:value 0, :validators [:singular]},
 :a-9  {:value [], :validators [:multiple]},
 :a-7  [{:value :b, :validators [0]}
        {:value "b", :validators [0]}]}
```
#### Explanation
* _**:a-5**_ failed because it's missing and is required
* _**:a-7**_ failed because two of provided values "b" and :b as a keyword will have a value :b which is not allowed
* _**:a-8**_ failed because it's value, nested map failed for key `:a-8-3-2` which is missing and required
* _**:a-9**_ failed because expected singular, but was provided multiple
* _**:a-10**_ failed because expected multiple values

## Happy using