; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.daemon
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [cider-ci.utils.with :as with]
    ))


(defmacro define [daemon-name start-fn stop-fn secs-pause & body]
  (let [stop (gensym "_stop_")]
    `(do 

       (defonce ~stop (atom (fn [])))

       (defn ~stop-fn []
         (@~stop))

       (defn ~start-fn []
         (~stop-fn)
         (let [done# (atom false)
               runner# (future (logging/info "daemon " ~daemon-name " started")
                               (loop []
                                 (when-not @done#
                                   (with/suppress-and-log-error
                                     ~@body)
                                   (Thread/sleep (Math/ceil (* ~secs-pause 1000)))
                                   (recur))))]
           (reset! ~stop (fn []
                           (reset! done# true)
                           (future-cancel runner#)
                           ;@runner#
                           (logging/info "daemon " ~daemon-name "stopped"))))))))


;(macroexpand-1 '(define "Blah" start stop 10 (logging/info "looping ...")))

