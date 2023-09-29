(ns livadator.core
  (:require [livadator.schema :refer [find-schema schema-schema]]))

(declare validate validate-value)

(defn- -sequential
  [element]
  (if (sequential? element) element [element]))

(defn- -proceed
  [val stop?]
  (if stop? (reduced val) val))

(defmacro erroneous?
  [data action otherwise]
  `(if-not (empty? ~data) ~action ~otherwise))

(defn- -validate-value
  [value required? stop-on-first-error?]
  (fn [acc index validator]
    (if (nil? value)
      (if required? [:missing] [])
      (try
        (if (map? validator)
          (validate value validator stop-on-first-error? true)
          (let [valid (validator value)]
            (if-not valid
              (-proceed (conj acc index) stop-on-first-error?)
              acc)))
        (catch Exception _ (conj acc index))))))

(defn- -singular
  [value validators required? stop-on-first-error?]
  (let [failed (reduce-kv (-validate-value value required? stop-on-first-error?) [] (-sequential validators))]
    (erroneous? failed {:value value :validators failed} {})))

(defn- -validate-singular
  [value validators required? multiple? stop-on-first-error?]
  (if-not (nil? multiple?)
    (if multiple?
      {:value value :validators [:singular]}
      (-singular value validators required? stop-on-first-error?))
    (-singular value validators required? stop-on-first-error?)))

(defn- -multiple
  [value validators stop-on-first-error?]
  (reduce #(let [failed (validate-value %2 validators stop-on-first-error?)]
             (erroneous? failed (-proceed (conj %1 failed) stop-on-first-error?) %1)) [] value))

(defn- -validate-multiple
  [value validators multiple? stop-on-first-error?]
  (if-not (nil? multiple?)
    (if multiple?
      (-multiple value validators stop-on-first-error?)
      {:value value :validators [:multiple]})
    (-multiple value validators stop-on-first-error?)))

(defn validate-value
  ([value validators stop-on-first-error?]
   (if (map? validators)
     (let [{:keys [required? multiple? validators]
            :or   {required? false}
            :as   schema} validators
           validators (or validators schema)]
       (if (sequential? value)
         (-validate-multiple value validators multiple? stop-on-first-error?)
         (-validate-singular value validators required? multiple? stop-on-first-error?)))
     (recur value {:validators (-sequential validators)} stop-on-first-error?))))

(defn validate-field
  [coll field schema stop-on-first-error?]
  (let [failed (validate-value (get coll field) (field schema) stop-on-first-error?)]
    (erroneous? failed {field failed} {})))

(declare validate-schema)
(defn- -validate
  [coll schema stop-on-first-error? validate-schema? ignore-not-in-schema?]
  (let [schema (if (map? schema) schema (find-schema schema))
        schema-validation (if validate-schema? (validate-schema schema) [])]
    (if (empty? schema-validation)
      (merge (reduce (fn [acc f] (let [invalid (validate-field coll f schema stop-on-first-error?)]
                                   (if-not (empty? invalid)
                                     (-proceed (merge acc invalid) stop-on-first-error?)
                                     acc))) {} (keys schema))
             (if (not ignore-not-in-schema?)
               (let [unknown-keys (apply dissoc coll (keys schema))]
                 (erroneous? unknown-keys {:unknown-keys unknown-keys} {}))
               {}))
      {:schema-invalid schema-validation})))

(defn validate
  ([coll schema]
   (validate coll schema true true))
  ([coll schema stop-on-first-error? ignore-not-in-schema?]
   (-validate coll schema stop-on-first-error? true ignore-not-in-schema?)))

(defn validate-schema
  [schema]
  (reduce-kv
    (fn [acc key value]
      (let [value (if (map? value) value {:validators value})
            result (-validate value (schema-schema validate-schema) false false false)]
        (erroneous? result (assoc acc key result) acc)))
    {} schema))
