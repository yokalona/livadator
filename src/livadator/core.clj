(ns livadator.core)

(declare -validate -validate-value validate-schema)

(defrecord Options
  [stop-on-first-error? ignore-not-in-schema?])

(defn- -sequential [element] (if (sequential? element) element [element]))

(defn- -proceed [val stop?] (if stop? (reduced val) val))

(defmacro erroneous? [data action otherwise] `(if-not (empty? ~data) ~action ~otherwise))

(def schema-schema
  {:required?  {:required?  false
                :multiple?  false
                :validators boolean?}
   :multiple?  {:required?  false
                :multiple?  false
                :validators boolean?}
   :validators {:required?  true
                :validators (fn [validators]
                              (if (map? validators)
                                (let [validation (validate-schema validators)]
                                  (erroneous? validation validation true))
                                true))}})

(defn- -validate-value-exact
  [value required? options]
  (fn [acc index validator]
    (if (nil? value)
      (if required? [:missing] [])
      (try
        (if (map? validator)
          (-validate value validator options true)
          (let [valid (validator value)]
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
  [coll field schema options]
  (let [failed (-validate-value (get coll field) (field schema) options)]
    (erroneous? failed {field failed} {})))

(defn- -validate
  [coll schema options validate-schema?]
  (let [schema-validation (if validate-schema? (validate-schema schema) [])]
    (if (empty? schema-validation)
      (merge (reduce
               (fn [acc f]
                 (let [invalid (-validate-field coll f schema options)]
                   (erroneous? invalid (-proceed (merge acc invalid) (:stop-on-first-error? options)) acc)))
               {}
               (keys schema))
             (if-not (:ignore-not-in-schema? options)
               (let [unknown-keys (apply dissoc coll (keys schema))]
                 (erroneous? unknown-keys {:unknown-keys unknown-keys} {}))
               {}))
      {:schema-invalid schema-validation})))

(defn validate
  ([coll schema]
   (validate coll schema (Options. true true)))
  ([coll schema options]
   (-validate coll schema options true)))

(defn validate-schema
  [schema]
  (reduce-kv
    (fn [acc key value]
      (let [value (if (map? value) value {:validators value})
            result (-validate value schema-schema (Options. false false) false)]
        (erroneous? result (assoc acc key result) acc)))
    {} schema))
