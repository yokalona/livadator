(ns livadator.core)

(def schemas (atom {}))

(defn register-schema
  [alias schema]
  (if-not (contains? @schemas alias)
    (swap! schemas assoc alias schema)
    @schema))

(defn unregister-schema
  [alias]
  (swap! schemas dissoc alias))

(defn unregister-all
  []
  (reset! schemas {}))

(declare validate)
(defn- -validate-value
  [value required? stop-on-first-error]
  (fn [acc index validator]
    (if (nil? value)
      (if required?
        [:missing]
        [])
      (try
        (if (map? validator)
          (validate value validator stop-on-first-error)
          (let [valid (validator value)]
            (if-not valid
              (let [acc (conj acc index)]
                (if stop-on-first-error
                  (reduced acc)
                  acc))
              acc)))
        (catch Exception _ (conj acc index))))))

(defn validate-value
  [value required? validators stop-on-first-error]
  (if (coll? validators)
    (if (sequential? value)
      (into [] (reduce #(let [failed (validate-value %2 required? validators stop-on-first-error)]
                          (if-not (empty? failed)
                            (let [acc (into %1 failed)]
                              (if stop-on-first-error
                                (reduced acc)
                                acc))
                            %1)) #{} value))
      (reduce-kv (-validate-value value required? stop-on-first-error) [] validators))
    (recur value required? [validators] stop-on-first-error)))

(defn get-validator
  [field schema]
  (let [{:keys [required? validator] :or {required? false} :as validate-only} (field schema)
        validator (or validator validate-only)
        validators (if (sequential? validator) validator [validator])]
    {:validators validators :required? required?}))

(defn validate-field
  [coll field schema stop-on-first-error]
  (let [value (get coll field)
        validators (get-validator field schema)
        failed (validate-value value (:required? validators) (:validators validators) stop-on-first-error)]
    (if-not (empty? failed)
      {field {:value value :validators failed}}
      {})))

(defn validate
  ([coll schema]
   (validate coll schema true))
  ([coll schema stop-on-first-error]
   (let [schema (if (map? schema) schema (schema @schemas))]
     (reduce (fn [acc f] (let [invalid (validate-field coll f schema stop-on-first-error)]
                           (if-not (empty? invalid)
                             (conj acc invalid)
                             acc))) [] (keys schema)))))
