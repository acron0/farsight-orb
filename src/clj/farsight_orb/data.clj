(ns farsight-orb.data
  (:require [lolesports.core :as lolesp]))

(def player-list
  "Initial payload"
  lolesp/tier-1-player-names)

(defn player-data [flags]
  (let [flags (set flags)
        players lolesp/tier-1-players]
    (map-indexed (fn [i player]
                   (println i)
                   (let [{:keys [team name role playerId]} player
                         name-with-tag (lolesp/create-player-tag player)
                         stats (lolesp/get-player-stats player)]
                     (hash-map :name name-with-tag :stats stats))) players)))
