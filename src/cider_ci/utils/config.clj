; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.utils.config
  (:require
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.duration :refer [parse-string-to-seconds]]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.io :as io]
    [clojure.set :refer [difference]]
    [me.raynes.fs :as clj-fs]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(defonce ^:private conf (atom {}))
(defn get-config [] @conf)

(defonce ^:private opts (atom
                          {:overrides {}
                           :filenames [(system-path ".." "config" "config_default.yml")
                                       (system-path "config" "config_default.yml")
                                       (system-path ".." "config" "config.yml")
                                       (system-path "config" "config.yml")]}))
(defn get-opts [] @opts)

;##############################################################################

(defn merge-in-config [params]
  (when-not (= (get-config)
               (deep-merge (get-config) params))
    (let [new-config (swap! conf
                            (fn [current-config params]
                              (deep-merge current-config params))
                            params)]
      (logging/info "config changed to " new-config))))


(defn read-configs-and-merge-in []
  (loop [config (get-config)
         filenames (:filenames @opts)]
    (if-let [filename (first filenames)]
      (if (.exists (io/as-file filename))
        (recur (try (let [add-on-string (slurp filename)
                          add-on (yaml/parse-string add-on-string)]
                      (deep-merge config add-on))
                    (catch Exception e
                      (logging/warn "Failed to read " filename " because " e)
                      config))
               (rest filenames))
        (recur config (rest filenames)))
      (merge-in-config (deep-merge config (:overrides @opts))))))


(defdaemon "reload-config" 1 (read-configs-and-merge-in))

;### Initialize ###############################################################

(defn initialize [options]

  (assert
    (empty?
      (difference (-> options keys set)
                  (-> @opts keys set)))
    "Opts must only contain the same keys as default-opts")

  (let [options (deep-merge @opts options)]
    (reset! opts options)
    (read-configs-and-merge-in)
    (start-reload-config)))


;### DB #######################################################################

(defn get-db-spec [service]
  (let [conf (get-config)]
    (deep-merge
      (or (-> conf :database ) {} )
      (or (-> conf :services service :database ) {} ))))


;### duration #################################################################

(defn parse-config-duration-to-seconds [& ks]
  (try (if-let [duration-config-value (-> (get-config) (get-in ks))]
         (parse-string-to-seconds duration-config-value)
         (logging/warn (str "No value to parse duration for " ks " was found.")))
       (catch Exception ex
         (cond (instance? clojure.lang.IExceptionInfo ex) (throw ex)
               :else (throw (ex-info "Duration parsing error."
                                     {:config-keys ks} ex))))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
