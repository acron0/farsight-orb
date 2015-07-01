(ns lolesports.core
  (:require [clj-lolapi.esports :as esports]
            [clojure.string :as str]))

(def roles
  {:role/adc     "AD Carry"
   :role/top     "Top Lane"
   :role/jungle  "Jungler"
   :role/mid     "Mid Lane"
   :role/support "Support"})

(def tier-1-tournament-keys
  "Tier 1 tournaments that we really care about..."
  [:tourney231
   :tourney230
   :tourney229
   :tourney226
   :tourney225])

(def tier-1-tournaments
  "The tier 1 tournaments"
  (map (fn [x] (assoc (val x) :tournamentId (read-string (str/replace (name (key x)) "tourney" ""))))
     (select-keys (esports/tournaments) tier-1-tournament-keys)))

(def tier-1-teams
  "The tier 1 teams"
  (mapcat (fn [x]
            (map (fn [y] (assoc y :tournamentId (:tournamentId x))) (-> x :contestants vals)))
          tier-1-tournaments))

(defn get-teams
  "returns the roster of a team"
  [tournament]
    (map #(assoc % :tournamentId (:tournamentId tournament)) (-> tournament :contestants vals)))

(defn get-roster
  "returns the roster of a team"
  ([team]
    (-> team :id esports/team :roster vals))
  ([team role]
    (filter #(= (:role %) (role roles)) (get-roster team))))

(def tier-1-players
  "The players in every tier 1 team"
  (mapcat (fn [team]
          (map (fn [player]
                 (assoc player :team (:acronym team))) (get-roster team))) tier-1-teams))

(def tier-1-player-names
  "The players formatted to just return their name and team tag"
  (mapv (fn [player] (str (:team player) " " (:name player))) tier-1-players))

(defn get-player-stats
  "returnd the stats of a player"
  ([player]
    (:tournaments (esports/player-stats (:playerId player))))
  ([player tournament]
   (first (filter #(= (:tournamentId tournament) (:tournamentId %)) (get-player-stats player)))))

(defn get-tournament-players
  "returns all players in a tournament"
  ([tournament]
    (mapcat get-roster (get-teams tournament)))
  ([tournament role]
    (filter #(= (:role %) (role roles)) (get-tournament-players tournament))))
