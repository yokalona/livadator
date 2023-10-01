# livadator

[![Build Status][gh-actions-badge]][gh-actions]

_**[li-va-datór]**_ - library for data structure validation.

>Schema-driven validation entails that each structure can incorporate yet another validating structure.
<div style="text-align: right"><i>chatGPT</i></div>

### Why not _clojure_/spec then?
It's rather complex. Personally, I do not see it fit my pet projects at all.
First of all its semantics is a set of a functions, rather the same data structure you're validating.
Defining types, requirements and all that jazz instead of just validating functions is challenging, at least for me.
Secondly, it requires some amount of research, like: how to define a range for a value, how to propagate custom error 
message if it's even possible? 

At the end of the day, the only thing that actually matters is data. 

As one once told:
> Use tools that fit your needs

and:
>It's not the victory we crave, but the fun along the way. Victory is just a perk, the reminder, where the road is and that the end will eventually come. 

This lib provided a lot of second and is the tool I actually use instead of _clojure_/spec

## Usage
**Leiningen**
Add to dependencies:
```clojure
[io.github.yokalona/livadator "1.0.0"]
```
**in REPL**
```clojure
(require '[livadator.core :as livadator])
```
**in application**
```clojure
(ns my-app.core
  (:require [livadator.core :as livadator]))
```

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

## Schema registry
Schema can be stored in schema registry, a special place, where schemas are associated with `alias` and then can be access by it.
Just call:
```clojure
(register-schema :my-schema {:key {:validators int?}})
```
From now on this schema will be accessible through this alias, however, alias can be only keyword, otherwise stuff can become too complex very quickly.
However, if you fancy of complex stuff - do what you gotta do.

Aliases can be nested, for instance:
```clojure
(register-schema :alias {:nested-key int?})

{:key {:validators :alias}}
```

## Validators
Validator is a function, that is executed against values to check if value is correct or not.

There are two types of validators, actually.

#### Simple
Returns true if value is correct and false if its not. In report it will be shown as an id.

```clojure
(validate {:key 1} {:key {:validators (fn [value] (< value 2))}})
;=>
{:key {:value 1 :validators [0]}}
```

#### Complex
Returns true if value is correct and anything else if not. In report this return value will be shown as a message, so it's useful to show custom errors and whatnot.

```clojure
(validate {:key 1} {:key {:validators
                          (fn [value] (if (< value 2)
                                        "Value is less than 2"
                                        true))}})
;=>
{:key {:value 1 :validators ["Value is less than 2"]}}
```

#### Exceptions

Exceptions act as complex validators, i.e. its message will be put as a fail of validation:

```clojure
(validate :invalid {:validators (fn [value] (throw (RuntimeException. (str "exception: " value))))})
;=>
{:value :invalid :validators ["exception: :invalid"]}
```

## Required? and multiple?
Each key can have special validators: **required?** and **multiple?**.

Required keys are mandatory and can not be absent from validating map.

However, multiple keys are a bit different:
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

>The schema, in its own right, possesses the inherent capability to function as a validator. This implies that when the schema is designated as the validation criterion for a specific key, the value associated with that key is subjected to a rigorous validation process, scrutinized against the standards and constraints outlined within the schema itself. In essence, the schema acts as a gatekeeper, ensuring that the value adheres strictly to the rules and structure it prescribes.

<div style="text-align: right"><i>chatGPT</i></div>

_in even other words out of this world(don't read unless bored)_

>At its core, the schema emerges as a linchpin in the intricate web of data validation. Its inherent capability to don the mantle of a validator is akin to bestowing a watchful guardian upon each piece of data. When the schema is anointed as the vanguard for a specific key, it enters into a meticulous dance of scrutiny and validation.
><sup>This validation process is more than a perfunctory check; it represents a comprehensive evaluation of the value at hand. The schema's discerning eye leaves no room for oversight, meticulously assessing each attribute and characteristic against the exacting standards encapsulated within its own structure. It's akin to a diligent inspector, combing through every detail to ensure the value complies with the defined parameters.</sup>
><sup><sup>In practical terms, the schema stands as a sentinel, ensuring data fidelity and precision. It serves as an architect of data integrity, constructing a fortress of rules and regulations that data must pass through unscathed. The schema's role is akin to that of a gatekeeper at the entrance to a secure realm, allowing only data that meets the stringent requirements to pass through.</sup></sup>
><sup><sup><sup>This gatekeeper role extends beyond mere validation; it encompasses the preservation of data quality and consistency. With each validation, the schema contributes to the broader goal of maintaining structured data's reliability. It facilitates seamless data exchange, mitigates the risk of data anomalies, and provides a solid foundation upon which data-driven applications can thrive.</sup></sup></sup>
><sup><sup><sup><sup>In a data-driven world where information is currency, the schema serves as a valuable asset, ensuring that data retains its intrinsic value and remains a reliable cornerstone for decision-making and innovation.</sup></sup></sup></sup>
<div style="text-align: right"><i>chatGPT</i><sup><sup>who would've guess, huh?</sup></sup></div>

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
                            (cond (map? validators) (let [validation (validate-schema validators)]
                                                      (erroneous? validation validation true))
                                  (keyword? validators) (if (contains? *schemas* validators)
                                                          true
                                                          {:schema-invalid :missing})
                                  :else (not (nil? validators))))}}
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

## Special options
There are two very special modes:
* **stop-on-first-error?** will stop on very first failed validator it encounters, saves time, but doesn't provide full report
* **ignore-not-in-schema?** will ignore every key, that is not specified in schema, i.e. will not fail on any key not in schema, which is less strict, but will not show errors in non required fields names
* **verbose?** will return full error report instead of just true/false

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
          (Options. (dont stop-on-first-error?) (dont ignore-not-in-schema?)))

;=>
{:a-5  {:value      nil,
        :validators [:missing]},
 :a-8  {:value      {:a-8-1 1,
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

## Pros and Cons
Pros:
1. It's simple
2. It's easy
3. ????
4. Use it already?

Cons:
1. There are tons of such libraries already

### Future plans
1. Publish this into clojars, duh!
2. ✅ Schema registry
   1. ✅ Aliases
   2. ✅ Nested aliases
3. Add more options, such as:
    1. Parallel execution
    2. Complex validation
    3. ✅ Verbose
4. Add cool looking logs and several levels of logging
5. Add context, for validations like "If key presents then this key should be also present"

## Happy using

[gh-actions-badge]: https://github.com/yokalona/livadator/workflows/ci/badge.svg
[gh-actions]: https://github.com/yokalona/livadator/actions
