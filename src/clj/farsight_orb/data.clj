(ns farsight-orb.data
  (:require [clj-lolapi.esports :as lolesp]))

(defn initial-payload
  "Initial payload"
  []
  (lolesp/tournaments))
