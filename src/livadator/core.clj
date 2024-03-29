(ns livadator.core
  "Schema-driven validation entails that each structure can incorporate yet another validating structure."
  (:gen-class))

(declare -validate -validate-value validate-schema find-schema)

(def ^:dynamic ^:private *schemas* {})

(defn- -register-schema
  [alias schema]
  (alter-var-root (var *schemas*) assoc alias schema)
  {})

(defn- -sequential [element] (if (sequential? element) element [element]))

(defn- -proceed [val stop?] (if stop? (reduced val) val))

(defmacro ^:private erroneous? [data action otherwise] `(if-not (empty? ~data) ~action ~otherwise))

(def ^:private schema-schema
  {:required?    {:required?  false
                  :multiple?  false
                  :validators boolean?}
   :multiple?    {:required?  false
                  :multiple?  false
                  :validators boolean?}
   :allow-empty? {:required   false
                  :multiple?  false
                  :validators boolean?}
   :validators   {:required?    true
                  :allow-empty? false
                  :validators   (fn [validators]
                                  (cond (map? validators) (let [validation (validate-schema validators)]
                                                            (erroneous? validation validation true))
                                        (keyword? validators) (if (contains? *schemas* validators) true {:schema-invalid :missing})
                                        :else (not (nil? validators))))}})

(defn- -validate-value-exact
  [value required? options]
  (fn [acc index validator]
    (if (nil? value)
      (if required? [:missing] [])
      (try (cond (map? validator) (if (map? value)
                                    (-validate value validator options)
                                    {:key-invalid :map-expected})
                 (keyword? validator) (-validate value (find-schema validator) options)
                 :else (let [{:keys [ok? message]
                              :or   {ok? true message index}} ((:interpreter options) (validator value))]
                         (if ok?
                           acc
                           (-proceed (conj acc (if (nil? message) index message)) (:stop-on-first-error? options)))))
           (catch Exception ex (conj acc (.getMessage ex)))))))

(defn- -singular
  [value validators required? options]
  (let [failed (reduce-kv (-validate-value-exact value required? options) [] (-sequential validators))]
    (erroneous? failed {:value value :validators failed} {})))

(defn- -validate-singular
  [value validators required? multiple? options]
  (if-not (nil? multiple?)
    (if multiple?
      {:value value :validators [:singular]}
      (-singular value validators required? options))
    (-singular value validators required? options)))

(defn- -multiple
  [value validators options]
  (reduce #(let [failed (-validate-value %2 validators options)]
             (erroneous? failed (-proceed (conj %1 failed) (:stop-on-first-error? options)) %1)) [] value))

(defn- -validate-multiple
  [value validators multiple? options]
  (if-not (nil? multiple?)
    (if multiple?
      (-multiple value validators options)
      {:value value :validators [:multiple]})
    (-multiple value validators options)))

(defn- -validate-value
  ([value validators options]
   (if (map? validators)
     (let [{:keys [required? multiple? allow-empty? validators]
            :or   {required?    (:always-required? options)
                   allow-empty? (:always-allow-empty? options)}
            :as   schema} validators
           validators (or validators schema)]
       (if (and (coll? value) (empty? value) (not allow-empty?))
         {:value value :validators [:empty]}
         (if (sequential? value)
           (-validate-multiple value validators multiple? options)
           (-validate-singular value validators required? multiple? options))))
     (recur value {:validators (-sequential validators)} options))))

(defn- -validate-field
  [coll schema options]
  (fn [acc field]
    (let [value (get coll field)
          failed (if (and (:skip-nested? options) (map? value)) [] (-validate-value value (field schema) options))]
      (erroneous? failed (-proceed (merge acc {field failed}) (:stop-on-first-error? options)) acc))))

(defn- -validate-fields
  [coll schema options]
  (reduce (-validate-field coll schema options) {} (keys schema)))

(defn- -validate-excess-fields
  [coll schema options]
  (if-not (:ignore-excess-keys? options)
    (let [unknown-keys (apply dissoc coll (keys schema))]
      (erroneous? unknown-keys {:unknown-keys unknown-keys} {}))
    {}))

(defn- -validate
  [coll schema options]
  (if (map? coll)
    (merge (-validate-fields coll schema options)
           (-validate-excess-fields coll schema options))
    (-validate-value coll schema options)))

(defn- -validation-process
  [coll schema options]
  (if (map? schema)
    (let [schema-validation (validate-schema schema)]
      (erroneous? schema-validation
                  {:schema-invalid schema-validation}
                  (-validate coll schema options)))
    (let [schema (find-schema schema)]
      (if (empty? schema)
        {:schema-invalid :missing}
        (-validate coll schema options)))))

(defn default-interpreter
  "Default interpreter - only boolean true values considered as success for validator"
  [& value]
  (let [value (first value)]
    (cond (true? value) {:ok? true}
          (false? value) {:ok? false}
          :else {:ok? false :message value})))

(defrecord Options
  [stop-on-first-error? ignore-excess-keys? verbose? skip-nested? always-required? always-allow-empty? interpreter])

(def ^:private
  default-options {:stop-on-first-error? true
                   :ignore-excess-keys?  true
                   :verbose?             true
                   :skip-nested?         false
                   :always-required?     false
                   :always-allow-empty?  false
                   :interpreter          default-interpreter})

(def ^:dynamic ^:private *default-options* default-options)

(defn override-defaults
  "Overrides default options"
  [options]
  (let [errors (-validate options
                          {:stop-on-first-error? boolean?
                           :ignore-excess-keys?  boolean?
                           :verbose?             boolean?
                           :skip-nested?         boolean?
                           :always-required?     boolean?
                           :always-allow-empty?  boolean?
                           :interpreter          fn?}
                          (map->Options {:stop-on-first-error? false
                                         :ignore-excess-keys?  true
                                         :verbose?             true
                                         :always-required?     false
                                         :skip-nested?         false
                                         :always-allow-empty?  true
                                         :interpreter          default-interpreter}))]
    (if (empty? errors)
      (alter-var-root (var *default-options*) merge options)
      errors)))

(defn reset-defaults
  "Resets default options to its original values"
  []
  (override-defaults default-options))

(defn options
  "Builder for options"
  [& options]
  (let [{:keys [stop-on-first-error? ignore-excess-keys? verbose? skip-nested? always-required? always-allow-empty? interpreter]}
        (merge *default-options* (first options))]
    (->Options stop-on-first-error? ignore-excess-keys? verbose? skip-nested? always-required? always-allow-empty? interpreter)))

(defn register-schema
  "Register `schema` in a registry, it then can be access via provided `alias`. Schema is validated before being inserted.
  `ignore-errors?` flag is used to amend schema validation upon adding to registry, use it iff something is broken withing validation process"
  ([alias schema]
   (register-schema alias schema false))
  ([alias schema ignore-errors?]
   (if-not (keyword? alias)
     {:alias-invalid :not-a-keyword}
     (let [schema-validation (if ignore-errors? [] (validate-schema schema))]
       (erroneous? schema-validation {:schema-invalid schema-validation} (-register-schema alias schema))))))

(defn unregister-schema
  "Unregisters `schema` from registry making it unavailable"
  [alias]
  (alter-var-root (var *schemas*) dissoc alias))

(defn find-schema
  "Finds `schema` in schema registry"
  [alias]
  (alias *schemas*))

(defn reset-schemas
  "Clears all registered schemas"
  []
  (alter-var-root (var *schemas*) {}))

(defn validate
  "Validates provided structure against `schema`. Schema can be alias. Returns a map of keys that are erroneous.

  **Example**

  ```clojure
  (validate {:key [0 1 2 3]} {:key int?}))
  ;=>
  {}

  (validate {:key [0 1 2 3 false]} {:key int?})
  ;=>
  {:key [{:value false :validators [0]}]}

  (validate :invalid {:validators (fn [value] (str \"this value is \" (name value)))})
  ;=>
  {:value :invalid :validators [\"this value is invalid\"]}
  ```

  See also: [[validate-schema]] [[valid?]]"
  ([coll schema]
   (validate coll schema (map->Options *default-options*)))
  ([coll schema options]
   (let [result (-validation-process coll schema options)]
     (if (:verbose? options)
       result
       (empty? result)))))

(defn valid?
  "Quickly tests if provided `col` is valid, schema can be alias

  **Example**
  ```clojure

  (valid? {:key 1} {:key int?})
  ;=> true

  (valid? {:key \"1\"} {:key int?})
  ;=> false

  ```

  See also: [[validate]]"
  [coll schema]
  (validate coll schema (map->Options {:stop-on-first-error? true
                                       :ignore-excess-keys?  false
                                       :verbose?             false
                                       :skip-nested?         false
                                       :always-required?     false
                                       :interpreter          default-interpreter})))

(defn validate-schema
  "Validates provided schema for correctness. Returns a map of keys that are erroneous.

  **Example**
  ```clojure

  (validate-schema {:key int?})
  ;=>
  {}

  (validate-schema {:key {:required? :invalid-value :validators []}})
  ;=>
  {:key {:required? {:validators [0] :value :invalid-value}}}
  ```

  See also: [[validate]]"
  [schema]
  (if (empty? schema)
    {:key {:validators {:value {}, :validators [:empty]}}}
    (reduce-kv
      (fn [acc key value]
        (let [value (if (map? value) value {:validators value})
              result (-validate value schema-schema (map->Options {:stop-on-first-error? false
                                                                   :ignore-excess-keys?  false
                                                                   :verbose?             true
                                                                   :skip-nested?         false
                                                                   :always-required?     false
                                                                   :interpreter          default-interpreter}))]
          (erroneous? result (assoc acc key result) acc)))
      {} schema)))
