; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.http
  (:require
    [cider-ci.utils.url.shared :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(def pattern
  #"(?i)(https?)://([^@]+@)?([^/]+)([^\?|#]+)(\?[^#]+)?(#.*)?" )

(defn dissect-basic-http-url [url]
  (as-> url url
    (re-matches pattern url)
    {:protocol (-> url (nth 1) clojure.string/lower-case)
     :authentication_with_at (nth url 2)
     :host_port (nth url 3)
     :path (nth url 4)
     :query (nth url 5)
     :fragment_with_hash (nth url 6)
     :parts url
     :url (nth url 0)}))

(defn dissect [url]
  (as-> url url
    (dissect-basic-http-url url)
    (merge url (host-port-dissect (:host_port url)))
    (merge url (auth-dissect (:authentication_with_at url)))
    (merge url (path-dissect (:path url)))))
