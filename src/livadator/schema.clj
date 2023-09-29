(ns livadator.schema)

(def ^:dynamic *schemas* {})

(defn schema-schema
  [validate-schema]
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
                                (not (nil? validators))))}})

(defn register-schema
  [alias schema]
  (if-not (contains? *schemas* alias)
    (alter-var-root *schemas* assoc schema)
    *schemas*))

(defn unregister-schema
  [alias]
  (alter-var-root *schemas* dissoc alias))

(defn unregister-all
  []
  (alter-var-root *schemas* (fn [_] {})))

(defn find-schema
  [schema]
  (schema *schemas*))