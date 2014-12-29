(ns cider-ci.utils.rdbms
  (:require 
    [cider-ci.utils.rdbms.conversion :as conversion]
    [cider-ci.utils.with :as with]
    [clj-postgresql.types]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    )
  (:import 
    [com.mchange.v2.c3p0 ComboPooledDataSource DataSources]
    ))


(defonce ^:private db-spec (atom nil))
(defn get-db-spec [] @db-spec)

(defonce ^:private ds (atom nil))
(defn get-ds [] @ds)

(defonce ^:private tables-metadata (atom nil))
(defn get-tables-metadata [] @tables-metadata)

(defn- get-columns-metadata [table-name db-conf]
  (jdbc/with-db-metadata [md db-conf]
    (into {} (sort 
               (map (fn [column-data]
                      [(keyword (:column_name column-data))
                       column-data])
                    (jdbc/metadata-result 
                      (.getColumns md nil nil table-name "")))))))

(defn- set-tables-metadata [db-conf]
  (reset! 
    tables-metadata
    (into {} (sort 
               (jdbc/with-db-metadata [md db-conf]
                 (map
                   (fn [table-data]
                     [(keyword (:table_name table-data))
                      (conj table-data
                            {:columns (get-columns-metadata 
                                        (:table_name table-data) db-conf)})])
                   (jdbc/metadata-result 
                     (.getTables md nil nil nil (into-array ["TABLE" "VIEW"])))
                   ))))))


(defn check-connection 
  "Performs a simple query an returns boolean true on success and
  false otherwise."
  []
  (try 
    (with/logging 
      (->> (jdbc/query (get-ds) ["SELECT true AS state"]) 
           first :state))
    (catch Exception _
      false)))


(defn- create-c3p0-datasources [db-conf]
  (logging/debug create-c3p0-datasources [db-conf])
  (reset! ds 
          {:datasource 
           (doto (ComboPooledDataSource.)
             (.setJdbcUrl (str "jdbc:" (:subprotocol db-conf) ":" (:subname db-conf)))
             (#(when-let [user (:user db-conf)] (.setUser % user)))
             (#(when-let [password (:password db-conf)] (.setPassword % password)))
             (#(when-let [max-pool-size (:max_pool_size db-conf)](.setMaxPoolSize % max-pool-size))))})
  )


(defn amend-db-conf [db-conf]
  (let [user (or (:user db-conf)  
                 (System/getenv "PGUSER"))
        password (or 
                   (:password db-conf)
                   (System/getenv "PGPASSWORD"))]
    (conj db-conf
          {:user user
           :password password})))

(defn reset []
  (reset! db-spec nil)
  (when @ds (.hardReset (:datasource @ds)))
  (reset! ds nil)
  (reset! tables-metadata nil))

(defn initialize [_db-conf]
  (let [db-conf (amend-db-conf _db-conf)]
    (logging/info initialize [db-conf])
    (reset! db-spec db-conf)
    (create-c3p0-datasources db-conf)
    (set-tables-metadata db-conf)
    (conversion/initialize @tables-metadata)))


;(initialize {:subprotocol "sqlite" :subname ":memory:"})
