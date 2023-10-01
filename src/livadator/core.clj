(ns livadator.core
  "Schema-driven validation entails that each structure can incorporate yet another validating structure."
  (:gen-class))

(declare -validate -validate-value validate-schema)

(def ^:dynamic ^:private *schemas* {})

(defn- -register-schema
  [alias schema]
  (alter-var-root (var *schemas*) assoc alias schema)
  {})

(defrecord Options
  [stop-on-first-error? ignore-excess-keys? verbose?])

(defn- -sequential [element] (if (sequential? element) element [element]))

(defn- -proceed [val stop?] (if stop? (reduced val) val))

(defmacro erroneous? [data action otherwise] `(if-not (empty? ~data) ~action ~otherwise))

(defn register-schema
  "Register `schema` in a registry, it then can be access via provided `alias`"
  [alias schema]
  (let [schema-validation (validate-schema schema)]
    (erroneous? schema-validation {:schema-invalid schema-validation} (-register-schema alias schema))))

(defn unregister-schema
  "Unregisters `schema` from registry making it unavailable"
  [alias]
  (alter-var-root (var *schemas*) dissoc alias))

(defn find-schema
  "Finds `schema` in schema registry"
  [alias]
  (alias *schemas*))

(defn reset-schemas
  []
  (alter-var-root (var *schemas*) {}))

(def schema-schema
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
                                    :else (not (nil? validators))))}})

(defn- -validate-value-exact
  [value required? options]
  (fn [acc index validator]
    (if (nil? value)
      (if required? [:missing] [])
      (try
        (cond
          (map? validator) (-validate value validator options)
          (keyword? validator) (-validate value (find-schema validator) options)
          :else (let [valid (validator value)]
                  (if (boolean? valid)
                    (if-not valid
                      (-proceed (conj acc index) (:stop-on-first-error? options))
                      acc)
                    (conj acc valid))))
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
     (let [{:keys [required? multiple? validators]
            :or   {required? false}
            :as   schema} validators
           validators (or validators schema)]
       (if (sequential? value)
         (-validate-multiple value validators multiple? options)
         (-validate-singular value validators required? multiple? options)))
     (recur value {:validators (-sequential validators)} options))))

(defn- -validate-field
  [coll schema options]
  (fn [acc field]
    (let [failed (-validate-value (get coll field) (field schema) options)]
      (erroneous? failed (-proceed (merge acc {field failed}) (:stop-on-first-error? options)) acc))))

(defn- -validate-fields
  [coll schema options]
  (reduce (-validate-field coll schema options) {} (keys schema)))

(defn -validate-excess-fields
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

  See also: [[validate-schema]]"
  ([coll schema]
   (validate coll schema (Options. true true true)))
  ([coll schema options]
   (let [result (-validation-process coll schema options)]
     (if (:verbose? options)
       result
       (empty? result)))))

(defn valid?
  "Quickly tests if provided `col` is valid, schema can be alias"
  [coll schema]
  (validate coll schema (Options. true false false)))

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
              result (-validate value schema-schema (Options. false false true))]
          (erroneous? result (assoc acc key result) acc)))
      {} schema)))
